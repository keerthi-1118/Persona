package com.persona.controller;

import com.persona.db.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.*;

/**
 * Persona — Habits Controller
 * Replaces: backend/routes/habits.py
 */
@RestController
@RequestMapping("/api/habits")
public class HabitsController {

    private final Database db;

    @Autowired
    public HabitsController(Database db) { this.db = db; }

    @GetMapping("/")
    public ResponseEntity<?> getHabits(HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        List<Map<String, Object>> habits = new ArrayList<>(
            db.query("SELECT * FROM habits WHERE user_id = ? ORDER BY created_at DESC", uid)
        );
        String today = db.todayStr();

        for (Map<String, Object> h : habits) {
            String hid = str(h.get("id"));
            List<Map<String, Object>> logs = db.query(
                "SELECT id FROM habit_logs WHERE habit_id=? AND date=? AND completed=TRUE", hid, today);
            h.put("done_today", !logs.isEmpty());
            h.put("streak",     calcStreak(hid));
        }
        return ResponseEntity.ok(habits);
    }

    @PostMapping("/")
    public ResponseEntity<?> createHabit(@RequestBody Map<String, Object> data, HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        if (str(data.get("name")).isEmpty()) return err(400, "name is required");

        String hid = db.newId();
        db.execute(
            "INSERT INTO habits (id, user_id, name, icon, color, target_days, created_at) VALUES (?,?,?,?,?,?,?)",
            hid, uid,
            data.get("name"),
            str(data.getOrDefault("icon", "⭐")),
            str(data.getOrDefault("color", "#6C63FF")),
            str(data.getOrDefault("target_days", "[\"mon\",\"tue\",\"wed\",\"thu\",\"fri\",\"sat\",\"sun\"]")),
            db.nowIso()
        );

        List<Map<String, Object>> rows = db.query("SELECT * FROM habits WHERE id = ?", hid);
        return ResponseEntity.status(201).body(rows.isEmpty() ? Map.of("id", hid) : rows.get(0));
    }

    @DeleteMapping("/{hid}")
    public ResponseEntity<?> deleteHabit(@PathVariable String hid, HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");
        db.execute("DELETE FROM habits WHERE id=? AND user_id=?", hid, uid);
        return ResponseEntity.ok(Map.of("message", "Habit deleted"));
    }

    @PatchMapping("/{hid}/check")
    public ResponseEntity<?> checkHabit(@PathVariable String hid, HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        String today = db.todayStr();
        List<Map<String, Object>> logs = db.query(
            "SELECT id, completed FROM habit_logs WHERE habit_id=? AND date=?", hid, today);

        boolean done;
        if (!logs.isEmpty()) {
            Map<String, Object> log = logs.get(0);
            Object completedVal = log.get("completed");
            boolean currentDone = completedVal instanceof Boolean ? (Boolean) completedVal
                : !"false".equalsIgnoreCase(String.valueOf(completedVal)) && !"0".equals(String.valueOf(completedVal));
            boolean newDone = !currentDone;
            db.execute("UPDATE habit_logs SET completed=? WHERE id=?", newDone, str(log.get("id")));
            done = newDone;
        } else {
            db.execute(
                "INSERT INTO habit_logs (id, habit_id, user_id, date, completed) VALUES (?,?,?,?,?)",
                db.newId(), hid, uid, today, true
            );
            done = true;
        }

        return ResponseEntity.ok(Map.of("done_today", done, "streak", calcStreak(hid)));
    }

    // ── Helpers ───────────────────────────────────────────────
    private int calcStreak(String habitId) {
        List<Map<String, Object>> logs = db.query(
            "SELECT date FROM habit_logs WHERE habit_id=? AND completed=TRUE ORDER BY date DESC", habitId);
        if (logs.isEmpty()) return 0;

        Set<String> dates = new HashSet<>();
        for (Map<String, Object> row : logs) {
            String d = str(row.get("date"));
            if (d.length() >= 10) dates.add(d.substring(0, 10));
        }

        int streak = 0;
        LocalDate check = LocalDate.now();
        while (dates.contains(check.toString())) {
            streak++;
            check = check.minusDays(1);
        }
        return streak;
    }

    private String uid(HttpSession s) { return (String) s.getAttribute("user_id"); }
    private String str(Object o)  { return o == null ? "" : o.toString(); }
    private int toInt(Object o)   {
        if (o == null) return 0;
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; }
    }
    private ResponseEntity<Map<String, Object>> err(int status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }
}

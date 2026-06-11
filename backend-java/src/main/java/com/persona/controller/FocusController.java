package com.persona.controller;

import com.persona.db.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.*;

/**
 * Persona — Focus Sessions Controller (Pomodoro tracking)
 * Replaces: backend/routes/focus.py
 */
@RestController
@RequestMapping("/api/focus")
public class FocusController {

    private final Database db;

    @Autowired
    public FocusController(Database db) { this.db = db; }

    @GetMapping("/")
    public ResponseEntity<?> getSessions(
        @RequestParam(required = false) String date,
        HttpSession session
    ) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");
        if (date == null || date.isEmpty()) date = db.todayStr();

        return ResponseEntity.ok(db.query(
            "SELECT * FROM focus_sessions WHERE user_id=? AND started_at::date = CAST(? AS date) ORDER BY started_at DESC",
            uid, date
        ));
    }

    @PostMapping("/start")
    public ResponseEntity<?> startSession(@RequestBody Map<String, Object> data, HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        String sid = db.newId();
        String now = db.nowIso();
        db.execute(
            "INSERT INTO focus_sessions (id, user_id, task_id, duration, type, started_at, completed, created_at) VALUES (?,?,?,?,?,?,?,?)",
            sid, uid,
            data.get("task_id"),
            toInt(data.getOrDefault("duration", 25)),
            str(data.getOrDefault("type", "focus")),
            now, false, now
        );

        return ResponseEntity.status(201).body(Map.of("session_id", sid, "started_at", now));
    }

    @PatchMapping("/{sid}/end")
    public ResponseEntity<?> endSession(@PathVariable String sid,
                                        @RequestBody Map<String, Object> data,
                                        HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        Object completedObj = data.getOrDefault("completed", true);
        boolean completed = completedObj instanceof Boolean ? (Boolean) completedObj
            : !"false".equalsIgnoreCase(String.valueOf(completedObj)) && !"0".equals(String.valueOf(completedObj));

        db.execute("UPDATE focus_sessions SET ended_at=?, completed=? WHERE id=? AND user_id=?",
            db.nowIso(), completed, sid, uid);
        return ResponseEntity.ok(Map.of("message", "Session ended"));
    }

    @GetMapping("/today-summary")
    public ResponseEntity<?> todaySummary(HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        String today = db.todayStr();
        List<Map<String, Object>> rows = db.query(
            "SELECT SUM(duration) as total_mins, COUNT(*) as sessions FROM focus_sessions " +
            "WHERE user_id=? AND started_at::date = CAST(? AS date) AND completed=TRUE AND type='focus'",
            uid, today
        );

        Map<String, Object> r = rows.isEmpty() ? new HashMap<>() : rows.get(0);
        return ResponseEntity.ok(Map.of(
            "total_minutes", r.getOrDefault("total_mins", 0),
            "sessions",      r.getOrDefault("sessions", 0)
        ));
    }

    // ── Helpers ───────────────────────────────────────────────
    private String uid(HttpSession s) { return (String) s.getAttribute("user_id"); }
    private String str(Object o)  { return o == null ? "" : o.toString(); }
    private int toInt(Object o)   {
        if (o == null) return 0;
        try { return (int) Double.parseDouble(o.toString()); } catch (Exception e) { return 0; }
    }
    private ResponseEntity<Map<String, Object>> err(int status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }
}

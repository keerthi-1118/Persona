package com.persona.controller;

import com.persona.db.Database;
import com.persona.service.SchedulerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.*;

/**
 * Persona — Scheduler Controller
 * Replaces: backend/routes/scheduler.py  +  backend/routes/planner.py
 */
@RestController
public class SchedulerController {

    private final Database db;
    private final SchedulerService schedulerService;
    private final com.persona.service.AiSchedulerService aiSchedulerService;

    @Autowired
    public SchedulerController(Database db, SchedulerService schedulerService, com.persona.service.AiSchedulerService aiSchedulerService) {
        this.db = db;
        this.schedulerService = schedulerService;
        this.aiSchedulerService = aiSchedulerService;
    }

    // POST /api/scheduler/auto-schedule
    @PostMapping("/api/scheduler/auto-schedule")
    public ResponseEntity<?> autoSchedule(HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");
        
        Map<String, Object> result;
        try {
            result = aiSchedulerService.generateSmartSchedule(uid);
        } catch (Exception e) {
            System.err.println("Gemini Scheduler failed, falling back to rule-based: " + e.getMessage());
            result = schedulerService.autoScheduleAll(uid);
        }

        return ResponseEntity.ok(Map.of(
            "scheduled", result.getOrDefault("scheduled", List.of()),
            "message",   String.valueOf(((List<?>) result.getOrDefault("scheduled", List.of())).size()) + " tasks scheduled"
        ));
    }

    // POST /api/planner/generate
    @PostMapping("/api/planner/generate")
    public ResponseEntity<?> generateSchedule(HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        List<Map<String, Object>> allTasks = db.query(
            "SELECT * FROM tasks WHERE user_id=? AND status IN ('pending','in_progress')", uid);

        if (allTasks.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                "explanation", "No pending tasks to schedule! Add some tasks first.",
                "scheduled",   List.of(),
                "tips",        List.of()
            ));
        }

        try {
            Map<String, Object> result;
            try {
                result = aiSchedulerService.generateSmartSchedule(uid);
            } catch (Exception e) {
                System.err.println("Gemini Planner failed, falling back to rule-based: " + e.getMessage());
                result = schedulerService.autoScheduleAll(uid);
            }
            List<?> scheduled = (List<?>) result.getOrDefault("scheduled", List.of());
            return ResponseEntity.ok(Map.of(
                "message",     "Smart Planner scheduled " + scheduled.size() + " tasks!",
                "explanation", result.getOrDefault("explanation", "Your tasks have been scheduled."),
                "tips",        result.getOrDefault("tips", List.of()),
                "scheduled",   scheduled
            ));
        } catch (Exception e) {
            return err(500, e.getMessage());
        }
    }

    // GET /api/planner/reminders
    @GetMapping("/api/planner/reminders")
    public ResponseEntity<?> getReminders(HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        String now  = db.nowIso();
        String soon = Instant.now().plusSeconds(3600).toString().replace("Z", "").substring(0, 23);

        List<Map<String, Object>> tasks = db.query(
            "SELECT id, title, start_time, end_time, priority FROM tasks " +
            "WHERE user_id=? AND status='pending' AND start_time >= ? AND start_time <= ?",
            uid, now, soon
        );
        return ResponseEntity.ok(tasks);
    }

    // ── Helpers ───────────────────────────────────────────────
    private String uid(HttpSession s) { return (String) s.getAttribute("user_id"); }
    private ResponseEntity<Map<String, Object>> err(int status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }
}

package com.persona.controller;

import com.persona.db.Database;
import com.persona.service.AiSchedulerService;
import com.persona.service.SchedulerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.*;

/**
 * Persona — AI Chat & Scheduling Controller
 * Replaces: backend/routes/ai_chat.py
 */
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final Database db;
    private final SchedulerService schedulerService;
    private final AiSchedulerService aiSchedulerService;

    @Autowired
    public AiChatController(Database db, SchedulerService schedulerService, AiSchedulerService aiSchedulerService) {
        this.db = db;
        this.schedulerService = schedulerService;
        this.aiSchedulerService = aiSchedulerService;
    }

    // POST /api/ai/schedule — trigger smart schedule
    @PostMapping("/schedule")
    public ResponseEntity<?> aiSchedule(HttpSession session) {
        String uid = uid(session);
        if (uid == null) uid = "guest";

        try {
            Map<String, Object> result;
            try {
                result = aiSchedulerService.generateSmartSchedule(uid);
            } catch (Exception e) {
                System.err.println("Gemini AI Schedule failed, falling back to rule-based: " + e.getMessage());
                result = schedulerService.autoScheduleAll(uid);
            }
            List<?> scheduled = (List<?>) result.getOrDefault("scheduled", List.of());
            return ResponseEntity.ok(Map.of(
                "message",     "Smart Scheduler allocated " + scheduled.size() + " tasks!",
                "explanation", result.getOrDefault("explanation", "Your tasks have been scheduled."),
                "tips",        result.getOrDefault("tips", List.of()),
                "scheduled",   scheduled
            ));
        } catch (Exception e) {
            return err(500, e.getMessage());
        }
    }

    // POST /api/ai/chat — free-form AI chat
    @PostMapping("/chat")
    public ResponseEntity<?> aiChat(@RequestBody Map<String, Object> data, HttpSession session) {
        String uid     = uid(session);
        if (uid == null) uid = "guest";
        String message = str(data.get("message")).strip();

        if (message.isEmpty()) return err(400, "Message is required");

        List<Map<String, Object>> tasks = db.query(
            "SELECT title, priority, end_time as due_date, status FROM tasks " +
            "WHERE user_id=? AND status != 'completed' LIMIT 10", uid
        );

        try {
            Map<String, Object> result = aiSchedulerService.chatWithAi(message, Map.of("tasks", tasks));
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(503).body(Map.of("reply", e.getMessage()));
        } catch (Exception e) {
            return err(500, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────
    private String uid(HttpSession s) { return (String) s.getAttribute("user_id"); }
    private String str(Object o)  { return o == null ? "" : o.toString(); }
    private ResponseEntity<Map<String, Object>> err(int status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }
}

package com.persona.controller;

import com.persona.db.Database;
import com.persona.service.WebPushService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.*;

/**
 * Persona — Push Notifications Controller
 * Replaces: backend/routes/push.py
 */
@RestController
@RequestMapping("/api/push")
public class PushController {

    private final Database db;
    private final WebPushService webPushService;

    @Value("${persona.vapid-public-key:}")
    private String vapidPublicKey;

    @Autowired
    public PushController(Database db, WebPushService webPushService) {
        this.db = db;
        this.webPushService = webPushService;
    }

    @GetMapping("/vapid-public-key")
    public ResponseEntity<?> getVapidPublicKey() {
        return ResponseEntity.ok(Map.of("public_key", vapidPublicKey));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@RequestBody Map<String, Object> sub, HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        String endpoint = str(sub.get("endpoint"));
        if (endpoint.isEmpty()) return err(400, "endpoint is required");

        @SuppressWarnings("unchecked")
        Map<String, Object> keys = (Map<String, Object>) sub.getOrDefault("keys", Map.of());
        String p256dh  = str(keys.get("p256dh"));
        String authKey = str(keys.get("auth"));

        List<Map<String, Object>> existing = db.query(
            "SELECT id FROM push_subscriptions WHERE user_id=? AND endpoint=?", uid, endpoint);

        if (!existing.isEmpty()) {
            db.execute("UPDATE push_subscriptions SET p256dh=?, auth=?, updated_at=? WHERE id=?",
                p256dh, authKey, db.nowIso(), str(existing.get(0).get("id")));
        } else {
            db.execute(
                "INSERT INTO push_subscriptions (id, user_id, endpoint, p256dh, auth, updated_at) VALUES (?,?,?,?,?,?)",
                db.newId(), uid, endpoint, p256dh, authKey, db.nowIso()
            );
        }
        return ResponseEntity.ok(Map.of("message", "Subscribed to push notifications"));
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribe(@RequestBody Map<String, Object> data, HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");
        String endpoint = str(data.get("endpoint"));
        if (!endpoint.isEmpty()) {
            db.execute("DELETE FROM push_subscriptions WHERE user_id=? AND endpoint=?", uid, endpoint);
        }
        return ResponseEntity.ok(Map.of("message", "Unsubscribed"));
    }

    @PostMapping("/send-reminder")
    public ResponseEntity<?> sendReminder(@RequestBody Map<String, Object> data, HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        String title = str(data.getOrDefault("title", "Persona Reminder"));
        String body  = str(data.getOrDefault("body", ""));
        String url   = str(data.getOrDefault("url", "/planner"));

        List<Map<String, Object>> subs = db.query("SELECT * FROM push_subscriptions WHERE user_id=?", uid);
        if (subs.isEmpty()) return ResponseEntity.ok(Map.of("message", "No subscriptions found"));

        int sent = 0;
        List<String> errors = new ArrayList<>();
        for (Map<String, Object> sub : subs) {
            try {
                webPushService.sendNotification(
                    str(sub.get("endpoint")),
                    str(sub.get("p256dh")),
                    str(sub.get("auth")),
                    title, body, url
                );
                sent++;
            } catch (Exception e) {
                errors.add(e.getMessage());
            }
        }
        return ResponseEntity.ok(Map.of("sent", sent, "errors", errors));
    }

    // ── Helpers ───────────────────────────────────────────────
    private String uid(HttpSession s) { return (String) s.getAttribute("user_id"); }
    private String str(Object o)  { return o == null ? "" : o.toString(); }
    private ResponseEntity<Map<String, Object>> err(int status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }
}

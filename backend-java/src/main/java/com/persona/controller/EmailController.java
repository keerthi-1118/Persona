package com.persona.controller;

import com.persona.service.EmailSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import java.util.*;

@RestController
@RequestMapping("/api/email")
public class EmailController {

    private final EmailSyncService emailSyncService;

    @Autowired
    public EmailController(EmailSyncService emailSyncService) {
        this.emailSyncService = emailSyncService;
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(HttpSession session) {
        String uid = uid(session);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        boolean linked = emailSyncService.isGoogleLinked(uid);
        Map<String, Object> filterConfig = emailSyncService.getOrCreateFilterConfig(uid);
        return ResponseEntity.ok(Map.of("linked", linked, "config", filterConfig));
    }

    @PostMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestBody Map<String, Object> data, HttpSession session) {
        String uid = uid(session);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        emailSyncService.updateFilterConfig(uid, data);
        return ResponseEntity.ok(Map.of("message", "Configuration updated"));
    }

    @GetMapping("/summaries")
    public ResponseEntity<?> getSummaries(HttpSession session) {
        String uid = uid(session);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        List<Map<String, Object>> summaries = emailSyncService.getRecentSummaries(uid);
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/unnotified")
    public ResponseEntity<?> getUnnotified(HttpSession session) {
        String uid = uid(session);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        List<Map<String, Object>> unnotified = emailSyncService.getUnnotifiedSummaries(uid);
        return ResponseEntity.ok(unnotified);
    }

    @PostMapping("/sync")
    public ResponseEntity<?> syncEmails(HttpSession session) {
        String uid = uid(session);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        try {
            int count = emailSyncService.syncAndAnalyzeEmails(uid);
            return ResponseEntity.ok(Map.of("message", "Synced " + count + " important emails successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    private String uid(HttpSession s) { return (String) s.getAttribute("user_id"); }
}

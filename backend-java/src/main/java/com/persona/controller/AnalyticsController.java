package com.persona.controller;

import com.persona.db.Database;
import com.persona.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

/**
 * Persona — Analytics Controller
 * Replaces: backend/routes/analytics.py
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @Autowired
    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/")
    public ResponseEntity<?> dashboardAnalytics(
        @RequestParam(defaultValue = "week") String period,
        HttpSession session
    ) {
        String uid = (String) session.getAttribute("user_id");
        if (uid == null) return ResponseEntity.status(401).body(java.util.Map.of("error", "Authentication required"));
        return ResponseEntity.ok(analyticsService.getAnalytics(uid, period));
    }
}

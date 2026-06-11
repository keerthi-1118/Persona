package com.persona.controller;

import com.persona.db.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Debug endpoint — shows system health, DB connectivity, and Gemini API status.
 * Accessible at GET /api/debug/db and GET /api/debug/gemini
 */
@RestController
public class DebugController {

    private final Database db;

    @Value("${spring.datasource.url:}")
    private String dbUrl;

    @Value("${persona.gemini-api-key:}")
    private String geminiApiKey;

    @Autowired
    public DebugController(Database db) {
        this.db = db;
    }

    /** Database health check — confirms PostgreSQL connectivity and tables. */
    @GetMapping("/api/debug/db")
    public Map<String, Object> debugDb() {
        Map<String, Object> info = new HashMap<>();
        info.put("datasource_url", dbUrl.replaceAll("password=[^&]*", "password=***"));
        info.put("database_type", "PostgreSQL");

        // Test query on users table
        try {
            List<Map<String, Object>> rows = db.query("SELECT COUNT(*) AS cnt FROM users");
            long count = rows.isEmpty() ? 0 : ((Number) rows.get(0).get("cnt")).longValue();
            info.put("users_table", "OK");
            info.put("users_count", count);
        } catch (Exception e) {
            info.put("users_table", "FAILED: " + e.getMessage());
        }

        // Test query on timetable table
        try {
            db.query("SELECT COUNT(*) FROM timetable");
            info.put("timetable_table", "OK");
        } catch (Exception e) {
            info.put("timetable_table", "FAILED: " + e.getMessage());
        }

        // Test query on tasks table
        try {
            db.query("SELECT COUNT(*) FROM tasks");
            info.put("tasks_table", "OK");
        } catch (Exception e) {
            info.put("tasks_table", "FAILED: " + e.getMessage());
        }

        // Test query on expenses table
        try {
            db.query("SELECT COUNT(*) FROM expenses");
            info.put("expenses_table", "OK");
        } catch (Exception e) {
            info.put("expenses_table", "FAILED: " + e.getMessage());
        }

        info.put("status", "Database health check complete");
        return info;
    }

    /** Gemini API connectivity check. */
    @GetMapping("/api/debug/gemini")
    public Map<String, Object> debugGemini() {
        Map<String, Object> info = new HashMap<>();

        boolean keySet = geminiApiKey != null
            && !geminiApiKey.isEmpty()
            && !geminiApiKey.equals("YOUR_GEMINI_API_KEY_HERE");

        info.put("gemini_key_configured", keySet);
        if (!keySet) {
            info.put("status", "MISSING — set PERSONA_GEMINI_API_KEY env variable on Render");
            return info;
        }

        // Ping Gemini with a minimal test prompt
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;
            String body = "{\"contents\":[{\"parts\":[{\"text\":\"Reply with just: OK\"}]}]}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            info.put("gemini_http_status", resp.statusCode());

            if (resp.statusCode() == 200) {
                info.put("status", "OK — Gemini API is working correctly");
            } else {
                info.put("status", "ERROR — Gemini returned: " + resp.body());
            }
        } catch (Exception e) {
            info.put("status", "ERROR — Could not reach Gemini: " + e.getMessage());
        }

        return info;
    }
}

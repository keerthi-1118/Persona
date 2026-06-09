package com.persona.controller;

import com.persona.db.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

@RestController
public class DebugController {

    private final Database db;

    @Autowired
    public DebugController(Database db) {
        this.db = db;
    }

    @GetMapping("/api/debug/db")
    public Map<String, Object> debugDb() {
        try {
            db.query("SELECT * FROM users LIMIT 1");
            return Map.of("status", "OK", "message", "Users table exists and query succeeded!");
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            return Map.of(
                "status", "ERROR",
                "message", e.getMessage() != null ? e.getMessage() : "Unknown error",
                "stackTrace", sw.toString()
            );
        }
    }
}

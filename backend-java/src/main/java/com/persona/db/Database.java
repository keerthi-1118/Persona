package com.persona.db;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persona — Database Utility
 * Replaces: backend/database.py
 *
 * Central wrapper around JdbcTemplate (SQLite).
 * Provides: query(), execute(), newId(), nowIso(), initDb()
 */
@Component
public class Database {

    private final JdbcTemplate jdbc;

    @Autowired
    public Database(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Execute a SELECT and return list of row-maps. */
    public List<Map<String, Object>> query(String sql, Object... params) {
        return jdbc.queryForList(sql, params);
    }

    /** Execute INSERT / UPDATE / DELETE. */
    public void execute(String sql, Object... params) {
        jdbc.update(sql, params);
    }

    /** Generate a UUID hex string (no dashes) — same as Python new_id(). */
    public String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** UTC ISO timestamp string — same as Python now_iso(). */
    public String nowIso() {
        String s = Instant.now().toString().replace("Z", "");
        return s.length() > 23 ? s.substring(0, 23) : s;
    }

    /** Today's date as YYYY-MM-DD string. */
    public String todayStr() {
        return java.time.LocalDate.now().toString();
    }

    /**
     * Initialize SQLite database from schema.sql on startup.
     * Replaces the init_db() call at bottom of database.py.
     */
    @PostConstruct
    public void initDb() {
        try {
            ClassPathResource resource = new ClassPathResource("schema.sql");
            String script = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // Enable foreign keys for SQLite
            jdbc.execute("PRAGMA foreign_keys = ON");

            // Split and execute each statement individually (SQLite doesn't support executescript via JDBC)
            for (String stmt : script.split(";")) {
                String trimmed = stmt.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                    try {
                        jdbc.execute(trimmed);
                    } catch (Exception e) {
                        // Ignore "already exists" errors from IF NOT EXISTS — they're expected
                    }
                }
            }

            // Dynamic migration: add assignment_id column to tasks table if it is missing
            try {
                jdbc.execute("ALTER TABLE tasks ADD COLUMN assignment_id TEXT");
                System.out.println("[OK] Dynamically added assignment_id column to tasks table.");
            } catch (Exception ignored) {
                // Column might already exist, ignore this error
            }

            System.out.println("[OK] SQLite Database Initialized.");
        } catch (IOException e) {
            System.err.println("[WARN] Could not load schema.sql: " + e.getMessage());
        }
    }

    /** Get the underlying JdbcTemplate (for advanced use). */
    public JdbcTemplate getJdbc() {
        return jdbc;
    }
}

package com.persona.db;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persona — Database Utility (PostgreSQL Only)
 *
 * Central wrapper around JdbcTemplate for PostgreSQL.
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
     * Initialize PostgreSQL database from schema_postgres.sql on startup.
     * Each SQL statement is executed individually, separated by semicolons.
     * "Already exists" errors are safely ignored so restarts are idempotent.
     */
    @PostConstruct
    public void initDb() {
        try {
            System.out.println("[Database] Connecting to PostgreSQL...");

            ClassPathResource resource = new ClassPathResource("schema_postgres.sql");
            String script = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // Split by semicolons and execute each statement individually
            String[] parts = script.split(";");
            int executed = 0;
            for (String part : parts) {
                // Strip comment lines
                StringBuilder sb = new StringBuilder();
                for (String line : part.split("\n")) {
                    if (!line.trim().startsWith("--")) {
                        sb.append(line).append("\n");
                    }
                }
                String stmt = sb.toString().trim();
                if (stmt.isEmpty()) continue;

                try {
                    jdbc.execute(stmt);
                    executed++;
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    // Ignore safe errors: already exists, duplicate, etc.
                    if (msg.contains("already exists") || msg.contains("duplicate")) {
                        // expected on restart — skip silently
                    } else {
                        System.err.println("[Database] Warning on: "
                            + stmt.substring(0, Math.min(60, stmt.length()))
                            + " => " + e.getMessage());
                    }
                }
            }

            // Ensure assignment_id column exists (safe for existing databases)
            try {
                jdbc.execute("ALTER TABLE tasks ADD COLUMN IF NOT EXISTS assignment_id TEXT");
            } catch (Exception ignored) {}

            System.out.println("[OK] PostgreSQL initialized. Ran " + executed + " statements.");
        } catch (Exception e) {
            System.err.println("[ERROR] Database initialization failed!");
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize PostgreSQL database", e);
        }
    }

    /** Get the underlying JdbcTemplate (for advanced use). */
    public JdbcTemplate getJdbc() {
        return jdbc;
    }
}

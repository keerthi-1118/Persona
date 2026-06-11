package com.persona;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Persona PWA — Spring Boot Application Entry Point
 * Replaces: backend/app.py
 */
@SpringBootApplication
@EnableScheduling
public class PersonaApplication {

    public static void main(String[] args) {
        // ── Fix Render DATABASE_URL format before Spring starts ──────────
        // Render provides: postgresql://user:pass@host/db
        // Spring Boot needs: jdbc:postgresql://user:pass@host/db
        // We convert and store as PERSONA_JDBC_URL to avoid self-reference issues
        fixDatabaseUrl();

        SpringApplication.run(PersonaApplication.class, args);
        System.out.println("\n  Persona PWA (Java / Spring Boot)");
        System.out.println("  Frontend : http://localhost:5000");
        System.out.println("  API      : http://localhost:5000/api/health\n");
    }

    private static void fixDatabaseUrl() {
        // Try DATABASE_URL (Render sets this automatically when you link a PostgreSQL db)
        String dbUrl = System.getenv("DATABASE_URL");
        if (dbUrl != null && !dbUrl.isEmpty()) {
            String jdbcUrl = dbUrl.startsWith("jdbc:") ? dbUrl : "jdbc:" + dbUrl;
            // Store as a NEW property name to avoid self-referential loops
            System.setProperty("PERSONA_JDBC_URL", jdbcUrl);
            System.out.println("[DB] DATABASE_URL converted: " + jdbcUrl.replaceAll(":[^:@]+@", ":***@"));
            return;
        }

        // Try SPRING_DATASOURCE_URL env var (manual override)
        String springUrl = System.getenv("SPRING_DATASOURCE_URL");
        if (springUrl != null && !springUrl.isEmpty()) {
            String jdbcUrl = springUrl.startsWith("jdbc:") ? springUrl : "jdbc:" + springUrl;
            System.setProperty("PERSONA_JDBC_URL", jdbcUrl);
            System.out.println("[DB] SPRING_DATASOURCE_URL converted: " + jdbcUrl.replaceAll(":[^:@]+@", ":***@"));
        }
    }
}

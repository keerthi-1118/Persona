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
        fixDatabaseUrl();

        SpringApplication.run(PersonaApplication.class, args);
        System.out.println("\n  Persona PWA (Java / Spring Boot)");
        System.out.println("  Frontend : http://localhost:5000");
        System.out.println("  API      : http://localhost:5000/api/health\n");
    }

    private static void fixDatabaseUrl() {
        // Only fix if SPRING_DATASOURCE_URL is not already manually set
        String alreadySet = System.getProperty("spring.datasource.url");
        if (alreadySet != null && !alreadySet.isEmpty()) return;

        // Try DATABASE_URL (Render sets this automatically when you link a PostgreSQL db)
        String dbUrl = System.getenv("DATABASE_URL");
        if (dbUrl != null && !dbUrl.isEmpty()) {
            if (!dbUrl.startsWith("jdbc:")) {
                dbUrl = "jdbc:" + dbUrl;
            }
            System.setProperty("spring.datasource.url", dbUrl);
            System.out.println("[DB] DATABASE_URL detected and converted to JDBC format.");
            return;
        }

        // Try SPRING_DATASOURCE_URL env var
        String springUrl = System.getenv("SPRING_DATASOURCE_URL");
        if (springUrl != null && !springUrl.isEmpty()) {
            if (!springUrl.startsWith("jdbc:")) {
                springUrl = "jdbc:" + springUrl;
            }
            System.setProperty("spring.datasource.url", springUrl);
            System.out.println("[DB] SPRING_DATASOURCE_URL detected and set.");
        }
    }
}

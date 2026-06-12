package com.persona.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Collections;

/**
 * Runs before ANY Spring beans or properties are processed.
 *
 * Render.com provides DATABASE_URL as:
 *   postgresql://user:pass@host/db   (no "jdbc:" prefix, sometimes no port)
 *
 * Spring Boot JDBC needs:
 *   jdbc:postgresql://user:pass@host:5432/db
 *
 * This class fixes the URL automatically at the lowest possible level.
 */
public class DatabaseUrlPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        // Priority 1: SPRING_DATASOURCE_URL or spring.datasource.url set externally
        String springUrl = env.getProperty("SPRING_DATASOURCE_URL");
        if (springUrl == null || springUrl.isBlank()) {
            springUrl = env.getProperty("spring.datasource.url");
            // If it is the default localhost fallback, ignore it so we don't rewrite it
            if (springUrl != null && springUrl.contains("localhost:5432/persona")) {
                springUrl = null;
            }
        }
        
        if (springUrl != null && !springUrl.isBlank()) {
            setDatasourceUrl(env, fixUrl(springUrl), "spring.datasource.url");
            return;
        }

        // Priority 2: DATABASE_URL (auto-set by Render when PostgreSQL is linked)
        String dbUrl = env.getProperty("DATABASE_URL");
        if (dbUrl != null && !dbUrl.isBlank()) {
            setDatasourceUrl(env, fixUrl(dbUrl), "DATABASE_URL");
        }
    }

    /**
     * Converts postgresql:// or postgres:// → jdbc:postgresql://
     * Also adds port :5432 if missing (Render internal URLs omit the port).
     */
    private static String fixUrl(String url) {
        // Step 1: ensure jdbc: prefix
        if (!url.startsWith("jdbc:")) {
            url = "jdbc:" + url;
        }

        // Step 2: replace jdbc:postgres:// → jdbc:postgresql:// (common variant)
        url = url.replace("jdbc:postgres://", "jdbc:postgresql://");

        // Step 3: add :5432 port if missing
        // Pattern: jdbc:postgresql://user:pass@host/db  (no port between host and /)
        // We need to check after the @ symbol
        try {
            int atIdx = url.lastIndexOf('@');
            if (atIdx != -1) {
                String afterAt = url.substring(atIdx + 1); // e.g. "host/db"
                if (!afterAt.contains(":")) {
                    // No port found after @ — insert :5432 before the database path
                    int slashIdx = afterAt.indexOf('/');
                    if (slashIdx != -1) {
                        String host = afterAt.substring(0, slashIdx);
                        String rest = afterAt.substring(slashIdx);
                        url = url.substring(0, atIdx + 1) + host + ":5432" + rest;
                    }
                }
            }
        } catch (Exception ignored) {}

        return url;
    }

    private static void setDatasourceUrl(ConfigurableEnvironment env, String jdbcUrl, String source) {
        System.out.println("[DB] " + source + " → converted to: "
            + jdbcUrl.replaceAll(":[^:@/]+@", ":***@"));
        env.getPropertySources().addFirst(
            new MapPropertySource("renderdDatabaseUrl",
                Collections.singletonMap("spring.datasource.url", jdbcUrl))
        );
    }
}

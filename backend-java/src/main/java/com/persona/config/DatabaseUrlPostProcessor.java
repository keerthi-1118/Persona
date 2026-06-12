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
     * Extracts user and password from authority section (user:pass@host) and appends as query params.
     */
    private static String fixUrl(String url) {
        try {
            // Strip any protocol prefixes to make parsing uniform
            String temp = url;
            if (temp.startsWith("jdbc:")) {
                temp = temp.substring(5);
            }
            if (temp.startsWith("postgresql://")) {
                temp = temp.substring(13);
            } else if (temp.startsWith("postgres://")) {
                temp = temp.substring(11);
            }

            // Check if there are credentials in the URL (contains '@')
            int atIdx = temp.lastIndexOf('@');
            if (atIdx != -1) {
                String credentials = temp.substring(0, atIdx);
                String hostAndDb = temp.substring(atIdx + 1);

                String user = "";
                String pass = "";
                int colonIdx = credentials.indexOf(':');
                if (colonIdx != -1) {
                    user = credentials.substring(0, colonIdx);
                    pass = credentials.substring(colonIdx + 1);
                } else {
                    user = credentials;
                }

                String hostAndPort = "";
                String dbName = "";
                int slashIdx = hostAndDb.indexOf('/');
                if (slashIdx != -1) {
                    hostAndPort = hostAndDb.substring(0, slashIdx);
                    dbName = hostAndDb.substring(slashIdx + 1);
                } else {
                    hostAndPort = hostAndDb;
                }

                // Add port 5432 if missing
                if (!hostAndPort.contains(":")) {
                    hostAndPort = hostAndPort + ":5432";
                }

                // Remove any existing query parameters from dbName to handle cleanly
                int queryIdx = dbName.indexOf('?');
                String queryParams = "";
                if (queryIdx != -1) {
                    queryParams = dbName.substring(queryIdx);
                    dbName = dbName.substring(0, queryIdx);
                }

                // Reconstruct clean JDBC URL
                String resolvedUrl = "jdbc:postgresql://" + hostAndPort + "/" + dbName;
                
                // Append credentials as query parameters
                StringBuilder sb = new StringBuilder(resolvedUrl);
                sb.append("?user=").append(user);
                if (!pass.isEmpty()) {
                    sb.append("&password=").append(pass);
                }
                
                // Append any existing parameters
                if (!queryParams.isEmpty()) {
                    sb.append("&").append(queryParams.substring(1)); // strip leading ?
                }

                return sb.toString();
            }
        } catch (Exception e) {
            System.err.println("[DB] Warning: Failed parsing database URL: " + e.getMessage() + ". Falling back to original URL.");
        }

        // Fallback to basic prefix addition if no '@' exists
        if (!url.startsWith("jdbc:")) {
            url = "jdbc:" + url;
        }
        url = url.replace("jdbc:postgres://", "jdbc:postgresql://");
        return url;
    }

    private static void setDatasourceUrl(ConfigurableEnvironment env, String jdbcUrl, String source) {
        System.out.println("[DB] " + source + " → converted to: "
            + jdbcUrl.replaceAll("password=[^&]*", "password=***"));
        env.getPropertySources().addFirst(
            new MapPropertySource("renderdDatabaseUrl",
                Collections.singletonMap("spring.datasource.url", jdbcUrl))
        );
    }
}

package com.persona.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * DataSource Configuration
 *
 * Handles the Render.com DATABASE_URL format mismatch.
 * Render provides: postgresql://user:pass@host:port/db
 * Spring Boot needs: jdbc:postgresql://user:pass@host:port/db
 *
 * This bean automatically converts the URL format if needed.
 */
@Configuration
public class DataSourceConfig {

    // Reads SPRING_DATASOURCE_URL first (manual override)
    @Value("${SPRING_DATASOURCE_URL:}")
    private String springDatasourceUrl;

    // Reads DATABASE_URL (auto-set by Render when you link a PostgreSQL db)
    @Value("${DATABASE_URL:}")
    private String databaseUrl;

    @Value("${SPRING_DATASOURCE_USERNAME:${DB_USER:}}")
    private String username;

    @Value("${SPRING_DATASOURCE_PASSWORD:${DB_PASSWORD:}}")
    private String password;

    @Value("${SPRING_DATASOURCE_MAX_POOL:5}")
    private int maxPoolSize;

    @Bean
    @Primary
    public DataSource dataSource() {
        String jdbcUrl = resolveJdbcUrl();
        System.out.println("[DataSource] Connecting to: " + maskPassword(jdbcUrl));

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(maxPoolSize);
        config.setConnectionTimeout(20000);
        config.setIdleTimeout(300000);
        config.setConnectionTestQuery("SELECT 1");

        // Parse username/password from the URL if not set separately
        if (username != null && !username.isEmpty()) {
            config.setUsername(username);
        }
        if (password != null && !password.isEmpty()) {
            config.setPassword(password);
        }

        return new HikariDataSource(config);
    }

    private String resolveJdbcUrl() {
        // Priority 1: Explicit SPRING_DATASOURCE_URL (already jdbc:postgresql://...)
        if (springDatasourceUrl != null && !springDatasourceUrl.isEmpty()) {
            return ensureJdbcPrefix(springDatasourceUrl);
        }

        // Priority 2: Render's DATABASE_URL (may be postgresql:// without jdbc: prefix)
        if (databaseUrl != null && !databaseUrl.isEmpty()) {
            return ensureJdbcPrefix(databaseUrl);
        }

        // Fallback: local development
        System.out.println("[DataSource] WARNING: No DATABASE_URL found. Using localhost fallback.");
        return "jdbc:postgresql://localhost:5432/persona";
    }

    /**
     * Converts postgresql:// → jdbc:postgresql://
     * Leaves jdbc:postgresql:// unchanged.
     */
    private String ensureJdbcPrefix(String url) {
        if (url.startsWith("jdbc:")) {
            return url; // already correct
        }
        if (url.startsWith("postgresql://") || url.startsWith("postgres://")) {
            // Render uses postgresql:// or postgres:// — add jdbc: prefix
            return "jdbc:" + url;
        }
        return url;
    }

    private String maskPassword(String url) {
        if (url == null) return "null";
        return url.replaceAll(":[^:@]+@", ":***@");
    }
}

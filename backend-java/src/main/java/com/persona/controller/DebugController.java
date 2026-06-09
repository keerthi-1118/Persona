package com.persona.controller;

import com.persona.db.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
public class DebugController {

    private final Database db;

    @Value("${spring.datasource.url:}")
    private String dbUrl;

    @Autowired
    public DebugController(Database db) {
        this.db = db;
    }

    @GetMapping("/api/debug/db")
    public Map<String, Object> debugDb() {
        Map<String, Object> debugInfo = new HashMap<>();
        debugInfo.put("spring_datasource_url", dbUrl);

        // 1. Check SQLite file location
        try {
            if (dbUrl != null && dbUrl.startsWith("jdbc:sqlite:")) {
                String path = dbUrl.substring("jdbc:sqlite:".length());
                File dbFile = new File(path);
                debugInfo.put("db_file_absolute_path", dbFile.getAbsolutePath());
                debugInfo.put("db_file_exists", dbFile.exists());
                debugInfo.put("db_file_size_bytes", dbFile.exists() ? dbFile.length() : 0);
                debugInfo.put("db_file_writeable", dbFile.getParentFile() != null ? dbFile.getParentFile().canWrite() : "N/A");
            }
        } catch (Exception e) {
            debugInfo.put("db_file_check_error", e.getMessage());
        }

        // 2. Check schema.sql resource
        try {
            ClassPathResource resource = new ClassPathResource("schema.sql");
            debugInfo.put("schema_resource_exists", resource.exists());
            if (resource.exists()) {
                String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                debugInfo.put("schema_size_chars", content.length());
                debugInfo.put("schema_snippet", content.substring(0, Math.min(content.length(), 200)));
            }
        } catch (Exception e) {
            debugInfo.put("schema_check_error", e.getMessage());
        }

        // 3. Try creating a table manually
        try {
            db.execute("CREATE TABLE IF NOT EXISTS test_debug (id TEXT PRIMARY KEY)");
            debugInfo.put("manual_table_create", "SUCCESS");
            db.execute("DROP TABLE test_debug");
        } catch (Exception e) {
            debugInfo.put("manual_table_create", "FAILED: " + e.getMessage());
        }

        // 4. Try running schema initialization manually
        try {
            ClassPathResource resource = new ClassPathResource("schema.sql");
            if (resource.exists()) {
                String script = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int count = 0;
                int failed = 0;
                StringBuilder errors = new StringBuilder();
                for (String stmt : script.split(";")) {
                    String trimmed = stmt.strip();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                        try {
                            db.execute(trimmed);
                            count++;
                        } catch (Exception ex) {
                            failed++;
                            errors.append("Stmt [").append(trimmed.substring(0, Math.min(trimmed.length(), 40))).append("...] failed: ").append(ex.getMessage()).append("\n");
                        }
                    }
                }
                debugInfo.put("manual_schema_init_statements_executed", count);
                debugInfo.put("manual_schema_init_statements_failed", failed);
                debugInfo.put("manual_schema_init_errors", errors.toString());
            }
        } catch (Exception e) {
            debugInfo.put("manual_schema_init_error", e.getMessage());
        }

        // 5. Query users table again
        try {
            db.query("SELECT * FROM users LIMIT 1");
            debugInfo.put("query_users_table", "SUCCESS");
        } catch (Exception e) {
            debugInfo.put("query_users_table", "FAILED: " + e.getMessage());
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            debugInfo.put("query_users_table_stacktrace", sw.toString());
        }

        return debugInfo;
    }
}

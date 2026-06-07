package com.persona.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.persona.db.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.Instant;
import java.time.Duration;
import java.util.*;

@Service
public class ClassroomApiService {

    private final Database db;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Value("${persona.google-client-id:}")
    private String googleClientId;

    @Value("${persona.google-client-secret:}")
    private String googleClientSecret;

    @Autowired
    public ClassroomApiService(Database db) {
        this.db = db;
    }

    private String getToken(String uid) {
        return getOrRefreshToken(uid);
    }

    private String getOrRefreshToken(String uid) {
        List<Map<String, Object>> rows = db.query(
            "SELECT access_token, refresh_token, updated_at FROM oauth_tokens WHERE user_id=?", uid
        );
        if (rows.isEmpty()) return null;

        String accessToken = (String) rows.get(0).get("access_token");
        String refreshToken = (String) rows.get(0).get("refresh_token");
        String updatedAtStr = (String) rows.get(0).get("updated_at");

        boolean isExpired = false;
        try {
            if (updatedAtStr != null) {
                // The nowIso() formats timestamps like 2026-05-30T10:06:12.345, missing the "Z"
                Instant updatedAt = Instant.parse(updatedAtStr + "Z");
                Instant now = Instant.now();
                if (Duration.between(updatedAt, now).toMinutes() > 50) {
                    isExpired = true;
                }
            } else {
                isExpired = true;
            }
        } catch (Exception e) {
            isExpired = true;
        }

        if (isExpired && refreshToken != null && !refreshToken.isEmpty()) {
            System.out.println("Google access token for user " + uid + " is expired. Attempting refresh...");
            try {
                String refreshedToken = performTokenRefresh(uid, refreshToken);
                if (refreshedToken != null) {
                    return refreshedToken;
                }
            } catch (Exception e) {
                System.err.println("Failed to refresh Google token: " + e.getMessage());
            }
        }

        return accessToken;
    }

    private String performTokenRefresh(String uid, String refreshToken) throws Exception {
        if (googleClientId == null || googleClientId.isEmpty() || googleClientSecret == null || googleClientSecret.isEmpty()) {
            System.err.println("Cannot refresh token: Client ID/Secret not configured");
            return null;
        }

        String body = "client_id=" + java.net.URLEncoder.encode(googleClientId, java.nio.charset.StandardCharsets.UTF_8) +
            "&client_secret=" + java.net.URLEncoder.encode(googleClientSecret, java.nio.charset.StandardCharsets.UTF_8) +
            "&refresh_token=" + java.net.URLEncoder.encode(refreshToken, java.nio.charset.StandardCharsets.UTF_8) +
            "&grant_type=refresh_token";

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://oauth2.googleapis.com/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) {
            Map<String, Object> tokenData = mapper.readValue(resp.body(), Map.class);
            String newAccessToken = (String) tokenData.get("access_token");
            String newRefreshToken = (String) tokenData.getOrDefault("refresh_token", refreshToken);

            db.execute(
                "UPDATE oauth_tokens SET access_token=?, refresh_token=?, updated_at=? WHERE user_id=?",
                newAccessToken, newRefreshToken, db.nowIso(), uid
            );
            System.out.println("Google access token successfully refreshed for user: " + uid);
            return newAccessToken;
        } else {
            System.err.println("Google token refresh endpoint returned status " + resp.statusCode() + ": " + resp.body());
        }
        return null;
    }

    public int syncAssignments(String uid) throws Exception {
        String token = getToken(uid);
        if (token == null) {
            throw new RuntimeException("No Google token found. Please connect Google Classroom first.");
        }

        int count = 0;
        
        HttpRequest coursesReq = HttpRequest.newBuilder()
            .uri(URI.create("https://classroom.googleapis.com/v1/courses?courseStates=ACTIVE"))
            .header("Authorization", "Bearer " + token)
            .GET().build();

        HttpResponse<String> coursesResp = client.send(coursesReq, HttpResponse.BodyHandlers.ofString());
        if (coursesResp.statusCode() != 200) {
            throw new Exception("Failed to fetch courses: " + coursesResp.body());
        }

        Map<String, Object> coursesData = mapper.readValue(coursesResp.body(), Map.class);
        List<Map<String, Object>> courses = (List<Map<String, Object>>) coursesData.getOrDefault("courses", List.of());

        String todayStr = LocalDate.now().toString();

        for (Map<String, Object> course : courses) {
            String courseId = (String) course.get("id");
            String courseName = (String) course.getOrDefault("name", "Unknown Course");

            HttpRequest cwReq = HttpRequest.newBuilder()
                .uri(URI.create("https://classroom.googleapis.com/v1/courses/" + courseId + "/courseWork"))
                .header("Authorization", "Bearer " + token)
                .GET().build();

            HttpResponse<String> cwResp = client.send(cwReq, HttpResponse.BodyHandlers.ofString());
            if (cwResp.statusCode() != 200) continue;

            Map<String, Object> cwData = mapper.readValue(cwResp.body(), Map.class);
            List<Map<String, Object>> courseworks = (List<Map<String, Object>>) cwData.getOrDefault("courseWork", List.of());

            for (Map<String, Object> cw : courseworks) {
                String gwId = (String) cw.get("id");
                String title = (String) cw.getOrDefault("title", "Untitled");
                String desc = (String) cw.getOrDefault("description", "");
                String link = (String) cw.getOrDefault("alternateLink", "");
                
                String dueDateStr = null;
                if (cw.containsKey("dueDate")) {
                    Map<String, Object> d = (Map<String, Object>) cw.get("dueDate");
                    int year = (Integer) d.getOrDefault("year", LocalDate.now().getYear());
                    int month = (Integer) d.getOrDefault("month", 1);
                    int day = (Integer) d.getOrDefault("day", 1);
                    dueDateStr = String.format("%04d-%02d-%02d", year, month, day);

                    if (dueDateStr.compareTo(todayStr) < 0) continue; // Skip past due
                } else {
                    continue; // Skip no due date
                }

                List<Map<String, Object>> existing = db.query(
                    "SELECT id FROM assignments WHERE course_id=? AND assignment_id=? AND user_id=?",
                    courseId, gwId, uid
                );

                if (!existing.isEmpty()) {
                    String aid = (String) existing.get(0).get("id");
                    db.execute(
                        "UPDATE assignments SET title=?, description=?, due_date=?, link=?, updated_at=? WHERE id=?",
                        title, desc, dueDateStr, link, db.nowIso(), aid
                    );
                    // Update corresponding task if it exists, or create if not
                    List<Map<String, Object>> existingTask = db.query("SELECT id FROM tasks WHERE assignment_id=?", aid);
                    if (!existingTask.isEmpty()) {
                        String taskTitle = courseName + ": " + title;
                        db.execute(
                            "UPDATE tasks SET title=?, description=?, end_time=?, updated_at=? WHERE id=?",
                            taskTitle, desc, dueDateStr, db.nowIso(), existingTask.get(0).get("id")
                        );
                    } else {
                        try {
                            String tid = db.newId();
                            String taskTitle = courseName + ": " + title;
                            db.execute(
                                "INSERT INTO tasks (id, user_id, title, description, priority, status, category, assignment_id, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                                tid, uid, taskTitle, desc, "high", "pending", "assignment", aid, db.nowIso(), db.nowIso()
                            );
                        } catch (Exception e) {
                            System.out.println("Warning: Failed to auto-create task for assignment " + e.getMessage());
                        }
                    }
                } else {
                    String aid = db.newId();
                    db.execute(
                        "INSERT INTO assignments (id, user_id, course_name, course_id, assignment_id, title, description, due_date, link, source, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                        aid, uid, courseName, courseId, gwId, title, desc, dueDateStr, link, "google_classroom", db.nowIso(), db.nowIso()
                    );
                    
                    try {
                        String tid = db.newId();
                        String taskTitle = courseName + ": " + title;
                        db.execute(
                            "INSERT INTO tasks (id, user_id, title, description, priority, status, category, assignment_id, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                            tid, uid, taskTitle, desc, "high", "pending", "assignment", aid, db.nowIso(), db.nowIso()
                        );
                    } catch (Exception e) {
                        System.out.println("Warning: Failed to auto-create task for assignment " + e.getMessage());
                    }
                }
                count++;
            }
        }
        return count;
    }
}

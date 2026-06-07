package com.persona.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.persona.db.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.Duration;
import java.util.*;

@Service
public class EmailSyncService {

    private final Database db;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Value("${persona.gemini-api-key:}")
    private String geminiApiKey;

    @Autowired
    public EmailSyncService(Database db) {
        this.db = db;
    }

    @PostConstruct
    public void init() {
        // Create necessary tables if not exist
        try {
            db.execute("CREATE TABLE IF NOT EXISTS email_filters (\n" +
                "    id          TEXT PRIMARY KEY,\n" +
                "    user_id     TEXT NOT NULL UNIQUE,\n" +
                "    senders     TEXT DEFAULT '[]',\n" +
                "    domains     TEXT DEFAULT '[]',\n" +
                "    keywords    TEXT DEFAULT '[]',\n" +
                "    enabled     INTEGER DEFAULT 1,\n" +
                "    last_synced_at TEXT\n" +
                ")");
            db.execute("CREATE TABLE IF NOT EXISTS email_summaries (\n" +
                "    id          TEXT PRIMARY KEY,\n" +
                "    user_id     TEXT NOT NULL,\n" +
                "    email_id    TEXT NOT NULL,\n" +
                "    sender      TEXT,\n" +
                "    subject     TEXT,\n" +
                "    summary     TEXT,\n" +
                "    action_taken TEXT,\n" +
                "    created_at  TEXT DEFAULT CURRENT_TIMESTAMP\n" +
                ")");
            // Alter columns dynamically for schema updates
            try { db.execute("ALTER TABLE email_summaries ADD COLUMN matched_keywords TEXT"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE email_summaries ADD COLUMN is_important INTEGER DEFAULT 1"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE email_summaries ADD COLUMN notified INTEGER DEFAULT 0"); } catch (Exception ignored) {}
            System.out.println("[OK] Email Tables Initialized.");
        } catch (Exception e) {
            System.err.println("Warning: Email tables initialization error: " + e.getMessage());
        }
    }

    public boolean isGoogleLinked(String uid) {
        List<Map<String, Object>> rows = db.query("SELECT id FROM oauth_tokens WHERE user_id=?", uid);
        return !rows.isEmpty();
    }

    public Map<String, Object> getOrCreateFilterConfig(String uid) {
        List<Map<String, Object>> rows = db.query("SELECT * FROM email_filters WHERE user_id=?", uid);
        if (rows.isEmpty()) {
            String fid = db.newId();
            String defaultSenders = "[]";
            String defaultDomains = "[]";
            String defaultKeywords = "[{\"text\":\"placement\",\"enabled\":true},{\"text\":\"internship\",\"enabled\":true},{\"text\":\"interview\",\"enabled\":true},{\"text\":\"deadline\",\"enabled\":true},{\"text\":\"job\",\"enabled\":true},{\"text\":\"crcs\",\"enabled\":true}]";
            db.execute("INSERT INTO email_filters (id, user_id, senders, domains, keywords, enabled) VALUES (?,?,?,?,?,?)",
                fid, uid, defaultSenders, defaultDomains, defaultKeywords, 1);
            return Map.of("senders", List.of(), "domains", List.of(), "keywords", List.of(
                Map.of("text", "placement", "enabled", true),
                Map.of("text", "internship", "enabled", true),
                Map.of("text", "interview", "enabled", true),
                Map.of("text", "deadline", "enabled", true),
                Map.of("text", "job", "enabled", true),
                Map.of("text", "crcs", "enabled", true)
            ), "enabled", 1);
        }
        Map<String, Object> r = rows.get(0);
        try {
            List<String> senders = mapper.readValue((String) r.get("senders"), List.class);
            List<String> domains = mapper.readValue((String) r.get("domains"), List.class);
            List<String> keywords = mapper.readValue((String) r.get("keywords"), List.class);
            return Map.of(
                "senders", senders,
                "domains", domains,
                "keywords", keywords,
                "enabled", r.get("enabled")
            );
        } catch (Exception e) {
            return Map.of("senders", List.of(), "domains", List.of(), "keywords", List.of(), "enabled", 1);
        }
    }

    public void updateFilterConfig(String uid, Map<String, Object> data) {
        try {
            String sendersStr = mapper.writeValueAsString(data.getOrDefault("senders", List.of()));
            String domainsStr = mapper.writeValueAsString(data.getOrDefault("domains", List.of()));
            String keywordsStr = mapper.writeValueAsString(data.getOrDefault("keywords", List.of()));
            int enabled = toInt(data.getOrDefault("enabled", 1));

            db.execute("UPDATE email_filters SET senders=?, domains=?, keywords=?, enabled=? WHERE user_id=?",
                sendersStr, domainsStr, keywordsStr, enabled, uid);
        } catch (Exception e) {
            System.err.println("Error updating filter configuration: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getRecentSummaries(String uid) {
        return db.query("SELECT * FROM email_summaries WHERE user_id=? ORDER BY created_at DESC LIMIT 15", uid);
    }

    public List<Map<String, Object>> getUnnotifiedSummaries(String uid) {
        try {
            List<Map<String, Object>> rows = db.query("SELECT * FROM email_summaries WHERE user_id=? AND notified=0 ORDER BY created_at DESC", uid);
            db.execute("UPDATE email_summaries SET notified=1 WHERE user_id=? AND notified=0", uid);
            return rows;
        } catch (Exception e) {
            System.err.println("Error fetching unnotified summaries: " + e.getMessage());
            return List.of();
        }
    }

    public int syncAndAnalyzeEmails(String uid) throws Exception {
        if (!isGoogleLinked(uid)) {
            throw new RuntimeException("Google Classroom/Gmail account not connected. Please link Google in the Assignments screen.");
        }

        String token = getGoogleToken(uid);
        if (token == null) {
            throw new RuntimeException("Google link expired. Please re-link Google first.");
        }

        Map<String, Object> config = getOrCreateFilterConfig(uid);
        if (toInt(config.get("enabled")) == 0) {
            return 0; // disabled
        }

        List<String> filterSenders = (List<String>) config.get("senders");
        List<String> filterDomains = (List<String>) config.get("domains");
        List<Object> filterKeywordsRaw = (List<Object>) config.get("keywords");

        // Parse keywords that could be objects ({"text": "Placement", "enabled": true}) or plain strings
        List<String> activeKeywords = new ArrayList<>();
        if (filterKeywordsRaw != null) {
            for (Object obj : filterKeywordsRaw) {
                if (obj instanceof String) {
                    activeKeywords.add((String) obj);
                } else if (obj instanceof Map) {
                    Map<String, Object> kwMap = (Map<String, Object>) obj;
                    boolean enabled = toInt(kwMap.get("enabled")) != 0;
                    if (enabled) {
                        String text = (String) kwMap.get("text");
                        if (text != null && !text.trim().isEmpty()) {
                            activeKeywords.add(text.trim());
                        }
                    }
                }
            }
        }

        // List messages (fetch max 15 unread messages for safety)
        String listUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=15&q=is:unread";
        HttpRequest listReq = HttpRequest.newBuilder()
            .uri(URI.create(listUrl))
            .header("Authorization", "Bearer " + token)
            .GET().build();

        HttpResponse<String> listResp = client.send(listReq, HttpResponse.BodyHandlers.ofString());
        if (listResp.statusCode() != 200) {
            throw new Exception("Gmail API Error: " + listResp.body());
        }

        Map<String, Object> listMap = mapper.readValue(listResp.body(), Map.class);
        List<Map<String, Object>> messagesList = (List<Map<String, Object>>) listMap.getOrDefault("messages", List.of());

        int count = 0;
        for (Map<String, Object> mMsg : messagesList) {
            String msgId = (String) mMsg.get("id");

            // Check if already processed
            List<Map<String, Object>> existing = db.query("SELECT id FROM email_summaries WHERE user_id=? AND email_id=?", uid, msgId);
            if (!existing.isEmpty()) continue;

            // Fetch full message
            String msgUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages/" + msgId;
            HttpRequest msgReq = HttpRequest.newBuilder()
                .uri(URI.create(msgUrl))
                .header("Authorization", "Bearer " + token)
                .GET().build();

            HttpResponse<String> msgResp = client.send(msgReq, HttpResponse.BodyHandlers.ofString());
            if (msgResp.statusCode() != 200) continue;

            Map<String, Object> messageMap = mapper.readValue(msgResp.body(), Map.class);
            Map<String, Object> payload = (Map<String, Object>) messageMap.get("payload");
            if (payload == null) continue;

            String from = "";
            String subject = "";
            String date = "";
            List<Map<String, String>> headers = (List<Map<String, String>>) payload.get("headers");
            if (headers != null) {
                for (Map<String, String> h : headers) {
                    if ("From".equalsIgnoreCase(h.get("name"))) from = h.get("value");
                    if ("Subject".equalsIgnoreCase(h.get("name"))) subject = h.get("value");
                    if ("Date".equalsIgnoreCase(h.get("name"))) date = h.get("value");
                }
            }

            String snippet = (String) messageMap.getOrDefault("snippet", "");
            
            // Apply filtering logic: match case-insensitively and collect matched terms
            List<String> matchedTerms = new ArrayList<>();
            String lowerFrom = from.toLowerCase();
            String lowerSubject = subject.toLowerCase();
            String lowerSnippet = snippet.toLowerCase();

            // 1. Check senders
            if (filterSenders != null) {
                for (String sender : filterSenders) {
                    String term = sender.trim();
                    if (!term.isEmpty() && lowerFrom.contains(term.toLowerCase())) {
                        matchedTerms.add(term);
                    }
                }
            }
            // 2. Check domains
            if (filterDomains != null) {
                for (String domain : filterDomains) {
                    String term = domain.trim();
                    if (!term.isEmpty() && lowerFrom.contains(term.toLowerCase())) {
                        matchedTerms.add(term);
                    }
                }
            }
            // 3. Check keywords/categories (placement, internship, etc.)
            for (String keyword : activeKeywords) {
                String term = keyword.trim();
                if (!term.isEmpty() && (lowerSubject.contains(term.toLowerCase()) || lowerSnippet.contains(term.toLowerCase()))) {
                    matchedTerms.add(term);
                }
            }

            boolean isImportant = !matchedTerms.isEmpty();
            if (!isImportant) continue; // Skip non-important email

            // Mark matched email as important in Gmail via API
            markEmailAsImportantInGmail(token, msgId);

            // Send to Gemini to summarize and extract tasks
            Map<String, Object> aiResult = analyzeEmailWithGemini(from, subject, date, snippet);
            if (aiResult == null) continue;

            String summaryText = (String) aiResult.getOrDefault("summary", "Summarization failed.");
            boolean createTask = (Boolean) aiResult.getOrDefault("createTask", false);
            String taskTitle = (String) aiResult.get("taskTitle");
            String taskDesc = (String) aiResult.get("taskDescription");
            String taskStartTime = (String) aiResult.get("taskStartTime");

            String actionTaken = "none";
            if (createTask && taskTitle != null && !taskTitle.trim().isEmpty()) {
                String tid = db.newId();
                db.execute("INSERT INTO tasks (id, user_id, title, description, start_time, priority, status, category, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    tid, uid, "[AI Email] " + taskTitle, taskDesc != null ? taskDesc : summaryText,
                    taskStartTime, "high", "pending", "reminder", db.nowIso(), db.nowIso());
                
                actionTaken = "created_task: " + taskTitle;
            }

            // Save summary along with matched terms, marking it as important (1) and unnotified (0)
            String sid = db.newId();
            String matchedKeywordsJson = mapper.writeValueAsString(matchedTerms);
            db.execute("INSERT INTO email_summaries (id, user_id, email_id, sender, subject, summary, action_taken, matched_keywords, is_important, notified) VALUES (?,?,?,?,?,?,?,?,?,?)",
                sid, uid, msgId, from, subject, summaryText, actionTaken, matchedKeywordsJson, 1, 0);

            count++;
        }

        db.execute("UPDATE email_filters SET last_synced_at=? WHERE user_id=?", db.nowIso(), uid);
        return count;
    }

    private Map<String, Object> analyzeEmailWithGemini(String sender, String subject, String date, String snippet) {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            return null;
        }

        String prompt = "You are a financial and student advisor AI assistant for a student productivity app.\n" +
            "Analyze the following student email to determine if it is important, generate a 1-sentence summary, and detect if a task/deadline should be created.\n" +
            "From: " + sender + "\n" +
            "Subject: " + subject + "\n" +
            "Date: " + date + "\n" +
            "Snippet: " + snippet + "\n\n" +
            "Please provide a JSON response in the following exact format, with nothing else:\n" +
            "{\n" +
            "  \"summary\": \"A short 1-sentence summary of this email (e.g. 'Placement drive for TCS is open, submit application before June 5th.')\",\n" +
            "  \"createTask\": true or false,\n" +
            "  \"taskTitle\": \"A clean title for the task (if createTask is true, e.g. 'TCS Application Deadline')\",\n" +
            "  \"taskDescription\": \"A short description/todo for the task (if createTask is true)\",\n" +
            "  \"taskStartTime\": \"The start time/deadline of the task formatted as ISO 8601 (YYYY-MM-DDTHH:MM:00) if a date/time is mentioned in the email, otherwise null\"\n" +
            "}";

        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;
            Map<String, Object> parts = Map.of("text", prompt);
            Map<String, Object> payload = Map.of(
                "contents", List.of(Map.of("parts", List.of(parts))),
                "generationConfig", Map.of(
                    "temperature", 0.1,
                    "responseMimeType", "application/json"
                )
            );

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                Map<String, Object> data = mapper.readValue(resp.body(), Map.class);
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) data.get("candidates");
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> partsList = (List<Map<String, Object>>) content.get("parts");
                String raw = ((String) partsList.get(0).get("text")).trim();

                if (raw.contains("```json")) {
                    raw = raw.split("```json")[1].split("```")[0].trim();
                } else if (raw.contains("```")) {
                    raw = raw.split("```")[1].split("```")[0].trim();
                }

                return mapper.readValue(raw, Map.class);
            }
        } catch (Exception e) {
            System.err.println("Gemini analysis of email failed: " + e.getMessage());
        }
        return null;
    }

    private String getGoogleToken(String uid) {
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
            System.out.println("Refreshing Google access token in Email Sync service...");
            try {
                String refreshed = performTokenRefresh(uid, refreshToken);
                if (refreshed != null) return refreshed;
            } catch (Exception e) {
                System.err.println("Failed to refresh token in Email service: " + e.getMessage());
            }
        }

        return accessToken;
    }

    @Value("${persona.google-client-id:}")
    private String googleClientId;

    @Value("${persona.google-client-secret:}")
    private String googleClientSecret;

    private String performTokenRefresh(String uid, String refreshToken) throws Exception {
        if (googleClientId == null || googleClientId.isEmpty() || googleClientSecret == null || googleClientSecret.isEmpty()) {
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
            return newAccessToken;
        }
        return null;
    }

    private void markEmailAsImportantInGmail(String token, String msgId) {
        try {
            String url = "https://gmail.googleapis.com/gmail/v1/users/me/messages/" + msgId + "/modify";
            Map<String, Object> body = Map.of("addLabelIds", List.of("IMPORTANT"));
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                System.out.println("[EmailSync] Successfully marked email " + msgId + " as IMPORTANT in Gmail.");
            } else {
                System.err.println("[EmailSync] Failed to mark email as important in Gmail: " + resp.body());
            }
        } catch (Exception e) {
            System.err.println("[EmailSync] Error calling Gmail modify API: " + e.getMessage());
        }
    }

    public List<String> getAllLinkedUserIds() {
        try {
            List<Map<String, Object>> rows = db.query("SELECT DISTINCT user_id FROM oauth_tokens");
            List<String> uids = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                uids.add((String) r.get("user_id"));
            }
            return uids;
        } catch (Exception e) {
            return List.of();
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 300000)
    public void scheduledSync() {
        System.out.println("[EmailSyncScheduler] Starting scheduled background email sync...");
        try {
            List<String> userIds = getAllLinkedUserIds();
            for (String uid : userIds) {
                try {
                    int count = syncAndAnalyzeEmails(uid);
                    if (count > 0) {
                        System.out.println("[EmailSyncScheduler] Scheduled sync: Found " + count + " new matches for user " + uid);
                    }
                } catch (Exception e) {
                    System.err.println("[EmailSyncScheduler] Background email sync failed for user " + uid + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[EmailSyncScheduler] Error in scheduled background email sync: " + e.getMessage());
        }
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Boolean) return (Boolean) o ? 1 : 0;
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; }
    }
}

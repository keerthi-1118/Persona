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
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AiSchedulerService {

    @Value("${persona.gemini-api-key:}")
    private String geminiApiKey;

    private final Database db;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Autowired
    public AiSchedulerService(Database db) {
        this.db = db;
    }

    private String generateContent(String prompt) throws Exception {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            throw new RuntimeException("GEMINI_API_KEY not set in application.properties");
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;

        Map<String, Object> payload = Map.of(
            "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
            "generationConfig", Map.of(
                "temperature", 0.7,
                "responseMimeType", "application/json"
            )
        );

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new Exception("Gemini API Error " + resp.statusCode() + ": " + resp.body());
        }

        Map<String, Object> data = mapper.readValue(resp.body(), Map.class);
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) data.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            return (String) parts.get(0).get("text");
        } catch (Exception e) {
            throw new Exception("Unexpected response format from Gemini: " + data);
        }
    }

    public Map<String, Object> generateSmartSchedule(String uid) throws Exception {
        List<Map<String, Object>> tasks = db.query(
            "SELECT * FROM tasks WHERE user_id=? AND status='pending' AND (start_time IS NULL OR is_scheduled=1)", uid);
        
        List<Map<String, Object>> timetable = db.query(
            "SELECT * FROM timetable WHERE user_id=?", uid);

        if (tasks.isEmpty()) {
            return Map.of(
                "scheduled", List.of(),
                "explanation", "No pending tasks to schedule! Add some tasks first.",
                "tips", List.of("Add tasks with priority and due dates to help the AI plan your day.")
            );
        }

        String todayDate = LocalDate.now().toString();
        String dayOfWeek = LocalDate.now().getDayOfWeek().toString();

        StringBuilder tasksText = new StringBuilder();
        for (Map<String, Object> t : tasks) {
            tasksText.append("- ID: ").append(t.get("id"))
                     .append(", Title: ").append(t.get("title"))
                     .append(", Priority: ").append(t.getOrDefault("priority", "medium"))
                     .append(", Due Date: ").append(t.getOrDefault("due_date", t.getOrDefault("end_time", "None")))
                     .append("\n");
        }

        StringBuilder timetableText = new StringBuilder();
        for (Map<String, Object> item : timetable) {
            timetableText.append("- Day: ").append(item.get("day_of_week"))
                         .append(", Time: ").append(item.get("start_time")).append("-").append(item.get("end_time"))
                         .append(", Label: ").append(item.get("label"))
                         .append("\n");
        }
        if (timetableText.length() == 0) timetableText.append("No classes\n");

        String prompt = "You are Persona, an AI Smart Scheduler for a student productivity app.\n" +
            "Today is " + dayOfWeek + ", " + todayDate + ".\n\n" +
            "Student's pending tasks:\n" + tasksText + "\n" +
            "Student's weekly class timetable (busy slots recurring weekly):\n" + timetableText + "\n" +
            "Intelligently schedule focus blocks for the pending tasks for the next 7 days (starting from " + todayDate + ").\n" +
            "Rules:\n" +
            "1. Schedule focus blocks strictly within these two daily windows: Morning (09:00 to 12:00) and Evening (16:00 to 22:00). Never schedule blocks before 09:00, or between 12:00 and 16:00 (this lunch/afternoon period is blocked).\n" +
            "2. Focus blocks should be 60 to 90 minutes long.\n" +
            "3. Strictly avoid the recurring class times on their respective days.\n" +
            "4. Higher priority tasks and closer deadlines must be scheduled first. Deadline-based tasks should appear daily until completed, getting higher scheduling priority as the deadline nears.\n\n" +
            "Return a JSON object with this exact schema:\n" +
            "{\n" +
            "  \"scheduled\": [\n" +
            "    {\n" +
            "      \"task_id\": \"task ID string\",\n" +
            "      \"start_time\": \"YYYY-MM-DDTHH:MM\",\n" +
            "      \"end_time\": \"YYYY-MM-DDTHH:MM\",\n" +
            "      \"reason\": \"Why this slot was selected (1 sentence)\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"explanation\": \"A friendly 2-3 sentence overview of the plan.\",\n" +
            "  \"tips\": [\"Tailored productivity tip 1\", \"Tip 2\"]\n" +
            "}\n" +
            "Do not include any other text, markdown blocks, or backticks.";

        String rawJson = generateContent(prompt).trim();
        
        // Strip markdown backticks if any
        if (rawJson.startsWith("```")) {
            rawJson = rawJson.replaceAll("^```(json)?|```$", "").trim();
        }

        Map<String, Object> aiResult = mapper.readValue(rawJson, Map.class);
        List<Map<String, Object>> scheduledBlocks = (List<Map<String, Object>>) aiResult.getOrDefault("scheduled", List.of());

        // Save to Database
        // 1. Clear future pending task blocks
        String now = db.nowIso();
        db.execute("DELETE FROM task_blocks WHERE start_time > ? AND task_id IN (SELECT id FROM tasks WHERE user_id=? AND status='pending')", now, uid);
        db.execute("UPDATE tasks SET start_time=NULL, end_time=NULL, is_scheduled=0 WHERE user_id=? AND status='pending' AND start_time > ? AND is_scheduled=1", uid, now);

        // 2. Insert new blocks
        List<Map<String, Object>> verifiedBlocks = new ArrayList<>();
        for (Map<String, Object> block : scheduledBlocks) {
            String taskId = (String) block.get("task_id");
            String startTime = (String) block.get("start_time");
            String endTime = (String) block.get("end_time");

            if (taskId != null && startTime != null && endTime != null) {
                // Ensure task belongs to user and is pending
                List<Map<String, Object>> taskCheck = db.query("SELECT title FROM tasks WHERE id=? AND user_id=? AND status='pending'", taskId, uid);
                if (!taskCheck.isEmpty()) {
                    String bid = db.newId();
                    db.execute("INSERT INTO task_blocks (id, task_id, start_time, end_time) VALUES (?, ?, ?, ?)",
                        bid, taskId, startTime, endTime);
                    db.execute("UPDATE tasks SET is_scheduled=1, updated_at=? WHERE id=?", db.nowIso(), taskId);

                    Map<String, Object> verified = new HashMap<>(block);
                    verified.put("title", taskCheck.get(0).get("title"));
                    verifiedBlocks.add(verified);
                }
            }
        }

        Map<String, Object> response = new HashMap<>(aiResult);
        response.put("scheduled", verifiedBlocks);
        return response;
    }

    public Map<String, Object> chatWithAi(String message, Map<String, Object> context) throws Exception {
        String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy, HH:mm"));

        List<Map<String, Object>> tasks = (List<Map<String, Object>>) context.getOrDefault("tasks", List.of());
        StringBuilder tasksText = new StringBuilder();
        for (int i = 0; i < Math.min(10, tasks.size()); i++) {
            Map<String, Object> t = tasks.get(i);
            tasksText.append("- ").append(t.getOrDefault("title", "?"))
                     .append(" (priority:").append(t.get("priority"))
                     .append(", due:").append(t.getOrDefault("due_date", "?")).append(")\n");
        }
        if (tasksText.length() == 0) tasksText.append("No tasks");

        String prompt = "You are Persona, an AI productivity assistant for a student app.\n" +
            "Today: " + now + "\n\n" +
            "Student's pending tasks:\n" + tasksText + "\n" +
            "Student says: \"" + message + "\"\n\n" +
            "Reply helpfully in 2-3 sentences. If they ask to reschedule, suggest specific times.\n" +
            "If they ask about habits or studies, give motivating advice. Be warm and encouraging, like a smart friend.";

        String rawResponse = generateContent(prompt);
        return Map.of("reply", rawResponse.trim());
    }
}

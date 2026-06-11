package com.persona.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.persona.db.Database;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Service
public class AiExpenseService {

    @Value("${persona.gemini-api-key:}")
    private String geminiApiKey;

    private final Database db;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    public AiExpenseService(Database db) {
        this.db = db;
    }

    public Map<String, Object> analyzeExpenses(String uid, String month) throws Exception {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            throw new RuntimeException("GEMINI_API_KEY not set in environment");
        }

        // Fetch expenses for the given month
        List<Map<String, Object>> expenses = db.query(
            "SELECT amount, category, description, date FROM expenses WHERE user_id = ? AND TO_CHAR(date, 'YYYY-MM') = ?",
            uid, month
        );

        if (expenses.isEmpty()) {
            return Map.of(
                "analysis", "You have no logged expenses for this month yet. Add some transactions to get AI analysis!",
                "advice", "Start logging your daily transactions to analyze your spending habits!",
                "unnecessarySpending", "$0.00"
            );
        }

        double totalSpent = 0.0;
        StringBuilder spendingList = new StringBuilder();
        for (Map<String, Object> exp : expenses) {
            double amt = 0.0;
            Object amtObj = exp.get("amount");
            if (amtObj instanceof Number) {
                amt = ((Number) amtObj).doubleValue();
            } else if (amtObj != null) {
                try {
                    amt = Double.parseDouble(amtObj.toString());
                } catch (Exception e) {}
            }

            String cat = (String) exp.get("category");
            if ("Income".equalsIgnoreCase(cat)) {
                continue; // ignore income in expense analysis
            }
            totalSpent += amt;
            String desc = (String) exp.get("description");
            String date = (String) exp.get("date");
            spendingList.append(String.format("- Date: %s, Category: %s, Amount: $%.2f, Description: %s\n",
                date, cat, amt, desc != null && !desc.isEmpty() ? desc : "None"));
        }

        if (totalSpent == 0.0) {
            return Map.of(
                "analysis", "No expenses logged this month. All logged items are income!",
                "advice", "Ensure you record your outgoing expenses to get detailed AI feedback.",
                "unnecessarySpending", "$0.00"
            );
        }

        // Draft prompt for Gemini
        String prompt = "You are a financial advisor AI assistant for a student productivity app.\n" +
            "Analyze the following student expenses for the month " + month + " (total spent: $" + String.format("%.2f", totalSpent) + "):\n\n" +
            spendingList.toString() + "\n" +
            "Please provide a JSON response in the following exact format, with nothing else. Make sure all descriptions (analysis, spendingPatterns, suggestions) are formatted as short, clear bullet points starting with a dash (-) so they are easy for a human to read quickly at a glance:\n" +
            "{\n" +
            "  \"analysis\": \"- Short point 1 about overall spending\\n- Short point 2 about overall spending\",\n" +
            "  \"advice\": \"A short, action-oriented financial tip or recommendation (1 sentence) to help them save next month.\",\n" +
            "  \"unnecessarySpending\": \"A short estimation of potential unnecessary spending (e.g. 'Estimated unnecessary spending: $35.00')\",\n" +
            "  \"highestCategories\": \"Identify the highest spending categories and what percentage of total spending they represent (e.g. 'Food (45%), Shopping (30%)')\",\n" +
            "  \"spendingPatterns\": \"- Short pattern 1 observed\\n- Short pattern 2 observed\",\n" +
            "  \"suggestions\": \"- Concrete suggestion 1 to save\\n- Concrete suggestion 2 to save\"\n" +
            "}";

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;

        Map<String, Object> parts = Map.of("text", prompt);
        Map<String, Object> payload = Map.of(
            "contents", List.of(Map.of("parts", List.of(parts))),
            "generationConfig", Map.of(
                "temperature", 0.2,
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
            throw new Exception("Gemini API Error: " + resp.body());
        }

        Map<String, Object> data = mapper.readValue(resp.body(), Map.class);
        String raw;
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) data.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> partsList = (List<Map<String, Object>>) content.get("parts");
            raw = ((String) partsList.get(0).get("text")).trim();
        } catch (Exception e) {
            throw new Exception("Unexpected response format: " + data);
        }

        if (raw.contains("```json")) {
            raw = raw.split("```json")[1].split("```")[0].trim();
        } else if (raw.contains("```")) {
            raw = raw.split("```")[1].split("```")[0].trim();
        }

        try {
            Map<String, Object> rawMap = mapper.readValue(raw, Map.class);
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof List) {
                    StringBuilder sb = new StringBuilder();
                    List<?> list = (List<?>) val;
                    for (int i = 0; i < list.size(); i++) {
                        sb.append(list.get(i).toString());
                        if (i < list.size() - 1) sb.append("\n");
                    }
                    sanitized.put(entry.getKey(), sb.toString());
                } else {
                    sanitized.put(entry.getKey(), val != null ? val.toString() : "");
                }
            }
            return sanitized;
        } catch (Exception e) {
            System.err.println("JSON Parsing failed. Output was: " + raw);
            return Map.of(
                "analysis", "Failed to parse analysis response.",
                "advice", "Try running again later.",
                "unnecessarySpending", "Unknown"
            );
        }
    }
}

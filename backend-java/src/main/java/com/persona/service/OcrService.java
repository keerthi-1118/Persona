package com.persona.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Service
public class OcrService {

    @Value("${persona.gemini-api-key:}")
    private String geminiApiKey;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    public List<Map<String, Object>> extractTimetableFromImage(byte[] imageData, String mimeType) throws Exception {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            throw new RuntimeException("GEMINI_API_KEY not set in environment");
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;

        if (mimeType == null || !mimeType.startsWith("image/")) {
            mimeType = "image/jpeg";
        }

        String b64Image = Base64.getEncoder().encodeToString(imageData);

        String prompt = "You are a timetable extraction assistant. I will provide an image of a class schedule.\n" +
            "Please extract all the classes/subjects with their day of the week, start time, and end time.\n\n" +
            "Rules:\n" +
            "1. day_of_week MUST be accurately interpreted and stored strictly as a 3-letter lowercase abbreviation: \"mon\", \"tue\", \"wed\", \"thu\", \"fri\", \"sat\", or \"sun\".\n" +
            "2. start_time and end_time MUST be in HH:MM 24-hour format (e.g. \"09:00\", \"14:30\").\n" +
            "3. subject MUST be the name of the class or block.\n\n" +
            "Output ONLY a JSON array in the exact format below, with nothing else:\n" +
            "[\n" +
            "  {\n" +
            "    \"day_of_week\": \"mon\",\n" +
            "    \"start_time\": \"09:00\",\n" +
            "    \"end_time\": \"10:30\",\n" +
            "    \"subject\": \"Physics 101\"\n" +
            "  }\n" +
            "]";

        Map<String, Object> inlineData = Map.of("mime_type", mimeType, "data", b64Image);
        Map<String, Object> parts1 = Map.of("text", prompt);
        Map<String, Object> parts2 = Map.of("inline_data", inlineData);

        Map<String, Object> payload = Map.of(
            "contents", List.of(Map.of("parts", List.of(parts1, parts2))),
            "generationConfig", Map.of("temperature", 0.2)
        );

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new Exception("API Error " + resp.statusCode() + ": " + resp.body());
        }

        Map<String, Object> data = mapper.readValue(resp.body(), Map.class);
        String raw;
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) data.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            raw = ((String) parts.get(0).get("text")).trim();
        } catch (Exception e) {
            throw new Exception("Unexpected response format: " + data);
        }

        if (raw.contains("```json")) {
            raw = raw.split("```json")[1].split("```")[0].trim();
        } else if (raw.contains("```")) {
            raw = raw.split("```")[1].split("```")[0].trim();
        }

        try {
            return mapper.readValue(raw, List.class);
        } catch (Exception e) {
            System.out.println("OCR Parsing Logic failed. Output was: " + raw);
            throw new Exception("Failed to parse extracted JSON from AI");
        }
    }
}

package com.persona.controller;

import com.persona.db.Database;
import com.persona.service.OcrService;
import com.persona.service.SchedulerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;
import java.util.*;

/**
 * Persona — Timetable Controller
 * Replaces: backend/routes/timetable.py
 */
@RestController
@RequestMapping("/api/timetable")
public class TimetableController {

    private final Database db;
    private final OcrService ocrService;
    private final SchedulerService schedulerService;

    private static final Set<String> VALID_DAYS = Set.of("mon","tue","wed","thu","fri","sat","sun");

    @Autowired
    public TimetableController(Database db, OcrService ocrService, SchedulerService schedulerService) {
        this.db = db;
        this.ocrService = ocrService;
        this.schedulerService = schedulerService;
    }

    @GetMapping("/")
    public ResponseEntity<?> getTimetable(HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Unauthorized");
        return ResponseEntity.ok(db.query("SELECT * FROM timetable WHERE user_id = ?", uid));
    }

    @PostMapping("/")
    public ResponseEntity<?> addClass(@RequestBody Map<String, Object> data, HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Unauthorized");

        String day   = str(data.get("day_of_week")).toLowerCase();
        if (day.length() > 3) day = day.substring(0, 3);
        String start = str(data.get("start_time"));
        String end   = str(data.get("end_time"));
        String label = str(data.getOrDefault("label", "Class"));
        String type  = str(data.getOrDefault("type", "class"));

        if (day.isEmpty() || start.isEmpty() || end.isEmpty()) {
            return err(400, "day_of_week, start_time, and end_time are required");
        }
        if (!VALID_DAYS.contains(day)) {
            return err(400, "Invalid day_of_week. Must be one of: mon, tue, wed, thu, fri, sat, sun");
        }

        try {
            String tid = db.newId();
            db.execute(
                "INSERT INTO timetable (id, user_id, day_of_week, start_time, end_time, label, type) VALUES (?,?,?,?,?,?,?)",
                tid, uid, day, start, end, label, type
            );
            return ResponseEntity.status(201).body(Map.of("message", "Timetable block added!", "id", tid));
        } catch (Exception e) {
            return err(500, "Database error occurred while adding class.");
        }
    }

    @PutMapping("/{blockId}")
    public ResponseEntity<?> updateClass(@PathVariable String blockId,
                                         @RequestBody Map<String, Object> data,
                                         HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Unauthorized");

        List<Map<String, Object>> existing = db.query("SELECT id FROM timetable WHERE id=? AND user_id=?", blockId, uid);
        if (existing.isEmpty()) return err(404, "Block not found");

        String day = str(data.get("day_of_week"));
        if (!day.isEmpty()) {
            day = day.toLowerCase();
            if (day.length() > 3) day = day.substring(0, 3);
            if (!VALID_DAYS.contains(day)) {
                return err(400, "Invalid day_of_week. Must be one of: mon, tue, wed, thu, fri, sat, sun");
            }
        }

        try {
            db.execute(
                "UPDATE timetable SET day_of_week=?, start_time=?, end_time=?, label=?, type=? WHERE id=?",
                day.isEmpty() ? data.get("day_of_week") : day,
                data.get("start_time"), data.get("end_time"),
                data.get("label"), data.get("type"), blockId
            );
            return ResponseEntity.ok(Map.of("message", "Block updated successfully"));
        } catch (Exception e) {
            return err(500, "Database error occurred while updating class.");
        }
    }

    @DeleteMapping("/{blockId}")
    public ResponseEntity<?> deleteClass(@PathVariable String blockId, HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Unauthorized");
        db.execute("DELETE FROM timetable WHERE id=? AND user_id=?", blockId, uid);
        return ResponseEntity.ok(Map.of("message", "Block deleted"));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadTimetable(
        @RequestParam("image") MultipartFile file,
        HttpSession session
    ) {
        String uid = uid(session);
        if (uid == null) return err(401, "Unauthorized");

        if (file.isEmpty()) return err(400, "No image file provided");

        try {
            List<Map<String, Object>> extracted = ocrService.extractTimetableFromImage(
                file.getBytes(), file.getContentType()
            );

            int insertedCount = 0;
            for (Map<String, Object> item : extracted) {
                String day     = str(item.get("day_of_week")).toLowerCase();
                if (day.length() > 3) day = day.substring(0, 3);
                String start   = str(item.get("start_time"));
                String end     = str(item.get("end_time"));
                String subject = str(item.getOrDefault("subject", "Class"));

                if (VALID_DAYS.contains(day) && !start.isEmpty() && !end.isEmpty()) {
                    List<Map<String, Object>> dup = db.query(
                        "SELECT id FROM timetable WHERE user_id=? AND day_of_week=? AND start_time=?",
                        uid, day, start
                    );
                    if (dup.isEmpty()) {
                        db.execute(
                            "INSERT INTO timetable (id, user_id, day_of_week, start_time, end_time, label, type) VALUES (?,?,?,?,?,?,?)",
                            db.newId(), uid, day, start, end, subject, "class"
                        );
                        insertedCount++;
                    }
                }
            }

            // Trigger rescheduling after new timetable
            try {
                schedulerService.autoScheduleAll(uid);
            } catch (Exception e) {
                System.err.println("Warning: Auto-schedule failed after upload: " + e.getMessage());
            }

            return ResponseEntity.ok(Map.of(
                "message",        "Successfully extracted and inserted " + insertedCount + " classes.",
                "extracted_data", extracted
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return err(500, "Failed to process image: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────
    private String uid(HttpSession s) { return (String) s.getAttribute("user_id"); }
    private String str(Object o) { return o == null ? "" : o.toString(); }
    private ResponseEntity<Map<String, Object>> err(int status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }
}

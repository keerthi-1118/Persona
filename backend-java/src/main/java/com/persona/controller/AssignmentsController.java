package com.persona.controller;

import com.persona.db.Database;
import com.persona.service.ClassroomApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.*;

/**
 * Persona — Assignments Controller
 * Replaces: backend/routes/assignments.py
 */
@RestController
@RequestMapping("/api/assignments")
public class AssignmentsController {

    private final Database db;
    private final ClassroomApiService classroomApiService;
    private final com.persona.service.SchedulerService schedulerService;
    private final com.persona.service.AiSchedulerService aiSchedulerService;

    @Autowired
    public AssignmentsController(
        Database db,
        ClassroomApiService classroomApiService,
        com.persona.service.SchedulerService schedulerService,
        com.persona.service.AiSchedulerService aiSchedulerService
    ) {
        this.db = db;
        this.classroomApiService = classroomApiService;
        this.schedulerService = schedulerService;
        this.aiSchedulerService = aiSchedulerService;
    }

    @GetMapping("/")
    public ResponseEntity<?> getAssignments(
        @RequestParam(required = false) String status,
        HttpSession session
    ) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        StringBuilder sql = new StringBuilder("SELECT * FROM assignments WHERE user_id = ?");
        List<Object> params = new ArrayList<>(List.of(uid));
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY due_date ASC");
        return ResponseEntity.ok(db.query(sql.toString(), params.toArray()));
    }

    @PostMapping("/")
    public ResponseEntity<?> createAssignment(@RequestBody Map<String, Object> data, HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        if (str(data.get("title")).isEmpty() || str(data.get("course_name")).isEmpty()) {
            return err(400, "title and course_name are required");
        }

        String aid = db.newId();
        db.execute(
            "INSERT INTO assignments (id, user_id, course_name, title, description, due_date, status, source, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
            aid, uid,
            data.get("course_name"), data.get("title"),
            data.get("description"), data.get("due_date"),
            str(data.getOrDefault("status", "pending")), "manual",
            db.nowIso(), db.nowIso()
        );

        List<Map<String, Object>> rows = db.query("SELECT * FROM assignments WHERE id = ?", aid);
        if (!rows.isEmpty()) {
            syncAssignmentToTask(aid, uid, rows.get(0));
            try {
                schedulerService.autoScheduleAll(uid);
            } catch (Exception ignored) {}
        }
        return ResponseEntity.status(201).body(rows.isEmpty() ? Map.of("id", aid) : rows.get(0));
    }

    @PutMapping("/{aid}")
    public ResponseEntity<?> updateAssignment(@PathVariable String aid,
                                              @RequestBody Map<String, Object> data,
                                              HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        List<Map<String, Object>> rows = db.query("SELECT id FROM assignments WHERE id=? AND user_id=?", aid, uid);
        if (rows.isEmpty()) return err(404, "Assignment not found");

        String[] allowed = {"title", "description", "due_date", "status", "course_name"};
        List<String> fields = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (String f : allowed) {
            if (data.containsKey(f)) {
                fields.add(f + " = ?");
                params.add(data.get(f));
            }
        }
        if (fields.isEmpty()) return err(400, "No fields to update");

        fields.add("updated_at = ?");
        params.add(db.nowIso());
        params.add(aid);

        db.execute("UPDATE assignments SET " + String.join(", ", fields) + " WHERE id = ?", params.toArray());
        List<Map<String, Object>> updated = db.query("SELECT * FROM assignments WHERE id = ?", aid);
        if (!updated.isEmpty()) {
            syncAssignmentToTask(aid, uid, updated.get(0));
            try {
                schedulerService.autoScheduleAll(uid);
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(updated.get(0));
    }

    @DeleteMapping("/{aid}")
    public ResponseEntity<?> deleteAssignment(@PathVariable String aid, HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        // Clean up linked tasks and scheduled blocks
        List<Map<String, Object>> linkedTasks = db.query("SELECT id FROM tasks WHERE assignment_id=? AND user_id=?", aid, uid);
        for (Map<String, Object> t : linkedTasks) {
            String tid = str(t.get("id"));
            db.execute("DELETE FROM task_blocks WHERE task_id=?", tid);
            db.execute("DELETE FROM tasks WHERE id=? AND user_id=?", tid, uid);
        }

        db.execute("DELETE FROM assignments WHERE id=? AND user_id=?", aid, uid);
        return ResponseEntity.ok(Map.of("message", "Assignment deleted"));
    }

    private void syncAssignmentToTask(String aid, String uid, Map<String, Object> data) {
        String title = str(data.get("title"));
        String courseName = str(data.get("course_name"));
        String description = str(data.get("description"));
        String dueDate = str(data.get("due_date"));
        String status = str(data.get("status"));

        String taskTitle = courseName.isEmpty() ? title : courseName + ": " + title;
        String taskStatus = ("submitted".equals(status) || "graded".equals(status)) ? "completed" : "pending";

        List<Map<String, Object>> existing = db.query("SELECT id FROM tasks WHERE assignment_id = ? AND user_id = ?", aid, uid);
        if (existing.isEmpty()) {
            String tid = db.newId();
            db.execute(
                "INSERT INTO tasks (id, user_id, title, description, end_time, priority, status, category, assignment_id, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                tid, uid, taskTitle, description, dueDate.isEmpty() ? null : dueDate,
                "high", taskStatus, "assignment", aid, db.nowIso(), db.nowIso()
            );
        } else {
            String tid = str(existing.get(0).get("id"));
            db.execute(
                "UPDATE tasks SET title=?, description=?, end_time=?, status=?, updated_at=? WHERE id=? AND user_id=?",
                taskTitle, description, dueDate.isEmpty() ? null : dueDate, taskStatus, db.nowIso(), tid, uid
            );
            if ("completed".equals(taskStatus)) {
                db.execute("DELETE FROM task_blocks WHERE task_id = ?", tid);
            }
        }
    }

    @PostMapping("/sync/classroom")
    public ResponseEntity<?> syncFromClassroom(HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");
        try {
            int count = classroomApiService.syncAssignments(uid);
            
            // Auto schedule tasks with deadlines using smart AI
            try {
                aiSchedulerService.generateSmartSchedule(uid);
            } catch (Exception ex) {
                System.err.println("Gemini Scheduler failed after classroom sync, falling back to rule-based: " + ex.getMessage());
                try {
                    schedulerService.autoScheduleAll(uid);
                } catch (Exception ex2) {
                    System.err.println("Rule-based Scheduler failed after classroom sync: " + ex2.getMessage());
                }
            }
            
            return ResponseEntity.ok(Map.of("message", "Synced " + count + " assignments and updated schedule with Smart AI"));
        } catch (Exception e) {
            return err(400, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────
    private String uid(HttpSession s) { return (String) s.getAttribute("user_id"); }
    private String str(Object o)  { return o == null ? "" : o.toString(); }
    private ResponseEntity<Map<String, Object>> err(int status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }
}

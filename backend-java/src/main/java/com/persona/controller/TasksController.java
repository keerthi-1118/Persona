package com.persona.controller;

import com.persona.db.Database;
import com.persona.service.SchedulerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.*;

/**
 * Persona — Tasks Controller
 * Replaces: backend/routes/tasks.py
 *
 * GET    /api/tasks          — list tasks (with ?status= ?priority= ?date=)
 * POST   /api/tasks          — create task
 * PUT    /api/tasks/{id}     — update task
 * DELETE /api/tasks/{id}     — delete task
 * PATCH  /api/tasks/{id}/complete — mark complete
 */
@RestController
@RequestMapping("/api/tasks")
public class TasksController {

    private final Database db;
    private final SchedulerService schedulerService;

    @Autowired
    public TasksController(Database db, SchedulerService schedulerService) {
        this.db = db;
        this.schedulerService = schedulerService;
    }

    @GetMapping("/")
    public ResponseEntity<?> getTasks(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String priority,
        @RequestParam(required = false) String date,
        HttpSession session
    ) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        StringBuilder sql = new StringBuilder("SELECT * FROM tasks WHERE user_id = ?");
        List<Object> params = new ArrayList<>(List.of(uid));

        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = ?");
            params.add(status);
        }

        List<Map<String, Object>> tasks = db.query(sql.toString(), params.toArray());

        // Filter by priority in Java
        if (priority != null && !priority.isEmpty()) {
            String p = priority;
            tasks = tasks.stream().filter(t -> p.equals(str(t.get("priority")))).toList();
        }
        tasks = new ArrayList<>(tasks); // make mutable

        // Fetch task_blocks and create virtual task entries
        List<Map<String, Object>> blocks = db.query(
            "SELECT * FROM task_blocks WHERE task_id IN (SELECT id FROM tasks WHERE user_id=?)", uid);
        Map<String, List<Map<String, Object>>> blocksByTask = new HashMap<>();
        for (Map<String, Object> b : blocks) {
            String tid = str(b.get("task_id"));
            blocksByTask.computeIfAbsent(tid, k -> new ArrayList<>()).add(b);
        }

        List<Map<String, Object>> virtualTasks = new ArrayList<>();
        for (Map<String, Object> t : tasks) {
            String tid = str(t.get("id"));
            if (blocksByTask.containsKey(tid)) {
                List<Map<String, Object>> tBlocks = blocksByTask.get(tid);
                for (int i = 0; i < tBlocks.size(); i++) {
                    Map<String, Object> b  = tBlocks.get(i);
                    Map<String, Object> vt = new HashMap<>(t);
                    vt.put("id",         tid + "___block_" + str(b.get("id")));
                    vt.put("title",      str(t.get("title")) + " (Session " + (i + 1) + ")");
                    vt.put("start_time", b.get("start_time"));
                    vt.put("end_time",   b.get("end_time"));
                    vt.put("is_block",   true);
                    virtualTasks.add(vt);
                }
                t.put("start_time", null);
            }
        }
        tasks.addAll(virtualTasks);

        // Filter by date
        if (date != null && !date.isEmpty()) {
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> t : tasks) {
                String st = str(t.get("start_time"));
                String et = str(t.get("end_time"));
                if (st.isEmpty()) {
                    if (!et.isEmpty() && et.length() >= 10 && et.substring(0, 10).compareTo(date) >= 0) {
                        String taskStatus = str(t.get("status"));
                        if ("completed".equals(taskStatus) || "cancelled".equals(taskStatus)) {
                            String updated = str(t.get("updated_at"));
                            String compDate = !updated.isEmpty() && updated.length() >= 10 ? updated.substring(0, 10) : "";
                            if (compDate.equals(date)) {
                                filtered.add(t);
                            }
                        } else {
                            filtered.add(t);
                        }
                    }
                    continue;
                }
                String stDate = st.length() >= 10 ? st.substring(0, 10) : st;
                String etDate = et.length() >= 10 ? et.substring(0, 10) : et;
                if (!et.isEmpty()) {
                    if (stDate.compareTo(date) <= 0 && date.compareTo(etDate) <= 0) filtered.add(t);
                } else {
                    if (stDate.equals(date)) filtered.add(t);
                }
            }
            tasks = filtered;
        }

        // Sort by priority then start_time
        Map<String, Integer> priMap = Map.of("urgent", 1, "high", 2, "medium", 3, "low", 4);
        tasks.sort(Comparator
            .comparingInt((Map<String, Object> t) -> priMap.getOrDefault(str(t.get("priority")), 5))
            .thenComparing(t -> str(t.getOrDefault("start_time", "9999-12-31")))
        );

        return ResponseEntity.ok(tasks);
    }

    @PostMapping("/")
    public ResponseEntity<?> createTask(@RequestBody Map<String, Object> data, HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        String title = trim(data.get("title"));
        if (title.isEmpty()) return err(400, "title is required");

        String priority = str(data.getOrDefault("priority", "medium"));
        Set<String> validPriorities = Set.of("low", "medium", "high", "urgent");
        if (!validPriorities.contains(priority)) return err(400, "Invalid priority");

        String tid = db.newId();
        db.execute(
            "INSERT INTO tasks (id, user_id, title, description, start_time, end_time, priority, status, category, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            tid, uid, title, data.get("description"),
            data.get("start_time"), data.get("end_time"),
            priority,
            str(data.getOrDefault("status", "pending")),
            str(data.getOrDefault("category", "general")),
            db.nowIso(), db.nowIso()
        );

        // Auto-schedule if no start_time provided
        if (data.get("start_time") == null || str(data.get("start_time")).isEmpty()) {
            try {
                schedulerService.autoScheduleTask(uid, tid);
            } catch (Exception e) {
                System.err.println("Scheduler warning: " + e.getMessage());
            }
        }

        List<Map<String, Object>> rows = db.query("SELECT * FROM tasks WHERE id = ?", tid);
        return ResponseEntity.status(201).body(rows.isEmpty() ? Map.of("id", tid) : rows.get(0));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<?> updateTask(@PathVariable String taskId,
                                        @RequestBody Map<String, Object> data,
                                        HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        String actualTaskId = taskId;
        if (taskId.contains("___block_")) {
            actualTaskId = taskId.split("___block_")[0];
        }

        List<Map<String, Object>> rows = db.query("SELECT id FROM tasks WHERE id = ? AND user_id = ?", actualTaskId, uid);
        if (rows.isEmpty()) return err(404, "Task not found");

        List<String> fields = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String[] allowed = {"title", "description", "start_time", "end_time", "priority", "status", "category"};
        for (String f : allowed) {
            if (data.containsKey(f)) {
                fields.add(f + " = ?");
                params.add(data.get(f));
            }
        }
        if (data.containsKey("start_time") || data.containsKey("end_time")) {
            fields.add("is_scheduled = ?");
            params.add(0);
        }
        if (fields.isEmpty()) return err(400, "No fields to update");

        fields.add("updated_at = ?");
        params.add(db.nowIso());
        params.add(actualTaskId);

        db.execute("UPDATE tasks SET " + String.join(", ", fields) + " WHERE id = ?", params.toArray());

        // Sync status back to linked assignment and delete blocks if completed
        if (data.containsKey("status")) {
            String statusVal = str(data.get("status"));
            if ("completed".equals(statusVal)) {
                db.execute("DELETE FROM task_blocks WHERE task_id = ?", actualTaskId);
            }
            List<Map<String, Object>> taskInfo = db.query("SELECT assignment_id FROM tasks WHERE id=? AND user_id=?", actualTaskId, uid);
            if (!taskInfo.isEmpty() && taskInfo.get(0).get("assignment_id") != null) {
                String aid = str(taskInfo.get(0).get("assignment_id"));
                if (!aid.isEmpty()) {
                    String newAsgmtStatus = "completed".equals(statusVal) ? "submitted" : "pending";
                    db.execute("UPDATE assignments SET status=?, updated_at=? WHERE id=? AND user_id=?",
                        newAsgmtStatus, db.nowIso(), aid, uid);
                }
            }
        }

        List<Map<String, Object>> updated = db.query("SELECT * FROM tasks WHERE id = ?", actualTaskId);
        return ResponseEntity.ok(updated.get(0));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<?> deleteTask(@PathVariable String taskId, HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        String actualTaskId = taskId;
        if (taskId.contains("___block_")) {
            actualTaskId = taskId.split("___block_")[0];
        }

        db.execute("DELETE FROM tasks WHERE id = ? AND user_id = ?", actualTaskId, uid);
        return ResponseEntity.ok(Map.of("message", "Task deleted"));
    }

    @PatchMapping("/{taskId}/complete")
    public ResponseEntity<?> completeTask(@PathVariable String taskId, HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        String actualTaskId = taskId;
        if (taskId.contains("___block_")) {
            actualTaskId = taskId.split("___block_")[0];
        }

        db.execute("UPDATE tasks SET status='completed', updated_at=? WHERE id=? AND user_id=?",
            db.nowIso(), actualTaskId, uid);
        
        // Clean up task blocks for the completed task
        db.execute("DELETE FROM task_blocks WHERE task_id=?", actualTaskId);

        // Sync to assignment if linked
        List<Map<String, Object>> taskInfo = db.query("SELECT assignment_id FROM tasks WHERE id=? AND user_id=?", actualTaskId, uid);
        if (!taskInfo.isEmpty() && taskInfo.get(0).get("assignment_id") != null) {
            String aid = str(taskInfo.get(0).get("assignment_id"));
            if (!aid.isEmpty()) {
                db.execute("UPDATE assignments SET status='submitted', updated_at=? WHERE id=? AND user_id=?",
                    db.nowIso(), aid, uid);
            }
        }

        return ResponseEntity.ok(Map.of("message", "Task marked complete"));
    }

    // ── Helpers ───────────────────────────────────────────────
    private String uid(HttpSession s) { return (String) s.getAttribute("user_id"); }
    private String str(Object o)  { return o == null ? "" : o.toString(); }
    private String trim(Object o) { return o == null ? "" : o.toString().trim(); }

    private ResponseEntity<Map<String, Object>> err(int status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }
}

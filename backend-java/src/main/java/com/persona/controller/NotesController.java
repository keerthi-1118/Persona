package com.persona.controller;

import com.persona.db.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.*;

/**
 * Persona — Notes Controller
 * Replaces: backend/routes/notes.py
 */
@RestController
@RequestMapping("/api/notes")
public class NotesController {

    private final Database db;

    @Autowired
    public NotesController(Database db) { this.db = db; }

    @GetMapping("/")
    public ResponseEntity<?> getNotes(HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");
        return ResponseEntity.ok(
            db.query("SELECT * FROM notes WHERE user_id=? ORDER BY pinned DESC, updated_at DESC", uid)
        );
    }

    @PostMapping("/")
    public ResponseEntity<?> createNote(@RequestBody Map<String, Object> data, HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        String nid = db.newId();
        db.execute(
            "INSERT INTO notes (id, user_id, title, content, tags, color, pinned, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
            nid, uid,
            str(data.getOrDefault("title", "Untitled Note")),
            str(data.getOrDefault("content", "")),
            str(data.getOrDefault("tags", "[]")),
            str(data.getOrDefault("color", "#1e1e2e")),
            toInt(data.getOrDefault("pinned", 0)),
            db.nowIso(), db.nowIso()
        );

        List<Map<String, Object>> rows = db.query("SELECT * FROM notes WHERE id = ?", nid);
        return ResponseEntity.status(201).body(rows.isEmpty() ? Map.of("id", nid) : rows.get(0));
    }

    @PutMapping("/{nid}")
    public ResponseEntity<?> updateNote(@PathVariable String nid,
                                        @RequestBody Map<String, Object> data,
                                        HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        List<Map<String, Object>> rows = db.query("SELECT id FROM notes WHERE id=? AND user_id=?", nid, uid);
        if (rows.isEmpty()) return err(404, "Note not found");

        String[] allowed = {"title", "content", "tags", "color", "pinned"};
        List<String> fields = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (String f : allowed) {
            if (data.containsKey(f)) {
                fields.add(f + " = ?");
                params.add(data.get(f));
            }
        }
        fields.add("updated_at = ?");
        params.add(db.nowIso());
        params.add(nid);

        db.execute("UPDATE notes SET " + String.join(", ", fields) + " WHERE id=?", params.toArray());
        List<Map<String, Object>> updated = db.query("SELECT * FROM notes WHERE id=?", nid);
        return ResponseEntity.ok(updated.get(0));
    }

    @DeleteMapping("/{nid}")
    public ResponseEntity<?> deleteNote(@PathVariable String nid, HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");
        db.execute("DELETE FROM notes WHERE id=? AND user_id=?", nid, uid);
        return ResponseEntity.ok(Map.of("message", "Note deleted"));
    }

    // ── Helpers ───────────────────────────────────────────────
    private String uid(HttpSession s) { return (String) s.getAttribute("user_id"); }
    private String str(Object o)  { return o == null ? "" : o.toString(); }
    private int toInt(Object o)   {
        if (o == null) return 0;
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; }
    }
    private ResponseEntity<Map<String, Object>> err(int status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }
}

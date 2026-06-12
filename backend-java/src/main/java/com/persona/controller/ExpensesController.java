package com.persona.controller;

import com.persona.db.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.*;

/**
 * Persona — Expenses Controller
 * Replaces: backend/routes/expenses.py
 */
@RestController
@RequestMapping("/api/expenses")
public class ExpensesController {

    private final Database db;
    private final com.persona.service.AiExpenseService aiExpenseService;

    @Autowired
    public ExpensesController(Database db, com.persona.service.AiExpenseService aiExpenseService) {
        this.db = db;
        this.aiExpenseService = aiExpenseService;
    }

    @GetMapping("/")
    public ResponseEntity<?> getExpenses(
        @RequestParam(required = false) String month,
        HttpSession session
    ) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        StringBuilder sql = new StringBuilder("SELECT * FROM expenses WHERE user_id = ?");
        List<Object> params = new ArrayList<>(List.of(uid));
        if (month != null && !month.isEmpty()) {
            sql.append(" AND TO_CHAR(date, 'YYYY-MM') = ?");
            params.add(month);
        }

        List<Map<String, Object>> rows = db.query(sql.toString(), params.toArray());
        rows = new ArrayList<>(rows);
        rows.sort(Comparator
            .comparing((Map<String, Object> r) -> str(r.get("date")))
            .thenComparing(r -> str(r.get("created_at")))
            .reversed()
        );

        // Category summary
        Map<String, Double> cats = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String c = str(r.getOrDefault("category", "other"));
            double amt = toDouble(r.get("amount"));
            cats.merge(c, amt, Double::sum);
        }
        List<Map<String, Object>> summary = new ArrayList<>();
        cats.forEach((k, v) -> summary.add(Map.of("category", k, "total", v)));

        return ResponseEntity.ok(Map.of("expenses", rows, "summary", summary));
    }

    @PostMapping("/")
    public ResponseEntity<?> createExpense(@RequestBody Map<String, Object> data, HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        if (data.get("amount") == null) return err(400, "amount is required");
        double amt;
        try {
            amt = Double.parseDouble(str(data.get("amount")));
            if (amt <= 0) return err(400, "amount must be positive");
        } catch (NumberFormatException e) {
            return err(400, "amount must be a number");
        }

        String eid = db.newId();
        String today = LocalDate.now().toString();
        db.execute(
            "INSERT INTO expenses (id, user_id, amount, category, description, date, created_at) VALUES (?,?,?,?,?,?,?)",
            eid, uid, amt,
            str(data.getOrDefault("category", "other")),
            data.get("description"),
            data.getOrDefault("date", today),
            db.nowIso()
        );

        List<Map<String, Object>> rows = db.query("SELECT * FROM expenses WHERE id = ?", eid);
        return ResponseEntity.status(201).body(rows.isEmpty() ? Map.of("id", eid) : rows.get(0));
    }

    @DeleteMapping("/{eid}")
    public ResponseEntity<?> deleteExpense(@PathVariable String eid, HttpSession session) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");
        db.execute("DELETE FROM expenses WHERE id=? AND user_id=?", eid, uid);
        return ResponseEntity.ok(Map.of("message", "Expense deleted"));
    }

    @PutMapping("/{eid}")
    public ResponseEntity<?> updateExpense(
        @PathVariable String eid,
        @RequestBody Map<String, Object> data,
        HttpSession session
    ) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");

        List<Map<String, Object>> existing = db.query("SELECT * FROM expenses WHERE id=? AND user_id=?", eid, uid);
        if (existing.isEmpty()) {
            return err(404, "Expense not found");
        }

        double amt = Double.parseDouble(str(data.getOrDefault("amount", existing.get(0).get("amount"))));
        String cat = str(data.getOrDefault("category", existing.get(0).get("category")));
        Object desc = data.getOrDefault("description", existing.get(0).get("description"));

        db.execute(
            "UPDATE expenses SET amount=?, category=?, description=? WHERE id=? AND user_id=?",
            amt, cat, desc, eid, uid
        );

        List<Map<String, Object>> rows = db.query("SELECT * FROM expenses WHERE id = ?", eid);
        return ResponseEntity.ok(rows.get(0));
    }

    @GetMapping("/total")
    public ResponseEntity<?> getTotal(
        @RequestParam(required = false) String month,
        HttpSession session
    ) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");
        if (month == null || month.isEmpty()) month = db.nowIso().substring(0, 7);

        List<Map<String, Object>> rows = db.query(
            "SELECT * FROM expenses WHERE user_id = ? AND TO_CHAR(date, 'YYYY-MM') = ?", uid, month
        );

        double totalSpent = 0, totalIncome = 0;
        for (Map<String, Object> r : rows) {
            double amt = toDouble(r.get("amount"));
            if ("Income".equals(str(r.get("category")))) totalIncome += amt;
            else totalSpent += amt;
        }

        return ResponseEntity.ok(Map.of(
            "total",         totalSpent,
            "total_spent",   totalSpent,
            "total_income",  totalIncome,
            "month",         month
        ));
    }

    @GetMapping("/analyze")
    public ResponseEntity<?> getAiAnalysis(
        @RequestParam(required = false) String month,
        HttpSession session
    ) {
        String uid = uid(session);
        if (uid == null) return err(401, "Authentication required");
        if (month == null || month.isEmpty()) {
            month = LocalDate.now().toString().substring(0, 7);
        }

        try {
            Map<String, Object> analysis = aiExpenseService.analyzeExpenses(uid, month);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            System.err.println("AI Expense Analysis failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(Map.of(
                "analysis", "Unable to analyze expenses at this time: " + e.getMessage(),
                "advice", "Please report this error to support.",
                "unnecessarySpending", "Unknown"
            ));
        }
    }

    // ── Helpers ───────────────────────────────────────────────
    private String uid(HttpSession s)  { return (String) s.getAttribute("user_id"); }
    private String str(Object o)       { return o == null ? "" : o.toString(); }
    private double toDouble(Object o)  {
        if (o == null) return 0;
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return 0; }
    }
    private ResponseEntity<Map<String, Object>> err(int status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }
}

package com.persona.service;

import com.persona.db.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AnalyticsService {

    private final Database db;

    @Autowired
    public AnalyticsService(Database db) {
        this.db = db;
    }

    public Map<String, Object> getAnalytics(String uid, String period) {
        int days = "month".equals(period) ? 30 : 7;
        String dtFromIso = LocalDateTime.now().minusDays(days).withHour(0).withMinute(0).withSecond(0).toString();

        // Tasks stats
        List<Map<String, Object>> tasks = db.query(
            "SELECT status, created_at FROM tasks WHERE user_id=? AND created_at>=?", uid, dtFromIso);
        Map<String, Integer> taskMap = new HashMap<>();
        for (Map<String, Object> t : tasks) {
            String st = (String) t.get("status");
            if (st != null) taskMap.merge(st, 1, Integer::sum);
        }
        int totalTasks = taskMap.values().stream().mapToInt(Integer::intValue).sum();
        if (totalTasks == 0) totalTasks = 1;
        int completedTasks = taskMap.getOrDefault("completed", 0);
        double completionRate = Math.round(((double) completedTasks / totalTasks) * 1000.0) / 10.0;

        // Focus stats
        List<Map<String, Object>> focusRows = db.query(
            "SELECT duration, started_at, completed, type FROM focus_sessions WHERE user_id=? AND started_at>=?", uid, dtFromIso);
        
        int focusMinutes = 0;
        int focusSessions = 0;
        Map<String, Integer> dailyFocusMap = new TreeMap<>();
        
        for (Map<String, Object> row : focusRows) {
            if (!"focus".equals(row.get("type"))) continue;
            Object compObj = row.get("completed");
            int comp = (compObj instanceof Number) ? ((Number) compObj).intValue() : 0;
            if (comp != 1) continue;
            
            Object durObj = row.get("duration");
            int dur = (durObj instanceof Number) ? ((Number) durObj).intValue() : 0;
            focusMinutes += dur;
            focusSessions++;

            Object startedObj = row.get("started_at");
            if (startedObj != null) {
                String dayStr = startedObj.toString().substring(0, 10);
                dailyFocusMap.merge(dayStr, dur, Integer::sum);
            }
        }
        double focusHours = Math.round((focusMinutes / 60.0) * 10.0) / 10.0;
        List<Map<String, Object>> dailyFocus = new ArrayList<>();
        dailyFocusMap.forEach((k, v) -> dailyFocus.add(Map.of("day", k, "mins", v)));

        // Expense stats
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate nextMonth = monthStart.plusMonths(1);
        List<Map<String, Object>> expensesRows = db.query(
            "SELECT category, amount, date FROM expenses WHERE user_id=? AND date>=? AND date<?",
            uid, monthStart.toString(), nextMonth.toString());
        
        Map<String, Double> byCategory = new TreeMap<>();
        double totalSpent = 0;
        for (Map<String, Object> row : expensesRows) {
            String cat = (String) row.getOrDefault("category", "Uncategorized");
            Object amtObj = row.get("amount");
            double amt = (amtObj instanceof Number) ? ((Number) amtObj).doubleValue() : 0.0;
            byCategory.merge(cat, amt, Double::sum);
            totalSpent += amt;
        }
        List<Map<String, Object>> expenses = new ArrayList<>();
        byCategory.forEach((k, v) -> expenses.add(Map.of("category", k, "total", v)));

        // Habit streaks
        List<Map<String, Object>> habitsRows = db.query("SELECT id, name FROM habits WHERE user_id=?", uid);
        List<Map<String, Object>> habitData = new ArrayList<>();
        for (Map<String, Object> h : habitsRows) {
            habitData.add(Map.of("name", h.get("name"), "streak", calcStreak((String) h.get("id"))));
        }

        // Productivity score
        int score = productivityScore(completionRate, focusHours, days, habitData.size());

        // Assignments due soon
        String dueCutoff = LocalDate.now().plusDays(7).toString();
        List<Map<String, Object>> dueSoonRows = db.query(
            "SELECT status FROM assignments WHERE user_id=? AND due_date<=?", uid, dueCutoff);
        int dueCount = (int) dueSoonRows.stream().filter(r -> "pending".equals(r.get("status"))).count();

        return Map.of(
            "period", period,
            "tasks", Map.of("total", totalTasks, "completed", completedTasks, "completion_rate", completionRate, "by_status", taskMap),
            "focus", Map.of("total_hours", focusHours, "total_sessions", focusSessions, "daily", dailyFocus),
            "expenses", Map.of("total_spent", Math.round(totalSpent * 100.0) / 100.0, "by_category", expenses),
            "habits", habitData,
            "productivity_score", score,
            "assignments_due_soon", dueCount
        );
    }

    private int productivityScore(double completionRate, double focusHours, int days, int habitCount) {
        double taskScore = completionRate * 0.4;
        double focusScore = Math.min(focusHours / (days * 2), 1.0) * 40.0;
        double habitScore = Math.min(habitCount * 5.0, 20.0);
        return (int) Math.min(taskScore + focusScore + habitScore, 100.0);
    }

    private int calcStreak(String habitId) {
        List<Map<String, Object>> logs = db.query("SELECT date, completed FROM habit_logs WHERE habit_id=? ORDER BY date DESC", habitId);
        if (logs.isEmpty()) return 0;
        
        Set<String> dates = new HashSet<>();
        for (Map<String, Object> row : logs) {
            Object compObj = row.get("completed");
            int comp = (compObj instanceof Number) ? ((Number) compObj).intValue() : 0;
            if (comp == 1 && row.get("date") != null) {
                dates.add(row.get("date").toString().substring(0, 10));
            }
        }
        
        int streak = 0;
        LocalDate check = LocalDate.now();
        while (dates.contains(check.toString())) {
            streak++;
            check = check.minusDays(1);
        }
        return streak;
    }
}

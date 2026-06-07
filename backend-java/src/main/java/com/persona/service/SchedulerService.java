package com.persona.service;

import com.persona.db.Database;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class SchedulerService {

    private final Database db;
    
    private static final Map<String, Integer> PRIORITY_WEIGHT = Map.of(
        "urgent", 4, "high", 3, "medium", 2, "low", 1
    );
    private static final int WORK_START = 8;
    private static final int WORK_END = 22;

    @Autowired
    public SchedulerService(Database db) {
        this.db = db;
    }

    private List<LocalDateTime[]> getBusySlots(String uid, LocalDate targetDate) {
        String dayStr = targetDate.toString();
        String dayAbbr = targetDate.getDayOfWeek().toString().substring(0, 3).toLowerCase();
        boolean isWeekend = dayAbbr.equals("sat") || dayAbbr.equals("sun");

        List<LocalDateTime[]> busy = new ArrayList<>();

        List<Map<String, Object>> tasks = db.query(
            "SELECT start_time, end_time FROM tasks WHERE user_id=? AND date(start_time)=? AND start_time IS NOT NULL", uid, dayStr);
        for (Map<String, Object> t : tasks) {
            try {
                LocalDateTime s = LocalDateTime.parse(t.get("start_time").toString().replace("Z", ""));
                Object etObj = t.get("end_time");
                LocalDateTime e = (etObj != null && !etObj.toString().isEmpty()) 
                    ? LocalDateTime.parse(etObj.toString().replace("Z", "")) 
                    : s.plusHours(1);
                busy.add(new LocalDateTime[]{s, e});
            } catch (Exception ignored) {}
        }

        if (!isWeekend) {
            List<Map<String, Object>> tt = db.query(
                "SELECT start_time, end_time FROM timetable WHERE user_id=? AND day_of_week=?", uid, dayAbbr);
            for (Map<String, Object> t : tt) {
                try {
                    LocalTime stTime = LocalTime.parse(t.get("start_time").toString());
                    LocalTime etTime = LocalTime.parse(t.get("end_time").toString());
                    busy.add(new LocalDateTime[]{targetDate.atTime(stTime), targetDate.atTime(etTime)});
                } catch (Exception ignored) {}
            }
        }

        busy.sort(Comparator.comparing(a -> a[0]));
        List<LocalDateTime[]> merged = new ArrayList<>();
        for (LocalDateTime[] current : busy) {
            if (merged.isEmpty()) {
                merged.add(current);
            } else {
                LocalDateTime[] prev = merged.get(merged.size() - 1);
                if (current[0].compareTo(prev[1]) <= 0) {
                    prev[1] = current[1].isAfter(prev[1]) ? current[1] : prev[1];
                } else {
                    merged.add(current);
                }
            }
        }
        return merged;
    }

    private long getDaysUntilDeadline(Map<String, Object> task) {
        Object dlObj = task.get("end_time");
        if (dlObj == null) dlObj = task.get("due_date");
        if (dlObj == null || dlObj.toString().isEmpty()) {
            return 9999;
        }
        try {
            LocalDate deadline = LocalDate.parse(dlObj.toString().substring(0, 10));
            return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), deadline);
        } catch (Exception e) {
            return 9999;
        }
    }

    private List<LocalDateTime> findFreeSlots(List<LocalDateTime[]> busy, LocalDate targetDate, int durationMins) {
        List<LocalDateTime> freeSlots = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // Morning Window: 09:00 to 12:00
        LocalDateTime w1Start = targetDate.atTime(9, 0);
        LocalDateTime w1End = targetDate.atTime(12, 0);
        
        // Evening Window: 16:00 to 22:00
        LocalDateTime w2Start = targetDate.atTime(16, 0);
        LocalDateTime w2End = targetDate.atTime(22, 0);

        List<LocalDateTime[]> windows = new ArrayList<>();
        windows.add(new LocalDateTime[]{w1Start, w1End});
        windows.add(new LocalDateTime[]{w2Start, w2End});

        for (LocalDateTime[] win : windows) {
            LocalDateTime current = win[0];
            LocalDateTime end = win[1];
            while (current.plusMinutes(durationMins).compareTo(end) <= 0) {
                // If targetDate is today, slot must start in the future
                if (targetDate.equals(now.toLocalDate()) && current.isBefore(now)) {
                    current = current.plusMinutes(30);
                    continue;
                }

                // Check overlap with busy slots
                boolean overlaps = false;
                LocalDateTime slotEnd = current.plusMinutes(durationMins);
                for (LocalDateTime[] b : busy) {
                    if (current.isBefore(b[1]) && slotEnd.isAfter(b[0])) {
                        overlaps = true;
                        break;
                    }
                }

                if (!overlaps) {
                    freeSlots.add(current);
                }
                current = current.plusMinutes(30);
            }
        }

        return freeSlots;
    }

    private void clearFuturePendingTasks(String uid) {
        String now = db.nowIso();
        db.execute("DELETE FROM task_blocks WHERE start_time > ? AND task_id IN (SELECT id FROM tasks WHERE user_id=? AND status='pending')", now, uid);
        db.execute("UPDATE tasks SET start_time=NULL, end_time=NULL, is_scheduled=0 WHERE user_id=? AND status='pending' AND start_time > ? AND is_scheduled=1", uid, now);
    }

    public Map<String, Object> autoScheduleAll(String uid) {
        clearFuturePendingTasks(uid);

        List<Map<String, Object>> pendingTasks = db.query("SELECT * FROM tasks WHERE user_id=? AND status='pending' AND start_time IS NULL", uid);
        
        if (pendingTasks.isEmpty()) {
            return Map.of("scheduled", List.of(), "explanation", "No pending tasks to schedule!", "tips", List.of());
        }

        pendingTasks = new ArrayList<>(pendingTasks);
        pendingTasks.sort((t1, t2) -> {
            long days1 = getDaysUntilDeadline(t1);
            long days2 = getDaysUntilDeadline(t2);

            boolean near1 = days1 <= 2;
            boolean near2 = days2 <= 2;

            if (near1 && !near2) return -1;
            if (!near1 && near2) return 1;

            int cmp = Long.compare(days1, days2);
            if (cmp != 0) return cmp;

            int p1 = PRIORITY_WEIGHT.getOrDefault(t1.get("priority"), 2);
            int p2 = PRIORITY_WEIGHT.getOrDefault(t2.get("priority"), 2);
            return Integer.compare(p2, p1);
        });

        List<Map<String, Object>> scheduled = new ArrayList<>();
        Random rand = new Random();

        for (Map<String, Object> task : pendingTasks) {
            Object dlObj = task.get("end_time");
            if (dlObj == null) dlObj = task.get("due_date");
            LocalDate deadline = LocalDate.now().plusDays(14);
            if (dlObj != null && !dlObj.toString().isEmpty()) {
                try {
                    deadline = LocalDate.parse(dlObj.toString().substring(0, 10));
                } catch (Exception ignored) {}
            }

            LocalDate target = LocalDate.now();
            String prioVal = (String) task.getOrDefault("priority", "medium");
            int sessionsNeeded = "urgent".equals(prioVal) ? 4 : "high".equals(prioVal) ? 3 : "medium".equals(prioVal) ? 2 : 1;
            int sessionsScheduled = 0;

            while (target.compareTo(deadline) <= 0 && sessionsScheduled < sessionsNeeded) {
                String dayAbbr = target.getDayOfWeek().toString().substring(0, 3).toLowerCase();
                boolean isWeekend = dayAbbr.equals("sat") || dayAbbr.equals("sun");
                int duration = isWeekend ? 90 : 60;

                List<LocalDateTime[]> busy = getBusySlots(uid, target);
                List<LocalDateTime> freeSlots = findFreeSlots(busy, target, duration);

                if (!freeSlots.isEmpty()) {
                    LocalDateTime slotStart = freeSlots.get(rand.nextInt(freeSlots.size()));
                    LocalDateTime slotEnd = slotStart.plusMinutes(duration);

                    String bid = db.newId();
                    db.execute("INSERT INTO task_blocks (id, task_id, start_time, end_time) VALUES (?, ?, ?, ?)",
                        bid, task.get("id"), slotStart.toString(), slotEnd.toString());
                    
                    db.execute("UPDATE tasks SET is_scheduled=1, updated_at=? WHERE id=?", db.nowIso(), task.get("id"));

                    scheduled.add(Map.of(
                        "task_id", task.get("id"),
                        "title", task.get("title"),
                        "start_time", slotStart.toString(),
                        "end_time", slotEnd.toString(),
                        "reason", (isWeekend ? "Weekend deep focus" : "Optimized available block") + " (Before deadline)"
                    ));
                    sessionsScheduled++;
                }
                target = target.plusDays(1);
            }
        }

        return Map.of(
            "scheduled", scheduled,
            "explanation", "I used priority-based allocation to schedule " + scheduled.size() + " tasks around your classes. Weekends have been allocated longer focus sessions.",
            "tips", List.of("Use weekends for longer 90-minute focus blocks", "Classes are strictly avoided during weekdays", "Google Classroom assignments will automatically slot in via deadline priority")
        );
    }
    
    public void autoScheduleTask(String uid, String tid) {
        autoScheduleAll(uid);
    }
}

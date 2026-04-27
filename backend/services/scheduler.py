"""
Persona — Smart Scheduling Service
Algorithm:
  1. Fetch user's timetable (blocked slots)
  2. Clear all currently scheduled FUTURE slots for pending tasks to allow dynamic recalculation.
  3. Sort tasks rigorously: (1) Nearest Deadline, (2) Priority Weight.
  4. Fill free slots (60 mins on weekdays, 90 mins on weekends).
  5. If weekend (Saturday/Sunday): skip timetable blocking completely.
"""
from datetime import datetime, timedelta, date
from database import db_query, db_execute, now_iso

# Priority weight (higher = schedule sooner)
PRIORITY_WEIGHT = {"urgent": 4, "high": 3, "medium": 2, "low": 1}
WORK_START      = 8    # 08:00
WORK_END        = 22   # 22:00

def _get_busy_slots(uid: str, target_date: date) -> list[tuple]:
    """
    Returns list of (start_dt, end_dt) busy intervals for target_date.
    On weekends (Sat, Sun), classes are ignored completely.
    """
    day_str  = target_date.isoformat()
    day_abbr = target_date.strftime("%a").lower()  # mon, tue, ...
    is_weekend = day_abbr in ("sat", "sun")

    busy = []

    # 1. Existing Tasks already scheduled today
    tasks = db_query(
        "SELECT start_time, end_time FROM tasks WHERE user_id=? AND date(start_time)=? AND start_time IS NOT NULL",
        (uid, day_str),
    )
    for t in tasks:
        try:
            s = datetime.fromisoformat(t["start_time"])
            e = datetime.fromisoformat(t["end_time"]) if t["end_time"] else s + timedelta(hours=1)
            busy.append((s, e))
        except Exception:
            pass

    # 2. Timetable Classes (only on weekdays)
    if not is_weekend:
        tt = db_query(
            "SELECT start_time, end_time FROM timetable WHERE user_id=? AND day_of_week=?",
            (uid, day_abbr),
        )
        for t in tt:
            try:
                s = datetime.combine(target_date, datetime.strptime(t["start_time"], "%H:%M").time())
                e = datetime.combine(target_date, datetime.strptime(t["end_time"],   "%H:%M").time())
                busy.append((s, e))
            except Exception:
                pass

    busy.sort(key=lambda x: x[0])
    # Merge overlapping busy slots to avoid logical gaps
    merged_busy = []
    for current in busy:
        if not merged_busy:
            merged_busy.append(current)
        else:
            prev = merged_busy[-1]
            if current[0] <= prev[1]:
                merged_busy[-1] = (prev[0], max(prev[1], current[1]))
            else:
                merged_busy.append(current)

    return merged_busy


def _find_free_slots(busy: list[tuple], target_date: date, duration_mins: int) -> list[datetime]:
    """
    Finds available slots of exactly `duration_mins` duration.
    """
    work_start = datetime.combine(target_date, datetime.strptime(f"{WORK_START:02d}:00", "%H:%M").time())
    work_end   = datetime.combine(target_date, datetime.strptime(f"{WORK_END:02d}:00",   "%H:%M").time())
    
    # If looking at today, can only schedule from NOW onwards
    now = datetime.now()
    if target_date == now.date():
        if now > work_start:
            # Round up to next 30 min block
            discard_minutes = now.minute % 30
            work_start = now + timedelta(minutes=(30 - discard_minutes))

    delta = timedelta(minutes=duration_mins)
    free_slots = []
    current = work_start

    for (bs, be) in busy:
        while current + delta <= bs:
            free_slots.append(current)
            current += delta
        current = max(current, be)

    # After last busy block
    while current + delta <= work_end:
        free_slots.append(current)
        current += delta

    return free_slots


def _clear_future_pending_tasks(uid: str):
    """
    To allow dynamic rescheduling, we unset any pending tasks 
    that were scheduled in the future, freeing up those slots.
    """
    now = now_iso()
    db_execute(
        "DELETE FROM task_blocks WHERE start_time > ? AND task_id IN "
        "(SELECT id FROM tasks WHERE user_id=? AND status='pending')",
        (now, uid)
    )
    db_execute(
        "UPDATE tasks SET start_time=NULL, end_time=NULL, is_scheduled=0 "
        "WHERE user_id=? AND status='pending' AND start_time > ? AND is_scheduled=1",
        (uid, now)
    )

def auto_schedule_all(uid: str) -> dict:
    """
    The main greedy scheduling engine.
    1. Unschedule all future pending tasks.
    2. Sort by (Deadlines, then Priority).
    3. Iterate and confidently assign tasks.
    """
    _clear_future_pending_tasks(uid)

    pending_tasks = db_query(
        "SELECT * FROM tasks WHERE user_id=? AND status='pending' AND start_time IS NULL",
        (uid,)
    )

    if not pending_tasks:
        return {"scheduled": [], "explanation": "No pending tasks to schedule!", "tips": []}

    # Sort priorities
    def sort_key(t):
        prio = PRIORITY_WEIGHT.get(t.get("priority", "medium"), 2)
        # Using 2099-12-31 as fallback so non-deadline tasks sink to the bottom
        deadline = t.get("end_time") or t.get("due_date") or "2099-12-31T23:59"
        return (deadline, -prio)

    pending_tasks.sort(key=sort_key)

    scheduled = []
    for task in pending_tasks:
        deadline_str = task.get("end_time") or task.get("due_date")
        if deadline_str:
            try:
                deadline = datetime.fromisoformat(deadline_str).date()
            except Exception:
                deadline = date.today() + timedelta(days=14)
        else:
            deadline = date.today() + timedelta(days=14)

        target = date.today()

        prio_val = task.get("priority", "medium")
        if prio_val == "urgent":
            sessions_needed = 4
        elif prio_val == "high":
            sessions_needed = 3
        elif prio_val == "medium":
            sessions_needed = 2
        else:
            sessions_needed = 1
            
        sessions_scheduled = 0

        while target <= deadline and sessions_scheduled < sessions_needed:
            is_weekend = target.strftime("%a").lower() in ("sat", "sun")
            # Weekend logic: Longer focus blocks
            duration = 90 if is_weekend else 60
            
            busy = _get_busy_slots(uid, target)
            free_slots = _find_free_slots(busy, target, duration_mins=duration)
            
            if free_slots:
                import random
                slot_start = random.choice(free_slots)
                slot_end   = slot_start + timedelta(minutes=duration)
                
                from database import new_id
                bid = new_id()
                db_execute(
                    "INSERT INTO task_blocks (id, task_id, start_time, end_time) VALUES (?, ?, ?, ?)",
                    (bid, task["id"], slot_start.isoformat(), slot_end.isoformat())
                )
                
                db_execute(
                    "UPDATE tasks SET is_scheduled=1, updated_at=? WHERE id=?",
                    (now_iso(), task["id"]),
                )
                scheduled.append({
                    "task_id": task["id"],
                    "title": task["title"],
                    "start_time": slot_start.isoformat(),
                    "end_time": slot_end.isoformat(),
                    "reason": f"{'Weekend deep focus' if is_weekend else 'Optimized available block'} (Before deadline)"
                })
                sessions_scheduled += 1
            
            target += timedelta(days=1)

    return {
        "scheduled": scheduled,
        "explanation": f"I used priority-based allocation to schedule {len(scheduled)} tasks around your classes. Weekends have been allocated longer focus sessions.",
        "tips": [
            "Use weekends for longer 90-minute focus blocks",
            "Classes are strictly avoided during weekdays",
            "Google Classroom assignments will automatically slot in via deadline priority"
        ]
    }


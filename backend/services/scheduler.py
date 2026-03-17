"""
Persona — Smart Scheduling Service
Algorithm:
  1. Fetch user's timetable (blocked slots)
  2. Build a list of busy intervals for the target date
  3. Fetch unscheduled tasks sorted by priority + deadline proximity
  4. Fill free 30-min / 60-min slots with tasks
"""
from datetime import datetime, timedelta, date
from database import db_query, db_execute, now_iso


# Priority weight (higher = schedule sooner)
PRIORITY_WEIGHT = {"urgent": 4, "high": 3, "medium": 2, "low": 1}
SLOT_DURATION   = 60   # minutes per auto-scheduled task slot
WORK_START      = 8    # 08:00
WORK_END        = 22   # 22:00


def _get_busy_slots(uid: str, target_date: date) -> list[tuple]:
    """
    Returns list of (start_dt, end_dt) busy intervals for target_date.
    Sources: existing tasks + timetable blocks.
    """
    day_str  = target_date.isoformat()
    day_abbr = target_date.strftime("%a").lower()  # mon, tue, ...

    busy = []

    # From tasks
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

    # From timetable
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
    return busy


def _find_free_slots(busy: list[tuple], target_date: date, duration_mins: int = SLOT_DURATION) -> list[datetime]:
    """
    Returns list of free slot start times (each `duration_mins` long).
    """
    work_start = datetime.combine(target_date, datetime.strptime(f"{WORK_START:02d}:00", "%H:%M").time())
    work_end   = datetime.combine(target_date, datetime.strptime(f"{WORK_END:02d}:00",   "%H:%M").time())
    delta      = timedelta(minutes=duration_mins)

    free_slots = []
    current    = work_start

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


def auto_schedule_task(uid: str, task_id: str):
    """
    Schedule a single task in the nearest available free slot.
    """
    rows = db_query("SELECT * FROM tasks WHERE id=?", (task_id,))
    if not rows:
        return
    task = rows[0]

    # Determine target date (use deadline if available, else today)
    if task.get("end_time"):
        try:
            deadline = datetime.fromisoformat(task["end_time"]).date()
        except Exception:
            deadline = date.today()
    else:
        deadline = date.today()

    # Try from today forward up to the deadline
    target = date.today()
    while target <= deadline:
        busy       = _get_busy_slots(uid, target)
        free_slots = _find_free_slots(busy, target)
        if free_slots:
            slot_start = free_slots[0]
            slot_end   = slot_start + timedelta(minutes=SLOT_DURATION)
            db_execute(
                "UPDATE tasks SET start_time=?, end_time=?, is_scheduled=1, updated_at=? WHERE id=?",
                (slot_start.isoformat(), slot_end.isoformat(), now_iso(), task_id),
            )
            return
        target += timedelta(days=1)


def auto_schedule_all(uid: str) -> list[dict]:
    """
    Auto-schedule all pending tasks without a start_time.
    Returns list of scheduled task ids.
    """
    tasks = db_query(
        "SELECT * FROM tasks WHERE user_id=? AND status='pending' AND start_time IS NULL ORDER BY "
        "CASE priority WHEN 'urgent' THEN 1 WHEN 'high' THEN 2 WHEN 'medium' THEN 3 ELSE 4 END",
        (uid,),
    )
    scheduled = []
    for task in tasks:
        auto_schedule_task(uid, task["id"])
        scheduled.append(task["id"])
    return scheduled

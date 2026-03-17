"""
Persona — Analytics Service
Computes productivity metrics for the dashboard.
"""
from datetime import datetime, date, timedelta
from database import db_query


def get_analytics(uid: str, period: str = "week") -> dict:
    if period == "month":
        days = 30
    else:
        days = 7

    dt_from = datetime.combine(date.today() - timedelta(days=days), datetime.min.time())
    dt_from_iso = dt_from.isoformat()

    # ── Task stats ────────────────────────────────────────────
    tasks = db_query(
        "SELECT status, created_at FROM tasks WHERE user_id=? AND created_at>=?",
        (uid, dt_from_iso),
    )
    task_map = {}
    for t in tasks:
        st = t.get("status")
        if not st:
            continue
        task_map[st] = task_map.get(st, 0) + 1
    total_tasks = sum(task_map.values()) or 1
    completed_tasks = task_map.get("completed", 0)
    completion_rate = round((completed_tasks / total_tasks) * 100, 1)

    # ── Focus stats ───────────────────────────────────────────
    focus_rows = db_query(
        "SELECT duration, started_at, completed, type FROM focus_sessions WHERE user_id=? AND started_at>=?",
        (uid, dt_from_iso),
    )
    focus_minutes = 0
    focus_sessions = 0
    daily_focus_map = {}
    for row in focus_rows:
        if str(row.get("type") or "") != "focus":
            continue
        if int(row.get("completed") or 0) != 1:
            continue
        duration = row.get("duration") or 0
        try:
            duration = int(duration)
        except Exception:
            duration = 0
        focus_minutes += duration
        focus_sessions += 1

        started_at = row.get("started_at")
        day_str = None
        if isinstance(started_at, str) and started_at:
            day_str = started_at[:10]
        elif isinstance(started_at, (datetime, date)):
            day_str = started_at.isoformat()[:10]
        if day_str:
            daily_focus_map[day_str] = daily_focus_map.get(day_str, 0) + duration

    focus_hours = round(focus_minutes / 60, 1)

    daily_focus = [
        {"day": day, "mins": mins}
        for day, mins in sorted(daily_focus_map.items(), key=lambda kv: kv[0])
    ]

    # ── Focus by day (for chart) ──────────────────────────────
    # ── Expense stats ─────────────────────────────────────────
    month_start = date.today().replace(day=1)
    next_month = (month_start.replace(day=28) + timedelta(days=4)).replace(day=1)
    expenses_rows = db_query(
        "SELECT category, amount, date FROM expenses WHERE user_id=? AND date>=? AND date<?",
        (uid, month_start.isoformat(), next_month.isoformat()),
    )
    by_category = {}
    total_spent = 0.0
    for row in expenses_rows:
        cat = row.get("category") or "Uncategorized"
        amt = row.get("amount") or 0
        try:
            amt = float(amt)
        except Exception:
            amt = 0.0
        by_category[cat] = by_category.get(cat, 0.0) + amt
        total_spent += amt
    expenses = [{"category": k, "total": v} for k, v in sorted(by_category.items(), key=lambda kv: kv[0])]

    # ── Habit streaks ─────────────────────────────────────────
    habits = db_query("SELECT id, name FROM habits WHERE user_id=?", (uid,))
    habit_data = []
    for h in habits:
        streak = _calc_streak(h["id"])
        habit_data.append({"name": h["name"], "streak": streak})

    # ── Productivity score (0-100) ────────────────────────────
    score = _productivity_score(completion_rate, focus_hours, days, len(habit_data))

    # ── Assignments due soon ──────────────────────────────────
    due_soon_rows = db_query(
        "SELECT status, due_date FROM assignments WHERE user_id=? AND due_date<=?",
        (uid, (date.today() + timedelta(days=7)).isoformat()),
    )
    due_count = 0
    for row in due_soon_rows:
        if (row.get("status") or "") == "pending":
            due_count += 1

    return {
        "period": period,
        "tasks": {
            "total": total_tasks,
            "completed": completed_tasks,
            "completion_rate": completion_rate,
            "by_status": task_map,
        },
        "focus": {
            "total_hours": focus_hours,
            "total_sessions": focus_sessions,
            "daily": [dict(r) for r in daily_focus],
        },
        "expenses": {
            "total_spent": round(total_spent, 2),
            "by_category": [dict(r) for r in expenses],
        },
        "habits": habit_data,
        "productivity_score": score,
        "assignments_due_soon": due_count,
    }


def _productivity_score(completion_rate: float, focus_hours: float, days: int, habit_count: int) -> int:
    """Simple weighted score 0-100."""
    task_score  = completion_rate * 0.4
    focus_score = min(focus_hours / (days * 2), 1.0) * 40   # max 2 hrs/day = perfect
    habit_score = min(habit_count * 5, 20)                   # each habit = 5 pts, max 20
    return int(min(task_score + focus_score + habit_score, 100))


def _calc_streak(habit_id: str) -> int:
    from datetime import date, timedelta
    logs = db_query(
        "SELECT date, completed FROM habit_logs WHERE habit_id=? ORDER BY date DESC",
        (habit_id,),
    )
    if not logs:
        return 0
    streak = 0
    check  = date.today()
    dates = {
        (row.get("date") or "")[:10]
        for row in logs
        if int(row.get("completed") or 0) == 1 and row.get("date")
    }
    while check.isoformat() in dates:
        streak += 1
        check  -= timedelta(days=1)
    return streak

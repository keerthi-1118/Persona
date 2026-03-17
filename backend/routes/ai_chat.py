"""
Persona — AI Chat & Scheduling Route
"""
from flask import Blueprint, request, jsonify, session
from database import db_query, new_id, now_iso
from services.ai_scheduler import schedule_tasks, chat_with_ai

ai_bp = Blueprint("ai", __name__)


@ai_bp.route("/schedule", methods=["POST"])
def ai_schedule():
    """AI auto-schedules all pending tasks using Gemini."""
    user_id = session.get("user_id", "guest")

    # Fetch pending tasks and filter in python
    all_pending = db_query("SELECT * FROM tasks WHERE user_id=?", (user_id,))
    tasks = [
        t for t in all_pending
        if t.get("status") in ("pending", "in_progress")
    ]
    # Sort by priority DESC, due_date ASC (Python sorting since REST order parsing is basic)
    pri_map = {"urgent": 4, "high": 3, "medium": 2, "low": 1}
    tasks.sort(key=lambda x: (
        -pri_map.get(x.get("priority", ""), 2),
        x.get("due_date") or "9999-12-31"
    ))

    # Fetch existing scheduled tasks
    all_tasks = db_query("SELECT * FROM tasks WHERE user_id=?", (user_id,))
    existing = []
    now = now_iso()
    for t in all_tasks:
        st = t.get("start_time")
        if st and str(st) >= now:
            existing.append({
                "title": t.get("title"),
                "start_time": st,
                "end_time": t.get("end_time")
            })

    # Fetch timetable
    timetable = db_query("SELECT * FROM timetable WHERE user_id=?", (user_id,))

    if not tasks:
        return jsonify({"explanation": "You have no pending tasks to schedule! Add some tasks first.", "scheduled": []})

    try:
        result = schedule_tasks(list(tasks), list(existing), list(timetable))

        # Apply the schedule to the database
        scheduled_count = 0
        for slot in result.get("scheduled", []):
            task_id = slot.get("task_id")
            if not task_id: continue
            db_execute = __import__("database").db_execute
            db_execute(
                "UPDATE tasks SET start_time=?, end_time=? WHERE id=? AND user_id=?",
                (slot.get("start_time"), slot.get("end_time"), task_id, user_id)
            )
            scheduled_count += 1

        return jsonify({
            "message":      f"AI scheduled {scheduled_count} tasks for you!",
            "explanation":  result.get("explanation", "Your tasks have been scheduled."),
            "tips":         result.get("tips", []),
            "scheduled":    result.get("scheduled", []),
        })

    except RuntimeError as e:
        # Gemini not configured — return helpful message
        return jsonify({
            "explanation": str(e),
            "scheduled":   [],
        }), 503

    except Exception as e:
        return jsonify({"error": str(e)}), 500


@ai_bp.route("/chat", methods=["POST"])
def ai_chat():
    """Free-form AI chat about scheduling and productivity."""
    user_id = session.get("user_id", "guest")
    data    = request.get_json() or {}
    message = data.get("message", "").strip()

    if not message:
        return jsonify({"error": "Message is required"}), 400

    # Gather context
    tasks = db_query(
        "SELECT title, priority, due_date, status FROM tasks WHERE user_id=? AND status != 'completed' LIMIT 10",
        (user_id,)
    )

    try:
        result = chat_with_ai(message, {"tasks": list(tasks)})
        return jsonify(result)
    except RuntimeError as e:
        return jsonify({"reply": str(e)}), 503
    except Exception as e:
        return jsonify({"error": str(e)}), 500

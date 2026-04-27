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

    try:
        from services.scheduler import auto_schedule_all
        result = auto_schedule_all(user_id)

        return jsonify({
            "message":      f"Smart Scheduler allocated {len(result.get('scheduled', []))} tasks!",
            "explanation":  result.get("explanation", "Your tasks have been scheduled."),
            "tips":         result.get("tips", []),
            "scheduled":    result.get("scheduled", []),
        }), 200

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

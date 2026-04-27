"""
Persona — Planner Routes
Exposes /api/planner/generate as the canonical AI schedule endpoint.
Also exposes /api/reminders for fetching upcoming task reminders.
"""
from flask import Blueprint, request, jsonify, session
from database import db_query, db_execute, now_iso

planner_bp = Blueprint("planner", __name__)


@planner_bp.route("/generate", methods=["POST"])
def generate_schedule():
    """
    AI-powered schedule generation.
    Delegates to the Gemini AI scheduler and writes results to DB.
    Frontend can also call /api/ai/schedule directly — this is an alias.
    """
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401

    # Fetch pending tasks
    all_tasks = db_query(
        "SELECT * FROM tasks WHERE user_id=? AND status IN ('pending','in_progress')",
        (uid,)
    )
    pri_map = {"urgent": 4, "high": 3, "medium": 2, "low": 1}
    tasks = sorted(
        all_tasks,
        key=lambda t: (-pri_map.get(t.get("priority", ""), 2), t.get("end_time") or "9999")
    )

    # Fetch existing scheduled tasks (for conflict avoidance)
    now = now_iso()
    existing = [
        {"title": t["title"], "start_time": t["start_time"], "end_time": t["end_time"]}
        for t in db_query("SELECT title, start_time, end_time FROM tasks WHERE user_id=?", (uid,))
        if t.get("start_time") and str(t["start_time"]) >= now
    ]

    # Fetch timetable (blocked slots)
    timetable = db_query("SELECT * FROM timetable WHERE user_id=?", (uid,))

    if not tasks:
        return jsonify({
            "explanation": "No pending tasks to schedule! Add some tasks first.",
            "scheduled": [],
            "tips": []
        }), 200

    try:
        from services.scheduler import auto_schedule_all
        result = auto_schedule_all(uid)

        # The result is now a dict containing 'scheduled', 'explanation', and 'tips'

        return jsonify({
            "message":     f"Smart Planner scheduled {len(result.get('scheduled', []))} tasks!",
            "explanation": result.get("explanation", "Your tasks have been scheduled."),
            "tips":        result.get("tips", []),
            "scheduled":   result.get("scheduled", []),
        }), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500


@planner_bp.route("/reminders", methods=["GET"])
def get_reminders():
    """
    Return tasks starting in the next 60 minutes (for the frontend to schedule local reminders).
    """
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401

    from datetime import datetime, timedelta
    now = datetime.utcnow()
    soon = (now + timedelta(minutes=60)).isoformat()
    now_str = now.isoformat()

    tasks = db_query(
        "SELECT id, title, start_time, end_time, priority FROM tasks "
        "WHERE user_id=? AND status='pending' AND start_time >= ? AND start_time <= ?",
        (uid, now_str, soon)
    )
    return jsonify(tasks), 200

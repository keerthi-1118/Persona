"""
Persona — Focus Sessions Routes (Pomodoro tracking)
"""
from flask import Blueprint, request, jsonify, session
from database import db_query, db_execute, new_id, now_iso

focus_bp = Blueprint("focus", __name__)


@focus_bp.route("/", methods=["GET"])
def get_sessions():
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    date = request.args.get("date", now_iso()[:10])
    return jsonify(db_query(
        "SELECT * FROM focus_sessions WHERE user_id=? AND date(started_at)=? ORDER BY started_at DESC",
        (uid, date)
    )), 200


@focus_bp.route("/start", methods=["POST"])
def start_session():
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    data = request.json or {}
    sid  = new_id()
    db_execute(
        "INSERT INTO focus_sessions (id, user_id, task_id, duration, type, started_at, completed, created_at) VALUES (?,?,?,?,?,?,?,?)",
        (sid, uid, data.get("task_id"), int(data.get("duration", 25)),
         data.get("type", "focus"), now_iso(), 0, now_iso()),
    )
    return jsonify({"session_id": sid, "started_at": now_iso()}), 201


@focus_bp.route("/<sid>/end", methods=["PATCH"])
def end_session(sid):
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    data = request.json or {}
    completed = 1 if data.get("completed", True) else 0
    db_execute(
        "UPDATE focus_sessions SET ended_at=?, completed=? WHERE id=? AND user_id=?",
        (now_iso(), completed, sid, uid),
    )
    return jsonify({"message": "Session ended"}), 200


@focus_bp.route("/today-summary", methods=["GET"])
def today_summary():
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    today = now_iso()[:10]
    rows  = db_query(
        "SELECT SUM(duration) as total_mins, COUNT(*) as sessions FROM focus_sessions WHERE user_id=? AND date(started_at)=? AND completed=1 AND type='focus'",
        (uid, today)
    )
    r = rows[0] if rows else {}
    return jsonify({"total_minutes": r.get("total_mins") or 0, "sessions": r.get("sessions") or 0}), 200

"""
Persona — Habits Routes
"""
from flask import Blueprint, request, jsonify, session
from database import db_query, db_execute, new_id, now_iso

habits_bp = Blueprint("habits", __name__)


@habits_bp.route("/", methods=["GET"])
def get_habits():
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    habits = db_query("SELECT * FROM habits WHERE user_id = ? ORDER BY created_at DESC", (uid,))
    today = now_iso()[:10]
    for h in habits:
        # Attach today's completion status
        logs = db_query("SELECT id FROM habit_logs WHERE habit_id=? AND date=? AND completed=1", (h["id"], today))
        h["done_today"] = len(logs) > 0
        # Calculate streak
        h["streak"] = _calc_streak(h["id"])
    return jsonify(habits), 200


@habits_bp.route("/", methods=["POST"])
def create_habit():
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    data = request.json or {}
    if not data.get("name"): return jsonify({"error": "name is required"}), 400
    hid = new_id()
    db_execute(
        "INSERT INTO habits (id, user_id, name, icon, color, target_days, created_at) VALUES (?,?,?,?,?,?,?)",
        (hid, uid, data["name"], data.get("icon", "⭐"), data.get("color", "#6C63FF"),
         data.get("target_days", '["mon","tue","wed","thu","fri","sat","sun"]'), now_iso()),
    )
    rows = db_query("SELECT * FROM habits WHERE id = ?", (hid,))
    return jsonify(rows[0] if rows else {"id": hid}), 201


@habits_bp.route("/<hid>", methods=["DELETE"])
def delete_habit(hid):
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    db_execute("DELETE FROM habits WHERE id=? AND user_id=?", (hid, uid))
    return jsonify({"message": "Habit deleted"}), 200


@habits_bp.route("/<hid>/check", methods=["PATCH"])
def check_habit(hid):
    """Toggle today's completion for a habit."""
    uid   = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    today = now_iso()[:10]
    logs  = db_query("SELECT id, completed FROM habit_logs WHERE habit_id=? AND date=?", (hid, today))
    if logs:
        new_val = 0 if logs[0]["completed"] else 1
        db_execute("UPDATE habit_logs SET completed=? WHERE id=?", (new_val, logs[0]["id"]))
        done = bool(new_val)
    else:
        db_execute(
            "INSERT INTO habit_logs (id, habit_id, user_id, date, completed) VALUES (?,?,?,?,?)",
            (new_id(), hid, uid, today, 1),
        )
        done = True
    return jsonify({"done_today": done, "streak": _calc_streak(hid)}), 200


def _calc_streak(habit_id: str) -> int:
    """Return current consecutive day streak for a habit."""
    from datetime import date, timedelta
    logs = db_query(
        "SELECT date FROM habit_logs WHERE habit_id=? AND completed=1 ORDER BY date DESC", (habit_id,)
    )
    if not logs:
        return 0
    streak = 0
    check  = date.today()
    dates  = {row["date"][:10] for row in logs}
    while check.isoformat() in dates:
        streak += 1
        check  -= timedelta(days=1)
    return streak

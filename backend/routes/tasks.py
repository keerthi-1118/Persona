"""
Persona — Tasks Routes
Full CRUD for tasks. Optionally triggers smart scheduler on create.
"""
from flask import Blueprint, request, jsonify, session
from database import db_query, db_execute, new_id, now_iso
from models import validate_task

tasks_bp = Blueprint("tasks", __name__)


def _require_auth():
    uid = session.get("user_id")
    if not uid:
        return None, jsonify({"error": "Authentication required"}), 401
    return uid, None, None


# ── GET /api/tasks ────────────────────────────────────────────
@tasks_bp.route("/", methods=["GET"])
def get_tasks():
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401

    status   = request.args.get("status")
    priority = request.args.get("priority")
    date     = request.args.get("date")      # YYYY-MM-DD

    sql    = "SELECT * FROM tasks WHERE user_id = ?"
    params = [uid]

    if status:
        sql += " AND status = ?"
        params.append(status)

    # Fetch tasks from Supabase REST API
    tasks = db_query(sql, tuple(params))

    # Filter by date and priority in Python because REST API doesn't support complex SQL functions easily
    if priority:
        tasks = [t for t in tasks if t.get("priority") == priority]
    if date:
        tasks = [t for t in tasks if str(t.get("start_time", "")).startswith(date)]

    # Sort in Python by priority and then start_time
    pri_map = {"urgent": 1, "high": 2, "medium": 3, "low": 4}
    tasks.sort(key=lambda x: (
        pri_map.get(x.get("priority", ""), 5),
        x.get("start_time") or "9999-12-31"
    ))

    return jsonify(tasks), 200


# ── POST /api/tasks ───────────────────────────────────────────
@tasks_bp.route("/", methods=["POST"])
def create_task():
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401

    data   = request.json or {}
    errors = validate_task(data)
    if errors:
        return jsonify({"errors": errors}), 400

    tid = new_id()
    db_execute(
        """INSERT INTO tasks
           (id, user_id, title, description, start_time, end_time, priority, status, category, created_at, updated_at)
           VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
        (tid, uid,
         data["title"], data.get("description"),
         data.get("start_time"), data.get("end_time"),
         data.get("priority", "medium"), data.get("status", "pending"),
         data.get("category", "general"), now_iso(), now_iso()),
    )

    # Auto-schedule if no start_time provided
    if not data.get("start_time"):
        try:
            from services.scheduler import auto_schedule_task
            auto_schedule_task(uid, tid)
        except Exception as e:
            print(f"Scheduler warning: {e}")

    rows = db_query("SELECT * FROM tasks WHERE id = ?", (tid,))
    return jsonify(rows[0] if rows else {"id": tid}), 201


# ── PUT /api/tasks/<id> ───────────────────────────────────────
@tasks_bp.route("/<task_id>", methods=["PUT"])
def update_task(task_id):
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401

    rows = db_query("SELECT id FROM tasks WHERE id = ? AND user_id = ?", (task_id, uid))
    if not rows:
        return jsonify({"error": "Task not found"}), 404

    data = request.json or {}
    fields = []
    params = []

    for field in ["title", "description", "start_time", "end_time", "priority", "status", "category"]:
        if field in data:
            fields.append(f"{field} = ?")
            params.append(data[field])

    if not fields:
        return jsonify({"error": "No fields to update"}), 400

    fields.append("updated_at = ?")
    params.append(now_iso())
    params.append(task_id)

    db_execute(f"UPDATE tasks SET {', '.join(fields)} WHERE id = ?", tuple(params))
    rows = db_query("SELECT * FROM tasks WHERE id = ?", (task_id,))
    return jsonify(rows[0]), 200


# ── DELETE /api/tasks/<id> ────────────────────────────────────
@tasks_bp.route("/<task_id>", methods=["DELETE"])
def delete_task(task_id):
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401

    db_execute("DELETE FROM tasks WHERE id = ? AND user_id = ?", (task_id, uid))
    return jsonify({"message": "Task deleted"}), 200


# ── PATCH /api/tasks/<id>/complete ───────────────────────────
@tasks_bp.route("/<task_id>/complete", methods=["PATCH"])
def complete_task(task_id):
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    db_execute("UPDATE tasks SET status='completed', updated_at=? WHERE id=? AND user_id=?",
               (now_iso(), task_id, uid))
    return jsonify({"message": "Task marked complete"}), 200

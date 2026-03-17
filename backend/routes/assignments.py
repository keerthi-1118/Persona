"""
Persona — Assignments Routes
Manual CRUD + Google Classroom sync entry point.
"""
from flask import Blueprint, request, jsonify, session
from database import db_query, db_execute, new_id, now_iso

assignments_bp = Blueprint("assignments", __name__)


@assignments_bp.route("/", methods=["GET"])
def get_assignments():
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401

    status = request.args.get("status")
    sql    = "SELECT * FROM assignments WHERE user_id = ?"
    params = [uid]
    if status:
        sql += " AND status = ?"
        params.append(status)
    sql += " ORDER BY due_date ASC"
    return jsonify(db_query(sql, tuple(params))), 200


@assignments_bp.route("/", methods=["POST"])
def create_assignment():
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401

    data = request.json or {}
    if not data.get("title") or not data.get("course_name"):
        return jsonify({"error": "title and course_name are required"}), 400

    aid = new_id()
    db_execute(
        """INSERT INTO assignments
           (id, user_id, course_name, title, description, due_date, status, source, created_at, updated_at)
           VALUES (?,?,?,?,?,?,?,?,?,?)""",
        (aid, uid, data["course_name"], data["title"],
         data.get("description"), data.get("due_date"),
         data.get("status", "pending"), "manual", now_iso(), now_iso()),
    )
    rows = db_query("SELECT * FROM assignments WHERE id = ?", (aid,))
    return jsonify(rows[0] if rows else {"id": aid}), 201


@assignments_bp.route("/<aid>", methods=["PUT"])
def update_assignment(aid):
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401

    rows = db_query("SELECT id FROM assignments WHERE id=? AND user_id=?", (aid, uid))
    if not rows:
        return jsonify({"error": "Assignment not found"}), 404

    data = request.json or {}
    allowed = ["title", "description", "due_date", "status", "course_name"]
    fields, params = [], []
    for field in allowed:
        if field in data:
            fields.append(f"{field} = ?")
            params.append(data[field])
    if not fields:
        return jsonify({"error": "No fields to update"}), 400
    fields.append("updated_at = ?")
    params.extend([now_iso(), aid])
    db_execute(f"UPDATE assignments SET {', '.join(fields)} WHERE id = ?", tuple(params))
    rows = db_query("SELECT * FROM assignments WHERE id = ?", (aid,))
    return jsonify(rows[0]), 200


@assignments_bp.route("/<aid>", methods=["DELETE"])
def delete_assignment(aid):
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    db_execute("DELETE FROM assignments WHERE id=? AND user_id=?", (aid, uid))
    return jsonify({"message": "Assignment deleted"}), 200


@assignments_bp.route("/sync/classroom", methods=["POST"])
def sync_from_classroom():
    """Trigger a Google Classroom sync for the current user."""
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    try:
        from services.classroom_api import sync_assignments
        count = sync_assignments(uid)
        return jsonify({"message": f"Synced {count} assignments from Google Classroom"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 400

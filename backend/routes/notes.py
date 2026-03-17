"""
Persona — Notes Routes
"""
from flask import Blueprint, request, jsonify, session
from database import db_query, db_execute, new_id, now_iso

notes_bp = Blueprint("notes", __name__)


@notes_bp.route("/", methods=["GET"])
def get_notes():
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    return jsonify(db_query(
        "SELECT * FROM notes WHERE user_id=? ORDER BY pinned DESC, updated_at DESC", (uid,)
    )), 200


@notes_bp.route("/", methods=["POST"])
def create_note():
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    data = request.json or {}
    nid  = new_id()
    db_execute(
        "INSERT INTO notes (id, user_id, title, content, tags, color, pinned, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
        (nid, uid, data.get("title", "Untitled Note"), data.get("content", ""),
         data.get("tags", "[]"), data.get("color", "#1e1e2e"),
         data.get("pinned", 0), now_iso(), now_iso()),
    )
    rows = db_query("SELECT * FROM notes WHERE id = ?", (nid,))
    return jsonify(rows[0] if rows else {"id": nid}), 201


@notes_bp.route("/<nid>", methods=["PUT"])
def update_note(nid):
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    rows = db_query("SELECT id FROM notes WHERE id=? AND user_id=?", (nid, uid))
    if not rows:
        return jsonify({"error": "Note not found"}), 404
    data = request.json or {}
    allowed = ["title", "content", "tags", "color", "pinned"]
    fields, params = [], []
    for f in allowed:
        if f in data:
            fields.append(f"{f} = ?")
            params.append(data[f])
    fields.append("updated_at = ?")
    params.extend([now_iso(), nid])
    db_execute(f"UPDATE notes SET {', '.join(fields)} WHERE id=?", tuple(params))
    rows = db_query("SELECT * FROM notes WHERE id=?", (nid,))
    return jsonify(rows[0]), 200


@notes_bp.route("/<nid>", methods=["DELETE"])
def delete_note(nid):
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    db_execute("DELETE FROM notes WHERE id=? AND user_id=?", (nid, uid))
    return jsonify({"message": "Note deleted"}), 200

"""
Persona — Timetable Routes
Handles the fixed weekly schedule (classes, labs, recurring blocks).
"""
from flask import Blueprint, request, jsonify, session
from database import db_query, db_execute, new_id

from functools import wraps

timetable_bp = Blueprint("timetable", __name__)

def current_user_id() -> str:
    return session.get("user_id", "")

def require_auth(f):
    @wraps(f)
    def wrap(*args, **kwargs):
        if not current_user_id():
            return jsonify({"error": "Unauthorized"}), 401
        return f(*args, **kwargs)
    return wrap

@timetable_bp.route("/", methods=["GET"])
@require_auth
def get_timetable():
    uid = current_user_id()
    rows = db_query("SELECT * FROM timetable WHERE user_id = ?", (uid,))
    return jsonify(rows), 200

@timetable_bp.route("/", methods=["POST"])
@require_auth
def add_class():
    uid = current_user_id()
    data = request.json or {}
    
    day = data.get("day_of_week")
    start = data.get("start_time")
    end = data.get("end_time")
    label = data.get("label", "Class")
    ttype = data.get("type", "class")
    
    if not day or not start or not end:
        return jsonify({"error": "day_of_week, start_time, and end_time are required"}), 400
        
    tid = new_id()
    db_execute(
        "INSERT INTO timetable (id, user_id, day_of_week, start_time, end_time, label, type) VALUES (?,?,?,?,?,?,?)",
        (tid, uid, day, start, end, label, ttype)
    )
    return jsonify({"message": "Timetable block added!", "id": tid}), 201

@timetable_bp.route("/<block_id>", methods=["PUT"])
@require_auth
def update_class(block_id):
    uid = current_user_id()
    data = request.json or {}
    
    existing = db_query("SELECT id FROM timetable WHERE id=? AND user_id=?", (block_id, uid))
    if not existing:
        return jsonify({"error": "Block not found"}), 404
        
    db_execute(
        "UPDATE timetable SET day_of_week=?, start_time=?, end_time=?, label=?, type=? WHERE id=?",
        (data.get("day_of_week"), data.get("start_time"), data.get("end_time"), data.get("label"), data.get("type"), block_id)
    )
    return jsonify({"message": "Block updated successfully"}), 200

@timetable_bp.route("/<block_id>", methods=["DELETE"])
@require_auth
def delete_class(block_id):
    uid = current_user_id()
    db_execute("DELETE FROM timetable WHERE id=? AND user_id=?", (block_id, uid))
    return jsonify({"message": "Block deleted"}), 200

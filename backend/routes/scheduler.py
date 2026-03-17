"""
Persona — Scheduler Routes
"""
from flask import Blueprint, request, jsonify, session
from services.scheduler import auto_schedule_all

scheduler_bp = Blueprint("scheduler", __name__)


@scheduler_bp.route("/auto-schedule", methods=["POST"])
def auto_schedule():
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    scheduled = auto_schedule_all(uid)
    return jsonify({"scheduled": scheduled, "message": f"{len(scheduled)} tasks scheduled"}), 200

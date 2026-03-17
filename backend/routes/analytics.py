"""
Persona — Analytics Routes
"""
from flask import Blueprint, request, jsonify, session
from services.analytics import get_analytics

analytics_bp = Blueprint("analytics", __name__)


@analytics_bp.route("/", methods=["GET"])
def dashboard_analytics():
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    period = request.args.get("period", "week")   # week | month
    return jsonify(get_analytics(uid, period)), 200

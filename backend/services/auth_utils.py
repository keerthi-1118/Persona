"""
Persona — Auth Utilities
Shared decorator and helpers for protected routes.
"""
from functools import wraps
from flask import session, jsonify
from database import db_query


def require_auth(f):
    """Decorator — returns 401 JSON if user is not logged in."""
    @wraps(f)
    def decorated(*args, **kwargs):
        if "user_id" not in session:
            return jsonify({"error": "Authentication required"}), 401
        return f(*args, **kwargs)
    return decorated


def current_user_id() -> str | None:
    return session.get("user_id")


def current_user() -> dict | None:
    uid = session.get("user_id")
    if not uid:
        return None
    rows = db_query(
        "SELECT id, username, email, avatar_url FROM users WHERE id = ?", (uid,)
    )
    return rows[0] if rows else None

"""
Persona — Push Notification Routes
Stores web push subscriptions and sends reminders.
Requires: pywebpush, cryptography (already in requirements.txt)
VAPID keys must be in .env:
  VAPID_PRIVATE_KEY=...
  VAPID_PUBLIC_KEY=...
  VAPID_CLAIMS_EMAIL=mailto:admin@persona.app
"""
import os
import json
from flask import Blueprint, request, jsonify, session
from database import db_query, db_execute, new_id, now_iso

push_bp = Blueprint("push", __name__)

VAPID_PRIVATE_KEY   = os.getenv("VAPID_PRIVATE_KEY", "")
VAPID_PUBLIC_KEY    = os.getenv("VAPID_PUBLIC_KEY", "")
VAPID_CLAIMS_EMAIL  = os.getenv("VAPID_CLAIMS_EMAIL", "mailto:admin@persona.app")


@push_bp.route("/vapid-public-key", methods=["GET"])
def get_vapid_public_key():
    """Return the VAPID public key so the frontend can subscribe to push."""
    return jsonify({"public_key": VAPID_PUBLIC_KEY}), 200


@push_bp.route("/subscribe", methods=["POST"])
def subscribe():
    """Store push subscription for a user."""
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401

    sub = request.json or {}
    endpoint   = sub.get("endpoint", "")
    p256dh     = sub.get("keys", {}).get("p256dh", "")
    auth_key   = sub.get("keys", {}).get("auth", "")

    if not endpoint:
        return jsonify({"error": "endpoint is required"}), 400

    # Upsert subscription
    existing = db_query("SELECT id FROM push_subscriptions WHERE user_id=? AND endpoint=?", (uid, endpoint))
    if existing:
        db_execute(
            "UPDATE push_subscriptions SET p256dh=?, auth=?, updated_at=? WHERE id=?",
            (p256dh, auth_key, now_iso(), existing[0]["id"]),
        )
    else:
        db_execute(
            "INSERT INTO push_subscriptions (id, user_id, endpoint, p256dh, auth, updated_at) VALUES (?,?,?,?,?,?)",
            (new_id(), uid, endpoint, p256dh, auth_key, now_iso()),
        )
    return jsonify({"message": "Subscribed to push notifications"}), 200


@push_bp.route("/unsubscribe", methods=["POST"])
def unsubscribe():
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    data = request.json or {}
    endpoint = data.get("endpoint", "")
    if endpoint:
        db_execute("DELETE FROM push_subscriptions WHERE user_id=? AND endpoint=?", (uid, endpoint))
    return jsonify({"message": "Unsubscribed"}), 200


@push_bp.route("/send-reminder", methods=["POST"])
def send_reminder():
    """Send a push notification to all subscriptions of a user."""
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401

    data    = request.json or {}
    title   = data.get("title", "Persona Reminder")
    body    = data.get("body", "")
    url     = data.get("url", "/planner")

    subs = db_query("SELECT * FROM push_subscriptions WHERE user_id=?", (uid,))
    if not subs:
        return jsonify({"message": "No subscriptions found"}), 200

    if not VAPID_PRIVATE_KEY:
        return jsonify({"error": "VAPID_PRIVATE_KEY not configured"}), 503

    sent = 0
    errors = []
    for sub in subs:
        try:
            from pywebpush import webpush, WebPushException
            webpush(
                subscription_info={
                    "endpoint": sub["endpoint"],
                    "keys": {"p256dh": sub["p256dh"], "auth": sub["auth"]},
                },
                data=json.dumps({"title": title, "body": body, "url": url}),
                vapid_private_key=VAPID_PRIVATE_KEY,
                vapid_claims={"sub": VAPID_CLAIMS_EMAIL},
            )
            sent += 1
        except Exception as e:
            errors.append(str(e))

    return jsonify({"sent": sent, "errors": errors}), 200

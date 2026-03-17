"""
Persona — Auth Routes  v4
Aligned to the Supabase users table schema (schema_postgres.sql):
  id, username, email, password, google_id, avatar_url, created_at, updated_at
"""
import os
from flask import Blueprint, request, jsonify, session, redirect
from werkzeug.security import generate_password_hash, check_password_hash
from database import db_query, db_execute, new_id, now_iso

auth_bp = Blueprint("auth", __name__)

GOOGLE_CLIENT_ID     = os.getenv("GOOGLE_CLIENT_ID", "")
GOOGLE_CLIENT_SECRET = os.getenv("GOOGLE_CLIENT_SECRET", "")
GOOGLE_REDIRECT_URI  = os.getenv("GOOGLE_REDIRECT_URI", "http://localhost:5000/api/auth/google/callback")


# ── Register ──────────────────────────────────────────────────
@auth_bp.route("/register", methods=["POST"])
def register():
    data     = request.json or {}
    username = data.get("username", "").strip()
    email    = data.get("email", "").strip().lower()
    password = data.get("password", "")

    if not username or not email or not password:
        return jsonify({"error": "Name, email and password are required"}), 400
    if len(password) < 6:
        return jsonify({"error": "Password must be at least 6 characters"}), 400

    existing = db_query("SELECT id FROM users WHERE email = ?", (email,))
    if existing:
        return jsonify({"error": "Email already registered. Please log in."}), 409

    uid = new_id()
    db_execute(
        "INSERT INTO users (id, username, email, password, created_at, updated_at) VALUES (?,?,?,?,?,?)",
        (uid, username, email, generate_password_hash(password), now_iso(), now_iso()),
    )
    session.permanent   = True
    session["user_id"]  = uid
    session["username"] = username
    return jsonify({
        "message":  "Account created successfully!",
        "user_id":  uid,
        "username": username,
    }), 201


# ── Login ─────────────────────────────────────────────────────
@auth_bp.route("/login", methods=["POST"])
def login():
    data     = request.json or {}
    email    = data.get("email", "").strip().lower()
    password = data.get("password", "")

    if not email or not password:
        return jsonify({"error": "Email and password are required"}), 400

    rows = db_query("SELECT * FROM users WHERE email = ?", (email,))
    if not rows:
        return jsonify({"error": "Invalid email or password"}), 401

    user     = rows[0]
    pwd_hash = user.get("password", "")

    if not pwd_hash:
        return jsonify({"error": "This account uses Google Sign-in. Please use that button."}), 401

    # Support both old sha256 hashes and new werkzeug hashes
    if pwd_hash.startswith("pbkdf2:") or pwd_hash.startswith("scrypt:"):
        valid = check_password_hash(pwd_hash, password)
    else:
        import hashlib, hmac as _hmac
        valid = _hmac.compare_digest(hashlib.sha256(password.encode()).hexdigest(), pwd_hash)
        if valid:
            db_execute("UPDATE users SET password=? WHERE id=?",
                       (generate_password_hash(password), user["id"]))

    if not valid:
        return jsonify({"error": "Invalid email or password"}), 401

    username = user.get("username") or user.get("name") or email.split("@")[0]
    session.permanent   = True
    session["user_id"]  = user["id"]
    session["username"] = username
    return jsonify({
        "message":  "Welcome back!",
        "user_id":  user["id"],
        "username": username,
    }), 200


# ── Logout ────────────────────────────────────────────────────
@auth_bp.route("/logout", methods=["POST"])
def logout():
    session.clear()
    return jsonify({"message": "Logged out"}), 200


# ── Session check ─────────────────────────────────────────────
@auth_bp.route("/me", methods=["GET"])
def me():
    if "user_id" not in session:
        return jsonify({"error": "Not authenticated"}), 401
    rows = db_query("SELECT * FROM users WHERE id = ?", (session["user_id"],))
    if not rows:
        session.clear()
        return jsonify({"error": "User not found"}), 404
    user = rows[0]
    return jsonify({
        "id":         user.get("id"),
        "username":   user.get("username") or user.get("name", ""),
        "email":      user.get("email", ""),
        "avatar_url": user.get("avatar_url") or user.get("picture") or "",
    }), 200


# ── Google OAuth — Step 1: Redirect ───────────────────────────
@auth_bp.route("/google", methods=["GET"])
def google_login():
    if not GOOGLE_CLIENT_ID:
        return jsonify({"error": "Google OAuth not configured. Add GOOGLE_CLIENT_ID to .env"}), 503

    import urllib.parse, secrets
    state = secrets.token_urlsafe(16)
    session["oauth_state"] = state

    params = urllib.parse.urlencode({
        "client_id":     GOOGLE_CLIENT_ID,
        "redirect_uri":  GOOGLE_REDIRECT_URI,
        "response_type": "code",
        "scope":         "openid email profile https://www.googleapis.com/auth/classroom.courses.readonly https://www.googleapis.com/auth/classroom.coursework.me.readonly",
        "access_type":   "offline",
        "prompt":        "select_account",
        "state":         state,
    })
    return redirect(f"https://accounts.google.com/o/oauth2/v2/auth?{params}")


# ── Google OAuth — Step 2: Callback ───────────────────────────
@auth_bp.route("/google/callback", methods=["GET"])
def google_callback():
    code  = request.args.get("code")
    state = request.args.get("state")
    error = request.args.get("error")

    if error:
        return redirect(f"/login?error={error}")
    if not code:
        return redirect("/login?error=missing_code")
    if state != session.get("oauth_state"):
        return redirect("/login?error=invalid_state")
    if not GOOGLE_CLIENT_ID or not GOOGLE_CLIENT_SECRET:
        return redirect("/login?error=oauth_not_configured")

    # Exchange code for token
    import requests as http_requests
    token_resp = http_requests.post(
        "https://oauth2.googleapis.com/token",
        data={
            "code":          code,
            "client_id":     GOOGLE_CLIENT_ID,
            "client_secret": GOOGLE_CLIENT_SECRET,
            "redirect_uri":  GOOGLE_REDIRECT_URI,
            "grant_type":    "authorization_code",
        },
    )
    if not token_resp.ok:
        return redirect("/login?error=token_exchange_failed")

    token_data   = token_resp.json()
    access_token = token_data.get("access_token")

    # Fetch user info
    user_resp = http_requests.get(
        "https://www.googleapis.com/oauth2/v2/userinfo",
        headers={"Authorization": f"Bearer {access_token}"},
    )
    if not user_resp.ok:
        return redirect("/login?error=userinfo_failed")

    info      = user_resp.json()
    email     = info.get("email", "")
    username  = info.get("name", email.split("@")[0])
    google_id = info.get("id", "")
    avatar    = info.get("picture", "")

    if not email:
        return redirect("/login?error=no_email")

    # Upsert user using actual table columns
    rows = db_query("SELECT * FROM users WHERE email = ?", (email,))
    if rows:
        uid = rows[0]["id"]
        try:
            db_execute(
                "UPDATE users SET google_id=?, avatar_url=?, updated_at=? WHERE id=?",
                (google_id, avatar, now_iso(), uid),
            )
        except Exception:
            pass
    else:
        uid = new_id()
        db_execute(
            "INSERT INTO users (id, username, email, password, google_id, avatar_url, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)",
            (uid, username, email, "", google_id, avatar, now_iso(), now_iso()),
        )

    # Save OAuth tokens for Classroom Sync
    refresh_token = token_data.get("refresh_token", "")
    token_expiry  = now_iso() # We'd ideally add expires_in seconds here
    
    t_rows = db_query("SELECT id FROM oauth_tokens WHERE user_id = ?", (uid,))
    try:
        if t_rows:
            # If Google didn't send a new refresh token, keep the old one intact
            update_sql = "UPDATE oauth_tokens SET access_token=?, updated_at=? {} WHERE user_id=?"
            if refresh_token:
                db_execute(update_sql.format(", refresh_token=?"), (access_token, now_iso(), refresh_token, uid))
            else:
                db_execute(update_sql.format(""), (access_token, now_iso(), uid))
        else:
            db_execute(
                "INSERT INTO oauth_tokens (id, user_id, access_token, refresh_token, updated_at) VALUES (?,?,?,?,?)",
                (new_id(), uid, access_token, refresh_token, now_iso()),
            )
    except Exception as e:
        print(f"Warning: Failed to save oauth_tokens: {e}")

    session.permanent   = True
    session["user_id"]  = uid
    session["username"] = username
    return redirect("/")

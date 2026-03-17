"""
Persona — Google Classroom API Service
Handles OAuth 2.0 flow and fetching courses + assignments.

Setup:
  1. Go to https://console.cloud.google.com
  2. Create a project → Enable "Google Classroom API"
  3. Configure OAuth consent screen (External)
  4. Create OAuth 2.0 Client ID (Web Application)
  5. Set Redirect URI: http://localhost:5000/api/auth/google/callback
  6. Copy Client ID + Secret into .env
"""
import os
from datetime import datetime
from database import db_query, db_execute, new_id, now_iso

SCOPES = [
    "openid",
    "https://www.googleapis.com/auth/userinfo.email",
    "https://www.googleapis.com/auth/userinfo.profile",
    "https://www.googleapis.com/auth/classroom.courses.readonly",
    "https://www.googleapis.com/auth/classroom.coursework.me.readonly",
    "https://www.googleapis.com/auth/classroom.student-submissions.me.readonly",
]

CLIENT_ID      = os.getenv("GOOGLE_CLIENT_ID", "")
CLIENT_SECRET  = os.getenv("GOOGLE_CLIENT_SECRET", "")
REDIRECT_URI   = os.getenv("GOOGLE_REDIRECT_URI", "http://localhost:5000/api/auth/google/callback")


def get_google_auth_url() -> str:
    """Generate the Google OAuth 2.0 authorization URL."""
    try:
        from google_auth_oauthlib.flow import Flow
        flow = Flow.from_client_config(
            {
                "web": {
                    "client_id": CLIENT_ID,
                    "client_secret": CLIENT_SECRET,
                    "redirect_uris": [REDIRECT_URI],
                    "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                    "token_uri": "https://oauth2.googleapis.com/token",
                }
            },
            scopes=SCOPES,
        )
        flow.redirect_uri = REDIRECT_URI
        auth_url, _ = flow.authorization_url(access_type="offline", include_granted_scopes="true", prompt="consent")
        return auth_url
    except Exception as e:
        raise RuntimeError(f"Google OAuth not configured: {e}")


def exchange_code_for_token(code: str) -> dict:
    """Exchange auth code for access + refresh tokens."""
    import requests as req
    resp = req.post(
        "https://oauth2.googleapis.com/token",
        data={
            "code": code,
            "client_id": CLIENT_ID,
            "client_secret": CLIENT_SECRET,
            "redirect_uri": REDIRECT_URI,
            "grant_type": "authorization_code",
        },
    )
    resp.raise_for_status()
    return resp.json()


def get_google_user_info(access_token: str) -> dict:
    """Fetch Google user profile."""
    import requests as req
    resp = req.get(
        "https://www.googleapis.com/oauth2/v2/userinfo",
        headers={"Authorization": f"Bearer {access_token}"},
    )
    resp.raise_for_status()
    return resp.json()


def _get_token(uid: str) -> str | None:
    rows = db_query("SELECT access_token FROM oauth_tokens WHERE user_id=?", (uid,))
    return rows[0]["access_token"] if rows else None


def sync_assignments(uid: str) -> int:
    """
    Fetch all coursework from Google Classroom and upsert into assignments table.
    Returns the number of assignments synced.
    """
    import requests as req
    token = _get_token(uid)
    if not token:
        raise RuntimeError("No Google token found. Please connect Google Classroom first.")

    headers = {"Authorization": f"Bearer {token}"}
    count   = 0

    # 1. Fetch courses
    courses_resp = req.get("https://classroom.googleapis.com/v1/courses?courseStates=ACTIVE", headers=headers)
    courses_resp.raise_for_status()
    courses = courses_resp.json().get("courses", [])

    for course in courses:
        course_id   = course["id"]
        course_name = course.get("name", "Unknown Course")

        # 2. Fetch coursework
        cw_resp = req.get(
            f"https://classroom.googleapis.com/v1/courses/{course_id}/courseWork",
            headers=headers,
        )
        if cw_resp.status_code != 200:
            continue
        courseworks = cw_resp.json().get("courseWork", [])

        for cw in courseworks:
            gw_id    = cw["id"]
            title    = cw.get("title", "Untitled")
            desc     = cw.get("description", "")
            link     = cw.get("alternateLink", "")
            due_date = None
            if "dueDate" in cw:
                d = cw["dueDate"]
                year  = d.get("year", datetime.now().year)
                month = d.get("month", 1)
                day   = d.get("day", 1)
                due_date = f"{year}-{month:02d}-{day:02d}"

                # Skip past due assignments
                today_str = datetime.now().strftime("%Y-%m-%d")
                if due_date < today_str:
                    continue  # Ignore old assignments
            else:
                continue # Skip assignments with no due date to avoid clutter

            # Upsert
            existing = db_query(
                "SELECT id FROM assignments WHERE course_id=? AND assignment_id=? AND user_id=?",
                (course_id, gw_id, uid),
            )
            if existing:
                db_execute(
                    "UPDATE assignments SET title=?, description=?, due_date=?, link=?, updated_at=? WHERE id=?",
                    (title, desc, due_date, link, now_iso(), existing[0]["id"]),
                )
            else:
                aid = new_id()
                db_execute(
                    """INSERT INTO assignments
                       (id, user_id, course_name, course_id, assignment_id, title, description, due_date, link, source, created_at, updated_at)
                       VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""",
                    (aid, uid, course_name, course_id, gw_id,
                     title, desc, due_date, link, "google_classroom", now_iso(), now_iso()),
                )
                # Automatically add this assignment as a pending Task so the AI Scheduler can organize it
                try:
                    tid = new_id()
                    task_title = f"{course_name}: {title}"
                    db_execute(
                        """INSERT INTO tasks 
                           (id, user_id, title, description, priority, status, category, created_at, updated_at)
                           VALUES (?,?,?,?,?,?,?,?,?)""",
                        (tid, uid, task_title, desc, "high", "pending", "assignment", now_iso(), now_iso())
                    )
                except Exception as e:
                    print("Warning: Failed to auto-create task for assignment", e)
                    
            count += 1

    return count

"""
Persona PWA — Flask Application Entry Point
"""
import os
from pathlib import Path
from flask import Flask, send_from_directory
from flask_cors import CORS
from dotenv import load_dotenv

load_dotenv()                             # loads .env
load_dotenv(Path(__file__).parent / "env.file", override=False)  # loads env.file (Supabase, etc.)

# Absolute path to the frontend directory
FRONTEND_DIR = str(Path(__file__).parent.parent / "frontend")


def create_app():
    app = Flask(__name__, static_folder=None)
    app.secret_key = os.getenv("SECRET_KEY", "persona-dev-secret-change-in-prod")
    app.config["SESSION_COOKIE_HTTPONLY"] = True
    app.config["SESSION_COOKIE_SAMESITE"] = "Lax"
    app.config["SESSION_COOKIE_SECURE"]   = os.getenv("FLASK_ENV") == "production"
    app.config["PERMANENT_SESSION_LIFETIME"] = 60 * 60 * 24 * 30   # 30 days

    # Allow all origins in development
    CORS(app, supports_credentials=True, origins="*")

    # ── Register Blueprints ───────────────────────────────────
    from routes.auth        import auth_bp
    from routes.tasks       import tasks_bp
    from routes.assignments import assignments_bp
    from routes.expenses    import expenses_bp
    from routes.habits      import habits_bp
    from routes.notes       import notes_bp
    from routes.analytics   import analytics_bp
    from routes.scheduler   import scheduler_bp
    from routes.focus       import focus_bp
    from routes.ai_chat     import ai_bp
    from routes.timetable   import timetable_bp

    app.register_blueprint(auth_bp,        url_prefix="/api/auth")
    app.register_blueprint(tasks_bp,       url_prefix="/api/tasks")
    app.register_blueprint(assignments_bp, url_prefix="/api/assignments")
    app.register_blueprint(expenses_bp,    url_prefix="/api/expenses")
    app.register_blueprint(habits_bp,      url_prefix="/api/habits")
    app.register_blueprint(notes_bp,       url_prefix="/api/notes")
    app.register_blueprint(analytics_bp,   url_prefix="/api/analytics")
    app.register_blueprint(scheduler_bp,   url_prefix="/api/scheduler")
    app.register_blueprint(focus_bp,       url_prefix="/api/focus")
    app.register_blueprint(ai_bp,          url_prefix="/api/ai")
    app.register_blueprint(timetable_bp,   url_prefix="/api/timetable")

    # ── Health check ──────────────────────────────────────────
    @app.route("/api/health")
    def health():
        return {"status": "ok", "app": "Persona PWA"}

    # ── Serve Frontend ────────────────────────────────────────
    @app.route("/")
    def index():
        return send_from_directory(FRONTEND_DIR, "index.html")

    @app.route("/<path:filepath>")
    def serve_static(filepath):
        # If no extension given, try serving as .html page
        if "." not in filepath.split("/")[-1]:
            try:
                return send_from_directory(FRONTEND_DIR, filepath + ".html")
            except Exception:
                return send_from_directory(FRONTEND_DIR, "index.html")
        return send_from_directory(FRONTEND_DIR, filepath)

    return app


if __name__ == "__main__":
    app = create_app()
    port = int(os.getenv("FLASK_PORT", 5000))
    debug = os.getenv("FLASK_ENV", "development") == "development"
    print(f"\n  Persona PWA")
    print(f"  Frontend : http://localhost:{port}")
    print(f"  API      : http://localhost:{port}/api/health\n")
    app.run(host="0.0.0.0", port=port, debug=debug)

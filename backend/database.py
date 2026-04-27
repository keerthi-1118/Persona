import os
import sqlite3
import uuid
from datetime import datetime
from pathlib import Path
from dotenv import load_dotenv

load_dotenv()

DB_MODE = os.getenv("DB_MODE", "local")
DB_PATH = Path(__file__).parent.parent / "database" / "persona.db"
SCHEMA_PATH = Path(__file__).parent.parent / "database" / "schema.sql"

def get_conn():
    conn = sqlite3.connect(DB_PATH, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn

def new_id() -> str:
    return str(uuid.uuid4()).replace("-", "")

def now_iso() -> str:
    return datetime.utcnow().isoformat()

def db_query(sql: str, params: tuple = ()) -> list[dict]:
    """Execute a SELECT and return list of dicts."""
    with get_conn() as conn:
        cursor = conn.execute(sql, params)
        return [dict(row) for row in cursor.fetchall()]

def db_execute(sql: str, params: tuple = ()):
    """Execute INSERT / UPDATE / DELETE."""
    with get_conn() as conn:
        conn.execute(sql, params)
        conn.commit()

def db_execute_many(sql: str, param_list: list):
    """Batch execute."""
    with get_conn() as conn:
        cursor = conn.cursor()
        cursor.executemany(sql, param_list)
        conn.commit()

def init_db():
    """Initialize SQLite database with schema if it doesn't exist."""
    print("Initializing Database...")
    with get_conn() as conn:
        with open(SCHEMA_PATH, "r", encoding="utf-8") as f:
            script = f.read()
            conn.executescript(script)
    print("[OK] Local SQLite Database Configured.")

# Run init automatically
try:
    init_db()
except Exception as e:
    print(f"[WARN] Database init error: {e}")

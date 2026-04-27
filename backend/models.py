"""
Persona — Models
Plain dataclass-style helpers for validation & serialisation.
No ORM needed since we use raw SQL via database.py.
"""
import uuid
from datetime import datetime


def new_id() -> str:
    return str(uuid.uuid4()).replace("-", "")


def now_iso() -> str:
    return datetime.utcnow().isoformat()


# ── Validation helpers ─────────────────────────────────────────

TASK_PRIORITIES = {"low", "medium", "high", "urgent"}
TASK_STATUSES   = {"pending", "in_progress", "completed", "cancelled"}
EXPENSE_CATEGORIES = {"food", "transport", "books", "entertainment", "health", "shopping", "other", "Income"}


def validate_task(data: dict) -> list[str]:
    errors = []
    if not data.get("title", "").strip():
        errors.append("title is required")
    if data.get("priority") and data["priority"] not in TASK_PRIORITIES:
        errors.append(f"priority must be one of {TASK_PRIORITIES}")
    if data.get("status") and data["status"] not in TASK_STATUSES:
        errors.append(f"status must be one of {TASK_STATUSES}")
    return errors


def validate_expense(data: dict) -> list[str]:
    errors = []
    if not data.get("amount"):
        errors.append("amount is required")
    try:
        amt = float(data["amount"])
        if amt <= 0:
            errors.append("amount must be positive")
    except (ValueError, TypeError):
        errors.append("amount must be a number")
    if data.get("category") and data["category"] not in EXPENSE_CATEGORIES:
        errors.append(f"category must be one of {EXPENSE_CATEGORIES}")
    return errors

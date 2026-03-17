"""
Persona — Expenses Routes
"""
from flask import Blueprint, request, jsonify, session
from database import db_query, db_execute, new_id, now_iso
from models import validate_expense

expenses_bp = Blueprint("expenses", __name__)


@expenses_bp.route("/", methods=["GET"])
def get_expenses():
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401

    month = request.args.get("month")  # YYYY-MM
    sql    = "SELECT * FROM expenses WHERE user_id = ?"
    params = [uid]
    if month:
        sql += " AND date LIKE ?"
        params.append(f"{month}%")
    
    rows = db_query(sql, tuple(params))
    
    # Sort in python side as fallback
    rows.sort(key=lambda x: (x.get("date", ""), x.get("created_at", "")), reverse=True)

    # Compute category summary in Python
    cats = {}
    for r in rows:
        c = r.get("category", "other")
        cats[c] = cats.get(c, 0) + float(r.get("amount", 0))
        
    summary = [{"category": k, "total": v} for k, v in cats.items()]

    return jsonify({"expenses": rows, "summary": summary}), 200


@expenses_bp.route("/", methods=["POST"])
def create_expense():
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401

    data   = request.json or {}
    errors = validate_expense(data)
    if errors:
        return jsonify({"errors": errors}), 400

    eid = new_id()
    db_execute(
        "INSERT INTO expenses (id, user_id, amount, category, description, date, created_at) VALUES (?,?,?,?,?,?,?)",
        (eid, uid, float(data["amount"]), data.get("category", "other"),
         data.get("description"), data.get("date", now_iso()[:10]), now_iso()),
    )
    rows = db_query("SELECT * FROM expenses WHERE id = ?", (eid,))
    return jsonify(rows[0] if rows else {"id": eid}), 201


@expenses_bp.route("/<eid>", methods=["DELETE"])
def delete_expense(eid):
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    db_execute("DELETE FROM expenses WHERE id=? AND user_id=?", (eid, uid))
    return jsonify({"message": "Expense deleted"}), 200


@expenses_bp.route("/total", methods=["GET"])
def get_total():
    uid = session.get("user_id")
    if not uid:
        return jsonify({"error": "Authentication required"}), 401
    
    month = request.args.get("month", now_iso()[:7])
    
    sql = "SELECT * FROM expenses WHERE user_id = ? AND date LIKE ?"
    params = (uid, f"{month}%")
    
    rows = db_query(sql, params)
    
    total_spent = sum(float(r.get("amount", 0)) for r in rows if r.get("category") != "Income")
    total_inc = sum(float(r.get("amount", 0)) for r in rows if r.get("category") == "Income")
    
    return jsonify({
        "total": total_spent, 
        "total_spent": total_spent, 
        "total_income": total_inc, 
        "month": month
    }), 200

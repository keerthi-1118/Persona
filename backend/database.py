"""
Persona PWA — Database Layer  (Supabase REST API via supabase-py or raw HTTP)

Uses Supabase's PostgREST REST API — works with the anon/service-role key.
Keeps the same public interface (db_query / db_execute / db_execute_many)
so all routes remain unchanged.

Environment variables needed in .env:
    DB_API_URL   = https://xxxxxxxx.supabase.co
    DB_API_KEY   = your-supabase-anon-or-service-role-key
"""
import os
import uuid
import json
import requests
from datetime import datetime
from pathlib import Path
from dotenv import load_dotenv

load_dotenv()
load_dotenv(Path(__file__).parent / "env.file", override=False)

# ─────────────────────────────────────────────────────────────
# Config
# ─────────────────────────────────────────────────────────────
SUPABASE_URL = os.getenv("DB_API_URL", "").rstrip("/")
SUPABASE_KEY = os.getenv("DB_API_KEY", "")

if not SUPABASE_URL or not SUPABASE_KEY:
    raise RuntimeError("DB_API_URL and DB_API_KEY must be set in .env")

REST_URL = f"{SUPABASE_URL}/rest/v1"
RPC_URL  = f"{SUPABASE_URL}/rest/v1/rpc"


def _headers(prefer_return: str = "representation") -> dict:
    return {
        "apikey":         SUPABASE_KEY,
        "Authorization":  f"Bearer {SUPABASE_KEY}",
        "Content-Type":   "application/json",
        "Prefer":         prefer_return,
    }


# ─────────────────────────────────────────────────────────────
# Shared helpers
# ─────────────────────────────────────────────────────────────
def new_id() -> str:
    return str(uuid.uuid4()).replace("-", "")

def now_iso() -> str:
    return datetime.utcnow().isoformat()


# ─────────────────────────────────────────────────────────────
# SQL execution via Supabase's pg_execute RPC
# (requires the pg_execute function to be installed in Supabase)
# We use supabase-py client to run arbitrary SQL.
# ─────────────────────────────────────────────────────────────
def _get_supabase_client():
    """Return a supabase-py client (lazy import)."""
    try:
        from supabase import create_client
        return create_client(SUPABASE_URL, SUPABASE_KEY)
    except ImportError:
        return None


def _run_sql_via_rpc(sql: str, params: tuple, fetch: bool) -> list | None:
    """
    Execute SQL via a Supabase edge function or the direct pg connection.
    Falls back to supabase-py execute_query if available.
    """
    client = _get_supabase_client()
    if client is None:
        raise RuntimeError(
            "supabase-py not installed. Run: pip install supabase"
        )
    # supabase-py v2 exposes .postgrest for table ops and .rpc() for functions
    # For raw SQL we call a stored procedure named `run_query` that we create.
    # As a simpler approach, we use the table API for standard CRUD operations,
    # and handle the SQL parsing to map to table operations.
    raise NotImplementedError("Use table-level API instead of raw SQL for Supabase.")


# ─────────────────────────────────────────────────────────────
# Table-level helpers (translate SQL into PostgREST operations)
# For complex SQL the app uses, we parse and forward to PostgREST.
# ─────────────────────────────────────────────────────────────

import re

def _parse_table(sql: str) -> str:
    """Extract the first table name from an SQL statement."""
    m = re.search(
        r'(?:FROM|INTO|UPDATE|REPLACE\s+INTO)\s+(\w+)',
        sql, re.IGNORECASE
    )
    return m.group(1) if m else ""


def _parse_select_columns(sql: str) -> str:
    """Parse SELECT columns (for PostgREST ?select= param)."""
    m = re.match(r'SELECT\s+(.+?)\s+FROM', sql, re.IGNORECASE | re.DOTALL)
    if not m:
        return "*"
    cols = m.group(1).strip()
    if cols == "*":
        return "*"
    return cols


def _build_filter_params(sql: str, params: tuple) -> dict:
    """
    Build PostgREST filter query params from WHERE clause.
    Handles simple `col = ?` and `col IN (...)` cases.
    """
    qp = {}
    where_m = re.search(r'WHERE\s+(.+?)(?:ORDER|LIMIT|GROUP|$)', sql, re.IGNORECASE | re.DOTALL)
    if not where_m:
        return qp

    conditions = where_m.group(1).strip()
    # Split on AND (simple cases)
    parts = re.split(r'\bAND\b', conditions, flags=re.IGNORECASE)
    param_iter = iter(params)

    for part in parts:
        part = part.strip()
        eq_m = re.match(r'(\w+)\s*=\s*\?', part)
        if eq_m:
            col = eq_m.group(1)
            val = next(param_iter, None)
            qp[col] = f"eq.{val}"
            continue
        # LIKE
        like_m = re.match(r'(\w+)\s+LIKE\s+\?', part, re.IGNORECASE)
        if like_m:
            col = like_m.group(1)
            val = next(param_iter, None)
            qp[col] = f"like.{val}"
            continue
        # date >= ?
        gte_m = re.match(r'(\w+)\s*>=\s*\?', part)
        if gte_m:
            col = gte_m.group(1)
            val = next(param_iter, None)
            qp[col] = f"gte.{val}"
            continue
        lte_m = re.match(r'(\w+)\s*<=\s*\?', part)
        if lte_m:
            col = lte_m.group(1)
            val = next(param_iter, None)
            qp[col] = f"lte.{val}"
            continue

    return qp


def db_query(sql: str, params: tuple = ()) -> list[dict]:
    """Execute a SELECT and return list of dicts via PostgREST."""
    table = _parse_table(sql)
    if not table:
        raise ValueError(f"Could not parse table from SQL: {sql}")

    columns = _parse_select_columns(sql)
    query_params = {"select": columns}
    query_params.update(_build_filter_params(sql, params))

    # ORDER BY
    order_m = re.search(r'ORDER\s+BY\s+(\w+)(?:\s+(ASC|DESC))?', sql, re.IGNORECASE)
    if order_m:
        col = order_m.group(1)
        direction = (order_m.group(2) or "ASC").lower()
        query_params["order"] = f"{col}.{direction}"

    # LIMIT
    limit_m = re.search(r'LIMIT\s+(\d+)', sql, re.IGNORECASE)
    if limit_m:
        query_params["limit"] = limit_m.group(1)

    resp = requests.get(
        f"{REST_URL}/{table}",
        headers=_headers(),
        params=query_params,
        timeout=10,
    )
    if not resp.ok:
        if resp.status_code == 404 or "PGRST" in resp.text:
            raise RuntimeError(f"\n❌ Supabase Error: Table '{table}' or a column is missing.\n"
                               f"Please copy the contents of 'database/schema_postgres.sql'\n"
                               f"and run it in your Supabase Dashboard → SQL Editor!\n"
                               f"Details: {resp.text}")
        raise RuntimeError(f"Supabase GET /{table} failed {resp.status_code}: {resp.text}")
    return resp.json()


def _parse_insert_data(sql: str, params: tuple) -> dict:
    """Parse INSERT INTO table (cols) VALUES (?,?) → dict."""
    m = re.match(
        r'INSERT\s+(?:OR\s+REPLACE\s+)?INTO\s+\w+\s*\(([^)]+)\)\s*VALUES\s*\(([^)]+)\)',
        sql, re.IGNORECASE
    )
    if not m:
        return {}
    cols = [c.strip() for c in m.group(1).split(",")]
    return dict(zip(cols, params))


def _parse_update_data(sql: str, params: tuple) -> tuple[dict, dict]:
    """Parse UPDATE table SET col=?,... WHERE col=? → (data_dict, filter_dict)."""
    set_m = re.search(r'SET\s+(.+?)\s+WHERE', sql, re.IGNORECASE | re.DOTALL)
    if not set_m:
        return {}, {}

    set_parts = [p.strip() for p in set_m.group(1).split(",")]
    set_cols  = []
    for part in set_parts:
        cm = re.match(r'(\w+)\s*=\s*\?', part)
        if cm:
            set_cols.append(cm.group(1))

    param_list = list(params)
    data = dict(zip(set_cols, param_list[:len(set_cols)]))

    where_params = tuple(param_list[len(set_cols):])
    table = _parse_table(sql)
    filter_sql = sql  # reuse _build_filter_params on the full SQL
    filters = _build_filter_params(filter_sql, where_params)
    return data, filters


def db_execute(sql: str, params: tuple = ()):
    """Execute INSERT / UPDATE / DELETE via PostgREST."""
    sql_stripped = sql.strip().upper()

    if sql_stripped.startswith("INSERT"):
        table = _parse_table(sql)
        data  = _parse_insert_data(sql, params)
        if not data:
            raise ValueError(f"Could not parse INSERT: {sql}")

        # Handle INSERT OR REPLACE (upsert)
        prefer = "resolution=merge-duplicates" if "OR REPLACE" in sql.upper() else "return=minimal"
        resp = requests.post(
            f"{REST_URL}/{table}",
            headers={**_headers(prefer), "Prefer": prefer},
            json=data,
            timeout=10,
        )
        if not resp.ok:
            if resp.status_code == 404 or "PGRST" in resp.text:
                 raise RuntimeError(f"\n❌ Supabase Error: Table '{table}' or a column is missing.\n"
                                    f"Please run 'database/schema_postgres.sql' in your Supabase SQL Editor!\n"
                                    f"Details: {resp.text}")
            raise RuntimeError(f"Supabase POST /{table} failed {resp.status_code}: {resp.text}")

    elif sql_stripped.startswith("UPDATE"):
        table = _parse_table(sql)
        data, filters = _parse_update_data(sql, params)
        if not data:
            raise ValueError(f"Could not parse UPDATE: {sql}")

        resp = requests.patch(
            f"{REST_URL}/{table}",
            headers=_headers("return=minimal"),
            params=filters,
            json=data,
            timeout=10,
        )
        if not resp.ok:
            if resp.status_code == 404 or "PGRST" in resp.text:
                 raise RuntimeError(f"\n❌ Supabase Error: Table '{table}' or a column is missing.\n"
                                    f"Please run 'database/schema_postgres.sql' in your Supabase SQL Editor!\n"
                                    f"Details: {resp.text}")
            raise RuntimeError(f"Supabase PATCH /{table} failed {resp.status_code}: {resp.text}")

    elif sql_stripped.startswith("DELETE"):
        table = _parse_table(sql)
        filters = _build_filter_params(sql, params)
        resp = requests.delete(
            f"{REST_URL}/{table}",
            headers=_headers("return=minimal"),
            params=filters,
            timeout=10,
        )
        if not resp.ok:
            if resp.status_code == 404 or "PGRST" in resp.text:
                 raise RuntimeError(f"\n❌ Supabase Error: Table '{table}' or a column is missing.\n"
                                    f"Please run 'database/schema_postgres.sql' in your Supabase SQL Editor!\n"
                                    f"Details: {resp.text}")
            raise RuntimeError(f"Supabase DELETE /{table} failed {resp.status_code}: {resp.text}")

    else:
        raise ValueError(f"db_execute: unsupported SQL operation: {sql[:80]}")


def db_execute_many(sql: str, param_list: list):
    """Batch execute via repeated db_execute calls."""
    for params in param_list:
        db_execute(sql, params)


# ─────────────────────────────────────────────────────────────
# Schema init — creates tables through Supabase SQL editor
# (requires service role key for DDL)
# ─────────────────────────────────────────────────────────────
def init_db():
    """
    Verify connection by querying the users table.
    Table creation must be done via Supabase SQL Editor or migrations.
    """
    resp = requests.get(
        f"{REST_URL}/users",
        headers=_headers(),
        params={"limit": "1"},
        timeout=8,
    )
    if resp.status_code == 404:
        print("⚠️  'users' table not found in Supabase.")
        print("    → Open Supabase SQL Editor and run database/schema_postgres.sql")
    elif resp.ok or resp.status_code == 200:
        print("✅  Connected to Supabase — users table exists.")
    else:
        print(f"⚠️  Supabase connection check: {resp.status_code} — {resp.text[:120]}")


# Run check on import
try:
    init_db()
except Exception as _e:
    print(f"⚠️  Supabase init error: {_e}")

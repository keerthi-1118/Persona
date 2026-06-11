-- ============================================================
-- Persona PWA — PostgreSQL Schema for Production (Render/Supabase)
-- Run once to initialize. All statements are safe to re-run.
-- ============================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id          TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    username    TEXT NOT NULL,
    email       TEXT NOT NULL UNIQUE,
    password    TEXT NOT NULL DEFAULT '',
    google_id   TEXT,
    avatar_url  TEXT,
    timezone    TEXT DEFAULT 'Asia/Kolkata',
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);

-- Tasks table
CREATE TABLE IF NOT EXISTS tasks (
    id           TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    user_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title        TEXT NOT NULL,
    description  TEXT,
    start_time   TIMESTAMPTZ,
    end_time     TIMESTAMPTZ,
    priority     TEXT CHECK(priority IN ('low','medium','high','urgent')) DEFAULT 'medium',
    status       TEXT CHECK(status IN ('pending','in_progress','completed','cancelled')) DEFAULT 'pending',
    category     TEXT DEFAULT 'general',
    is_scheduled BOOLEAN DEFAULT FALSE,
    assignment_id TEXT,
    created_at   TIMESTAMPTZ DEFAULT NOW(),
    updated_at   TIMESTAMPTZ DEFAULT NOW()
);

-- Task blocks (Dynamic AI focus sessions for multi-day tasks)
CREATE TABLE IF NOT EXISTS task_blocks (
    id           TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    task_id      TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    start_time   TIMESTAMPTZ NOT NULL,
    end_time     TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ DEFAULT NOW()
);

-- Assignments table
CREATE TABLE IF NOT EXISTS assignments (
    id             TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    user_id        TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_name    TEXT NOT NULL,
    course_id      TEXT,
    assignment_id  TEXT,
    title          TEXT NOT NULL,
    description    TEXT,
    due_date       TIMESTAMPTZ,
    status         TEXT CHECK(status IN ('pending','submitted','late','graded')) DEFAULT 'pending',
    link           TEXT,
    source         TEXT DEFAULT 'manual',
    created_at     TIMESTAMPTZ DEFAULT NOW(),
    updated_at     TIMESTAMPTZ DEFAULT NOW()
);

-- Expenses table
CREATE TABLE IF NOT EXISTS expenses (
    id          TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount      NUMERIC(12,2) NOT NULL,
    category    TEXT CHECK(category IN ('food','transport','books','entertainment','health','shopping','other','Income')) DEFAULT 'other',
    description TEXT,
    date        DATE DEFAULT CURRENT_DATE,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- Habits table
CREATE TABLE IF NOT EXISTS habits (
    id          TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    icon        TEXT DEFAULT '⭐',
    color       TEXT DEFAULT '#6C63FF',
    target_days TEXT DEFAULT '["mon","tue","wed","thu","fri","sat","sun"]',
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- Habit logs
CREATE TABLE IF NOT EXISTS habit_logs (
    id        TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    habit_id  TEXT NOT NULL REFERENCES habits(id) ON DELETE CASCADE,
    user_id   TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date      DATE NOT NULL DEFAULT CURRENT_DATE,
    completed BOOLEAN DEFAULT TRUE,
    UNIQUE(habit_id, date)
);

-- Notes table
CREATE TABLE IF NOT EXISTS notes (
    id         TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    user_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title      TEXT DEFAULT 'Untitled Note',
    content    TEXT,
    tags       TEXT DEFAULT '[]',
    color      TEXT DEFAULT '#1e1e2e',
    pinned     BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Focus sessions
CREATE TABLE IF NOT EXISTS focus_sessions (
    id         TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    user_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    task_id    TEXT REFERENCES tasks(id) ON DELETE SET NULL,
    duration   INTEGER NOT NULL,
    type       TEXT CHECK(type IN ('focus','short_break','long_break')) DEFAULT 'focus',
    started_at TIMESTAMPTZ NOT NULL,
    ended_at   TIMESTAMPTZ,
    completed  BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Timetable
CREATE TABLE IF NOT EXISTS timetable (
    id          TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    day_of_week TEXT CHECK(day_of_week IN ('mon','tue','wed','thu','fri','sat','sun')) NOT NULL,
    start_time  TEXT NOT NULL,
    end_time    TEXT NOT NULL,
    label       TEXT,
    type        TEXT DEFAULT 'class'
);

-- OAuth tokens
CREATE TABLE IF NOT EXISTS oauth_tokens (
    id            TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    user_id       TEXT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    access_token  TEXT NOT NULL,
    refresh_token TEXT,
    token_expiry  TIMESTAMPTZ,
    scope         TEXT,
    updated_at    TIMESTAMPTZ DEFAULT NOW()
);

-- Web Push subscriptions
CREATE TABLE IF NOT EXISTS push_subscriptions (
    id          TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint    TEXT NOT NULL,
    p256dh      TEXT,
    auth        TEXT,
    updated_at  TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, endpoint)
);

-- Email sync filter settings
CREATE TABLE IF NOT EXISTS email_filters (
    id          TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    user_id     TEXT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    senders     TEXT DEFAULT '[]',
    domains     TEXT DEFAULT '[]',
    keywords    TEXT DEFAULT '[]',
    enabled     INTEGER DEFAULT 1,
    last_synced_at TEXT
);

-- Synced priority/important email summaries
CREATE TABLE IF NOT EXISTS email_summaries (
    id               TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    user_id          TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email_id         TEXT NOT NULL,
    sender           TEXT,
    subject          TEXT,
    summary          TEXT,
    action_taken     TEXT,
    matched_keywords TEXT,
    is_important     INTEGER DEFAULT 1,
    notified         INTEGER DEFAULT 0,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_tasks_user       ON tasks(user_id);
CREATE INDEX IF NOT EXISTS idx_tasks_status     ON tasks(status);
CREATE INDEX IF NOT EXISTS idx_assignments_user ON assignments(user_id);
CREATE INDEX IF NOT EXISTS idx_expenses_user    ON expenses(user_id);
CREATE INDEX IF NOT EXISTS idx_habit_logs_habit ON habit_logs(habit_id);
CREATE INDEX IF NOT EXISTS idx_focus_user       ON focus_sessions(user_id);

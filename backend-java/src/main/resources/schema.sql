-- ============================================================
-- Persona PWA — Database Schema
-- Compatible with SQLite (local) | PostgreSQL (production)
-- ============================================================

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id          TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    username    TEXT NOT NULL UNIQUE,
    email       TEXT NOT NULL UNIQUE,
    password    TEXT NOT NULL,           -- bcrypt hashed
    google_id   TEXT,
    avatar_url  TEXT,
    timezone    TEXT DEFAULT 'Asia/Kolkata',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Tasks table
CREATE TABLE IF NOT EXISTS tasks (
    id          TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       TEXT NOT NULL,
    description TEXT,
    start_time  DATETIME,
    end_time    DATETIME,
    priority    TEXT CHECK(priority IN ('low','medium','high','urgent')) DEFAULT 'medium',
    status      TEXT CHECK(status IN ('pending','in_progress','completed','cancelled')) DEFAULT 'pending',
    category    TEXT DEFAULT 'general',
    is_scheduled INTEGER DEFAULT 0,     -- 1 if auto-scheduled by AI
    assignment_id TEXT,                 -- Link to assignments table
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Task blocks (Dynamic AI focus sessions for multi-day tasks)
CREATE TABLE IF NOT EXISTS task_blocks (
    id          TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    task_id     TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    start_time  DATETIME NOT NULL,
    end_time    DATETIME NOT NULL,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Assignments table (Google Classroom sync + manual)
CREATE TABLE IF NOT EXISTS assignments (
    id              TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    user_id         TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_name     TEXT NOT NULL,
    course_id       TEXT,               -- Google Classroom course ID
    assignment_id   TEXT,               -- Google Classroom coursework ID
    title           TEXT NOT NULL,
    description     TEXT,
    due_date        DATETIME,
    status          TEXT CHECK(status IN ('pending','submitted','late','graded')) DEFAULT 'pending',
    link            TEXT,               -- Google Classroom link
    source          TEXT DEFAULT 'manual', -- 'manual' | 'google_classroom'
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Expenses table
CREATE TABLE IF NOT EXISTS expenses (
    id          TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount      REAL NOT NULL,
    category    TEXT CHECK(category IN ('food','transport','books','entertainment','health','shopping','other','Income')) DEFAULT 'other',
    description TEXT,
    date        DATE DEFAULT (date('now')),
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Habits table
CREATE TABLE IF NOT EXISTS habits (
    id          TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    icon        TEXT DEFAULT '⭐',
    color       TEXT DEFAULT '#6C63FF',
    target_days TEXT DEFAULT '["mon","tue","wed","thu","fri","sat","sun"]', -- JSON array
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Habit logs (daily check-ins)
CREATE TABLE IF NOT EXISTS habit_logs (
    id          TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    habit_id    TEXT NOT NULL REFERENCES habits(id) ON DELETE CASCADE,
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date        DATE NOT NULL DEFAULT (date('now')),
    completed   INTEGER DEFAULT 1,
    UNIQUE(habit_id, date)
);

-- Notes table
CREATE TABLE IF NOT EXISTS notes (
    id          TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       TEXT DEFAULT 'Untitled Note',
    content     TEXT,
    tags        TEXT DEFAULT '[]',      -- JSON array of tags
    color       TEXT DEFAULT '#1e1e2e',
    pinned      INTEGER DEFAULT 0,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Focus sessions (Pomodoro tracking)
CREATE TABLE IF NOT EXISTS focus_sessions (
    id          TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    task_id     TEXT REFERENCES tasks(id) ON DELETE SET NULL,
    duration    INTEGER NOT NULL,       -- in minutes
    type        TEXT CHECK(type IN ('focus','short_break','long_break')) DEFAULT 'focus',
    started_at  DATETIME NOT NULL,
    ended_at    DATETIME,
    completed   INTEGER DEFAULT 0,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- User timetable (recurring schedule blocks — used by scheduler)
CREATE TABLE IF NOT EXISTS timetable (
    id          TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    day_of_week TEXT CHECK(day_of_week IN ('mon','tue','wed','thu','fri','sat','sun')) NOT NULL,
    start_time  TEXT NOT NULL,          -- e.g. "09:00"
    end_time    TEXT NOT NULL,          -- e.g. "10:30"
    label       TEXT,                   -- e.g. "Math Class"
    type        TEXT DEFAULT 'class'    -- 'class' | 'blocked' | 'other'
);

-- Google OAuth tokens
CREATE TABLE IF NOT EXISTS oauth_tokens (
    id              TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    user_id         TEXT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    access_token    TEXT NOT NULL,
    refresh_token   TEXT,
    token_expiry    DATETIME,
    scope           TEXT,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_tasks_user ON tasks(user_id);
CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);
CREATE INDEX IF NOT EXISTS idx_assignments_user ON assignments(user_id);
CREATE INDEX IF NOT EXISTS idx_expenses_user ON expenses(user_id);
CREATE INDEX IF NOT EXISTS idx_habit_logs_habit ON habit_logs(habit_id);
CREATE INDEX IF NOT EXISTS idx_focus_user ON focus_sessions(user_id);

-- Web Push subscriptions (for smart reminders)
CREATE TABLE IF NOT EXISTS push_subscriptions (
    id          TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint    TEXT NOT NULL,
    p256dh      TEXT,
    auth        TEXT,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, endpoint)
);

-- Email sync filters settings
CREATE TABLE IF NOT EXISTS email_filters (
    id          TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    user_id     TEXT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    senders     TEXT DEFAULT '[]', -- JSON array
    domains     TEXT DEFAULT '[]', -- JSON array
    keywords    TEXT DEFAULT '[]', -- JSON array
    enabled     INTEGER DEFAULT 1,
    last_synced_at TEXT
);

-- Synced priority/important email summaries
CREATE TABLE IF NOT EXISTS email_summaries (
    id          TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email_id    TEXT NOT NULL,
    sender      TEXT,
    subject     TEXT,
    summary     TEXT,
    action_taken TEXT,
    matched_keywords TEXT,      -- JSON array of matched keywords/terms
    is_important INTEGER DEFAULT 1,
    notified    INTEGER DEFAULT 0,  -- 0 = unnotified, 1 = notified
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);

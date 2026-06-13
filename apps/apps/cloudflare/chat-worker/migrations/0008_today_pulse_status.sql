-- Migration for Hello Status (Today Pulse) - Phase 1

CREATE TABLE IF NOT EXISTS statuses (
    id TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL,
    type TEXT NOT NULL,
    text TEXT,
    prompt_id TEXT,
    chain_id TEXT,
    audience TEXT NOT NULL DEFAULT 'household',
    expires_at INTEGER NOT NULL,
    archived_at INTEGER,
    archive_state TEXT NOT NULL DEFAULT 'pending',
    reply_count INTEGER NOT NULL DEFAULT 0,
    reaction_summary TEXT NOT NULL DEFAULT '{}',
    view_count INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS status_media (
    id TEXT PRIMARY KEY,
    status_id TEXT NOT NULL,
    r2_key TEXT NOT NULL,
    media_type TEXT NOT NULL,
    width INTEGER,
    height INTEGER,
    duration_seconds REAL,
    expires_at INTEGER NOT NULL,
    deleted_at INTEGER
);

CREATE TABLE IF NOT EXISTS status_views (
    id TEXT PRIMARY KEY,
    status_id TEXT NOT NULL,
    viewer_id TEXT NOT NULL,
    viewed_at INTEGER NOT NULL,
    completed_at INTEGER
);

CREATE TABLE IF NOT EXISTS status_reactions (
    id TEXT PRIMARY KEY,
    status_id TEXT NOT NULL,
    reactor_id TEXT NOT NULL,
    emoji TEXT NOT NULL,
    reacted_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS status_replies (
    id TEXT PRIMARY KEY,
    status_id TEXT NOT NULL,
    sender_id TEXT NOT NULL,
    text TEXT,
    media_url TEXT,
    sent_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS status_chains (
    id TEXT PRIMARY KEY,
    prompt_text TEXT NOT NULL,
    creator_id TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    member_ids TEXT NOT NULL DEFAULT '[]'
);

CREATE TABLE IF NOT EXISTS status_archive_jobs (
    id TEXT PRIMARY KEY,
    status_id TEXT NOT NULL,
    owner_id TEXT NOT NULL,
    state TEXT NOT NULL DEFAULT 'pending',
    created_at INTEGER NOT NULL,
    acked_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_statuses_owner_id ON statuses(owner_id);
CREATE INDEX IF NOT EXISTS idx_statuses_expires_at ON statuses(expires_at);
CREATE INDEX IF NOT EXISTS idx_statuses_archive_state ON statuses(archive_state);
CREATE INDEX IF NOT EXISTS idx_status_media_status_id ON status_media(status_id);
CREATE INDEX IF NOT EXISTS idx_status_views_status_id ON status_views(status_id);
CREATE INDEX IF NOT EXISTS idx_status_reactions_status_id ON status_reactions(status_id);
CREATE INDEX IF NOT EXISTS idx_status_replies_status_id ON status_replies(status_id);
CREATE INDEX IF NOT EXISTS idx_status_archive_jobs_owner_id ON status_archive_jobs(owner_id);
CREATE INDEX IF NOT EXISTS idx_status_archive_jobs_state ON status_archive_jobs(state);

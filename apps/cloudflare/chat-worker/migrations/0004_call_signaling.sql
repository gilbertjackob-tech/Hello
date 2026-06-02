CREATE TABLE IF NOT EXISTS call_sessions (
  id TEXT PRIMARY KEY,
  chat_id TEXT NOT NULL,
  caller_user_id TEXT NOT NULL,
  receiver_user_id TEXT NOT NULL,
  type TEXT NOT NULL DEFAULT 'audio',
  mode TEXT NOT NULL DEFAULT 'direct',
  max_participants INTEGER NOT NULL DEFAULT 2,
  status TEXT NOT NULL DEFAULT 'ringing',
  started_at INTEGER NOT NULL,
  answered_at INTEGER,
  ended_at INTEGER,
  end_reason TEXT,
  created_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
  updated_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
  FOREIGN KEY (caller_user_id) REFERENCES users(id),
  FOREIGN KEY (receiver_user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS call_participants (
  call_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  role TEXT NOT NULL,
  joined_at INTEGER,
  left_at INTEGER,
  PRIMARY KEY (call_id, user_id),
  FOREIGN KEY (call_id) REFERENCES call_sessions(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS call_events (
  id TEXT PRIMARY KEY,
  call_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  sender_user_id TEXT,
  receiver_user_id TEXT,
  payload_json TEXT NOT NULL,
  created_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
  FOREIGN KEY (call_id) REFERENCES call_sessions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_call_sessions_users
  ON call_sessions (caller_user_id, receiver_user_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_call_events_call_created
  ON call_events (call_id, created_at ASC);

CREATE TABLE IF NOT EXISTS device_push_tokens (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  token TEXT NOT NULL,
  platform TEXT,
  device_name TEXT,
  created_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
  updated_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
  UNIQUE (user_id, token),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

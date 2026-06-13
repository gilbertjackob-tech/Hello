CREATE TABLE IF NOT EXISTS notification_events (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  type TEXT NOT NULL,
  channel TEXT NOT NULL,
  priority TEXT NOT NULL,
  collapse_key TEXT,
  payload_json TEXT NOT NULL,
  delivery_status TEXT NOT NULL DEFAULT 'realtime_only',
  created_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000)
);

CREATE INDEX IF NOT EXISTS idx_notification_events_user_created
  ON notification_events (user_id, created_at DESC);

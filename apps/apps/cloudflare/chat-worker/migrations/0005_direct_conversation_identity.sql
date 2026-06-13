ALTER TABLE conversations ADD COLUMN direct_key TEXT;

CREATE INDEX IF NOT EXISTS idx_conversations_direct_key
  ON conversations (direct_key)
  WHERE type = 'direct' AND direct_key IS NOT NULL;

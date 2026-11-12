-- Per-account monotonic cursor allocator for stable pagination under concurrent/same-ms writes.
CREATE TABLE IF NOT EXISTS sync_account_clocks (
  user_id TEXT PRIMARY KEY NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  last_server_updated_at INTEGER NOT NULL CHECK (last_server_updated_at >= 0)
);

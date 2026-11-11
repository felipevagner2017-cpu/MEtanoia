-- Authenticated crypto context is stored separately so existing ciphertext rows
-- remain untouched and this migration can be safely re-applied by local validators.
CREATE TABLE IF NOT EXISTS sync_record_context (
  user_id TEXT NOT NULL,
  record_id TEXT NOT NULL,
  source_device_id TEXT NOT NULL,
  purpose TEXT NOT NULL DEFAULT 'record',
  PRIMARY KEY(user_id, record_id),
  FOREIGN KEY(user_id, record_id) REFERENCES records(user_id, record_id) ON DELETE CASCADE,
  FOREIGN KEY(source_device_id) REFERENCES devices(id) ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_sync_record_context_source_device
  ON sync_record_context(user_id, source_device_id);

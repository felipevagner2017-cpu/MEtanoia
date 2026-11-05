-- Bind each session to its originating device so revocation is scoped safely.
ALTER TABLE sessions ADD COLUMN device_id TEXT REFERENCES devices(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_sessions_device_active ON sessions(device_id, revoked_at, expires_at);

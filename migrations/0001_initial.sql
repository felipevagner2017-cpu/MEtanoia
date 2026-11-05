-- MEtanoia central data schema — D1 / SQLite
-- Version: 0001. Never apply to the Restaura Lar database.
-- All user content is stored as authenticated ciphertext, not plaintext JSON.

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY NOT NULL,
  email_normalized TEXT NOT NULL UNIQUE,
  password_salt_b64 TEXT NOT NULL,
  password_hash_b64 TEXT NOT NULL,
  password_kdf TEXT NOT NULL DEFAULT 'PBKDF2-SHA256-310000',
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','disabled')),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
  id TEXT PRIMARY KEY NOT NULL,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_id TEXT REFERENCES devices(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  revoked_at INTEGER
);
CREATE INDEX IF NOT EXISTS idx_sessions_user_expiry ON sessions(user_id, expires_at);
CREATE INDEX IF NOT EXISTS idx_sessions_device_active ON sessions(device_id, revoked_at, expires_at);

CREATE TABLE IF NOT EXISTS devices (
  id TEXT PRIMARY KEY NOT NULL,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  platform TEXT NOT NULL CHECK (platform IN ('android','pwa','web')),
  label TEXT NOT NULL DEFAULT '',
  last_seen_at INTEGER NOT NULL,
  revoked_at INTEGER,
  UNIQUE(user_id, id)
);
CREATE INDEX IF NOT EXISTS idx_devices_user_active ON devices(user_id, revoked_at);

CREATE TABLE IF NOT EXISTS records (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  record_id TEXT NOT NULL,
  dimension TEXT NOT NULL CHECK (dimension IN ('DEVOTION','MATRIMONIAL','PROFESSIONAL','DAILY')),
  record_type TEXT NOT NULL,
  payload_ciphertext TEXT NOT NULL,
  encryption_version INTEGER NOT NULL DEFAULT 1,
  revision INTEGER NOT NULL DEFAULT 1 CHECK (revision > 0),
  client_updated_at INTEGER NOT NULL,
  server_updated_at INTEGER NOT NULL,
  deleted_at INTEGER,
  PRIMARY KEY(user_id, record_id)
);
CREATE INDEX IF NOT EXISTS idx_records_user_server_time ON records(user_id, server_updated_at);
CREATE INDEX IF NOT EXISTS idx_records_user_dimension ON records(user_id, dimension, deleted_at);

CREATE TABLE IF NOT EXISTS sync_operations (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  operation_id TEXT NOT NULL,
  device_id TEXT NOT NULL,
  record_id TEXT NOT NULL,
  operation TEXT NOT NULL CHECK (operation IN ('upsert','delete')),
  base_revision INTEGER NOT NULL DEFAULT 0,
  result_revision INTEGER,
  status TEXT NOT NULL CHECK (status IN ('applied','conflict','rejected')),
  server_updated_at INTEGER NOT NULL,
  PRIMARY KEY(user_id, operation_id),
  FOREIGN KEY(user_id, device_id) REFERENCES devices(user_id, id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_sync_ops_user_time ON sync_operations(user_id, server_updated_at);

CREATE TABLE IF NOT EXISTS consents (
  id TEXT PRIMARY KEY NOT NULL,
  owner_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  participant_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  purpose TEXT NOT NULL,
  scope TEXT NOT NULL,
  granted_at INTEGER NOT NULL,
  revoked_at INTEGER,
  evidence_ciphertext TEXT NOT NULL,
  CHECK (owner_user_id <> participant_user_id)
);
CREATE INDEX IF NOT EXISTS idx_consents_pair ON consents(owner_user_id, participant_user_id, purpose, revoked_at);

CREATE TABLE IF NOT EXISTS ai_conversations (
  id TEXT PRIMARY KEY NOT NULL,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title_ciphertext TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  archived_at INTEGER
);
CREATE INDEX IF NOT EXISTS idx_ai_conversations_user_updated ON ai_conversations(user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS ai_messages (
  id TEXT PRIMARY KEY NOT NULL,
  conversation_id TEXT NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role TEXT NOT NULL CHECK (role IN ('user','assistant','system')),
  content_ciphertext TEXT NOT NULL,
  model TEXT,
  input_tokens INTEGER NOT NULL DEFAULT 0,
  output_tokens INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ai_messages_conversation_time ON ai_messages(conversation_id, created_at);

CREATE TABLE IF NOT EXISTS ai_usage_daily (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  usage_date TEXT NOT NULL,
  request_count INTEGER NOT NULL DEFAULT 0,
  input_tokens INTEGER NOT NULL DEFAULT 0,
  output_tokens INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(user_id, usage_date)
);

CREATE TABLE IF NOT EXISTS backups (
  id TEXT PRIMARY KEY NOT NULL,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_id TEXT NOT NULL,
  format_version INTEGER NOT NULL,
  payload_ciphertext TEXT NOT NULL,
  sha256 TEXT NOT NULL,
  record_count INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  FOREIGN KEY(user_id, device_id) REFERENCES devices(user_id, id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_backups_user_time ON backups(user_id, created_at DESC);

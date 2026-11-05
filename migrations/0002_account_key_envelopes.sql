-- Encrypted client-side account key envelopes. The server never receives the passphrase or plaintext DEK.
CREATE TABLE IF NOT EXISTS account_key_envelopes (
  user_id TEXT PRIMARY KEY NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  envelope_json TEXT NOT NULL,
  envelope_version INTEGER NOT NULL CHECK (envelope_version = 1),
  created_by_device_id TEXT NOT NULL,
  updated_by_device_id TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  FOREIGN KEY(user_id, created_by_device_id) REFERENCES devices(user_id, id) ON DELETE RESTRICT,
  FOREIGN KEY(user_id, updated_by_device_id) REFERENCES devices(user_id, id) ON DELETE RESTRICT
);

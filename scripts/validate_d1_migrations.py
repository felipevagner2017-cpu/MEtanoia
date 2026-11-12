#!/usr/bin/env python3
"""Validate Cloudflare D1/SQLite migrations locally without touching any remote database."""
from pathlib import Path
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
MIGRATIONS = sorted((ROOT / "migrations").glob("*.sql"))
if not MIGRATIONS:
    raise SystemExit("No D1 migrations found")

db = sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys = ON")
for migration in MIGRATIONS:
    db.executescript(migration.read_text(encoding="utf-8"))

tables = {
    row[0]
    for row in db.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
    )
}
required = {
    "users", "sessions", "devices", "records", "sync_operations",
    "consents", "ai_conversations", "ai_messages", "ai_usage_daily", "backups", "account_key_envelopes",
    "sync_record_context", "sync_account_clocks",
}
missing = sorted(required - tables)
if missing:
    raise SystemExit(f"Missing tables: {', '.join(missing)}")

# Migrations must be safe to re-run and preserve the schema.
for migration in MIGRATIONS:
    db.executescript(migration.read_text(encoding="utf-8"))

# Validate important relational constraints and enumerations.
db.execute(
    "INSERT INTO users (id,email_normalized,password_salt_b64,password_hash_b64,created_at,updated_at) "
    "VALUES ('u1','owner@example.invalid','salt','hash',1,1)"
)
db.execute(
    "INSERT INTO devices (id,user_id,platform,last_seen_at) VALUES ('d1','u1','android',1)"
)
db.execute(
    "INSERT INTO sessions (id,user_id,device_id,token_hash,created_at,expires_at) "
    "VALUES ('s1','u1','d1','token-hash',1,9999999999)"
)
session_device = db.execute(
    "SELECT device_id FROM sessions WHERE id='s1'"
).fetchone()[0]
if session_device != "d1":
    raise SystemExit("Session-to-device binding is missing")

db.execute(
    "INSERT INTO account_key_envelopes(user_id,envelope_json,envelope_version,created_by_device_id,updated_by_device_id,created_at,updated_at) "
    "VALUES ('u1','{\\\"format\\\":\\\"metanoia-key-envelope\\\"}',1,'d1','d1',2,2)"
)
if db.execute("SELECT user_id FROM account_key_envelopes WHERE user_id='u1'").fetchone()[0] != "u1":
    raise SystemExit("Encrypted account key envelope storage failed")
try:
    db.execute(
        "INSERT INTO account_key_envelopes(user_id,envelope_json,envelope_version,created_by_device_id,updated_by_device_id,created_at,updated_at) "
        "VALUES ('u2','{}',1,'d1','d1',2,2)"
    )
except sqlite3.IntegrityError:
    pass
else:
    raise SystemExit("Account key envelope device isolation constraint is missing")

db.execute(
    "INSERT INTO records (user_id,record_id,dimension,record_type,payload_ciphertext,client_updated_at,server_updated_at) "
    "VALUES ('u1','r1','DEVOTION','journal','ciphertext',1,1)"
)
db.execute(
    "INSERT INTO sync_record_context(user_id,record_id,source_device_id,purpose) VALUES ('u1','r1','d1','record')"
)
if db.execute("SELECT source_device_id,purpose FROM sync_record_context WHERE user_id='u1' AND record_id='r1'").fetchone() != ("d1","record"):
    raise SystemExit("Authenticated record crypto context is not bound to the source device")
# Validate the exact atomic compare-and-swap pattern used by /api/sync/push.
def apply_sync(operation_id: str, record_id: str, base_revision: int, next_revision: int, now: int):
    write = db.execute(
        "INSERT INTO records(user_id,record_id,dimension,record_type,payload_ciphertext,encryption_version,revision,client_updated_at,server_updated_at,deleted_at) "
        "VALUES(?,?,?,'journal','ciphertext',1,?,?,?,NULL) "
        "ON CONFLICT(user_id,record_id) DO UPDATE SET revision=excluded.revision,client_updated_at=excluded.client_updated_at,server_updated_at=excluded.server_updated_at "
        "WHERE records.revision=?",
        ("u1", record_id, "DEVOTION", next_revision, now, now, base_revision),
    )
    changed = write.rowcount
    db.execute(
        "INSERT INTO sync_operations(user_id,operation_id,device_id,record_id,operation,base_revision,result_revision,status,server_updated_at) "
        "SELECT ?,?,?,?,?,?,?, 'applied', ? WHERE changes()=1",
        ("u1", operation_id, "d1", record_id, "upsert", base_revision, next_revision, now),
    )
    return changed

if apply_sync("op-first", "atomic-record", 0, 1, 10) != 1:
    raise SystemExit("Initial atomic sync insert failed")
if apply_sync("op-stale", "atomic-record", 0, 1, 11) != 0:
    raise SystemExit("Stale revision was not rejected atomically")
if db.execute("SELECT COUNT(*) FROM sync_operations WHERE operation_id='op-stale'").fetchone()[0] != 0:
    raise SystemExit("Rejected stale operation was incorrectly recorded as applied")
if apply_sync("op-second", "atomic-record", 1, 2, 12) != 1:
    raise SystemExit("Valid next revision failed")
if db.execute("SELECT revision FROM records WHERE record_id='atomic-record'").fetchone()[0] != 2:
    raise SystemExit("Atomic sync revision did not advance correctly")

try:
    db.execute(
        "INSERT INTO records (user_id,record_id,dimension,record_type,payload_ciphertext,client_updated_at,server_updated_at) "
        "VALUES ('u1','r2','UNKNOWN','journal','ciphertext',1,1)"
    )
except sqlite3.IntegrityError:
    pass
else:
    raise SystemExit("Record dimension CHECK constraint is missing")

try:
    db.execute(
        "INSERT INTO users (id,email_normalized,password_salt_b64,password_hash_b64,created_at,updated_at) "
        "VALUES ('u2','owner@example.invalid','salt','hash',1,1)"
    )
except sqlite3.IntegrityError:
    pass
else:
    raise SystemExit("Unique email constraint is missing")

print(f"Validated {len(MIGRATIONS)} D1 migration(s), {len(tables)} tables, encrypted account key envelope storage, idempotent re-application, device-bound sessions, atomic sync revision checks, and key constraints.")

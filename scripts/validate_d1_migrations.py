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
    "consents", "ai_conversations", "ai_messages", "ai_usage_daily", "backups",
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
    "INSERT INTO records (user_id,record_id,dimension,record_type,payload_ciphertext,client_updated_at,server_updated_at) "
    "VALUES ('u1','r1','DEVOTION','journal','ciphertext',1,1)"
)
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

print(f"Validated {len(MIGRATIONS)} D1 migration(s), {len(tables)} tables, idempotent re-application, and key constraints.")

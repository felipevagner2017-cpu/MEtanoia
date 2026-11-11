-- Preserve the authenticated source device and cryptographic purpose for shared v2 records.
-- Legacy rows remain readable with explicit legacy defaults; new v2 writes are validated by the Worker.
ALTER TABLE records ADD COLUMN source_device_id TEXT NOT NULL DEFAULT '';
ALTER TABLE records ADD COLUMN purpose TEXT NOT NULL DEFAULT 'record';

-- source_device_id is already present in the existing records schema.
-- Add the authenticated cryptographic purpose needed by shared v2 records.
ALTER TABLE records ADD COLUMN purpose TEXT NOT NULL DEFAULT 'record';

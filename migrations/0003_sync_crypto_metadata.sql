-- Preserve the authenticated v2 record derivation context without exposing plaintext.
-- source_device_id and purpose are authenticated AAD inputs; the server stores them opaquely.
ALTER TABLE records ADD COLUMN source_device_id TEXT;
ALTER TABLE records ADD COLUMN purpose TEXT;

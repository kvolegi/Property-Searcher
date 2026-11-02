-- Migration V2: Add raw_payload JSON/TEXT column according to ADR 0004
ALTER TABLE discovered_properties ADD COLUMN IF NOT EXISTS raw_payload TEXT;

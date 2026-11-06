-- Batch 9 (7 Nov 2026): how many of each entry a profile found (crops harvested, ores mined, fish caught, mobs killed).
-- Rows from before count as found once.
ALTER TABLE collection_entries ADD COLUMN IF NOT EXISTS amount BIGINT NOT NULL DEFAULT 1;

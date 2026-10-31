-- Owner F10 (REVIEW3): after a member leaves or is kicked, that member slot stays locked (members.slot-lock-hours, 48 h)
-- before the island can invite someone into it. One row per freed slot; until_at = epoch millis when the slot opens again.
-- member_uuid = the profile key that left, left_reason = leave | kick. Staff removals and kicks of members inactive
-- 21+ days never create a row. Invites and accepts count rows with until_at > now inside their island-row lock.
CREATE TABLE IF NOT EXISTS island_slot_locks (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  island_id BINARY(16) NOT NULL,
  member_uuid BINARY(16) NOT NULL,
  left_reason VARCHAR(16) NOT NULL,
  created_at BIGINT NOT NULL,
  until_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_island_slot_locks_island ON island_slot_locks (island_id, until_at);

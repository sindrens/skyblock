-- Phase P7 (spec 13.2): staff island bans from the punishment ladder ("island-ban <time|perm>"). The player (player UUID,
-- every profile) can't enter this one island until until_at, even as a member; staff with the visit bypass still can.
-- until_at = epoch millis (9223372036854775807 = until lifted). reference = what created it (punishment:<id>), used by
-- undo/pardon to lift exactly this ban. Rows are never deleted: lifted_at marks a lifted ban (history).
CREATE TABLE IF NOT EXISTS island_staff_bans (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  island_id BINARY(16) NOT NULL,
  uuid BINARY(16) NOT NULL,
  until_at BIGINT NOT NULL,
  reason VARCHAR(255) NOT NULL,
  actor VARCHAR(32) NOT NULL,
  reference VARCHAR(64) NULL,
  created_at BIGINT NOT NULL,
  lifted_at BIGINT NULL
);

CREATE INDEX IF NOT EXISTS idx_island_staff_bans_island ON island_staff_bans (island_id, uuid);

CREATE INDEX IF NOT EXISTS idx_island_staff_bans_reference ON island_staff_bans (reference);

-- Owner answer 251 (round 1): when a member is kicked, a backup of their island profile is kept so admins can restore it
-- (/isadmin restore). member_role = the island role they had; money = the profile's money at the kick (NULL = economy
-- not available); inventory/ender_chest = ItemStack.serializeItemsAsBytes of that profile, captured only when the profile
-- was online and active at the kick (items_captured = FALSE otherwise). restored_at: membership + money restored (once);
-- items_restored_at: items given back (once, only into an empty inventory and ender chest so nothing is duplicated).
CREATE TABLE IF NOT EXISTS island_kick_backups (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  island_id BINARY(16) NOT NULL,
  island_name VARCHAR(64) NOT NULL,
  profile_id BINARY(16) NOT NULL,
  player_id BINARY(16) NOT NULL,
  player_name VARCHAR(16) NOT NULL,
  member_role VARCHAR(16) NOT NULL,
  money BIGINT NULL,
  items_captured BOOLEAN NOT NULL DEFAULT FALSE,
  inventory MEDIUMBLOB NULL,
  ender_chest MEDIUMBLOB NULL,
  removal VARCHAR(16) NOT NULL,
  kicked_by VARCHAR(32) NOT NULL,
  created_at BIGINT NOT NULL,
  restored_at BIGINT NULL,
  restored_by VARCHAR(32) NULL,
  items_restored_at BIGINT NULL
);

CREATE INDEX IF NOT EXISTS idx_island_kick_backups_player ON island_kick_backups (player_id);

CREATE INDEX IF NOT EXISTS idx_island_kick_backups_profile ON island_kick_backups (profile_id);

-- Owner answer 247 (round 1): no personal member homes (/is setmyhome is gone); the table is no longer used.
DROP TABLE IF EXISTS island_member_homes;

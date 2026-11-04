-- Owner 4 Nov 2026, "island = profile": leaving an island (leave, kick, disband, staff delete) wipes the profile.
-- A wipe retires the profile key (profiles.deleted_at set, every module's rows stay untouched) and puts a fresh profile in
-- the slot. The retired key + its saved state ARE the snapshot; this table lists them for /profiles snapshots and restore.
-- purged_at set = the retention ended and every module deleted the old key's rows (ProfileDeletedEvent).
CREATE TABLE IF NOT EXISTS profile_snapshots (
  id BIGINT NOT NULL AUTO_INCREMENT,
  owner BINARY(16) NOT NULL,
  profile_id BINARY(16) NOT NULL,
  replaced_by BINARY(16) NULL,
  slot INT NOT NULL,
  reason VARCHAR(24) NOT NULL,
  actor_name VARCHAR(32) NOT NULL,
  island_id BINARY(16) NULL,
  island_name VARCHAR(64) NULL,
  money BIGINT NULL,
  shards BIGINT NULL,
  items INT NULL,
  created_at BIGINT NOT NULL,
  expires_at BIGINT NOT NULL,
  restored_at BIGINT NULL,
  restored_by VARCHAR(32) NULL,
  purged_at BIGINT NULL,
  notified BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_profile_snapshots_owner ON profile_snapshots (owner, created_at);
CREATE INDEX IF NOT EXISTS idx_profile_snapshots_expires ON profile_snapshots (expires_at);
CREATE INDEX IF NOT EXISTS idx_profile_snapshots_profile ON profile_snapshots (profile_id);

-- The player file of this (offline) player still holds the items of profile_id, a profile that is no longer active
-- (wiped or restored away while they were offline). The next join saves the file's state into profile_states for
-- profile_id first, then applies the active profile's state. One row per player.
CREATE TABLE IF NOT EXISTS profile_captures (
  owner BINARY(16) NOT NULL,
  profile_id BINARY(16) NOT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (owner)
);

-- Crash safety: written before an island removal is saved, removed by the wipe. A leftover row (crash in between) is
-- finished by the next join or the sweep when the profile is no longer on island_id, or dropped when it still is.
CREATE TABLE IF NOT EXISTS profile_wipe_intents (
  profile_id BINARY(16) NOT NULL,
  owner BINARY(16) NOT NULL,
  reason VARCHAR(24) NOT NULL,
  actor_name VARCHAR(32) NOT NULL,
  island_id BINARY(16) NULL,
  island_name VARCHAR(64) NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (profile_id)
);

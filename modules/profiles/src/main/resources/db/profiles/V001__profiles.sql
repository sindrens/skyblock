-- Island profiles (PROF, owner decisions P1-P12). A profile = one island + everything that belongs to it.
-- The profile id is the key every per-profile table stores (eco_accounts.owner_id, skills.player, island_members.uuid ...).
-- The first profile of an account uses the player UUID as its id, so every row written before profiles already belongs
-- to profile 1: this migration only adds the profile rows, it moves no data. Later profiles get random UUIDs.
-- name/icon NULL = the default name/icon. deleted_at set = deleted (the id is never used again).
CREATE TABLE IF NOT EXISTS profiles (
  id BINARY(16) NOT NULL,
  owner BINARY(16) NOT NULL,
  slot INT NOT NULL,
  name VARCHAR(32) NULL,
  icon VARCHAR(64) NULL,
  created_at BIGINT NOT NULL,
  last_played_at BIGINT NOT NULL DEFAULT 0,
  playtime_ms BIGINT NOT NULL DEFAULT 0,
  settings TEXT NULL,
  deleted_at BIGINT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_profiles_owner ON profiles (owner, deleted_at);

-- One row per player: the active profile (also the last active one of offline players). Switches lock this row.
CREATE TABLE IF NOT EXISTS profile_accounts (
  owner BINARY(16) NOT NULL,
  active_profile BINARY(16) NOT NULL,
  switched_at BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (owner)
);

-- Saved player state of profiles that are not being played: inventory + armor + offhand, ender chest (both
-- ItemStack.serializeItemsAsBytes) and XP/health/food/effects/location (YAML). The active profile's state lives in the
-- normal player file; a row for the ACTIVE profile means "switched but not applied yet" and is applied on the next join.
CREATE TABLE IF NOT EXISTS profile_states (
  profile_id BINARY(16) NOT NULL,
  owner BINARY(16) NOT NULL,
  inventory MEDIUMBLOB NULL,
  ender_chest MEDIUMBLOB NULL,
  data TEXT NULL,
  saved_at BIGINT NOT NULL,
  PRIMARY KEY (profile_id)
);

-- Stored slot unlocks (P6/P8): source gems (kept at a season reset) or grant (lost). Rank slots are live permissions.
CREATE TABLE IF NOT EXISTS profile_slots (
  owner BINARY(16) NOT NULL,
  slot INT NOT NULL,
  source VARCHAR(8) NOT NULL,
  season_id INT NOT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (owner, slot)
);

-- Existing players: profile 1 with id = player UUID (all their existing data), playtime so far, active.
INSERT INTO profiles (id, owner, slot, name, icon, created_at, last_played_at, playtime_ms, settings, deleted_at)
  SELECT p.uuid, p.uuid, 1, NULL, NULL, p.first_seen, p.last_seen, p.playtime_ms, NULL, NULL FROM players p
  WHERE NOT EXISTS (SELECT 1 FROM profiles x WHERE x.id = p.uuid);

INSERT INTO profile_accounts (owner, active_profile, switched_at)
  SELECT p.uuid, p.uuid, 0 FROM players p
  WHERE NOT EXISTS (SELECT 1 FROM profile_accounts a WHERE a.owner = p.uuid);

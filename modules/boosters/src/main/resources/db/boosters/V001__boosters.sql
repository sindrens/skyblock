-- Boosters (PHASE-10-REWARDS.md M10.2). Times in milliseconds. Booster types: sell, skill_xp, tool_xp, crystals.

-- Running personal boosters, one per profile and type (same type never stacks, it adds time: R3). remaining_ms only
-- counts down while the profile is online and active; usage is subtracted relatively (remaining_ms - used), so it can
-- never undo time added by an activation at the same moment. Rows at or below 0 are finished and deleted.
CREATE TABLE IF NOT EXISTS booster_active (
  profile_key BINARY(16) NOT NULL,
  booster_type VARCHAR(16) NOT NULL,
  bonus_percent INT NOT NULL,
  remaining_ms BIGINT NOT NULL,
  started_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key, booster_type)
);

-- Banked booster time not started yet (the AFK pool's sell booster): starts by itself when the type is free and the
-- player is not held (inside the AFK pool). The newest strength applies to the whole bank of a type.
CREATE TABLE IF NOT EXISTS booster_bank (
  profile_key BINARY(16) NOT NULL,
  booster_type VARCHAR(16) NOT NULL,
  bonus_percent INT NOT NULL,
  banked_ms BIGINT NOT NULL,
  bank_source VARCHAR(32) NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key, booster_type)
);

-- Every booster item ever started (its unique id): the same item can never start twice, even when a copy or a crash
-- rollback brings it back.
CREATE TABLE IF NOT EXISTS booster_used_items (
  item_uuid BINARY(16) NOT NULL,
  profile_key BINARY(16) NOT NULL,
  item_kind VARCHAR(16) NOT NULL,
  used_at BIGINT NOT NULL,
  PRIMARY KEY (item_uuid)
);

-- Global boosters: one active per type, the rest queued in order. remaining_ms counts down while the server runs.
CREATE TABLE IF NOT EXISTS booster_global (
  global_id BIGINT NOT NULL AUTO_INCREMENT,
  booster_type VARCHAR(16) NOT NULL,
  bonus_percent INT NOT NULL,
  duration_ms BIGINT NOT NULL,
  remaining_ms BIGINT NOT NULL,
  starter_uuid BINARY(16) NOT NULL,
  starter_profile BINARY(16) NOT NULL,
  starter_name VARCHAR(16) NOT NULL,
  global_state VARCHAR(8) NOT NULL,
  created_at BIGINT NOT NULL,
  started_at BIGINT NULL,
  ended_at BIGINT NULL,
  PRIMARY KEY (global_id)
);

CREATE INDEX IF NOT EXISTS idx_booster_global_state ON booster_global (global_state, booster_type, global_id);

-- Thanks for a global booster (RW2): once per booster per player. gem_state 0 = gem not paid yet (retried at start-up,
-- idempotent), 1 = paid, 2 = no gem (own IP, or the booster's gem limit was reached).
CREATE TABLE IF NOT EXISTS booster_thanks (
  global_id BIGINT NOT NULL,
  thanker_uuid BINARY(16) NOT NULL,
  gem_state INT NOT NULL,
  thanked_at BIGINT NOT NULL,
  PRIMARY KEY (global_id, thanker_uuid)
);

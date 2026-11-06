-- Milestones (Complete Developer Spec §7.10). scope_type ISLAND (scope_id = island id) or PROFILE (scope_id = profile key).
-- A progress row exists once the chain was discovered (first matching action), so storage grows with real play only.
-- value = counted actions; bonus_value = extra progress from progress boosters (counts for tier targets only).
-- Tier completion is derived (highest tier whose target <= value + bonus_value) and recorded once in milestone_tiers.
-- Times in epoch millis. Valid on MariaDB 11.8 and H2.
CREATE TABLE IF NOT EXISTS milestone_progress (
  scope_type VARCHAR(8) NOT NULL,
  scope_id BINARY(16) NOT NULL,
  chain_id VARCHAR(48) NOT NULL,
  progress BIGINT NOT NULL,
  bonus_progress BIGINT NOT NULL DEFAULT 0,
  discovered_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (scope_type, scope_id, chain_id)
);

-- Who on an island did how much of a chain ("You contributed 18%").
CREATE TABLE IF NOT EXISTS milestone_contrib (
  island_id BINARY(16) NOT NULL,
  chain_id VARCHAR(48) NOT NULL,
  profile_key BINARY(16) NOT NULL,
  amount BIGINT NOT NULL,
  PRIMARY KEY (island_id, chain_id, profile_key)
);

-- Completed tiers (READY until claimed).
CREATE TABLE IF NOT EXISTS milestone_tiers (
  scope_type VARCHAR(8) NOT NULL,
  scope_id BINARY(16) NOT NULL,
  chain_id VARCHAR(48) NOT NULL,
  tier INT NOT NULL,
  completed_at BIGINT NOT NULL,
  PRIMARY KEY (scope_type, scope_id, chain_id, tier)
);

-- Claimed tiers: island tiers once per island by any member; personal tiers by the profile. Claiming tier N also marks
-- the lower unclaimed tiers (perks only; the perk is always the highest claimed tier).
CREATE TABLE IF NOT EXISTS milestone_claims (
  scope_type VARCHAR(8) NOT NULL,
  scope_id BINARY(16) NOT NULL,
  chain_id VARCHAR(48) NOT NULL,
  tier INT NOT NULL,
  claimed_by_profile BINARY(16) NOT NULL,
  claimed_at BIGINT NOT NULL,
  PRIMARY KEY (scope_type, scope_id, chain_id, tier)
);

-- Unclaimed READY island tiers kept across an island reset inside a season (owner R3 r3).
CREATE TABLE IF NOT EXISTS milestone_carryover (
  island_id BINARY(16) NOT NULL,
  chain_id VARCHAR(48) NOT NULL,
  tier INT NOT NULL,
  PRIMARY KEY (island_id, chain_id, tier)
);

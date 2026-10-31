-- Battle pass (PHASE-10-REWARDS.md M10.8). Times in epoch milliseconds. season_id = pass.yml -> season.id.

-- Pass XP of a profile in a season (the free track and the level are per profile, P10-28).
CREATE TABLE IF NOT EXISTS pass_progress (
  profile_key BINARY(16) NOT NULL,
  season_id VARCHAR(32) NOT NULL,
  pass_xp BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key, season_id)
);

-- Idempotency: one row per XP event that carried a key (e.g. quests:<grant id>). A retried event finds its row and adds
-- nothing. Old rows are pruned after history.keep-days.
CREATE TABLE IF NOT EXISTS pass_xp_events (
  event_key VARCHAR(96) NOT NULL,
  profile_key BINARY(16) NOT NULL,
  season_id VARCHAR(32) NOT NULL,
  xp_added INT NOT NULL,
  xp_source VARCHAR(32) NOT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (event_key)
);

CREATE INDEX IF NOT EXISTS idx_pass_xp_events_time ON pass_xp_events (created_at);

-- Claimed tiers. Free track: owner_key = profile key. Premium track: owner_key = the player's account UUID, so a premium
-- cosmetic is given once per account on whichever profile reaches the level first (P10-28). The primary key makes a
-- tier claimable once; the claim row is written in the same transaction as the payout.
CREATE TABLE IF NOT EXISTS pass_claims (
  owner_key BINARY(16) NOT NULL,
  season_id VARCHAR(32) NOT NULL,
  pass_track VARCHAR(8) NOT NULL,
  tier_level INT NOT NULL,
  profile_key BINARY(16) NOT NULL,
  player_uuid BINARY(16) NOT NULL,
  claimed_at BIGINT NOT NULL,
  PRIMARY KEY (owner_key, season_id, pass_track, tier_level)
);

-- Premium pass per account and season (P10-28). Written by staff (/pass admin premium) now and by the store later
-- (Phase 11). The permission skyblock.pass.premium also counts as premium.
CREATE TABLE IF NOT EXISTS pass_premium (
  player_uuid BINARY(16) NOT NULL,
  season_id VARCHAR(32) NOT NULL,
  granted_by VARCHAR(48) NOT NULL,
  granted_at BIGINT NOT NULL,
  PRIMARY KEY (player_uuid, season_id)
);

-- Hand-outs after a claim's transaction (keys, boosters, titles, tags). Written in that transaction as 'pending'; moved to
-- 'sending' right before the hand-out and to 'delivered' after it. A crash while 'sending' sends titles/tags again
-- (idempotent) and moves keys and boosters to 'review' for staff, so a reward can be lost but never doubled.
CREATE TABLE IF NOT EXISTS pass_grants (
  grant_id BINARY(16) NOT NULL,
  profile_key BINARY(16) NOT NULL,
  player_uuid BINARY(16) NOT NULL,
  season_id VARCHAR(32) NOT NULL,
  tier_level INT NOT NULL,
  grant_kind VARCHAR(16) NOT NULL,
  grant_ref VARCHAR(64) NOT NULL,
  amount BIGINT NOT NULL,
  grant_state VARCHAR(12) NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (grant_id)
);

CREATE INDEX IF NOT EXISTS idx_pass_grants_state ON pass_grants (grant_state, profile_key);

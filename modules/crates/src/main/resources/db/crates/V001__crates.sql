-- Crates and keys (PHASE-10 M10.1). Everything is per PROFILE key (owner decisions 127, P2, P4).
-- crate_keys: two counters per profile and crate. Bound keys come from sources an alt could farm (votes, AFK pool,
-- daily calendar, playtime, rank claims) and can never leave the profile; tradeable keys can be withdrawn later (M10.4).
CREATE TABLE IF NOT EXISTS crate_keys (
  profile_key BINARY(16) NOT NULL,
  crate_id VARCHAR(32) NOT NULL,
  bound_amount BIGINT NOT NULL DEFAULT 0,
  tradeable_amount BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key, crate_id)
);

-- Opens since the last top-band reward. season_id: a row of an older season counts as 0 (pity resets each season).
CREATE TABLE IF NOT EXISTS crate_pity (
  profile_key BINARY(16) NOT NULL,
  crate_id VARCHAR(32) NOT NULL,
  opens_since_top INT NOT NULL DEFAULT 0,
  season_id INT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key, crate_id)
);

-- One row per open, written in the SAME database transaction that takes the key, moves the pity counter and pays the
-- currencies, so a roll is saved before anything is shown. grant_state:
--   done       paid inside the transaction (money, crystals, shards, keys, crystal fallback)
--   pending    item / cosmetic / fuel to hand out after the commit -> delivered (or failed: staff are told)
--   review     still pending at startup (crash mid hand-out): items are never handed out twice, staff check and give
--              them by hand (audit crates/crate_review). Cosmetics are simply granted again (idempotent).
CREATE TABLE IF NOT EXISTS crate_opens (
  open_id BINARY(16) NOT NULL,
  batch_id BINARY(16) NOT NULL,
  profile_key BINARY(16) NOT NULL,
  player_uuid BINARY(16) NOT NULL,
  crate_id VARCHAR(32) NOT NULL,
  reward_id VARCHAR(32) NOT NULL,
  reward_kind VARCHAR(16) NOT NULL,
  reward_ref VARCHAR(64) NOT NULL,
  reward_amount BIGINT NOT NULL,
  top_band BOOLEAN NOT NULL,
  pity_forced BOOLEAN NOT NULL,
  bound_key BOOLEAN NOT NULL,
  grant_state VARCHAR(12) NOT NULL,
  season_id INT NOT NULL,
  opened_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (open_id)
);

CREATE INDEX IF NOT EXISTS idx_crate_opens_profile ON crate_opens (profile_key, opened_at);
CREATE INDEX IF NOT EXISTS idx_crate_opens_state ON crate_opens (grant_state);
CREATE INDEX IF NOT EXISTS idx_crate_opens_batch ON crate_opens (batch_id);

-- AFK pool (O10, RW3). Times in milliseconds, day_key = yyyymmdd in the reset time zone (Europe/Oslo).
-- afk_state: one row per account: rewards given so far (the next one's number is its idempotency key) and the
-- credited time since the last reward (kept across leaving and midnight).
CREATE TABLE IF NOT EXISTS afk_state (
  player_uuid BINARY(16) NOT NULL,
  reward_seq BIGINT NOT NULL DEFAULT 0,
  progress_ms BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (player_uuid)
);

-- Time credited per account per day (daily cap 8 h, O10).
CREATE TABLE IF NOT EXISTS afk_daily (
  player_uuid BINARY(16) NOT NULL,
  day_key INT NOT NULL,
  credited_ms BIGINT NOT NULL DEFAULT 0,
  rewards_today INT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (player_uuid, day_key)
);

-- Key milestones reached (the primary key makes each one happen once per account per day).
CREATE TABLE IF NOT EXISTS afk_milestones (
  player_uuid BINARY(16) NOT NULL,
  day_key INT NOT NULL,
  milestone_min INT NOT NULL,
  reached_at BIGINT NOT NULL,
  PRIMARY KEY (player_uuid, day_key, milestone_min)
);

-- Keys and island fuel not delivered yet (crates / Island Fuel do not exist yet, or the service failed).
-- One open row per account, kind and target; delivered rows keep delivered_at for staff history.
CREATE TABLE IF NOT EXISTS afk_grants (
  grant_id BIGINT NOT NULL AUTO_INCREMENT,
  player_uuid BINARY(16) NOT NULL,
  profile_key BINARY(16) NOT NULL,
  grant_kind VARCHAR(16) NOT NULL,
  target_ref VARCHAR(48) NOT NULL,
  amount BIGINT NOT NULL,
  created_at BIGINT NOT NULL,
  delivered_at BIGINT NULL,
  PRIMARY KEY (grant_id)
);

CREATE INDEX IF NOT EXISTS idx_afk_grants_open ON afk_grants (player_uuid, delivered_at);

-- Banked and running personal sell boosters won in the pool, per profile (P: no transfer between profiles).
CREATE TABLE IF NOT EXISTS afk_boosters (
  profile_key BINARY(16) NOT NULL,
  bonus_percent INT NOT NULL,
  banked_ms BIGINT NOT NULL DEFAULT 0,
  active_until BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key)
);

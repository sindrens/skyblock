-- Achievements (F3). Progress per profile key and metric: counters hold the running total, gauges the best value seen.
-- Unlocks: one row per profile, achievement and tier (the primary key makes every tier unlock exactly once).
-- owner_uuid = the account (reward titles are account-wide, owner decision PR1).
CREATE TABLE IF NOT EXISTS achievement_progress (
  profile_key BINARY(16) NOT NULL,
  metric VARCHAR(32) NOT NULL,
  amount BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key, metric)
);

CREATE TABLE IF NOT EXISTS achievement_unlocks (
  profile_key BINARY(16) NOT NULL,
  achievement VARCHAR(48) NOT NULL,
  tier INT NOT NULL,
  owner_uuid BINARY(16) NOT NULL,
  unlocked_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key, achievement, tier)
);

CREATE INDEX IF NOT EXISTS idx_achievement_unlocks_owner ON achievement_unlocks (owner_uuid);

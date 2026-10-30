-- AC3: island achievements. Progress per island and metric (counters: running total, gauges: best value seen), unlocks
-- one row per island, achievement and tier (the primary key makes every island tier unlock exactly once).
CREATE TABLE IF NOT EXISTS achievement_island_progress (
  island_id BINARY(16) NOT NULL,
  metric VARCHAR(32) NOT NULL,
  amount BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, metric)
);

CREATE TABLE IF NOT EXISTS achievement_island_unlocks (
  island_id BINARY(16) NOT NULL,
  achievement VARCHAR(48) NOT NULL,
  tier INT NOT NULL,
  unlocked_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, achievement, tier)
);

-- AC4: season summary per profile, written by the season reset before progress is wiped (titles stay on the account).
CREATE TABLE IF NOT EXISTS achievement_history (
  season_id INT NOT NULL,
  profile_key BINARY(16) NOT NULL,
  owner_uuid BINARY(16) NOT NULL,
  tiers_done INT NOT NULL DEFAULT 0,
  points INT NOT NULL DEFAULT 0,
  completed INT NOT NULL DEFAULT 0,
  island_tiers INT NOT NULL DEFAULT 0,
  island_points INT NOT NULL DEFAULT 0,
  titles VARCHAR(1024) NOT NULL DEFAULT '',
  created_at BIGINT NOT NULL,
  PRIMARY KEY (season_id, profile_key)
);

CREATE INDEX IF NOT EXISTS idx_achievement_history_owner ON achievement_history (owner_uuid);

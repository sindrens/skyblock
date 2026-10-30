-- Dynamic island level (progression module, PHASE-5 M5.2). One row per island and season: the last calculated level,
-- the points per source after the caps (pts_*), the highest level this season (announcements only on new highs) and
-- when the current level was reached (leaderboard tie-break).
CREATE TABLE IF NOT EXISTS island_level (
  island_id BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  total_points DOUBLE NOT NULL DEFAULT 0,
  earned_points DOUBLE NOT NULL DEFAULT 0,
  island_level INT NOT NULL DEFAULT 0,
  best_level INT NOT NULL DEFAULT 0,
  level_reached_at BIGINT NOT NULL DEFAULT 0,
  pts_pillars DOUBLE NOT NULL DEFAULT 0,
  pts_upgrades DOUBLE NOT NULL DEFAULT 0,
  pts_achievements DOUBLE NOT NULL DEFAULT 0,
  pts_collections DOUBLE NOT NULL DEFAULT 0,
  pts_skills DOUBLE NOT NULL DEFAULT 0,
  pts_depths DOUBLE NOT NULL DEFAULT 0,
  pts_bank DOUBLE NOT NULL DEFAULT 0,
  pts_blocks DOUBLE NOT NULL DEFAULT 0,
  block_worth DOUBLE NOT NULL DEFAULT 0,
  bank_low BIGINT NOT NULL DEFAULT 0,
  blocks_scanned_at BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, season_id)
);

CREATE INDEX IF NOT EXISTS idx_island_level_top ON island_level (season_id, island_level);

-- Block worth per island chunk (live tracked, replaced by full rescans). blocks = placed blocks, spawners = spawner stacks.
CREATE TABLE IF NOT EXISTS island_block_worth (
  island_id BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  chunk_x INT NOT NULL,
  chunk_z INT NOT NULL,
  blocks_worth DOUBLE NOT NULL DEFAULT 0,
  spawners_worth DOUBLE NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, season_id, chunk_x, chunk_z)
);

-- One row per island, season and day (epoch day, level time zone): the season's level/value history (owner V2, the CSV
-- export comes with M5.7).
CREATE TABLE IF NOT EXISTS island_level_history (
  island_id BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  history_day INT NOT NULL,
  island_level INT NOT NULL,
  earned_points DOUBLE NOT NULL,
  total_points DOUBLE NOT NULL,
  block_worth DOUBLE NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, season_id, history_day)
);

-- Island bank balance samples (source 7 counts the lowest balance of the last 24 h). Old rows are pruned.
CREATE TABLE IF NOT EXISTS island_bank_samples (
  island_id BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  sampled_at BIGINT NOT NULL,
  balance BIGINT NOT NULL,
  PRIMARY KEY (island_id, season_id, sampled_at)
);

CREATE INDEX IF NOT EXISTS idx_island_bank_samples_time ON island_bank_samples (season_id, sampled_at);

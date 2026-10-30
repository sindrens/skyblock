-- Island pillars (progression module, PHASE-5 M5.1). xp = total pillar XP of the island this season (authoritative,
-- written as deltas in batches); pillar_level = the level at the last write (levels never go down within a season).
CREATE TABLE IF NOT EXISTS island_pillars (
  island_id BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  pillar VARCHAR(16) NOT NULL,
  xp DOUBLE NOT NULL DEFAULT 0,
  pillar_level INT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, season_id, pillar)
);

CREATE INDEX IF NOT EXISTS idx_island_pillars_rank ON island_pillars (season_id, pillar, pillar_level);

-- Pillar XP and levels gained per island, pillar and week (week_start = epoch day of the Monday, Europe/Oslo) for the
-- weekly pillar gain board (M5.7).
CREATE TABLE IF NOT EXISTS island_pillar_weekly (
  island_id BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  pillar VARCHAR(16) NOT NULL,
  week_start INT NOT NULL,
  xp_gained DOUBLE NOT NULL DEFAULT 0,
  levels_gained INT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, season_id, pillar, week_start)
);

CREATE INDEX IF NOT EXISTS idx_island_pillar_weekly_week ON island_pillar_weekly (season_id, week_start, pillar);

-- Synergy milestones reached (all shown pillars at that level), once per island and season. The row is written in the
-- same database transaction as the members' crystal rewards, so a reward is paid exactly when the row exists.
CREATE TABLE IF NOT EXISTS island_pillar_milestones (
  island_id BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  milestone INT NOT NULL,
  reached_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, season_id, milestone)
);

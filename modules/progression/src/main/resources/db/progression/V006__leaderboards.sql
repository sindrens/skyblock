-- Island leaderboards (progression module, PHASE-5 M5.7).
-- leaderboard_week_base: every island's earned level points and summed pillar levels at the start of a week (Monday
-- 00:00 Europe/Oslo, week_start = epoch day of that Monday). Weekly gain = now - base; an island without a base row was
-- created during the week and counts from 0. Written together with the final places of the week before (one transaction),
-- so a week's base exists exactly when the week before it was closed.
CREATE TABLE IF NOT EXISTS leaderboard_week_base (
  season_id INT NOT NULL,
  week_start INT NOT NULL,
  island_id BINARY(16) NOT NULL,
  earned_points DOUBLE NOT NULL,
  pillar_levels INT NOT NULL,
  PRIMARY KEY (season_id, week_start, island_id)
);

-- leaderboard_weekly: the final places of a week (board = weekly_gain_solo, weekly_gain_team, weekly_pillars_solo,
-- weekly_pillars_team). rewarded stays 0 until the weekly rewards exist (owner question PG27).
CREATE TABLE IF NOT EXISTS leaderboard_weekly (
  season_id INT NOT NULL,
  week_start INT NOT NULL,
  board VARCHAR(32) NOT NULL,
  place INT NOT NULL,
  island_id BINARY(16) NOT NULL,
  score DOUBLE NOT NULL,
  rewarded TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (season_id, week_start, board, place)
);

-- leaderboard_hidden: player accounts whose islands never show on the boards (staff, owner 576). Kept in step with the
-- permission skyblock.progression.top.hidden whenever the player joins or their rank changes, so offline staff stay
-- hidden after a restart.
CREATE TABLE IF NOT EXISTS leaderboard_hidden (
  account_id BINARY(16) NOT NULL PRIMARY KEY,
  hidden_at BIGINT NOT NULL
);

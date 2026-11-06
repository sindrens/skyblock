-- 6 Nov 2026 (V004: the old join tutorial used V001-V003): servers that ran the old join tutorial (73d18644) recorded V001 with its old tables, so the quest-book
-- tables below never got created there. Same statements as V001, all IF NOT EXISTS: a no-op on new servers.


-- One row per player who started: skipped_at = 0 while not skipped; bar_hidden = 1 after /tutorial hide.
CREATE TABLE IF NOT EXISTS tutorial_players (
  player_id BINARY(16) NOT NULL PRIMARY KEY,
  skipped_at BIGINT NOT NULL,
  bar_hidden INT NOT NULL,
  started_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);

-- Steps: done_at = 0 while open (progress = the counter so far, e.g. crops harvested), else the time it was done.
CREATE TABLE IF NOT EXISTS tutorial_steps (
  player_id BINARY(16) NOT NULL,
  step_key VARCHAR(64) NOT NULL,
  progress BIGINT NOT NULL,
  done_at BIGINT NOT NULL,
  PRIMARY KEY (player_id, step_key)
);

-- Finished chapters (also chapters closed by /tutorial skip: was_skipped = 1). Cleared by /tutorial admin reset.
CREATE TABLE IF NOT EXISTS tutorial_chapters (
  player_id BINARY(16) NOT NULL,
  chapter_id VARCHAR(32) NOT NULL,
  done_at BIGINT NOT NULL,
  was_skipped INT NOT NULL,
  PRIMARY KEY (player_id, chapter_id)
);

-- Rewards paid: written BEFORE the payout (a crash may lose a reward, never pay it twice). Never cleared, also not by a
-- staff reset, so every chapter pays once per account.
CREATE TABLE IF NOT EXISTS tutorial_rewards (
  player_id BINARY(16) NOT NULL,
  chapter_id VARCHAR(32) NOT NULL,
  paid_at BIGINT NOT NULL,
  PRIMARY KEY (player_id, chapter_id)
);

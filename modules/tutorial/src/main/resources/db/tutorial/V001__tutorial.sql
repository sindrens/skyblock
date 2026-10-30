-- Join tutorial (TUTORIAL.md §9.5). Progress is per ACCOUNT (player uuid), never per profile (TQ3).
-- progress_state: new | active | finished | legacy | staff_skipped. step_id: the step to do now (NULL when not running).
-- The finish is one transaction together with the reward (guard: active -> finished must hit exactly one row).
-- rewarded: the finish reward was paid once for this account (a staff reset never pays it again).
CREATE TABLE IF NOT EXISTS tutorial_progress (
  player_uuid BINARY(16) NOT NULL,
  progress_state VARCHAR(16) NOT NULL,
  step_id VARCHAR(32) NULL,
  step_started_at BIGINT NOT NULL DEFAULT 0,
  started_at BIGINT NULL,
  finished_at BIGINT NULL,
  finished_profile BINARY(16) NULL,
  replays INT NOT NULL DEFAULT 0,
  flow_version INT NOT NULL DEFAULT 1,
  pack_given BOOLEAN NOT NULL DEFAULT FALSE,
  rewarded BOOLEAN NOT NULL DEFAULT FALSE,
  chest_world VARCHAR(64) NULL,
  chest_x INT NOT NULL DEFAULT 0,
  chest_y INT NOT NULL DEFAULT 0,
  chest_z INT NOT NULL DEFAULT 0,
  PRIMARY KEY (player_uuid)
);

-- Steps done (early = done before the step was reached, TQ21).
CREATE TABLE IF NOT EXISTS tutorial_step_done (
  player_uuid BINARY(16) NOT NULL,
  step_id VARCHAR(32) NOT NULL,
  done_at BIGINT NOT NULL,
  early BOOLEAN NOT NULL DEFAULT FALSE,
  profile_id BINARY(16) NULL,
  PRIMARY KEY (player_uuid, step_id)
);

-- Counters of counted steps (plant 3 seeds, mine 15 blocks) so a disconnect keeps the progress.
CREATE TABLE IF NOT EXISTS tutorial_counts (
  player_uuid BINARY(16) NOT NULL,
  step_id VARCHAR(32) NOT NULL,
  amount INT NOT NULL DEFAULT 0,
  PRIMARY KEY (player_uuid, step_id)
);

-- Funnel events (§10 analytics later): start, done, stuck, skip, quit, resume, replay, staff_skip, staff_reset, finish.
CREATE TABLE IF NOT EXISTS tutorial_events (
  event_id BIGINT NOT NULL AUTO_INCREMENT,
  player_uuid BINARY(16) NOT NULL,
  step_id VARCHAR(32) NULL,
  event_kind VARCHAR(16) NOT NULL,
  created_at BIGINT NOT NULL,
  bedrock BOOLEAN NOT NULL DEFAULT FALSE,
  millis_in_step BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (event_id)
);

CREATE INDEX IF NOT EXISTS idx_tutorial_events_time ON tutorial_events (created_at);
CREATE INDEX IF NOT EXISTS idx_tutorial_events_player ON tutorial_events (player_uuid, created_at);

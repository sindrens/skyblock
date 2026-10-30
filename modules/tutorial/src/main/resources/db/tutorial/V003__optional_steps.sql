-- Optional steps (TUTORIAL.md §3.1, O1-O9, TQ8, TQ26): once per ACCOUNT. tipped_at = the first-visit tip was shown,
-- done_at = finished (0 = not yet), rewarded = the small crystal reward was paid (one transaction with done_at),
-- progress_value = blocks broken, or the bit mask of visited places.
CREATE TABLE IF NOT EXISTS tutorial_optional (
  player_uuid BINARY(16) NOT NULL,
  step_id VARCHAR(32) NOT NULL,
  tipped_at BIGINT NOT NULL DEFAULT 0,
  done_at BIGINT NOT NULL DEFAULT 0,
  rewarded BOOLEAN NOT NULL DEFAULT FALSE,
  progress_value INT NOT NULL DEFAULT 0,
  PRIMARY KEY (player_uuid, step_id)
);

CREATE INDEX IF NOT EXISTS idx_tutorial_events_step ON tutorial_events (step_id, event_kind);

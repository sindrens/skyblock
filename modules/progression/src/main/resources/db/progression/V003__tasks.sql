-- Challenge book task counters (progression module, PHASE-5 M5.3). One row per island, season and counter key
-- ('mine:coal_ore', 'earn:any', 'deliver:<task id>'). Counters are shared by every task that reads them and count from
-- the island's creation (owner P5-9). Written as deltas: buffered for normal actions, at once for deliveries (in the
-- same transaction as the delivery).
CREATE TABLE IF NOT EXISTS island_task_progress (
  island_id BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  task_target VARCHAR(96) NOT NULL,
  amount BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, season_id, task_target)
);

-- Completed tasks, once per island, task and season. Written in the same database transaction as the members' crystal
-- rewards, so a reward is paid exactly when the row exists (never twice).
CREATE TABLE IF NOT EXISTS island_task_done (
  island_id BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  task_id VARCHAR(64) NOT NULL,
  done_by BINARY(16) NULL,
  done_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, season_id, task_id)
);

-- ECON-SPAWNERS step 9: kills of tier 1-4 mobs per island (unlock of the next spawner tier in /shop).
-- Written in batches (write-behind), never trusted for money; deleted when the island is disbanded or reset.
CREATE TABLE IF NOT EXISTS stacking_kill_counts (
  island_id BINARY(16) NOT NULL,
  mob VARCHAR(48) NOT NULL,
  kills BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, mob)
);

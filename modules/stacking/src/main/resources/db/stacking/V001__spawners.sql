-- Stacked spawners (STACK). The database is the source of truth for every spawner block: the chunk only holds the
-- block. Every change is a conditional statement on the expected amount (... WHERE amount = ?), so two changes can never
-- both succeed on the same old value, and items are handed out or taken only after the row changed.
-- A spawner block without a row (e.g. after a crash between saving the row and saving the chunk) is removed, never
-- trusted: losing a spawner in a crash is acceptable, duplicating one is not.
CREATE TABLE IF NOT EXISTS stacking_spawners (
  world VARCHAR(64) NOT NULL,
  x INT NOT NULL,
  y INT NOT NULL,
  z INT NOT NULL,
  entity_type VARCHAR(64) NOT NULL,
  amount INT NOT NULL,
  island_id BINARY(16) NULL,
  placed_by BINARY(16) NULL,
  placed_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (world, x, y, z)
);
CREATE INDEX IF NOT EXISTS idx_stacking_spawners_island ON stacking_spawners (island_id);

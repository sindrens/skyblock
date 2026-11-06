-- Chunk Collectors (owner 4 Nov 2026: "Coming soon" built for real). One row per placed collector (one per chunk).
CREATE TABLE IF NOT EXISTS stacking_chunk_collectors (
  world VARCHAR(64) NOT NULL,
  x INT NOT NULL,
  y INT NOT NULL,
  z INT NOT NULL,
  island_id BINARY(16) NOT NULL,
  owner_player BINARY(16) NOT NULL,
  placed_at BIGINT NOT NULL,
  PRIMARY KEY (world, x, y, z)
);

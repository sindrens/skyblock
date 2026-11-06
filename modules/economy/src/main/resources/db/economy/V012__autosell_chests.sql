-- Auto-Sell Chests (owner 4 Nov 2026: "Coming soon" items built for real). One row per placed chest.
CREATE TABLE IF NOT EXISTS economy_autosell_chests (
  world VARCHAR(64) NOT NULL,
  x INT NOT NULL,
  y INT NOT NULL,
  z INT NOT NULL,
  island_id BINARY(16) NOT NULL,
  owner_profile BINARY(16) NOT NULL,
  owner_player BINARY(16) NOT NULL,
  placed_at BIGINT NOT NULL,
  PRIMARY KEY (world, x, y, z)
);

CREATE INDEX IF NOT EXISTS idx_economy_autosell_island ON economy_autosell_chests (island_id);

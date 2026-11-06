-- Island Guide "Kindle" (docs/planning/ISLAND_GUIDE.md): where the leader/admins moved the guide (no row = next to the
-- island home). Removed with the island.
CREATE TABLE IF NOT EXISTS guide_spots (
  island_id BINARY(16) NOT NULL PRIMARY KEY,
  world VARCHAR(64) NOT NULL,
  x DOUBLE NOT NULL,
  y DOUBLE NOT NULL,
  z DOUBLE NOT NULL,
  yaw FLOAT NOT NULL,
  moved_by BINARY(16) NULL,
  moved_at BIGINT NOT NULL
);

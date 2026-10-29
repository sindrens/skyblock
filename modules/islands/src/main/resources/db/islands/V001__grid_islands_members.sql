-- Islands module M2.1: the slot grid, islands (with season history) and members (owner only until M2.3).

-- One row per 512x512 slot ever handed out. id = spiral index from grid 0,0. state: used | clearing | free.
-- A free slot (after a disband/reset in M2.7) is reused before a new one is added.
CREATE TABLE IF NOT EXISTS island_slots (
  id INT NOT NULL,
  grid_x INT NOT NULL,
  grid_z INT NOT NULL,
  state VARCHAR(16) NOT NULL,
  island_id BINARY(16) NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_island_slots_grid ON island_slots (grid_x, grid_z);
CREATE INDEX IF NOT EXISTS idx_island_slots_state ON island_slots (state, id);

-- Islands. Rows are never deleted: a disbanded island gets deleted_at and keeps its season_id for history.
-- type: solo | team (solo turns into team forever on the first invite, DESIGN 3.2). size_chunks: border width (odd).
-- home_*: NULL until the starter island was built. settings: JSON for M2.5.
CREATE TABLE IF NOT EXISTS islands (
  id BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  owner_uuid BINARY(16) NOT NULL,
  type VARCHAR(8) NOT NULL,
  slot_id INT NOT NULL,
  size_chunks INT NOT NULL,
  home_x DOUBLE NULL,
  home_y DOUBLE NULL,
  home_z DOUBLE NULL,
  home_yaw FLOAT NULL,
  home_pitch FLOAT NULL,
  name VARCHAR(48) NULL,
  settings TEXT NULL,
  created_at BIGINT NOT NULL,
  deleted_at BIGINT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_islands_owner ON islands (owner_uuid, deleted_at);
CREATE INDEX IF NOT EXISTS idx_islands_slot ON islands (slot_id);
CREATE INDEX IF NOT EXISTS idx_islands_season ON islands (season_id);

-- Members of active islands. role: leader | mod | member (coop is temporary and never stored).
-- The unique index on uuid is the database guarantee for "one island per player".
CREATE TABLE IF NOT EXISTS island_members (
  island_id BINARY(16) NOT NULL,
  uuid BINARY(16) NOT NULL,
  role VARCHAR(16) NOT NULL,
  joined_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, uuid)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_island_members_uuid ON island_members (uuid);

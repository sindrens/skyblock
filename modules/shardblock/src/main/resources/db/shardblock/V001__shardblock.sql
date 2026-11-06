-- ECON-SHARDS: placed shard blocks. The database is the truth; a block without a row is a plain block and pays nothing.
-- placed_by holds the placer's PROFILE key (staff removal returns the block to that profile's /box).
-- A row in state 'regrowing' is ready again once ready_at has passed (no write needed when it regrows).
CREATE TABLE IF NOT EXISTS shard_blocks (
  block_id BIGINT NOT NULL AUTO_INCREMENT,
  world VARCHAR(64) NOT NULL,
  x INT NOT NULL,
  y INT NOT NULL,
  z INT NOT NULL,
  island_id BINARY(16) NOT NULL,
  placed_by BINARY(16) NOT NULL,
  respawn_level INT NOT NULL DEFAULT 0,
  double_level INT NOT NULL DEFAULT 0,
  state VARCHAR(12) NOT NULL,
  ready_at BIGINT NOT NULL DEFAULT 0,
  breaks BIGINT NOT NULL DEFAULT 0,
  shards_paid BIGINT NOT NULL DEFAULT 0,
  placed_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (block_id),
  CONSTRAINT uq_shard_blocks_pos UNIQUE (world, x, y, z)
);
CREATE INDEX IF NOT EXISTS idx_shard_blocks_island ON shard_blocks (island_id);
CREATE INDEX IF NOT EXISTS idx_shard_blocks_ready ON shard_blocks (state, ready_at);

-- Removed blocks (pick-up, island removed, staff), kept for staff restore; never deleted by the plugin.
CREATE TABLE IF NOT EXISTS shard_blocks_archive (
  archive_id BIGINT NOT NULL AUTO_INCREMENT,
  block_id BIGINT NOT NULL,
  world VARCHAR(64) NOT NULL,
  x INT NOT NULL,
  y INT NOT NULL,
  z INT NOT NULL,
  island_id BINARY(16) NOT NULL,
  placed_by BINARY(16) NOT NULL,
  respawn_level INT NOT NULL,
  double_level INT NOT NULL,
  breaks BIGINT NOT NULL,
  shards_paid BIGINT NOT NULL,
  reason VARCHAR(24) NOT NULL,
  archived_by VARCHAR(36) NULL,
  archived_at BIGINT NOT NULL,
  PRIMARY KEY (archive_id)
);
CREATE INDEX IF NOT EXISTS idx_shard_blocks_archive_island ON shard_blocks_archive (island_id);

-- Shards paid per profile and Oslo day (the daily cap, checked inside the payout transaction).
CREATE TABLE IF NOT EXISTS shard_block_daily (
  profile_key BINARY(16) NOT NULL,
  day INT NOT NULL,
  shards BIGINT NOT NULL,
  PRIMARY KEY (profile_key, day)
);

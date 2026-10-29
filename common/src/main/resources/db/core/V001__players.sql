-- Core player data (Phase 0 M0.4). Global across seasons, so no season_id here.
-- Names: Java max 16 chars, Bedrock names get Floodgate's prefix, so 32 is safe.
CREATE TABLE IF NOT EXISTS players (
  uuid BINARY(16) NOT NULL PRIMARY KEY,
  name VARCHAR(32) NOT NULL,
  name_lower VARCHAR(32) NOT NULL,
  first_seen BIGINT NOT NULL,
  last_seen BIGINT NOT NULL,
  playtime_ms BIGINT NOT NULL DEFAULT 0,
  bedrock BOOLEAN NOT NULL DEFAULT FALSE,
  discord_id BIGINT NULL,
  settings TEXT NULL
);

CREATE INDEX IF NOT EXISTS idx_players_name_lower ON players (name_lower);

CREATE TABLE IF NOT EXISTS name_history (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  uuid BINARY(16) NOT NULL,
  name VARCHAR(32) NOT NULL,
  name_lower VARCHAR(32) NOT NULL,
  first_used BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_name_history_uuid ON name_history (uuid);

CREATE INDEX IF NOT EXISTS idx_name_history_name ON name_history (name_lower);

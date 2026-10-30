-- Custom tools (tools module, M4.2). The item carries the live data; this table mirrors it (staff lookups, history,
-- atomic Forge/enchant operations). perks/stats/owner_history are JSON. blueprint_id is the unique id of the blueprint
-- item a special tool was crafted from: unique, so a duplicated blueprint can never craft twice.
CREATE TABLE IF NOT EXISTS tools (
  tool_id BINARY(16) NOT NULL PRIMARY KEY,
  kind VARCHAR(16) NOT NULL,
  tier INT NOT NULL,
  level INT NOT NULL DEFAULT 1,
  xp DOUBLE NOT NULL DEFAULT 0,
  prestige INT NOT NULL DEFAULT 0,
  prestige_season INT NOT NULL DEFAULT 0,
  perks TEXT NOT NULL,
  skin VARCHAR(48) NULL,
  ability VARCHAR(48) NULL,
  starter BOOLEAN NOT NULL DEFAULT FALSE,
  stats TEXT NOT NULL,
  owner BINARY(16) NULL,
  owner_history TEXT NOT NULL,
  blueprint_id BINARY(16) NULL,
  source VARCHAR(64) NOT NULL,
  season_id INT NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_tools_blueprint ON tools (blueprint_id);
CREATE INDEX IF NOT EXISTS idx_tools_owner ON tools (owner);

-- Skin collection per player (kept across seasons, so no season_id).
CREATE TABLE IF NOT EXISTS skins_owned (
  player BINARY(16) NOT NULL,
  skin VARCHAR(48) NOT NULL,
  source VARCHAR(64) NOT NULL,
  obtained_at BIGINT NOT NULL,
  PRIMARY KEY (player, skin)
);

-- Blueprints a player has found, per season (collection feel and staff lookups).
CREATE TABLE IF NOT EXISTS blueprints_found (
  player BINARY(16) NOT NULL,
  blueprint VARCHAR(48) NOT NULL,
  season_id INT NOT NULL,
  found INT NOT NULL DEFAULT 0,
  first_at BIGINT NOT NULL,
  last_at BIGINT NOT NULL,
  PRIMARY KEY (player, blueprint, season_id)
);

-- Last starter kit claim per player (24 h cooldown, owner decision).
CREATE TABLE IF NOT EXISTS starter_claims (
  player BINARY(16) NOT NULL PRIMARY KEY,
  last_claim BIGINT NOT NULL,
  claims INT NOT NULL DEFAULT 0
);

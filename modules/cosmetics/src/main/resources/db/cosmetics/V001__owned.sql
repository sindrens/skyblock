-- Titles and tags a player owns (cosmetics module). Kept across seasons unless the cosmetic is marked otherwise.
CREATE TABLE IF NOT EXISTS cosmetics_owned (
  uuid BINARY(16) NOT NULL,
  kind VARCHAR(8) NOT NULL,
  cosmetic_id VARCHAR(64) NOT NULL,
  source VARCHAR(128) NULL,
  granted_by VARCHAR(64) NULL,
  granted_at BIGINT NOT NULL,
  PRIMARY KEY (uuid, kind, cosmetic_id)
);

CREATE INDEX IF NOT EXISTS idx_cosmetics_owned_id ON cosmetics_owned (kind, cosmetic_id);

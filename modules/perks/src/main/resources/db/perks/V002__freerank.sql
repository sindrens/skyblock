-- /freerank claims (perks module). Account-wide and kept forever (no season_id): the rank and tag are permanent.
-- One row per player; the primary key makes a second claim impossible. tag = the chosen tag (NULL until picked).
CREATE TABLE IF NOT EXISTS perks_freerank (
  uuid BINARY(16) NOT NULL PRIMARY KEY,
  name VARCHAR(16) NOT NULL,
  rank_group VARCHAR(64) NOT NULL,
  claimed_at BIGINT NOT NULL,
  tag VARCHAR(64) NULL,
  tag_at BIGINT NULL
);

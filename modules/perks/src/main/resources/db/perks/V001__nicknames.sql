-- Nicknames (perks module). Global across seasons (cosmetic), so no season_id.
-- nick = the formatted value (safe MiniMessage subset), nick_plain/nick_lower = the visible letters (unique).
CREATE TABLE IF NOT EXISTS perks_nicknames (
  uuid BINARY(16) NOT NULL PRIMARY KEY,
  nick VARCHAR(512) NOT NULL,
  nick_plain VARCHAR(32) NOT NULL,
  nick_lower VARCHAR(32) NOT NULL,
  set_by VARCHAR(36) NULL,
  updated_at BIGINT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_perks_nick_lower ON perks_nicknames (nick_lower);

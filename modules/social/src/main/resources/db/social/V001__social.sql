-- Social module (ignore lists, IP history for /whois alts). Global across seasons, so no season_id.
CREATE TABLE IF NOT EXISTS social_ignores (
  owner BINARY(16) NOT NULL,
  ignored BINARY(16) NOT NULL,
  created BIGINT NOT NULL,
  PRIMARY KEY (owner, ignored)
);

-- Addresses a player joined from (staff-only /whois + alt detection). One row per player + address.
CREATE TABLE IF NOT EXISTS social_player_ips (
  uuid BINARY(16) NOT NULL,
  ip VARCHAR(45) NOT NULL,
  first_seen BIGINT NOT NULL,
  last_seen BIGINT NOT NULL,
  PRIMARY KEY (uuid, ip)
);

CREATE INDEX IF NOT EXISTS idx_social_player_ips_ip ON social_player_ips (ip);

-- Punishments (Phase 1 M1.8). Season-independent and kept forever (owner decision).
-- type: BAN, MUTE, WARN, KICK. expires NULL = permanent (KICK rows are history only).
-- removed_* is set when a punishment is pardoned (unban/unmute/unwarn) and always needs a reason.
CREATE TABLE IF NOT EXISTS punishments (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  type VARCHAR(8) NOT NULL,
  target_uuid BINARY(16) NULL,
  target_name VARCHAR(32) NOT NULL,
  ip VARCHAR(45) NULL,
  ip_wide BOOLEAN NOT NULL DEFAULT FALSE,
  reason VARCHAR(255) NOT NULL,
  template VARCHAR(48) NULL,
  ladder_step INT NULL,
  issuer_uuid BINARY(16) NULL,
  issuer_name VARCHAR(32) NOT NULL,
  created BIGINT NOT NULL,
  expires BIGINT NULL,
  silent BOOLEAN NOT NULL DEFAULT FALSE,
  removed_at BIGINT NULL,
  removed_by_uuid BINARY(16) NULL,
  removed_by_name VARCHAR(32) NULL,
  removed_reason VARCHAR(255) NULL
);

CREATE INDEX IF NOT EXISTS idx_punishments_target ON punishments (target_uuid, type);

CREATE INDEX IF NOT EXISTS idx_punishments_ip ON punishments (ip, type);

CREATE INDEX IF NOT EXISTS idx_punishments_created ON punishments (created);

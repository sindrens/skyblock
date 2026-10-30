-- Phase P (P4): approval (13.4), watchlist (13.5), "mute ended" notice (13.13). Probation (13.7) is derived, no column.
-- Never edit V001-V004. Re-runnable: every ALTER uses IF NOT EXISTS.

-- 13.4: the approver of a requested punishment (issuer_* stays the staff member who asked). NULL = no approval needed.
ALTER TABLE punishments ADD COLUMN IF NOT EXISTS approved_by_uuid BINARY(16) NULL;

ALTER TABLE punishments ADD COLUMN IF NOT EXISTS approved_by_name VARCHAR(32) NULL;

-- 13.13: the "Your mute has ended" / "Welcome back" line was sent (online at the end, or once on the next join).
ALTER TABLE punishments ADD COLUMN IF NOT EXISTS end_notice_sent BOOLEAN NOT NULL DEFAULT FALSE;

-- Everything that already ended before P4 counts as told, so nobody gets an old "mute ended" line after the update.
-- (* 1000.0: H2 returns UNIX_TIMESTAMP() as INT, which would overflow at * 1000.)
UPDATE punishments SET end_notice_sent = TRUE WHERE expires IS NOT NULL AND expires <= UNIX_TIMESTAMP() * 1000.0;

CREATE INDEX IF NOT EXISTS idx_punishments_end_notice ON punishments (target_uuid, end_notice_sent);

-- 13.4: requests for big punishments. Nothing is applied until approved. status: PENDING, APPROVED, DENIED, SKIPPED
-- (approved but the player was already punished for longer). A PENDING row past 'expires' counts as expired.
-- reason_key = the reason id (or type + reason text): a second request for the same player and reason is merged.
CREATE TABLE IF NOT EXISTS punishment_requests (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  target_uuid BINARY(16) NULL,
  target_name VARCHAR(32) NOT NULL,
  reason_key VARCHAR(48) NOT NULL,
  type VARCHAR(8) NOT NULL,
  duration_ms BIGINT NULL,
  ip_wide BOOLEAN NOT NULL DEFAULT FALSE,
  ip VARCHAR(45) NULL,
  reason VARCHAR(255) NOT NULL,
  template VARCHAR(48) NULL,
  ladder_step INT NULL,
  weight_tenths INT NOT NULL DEFAULT 10,
  silent BOOLEAN NOT NULL DEFAULT FALSE,
  source VARCHAR(48) NULL,
  requester_uuid BINARY(16) NULL,
  requester_name VARCHAR(32) NOT NULL,
  created BIGINT NOT NULL,
  expires BIGINT NOT NULL,
  status VARCHAR(12) NOT NULL,
  handled_at BIGINT NULL,
  handler_uuid BINARY(16) NULL,
  handler_name VARCHAR(32) NULL,
  deny_reason VARCHAR(255) NULL,
  punishment_id BIGINT NULL,
  merges INT NOT NULL DEFAULT 0,
  notified BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_punishment_requests_target ON punishment_requests (target_uuid, status);

CREATE INDEX IF NOT EXISTS idx_punishment_requests_status ON punishment_requests (status, expires);

CREATE INDEX IF NOT EXISTS idx_punishment_requests_requester ON punishment_requests (requester_uuid, created);

-- 13.5: watched players. Staff with skyblock.staff.notify get one line when they join; the player is told nothing.
-- Removing keeps the row (removed_* columns). expires NULL = until removed.
CREATE TABLE IF NOT EXISTS watchlist (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  target_uuid BINARY(16) NOT NULL,
  target_name VARCHAR(32) NOT NULL,
  author_uuid BINARY(16) NULL,
  author_name VARCHAR(32) NOT NULL,
  reason VARCHAR(255) NULL,
  created BIGINT NOT NULL,
  expires BIGINT NULL,
  removed BOOLEAN NOT NULL DEFAULT FALSE,
  removed_at BIGINT NULL,
  removed_by_uuid BINARY(16) NULL,
  removed_by_name VARCHAR(32) NULL
);

CREATE INDEX IF NOT EXISTS idx_watchlist_target ON watchlist (target_uuid, removed);

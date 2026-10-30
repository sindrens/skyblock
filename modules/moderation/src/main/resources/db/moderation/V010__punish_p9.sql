-- Phase P (P9): rogue/hacked staff guard + rollback (14.1), false report penalty (14.5, report-block step: no column,
-- type REPORT_BLOCK fits VARCHAR(16)), data deletion (14.10).
-- Never edit V001-V009. Re-runnable: IF NOT EXISTS everywhere.

-- 14.1: guard state per staff member (account-wide). guard_state: BLOCKED (went over a limit in block mode, waits for
-- /pguard allow), LOCKED (/pguard lock, until /pguard unlock), ALLOWED (/pguard allow: limits only alert until
-- allowed_until). No row = normal. hit_text: what caused a block ("bans 6/5").
CREATE TABLE IF NOT EXISTS punish_guard (
  staff_uuid BINARY(16) NOT NULL PRIMARY KEY,
  staff_name VARCHAR(32) NOT NULL,
  guard_state VARCHAR(8) NOT NULL,
  allowed_until BIGINT NULL,
  hit_text VARCHAR(32) NULL,
  set_by_name VARCHAR(32) NOT NULL,
  updated BIGINT NOT NULL
);

-- 14.1: one row per /prollback run (the "audit entry group"). since_ms/until_ms = the period that was rolled back.
CREATE TABLE IF NOT EXISTS punish_rollbacks (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  staff_uuid BINARY(16) NOT NULL,
  staff_name VARCHAR(32) NOT NULL,
  since_ms BIGINT NOT NULL,
  until_ms BIGINT NOT NULL,
  run_by_uuid BINARY(16) NULL,
  run_by_name VARCHAR(32) NOT NULL,
  created BIGINT NOT NULL,
  punishment_count INT NOT NULL DEFAULT 0,
  sanction_count INT NOT NULL DEFAULT 0
);

-- 14.1: punishments/sanctions reversed by a rollback point to it (shown as "reversed (rollback)" in /pstats).
ALTER TABLE punishments ADD COLUMN IF NOT EXISTS rollback_id BIGINT NULL;

ALTER TABLE sanctions ADD COLUMN IF NOT EXISTS rollback_id BIGINT NULL;

-- 14.10: rows kept as the minimal ledger after /ppurge (name replaced by the account id, reason text by the reason id,
-- no IP unless an IP-wide ban is still in force). Deleted automatically after extras.purge.keep-ledger.
ALTER TABLE punishments ADD COLUMN IF NOT EXISTS purged_at BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_punishments_purged ON punishments (purged_at);

-- 14.5: the weekly cap of false-report suggestions looks up suggestions per target + reason id.
CREATE INDEX IF NOT EXISTS idx_punishment_suggestions_reason ON punishment_suggestions (target_uuid, reason_id, created);

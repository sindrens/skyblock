-- Phase P (P1): /p menu + warn rules. Never edit V001.
-- source: NULL = manual; 'auto:warn-rule:<at>' / 'auto:warn-rule:repeat' = created by an automatic warn rule
-- (applied by a staff click on [Apply], or by the system in auto mode).
ALTER TABLE punishments ADD COLUMN source VARCHAR(48) NULL;

-- Suggestions created by the warn rules (later also by the chat filter / anticheat, spec 12.8 and 13.3).
-- status: PENDING (waiting for staff), APPLIED ([Apply] clicked), DISMISSED ([Dismiss] clicked), AUTO (applied by the
-- system in auto mode), SKIPPED (nothing applied: exempt or already muted/banned for longer). A PENDING row past
-- 'expires' counts as expired. The warn counter is never stored: it is derived from punishments + these rows.
CREATE TABLE IF NOT EXISTS punishment_suggestions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  target_uuid BINARY(16) NOT NULL,
  target_name VARCHAR(32) NOT NULL,
  kind VARCHAR(16) NOT NULL,
  rule_at INT NULL,
  repeat_trigger BOOLEAN NOT NULL DEFAULT FALSE,
  action VARCHAR(32) NOT NULL,
  reason VARCHAR(255) NOT NULL,
  source VARCHAR(48) NOT NULL,
  warn_count INT NOT NULL DEFAULT 0,
  created BIGINT NOT NULL,
  expires BIGINT NULL,
  status VARCHAR(12) NOT NULL,
  handled_at BIGINT NULL,
  handled_by_uuid BINARY(16) NULL,
  handled_by_name VARCHAR(32) NULL,
  punishment_id BIGINT NULL,
  note VARCHAR(32) NULL
);

CREATE INDEX IF NOT EXISTS idx_punishment_suggestions_target ON punishment_suggestions (target_uuid, created);

CREATE INDEX IF NOT EXISTS idx_punishment_suggestions_status ON punishment_suggestions (status, expires);

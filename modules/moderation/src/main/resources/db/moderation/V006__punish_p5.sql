-- Phase P (P5): bulk punish (13.8), punish from logs (13.11), appeals (13.10).
-- Never edit V001-V005. Re-runnable: every ALTER uses IF NOT EXISTS.
-- Bulk punishments need no column: each row carries source 'bulk:<group>' (one group per bulk action).

-- 13.11: a request made from the punish menu opened on a /logs chat line keeps that line, so the evidence is the same
-- line when an approver applies it later. NULL = normal evidence (the player's last chat lines).
ALTER TABLE punishment_requests ADD COLUMN IF NOT EXISTS evidence_entry BIGINT NULL;

-- 13.10: appeals. One OPEN appeal per punishment. status: OPEN, ACCEPTED (punishment lifted), REDUCED (shorter end),
-- DENIED (response = the reason), CLOSED (the punishment ended or was lifted before anyone handled the appeal).
-- issuer_* = who gave the punishment (accepted appeals count as reversed for them in /pstats); handler_* = who handled it.
-- old_expires / new_expires: the end before and after a reduce (NULL = permanent). player_notified: the player was told
-- the decision (now, or on the next join).
CREATE TABLE IF NOT EXISTS appeals (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  punishment_id BIGINT NOT NULL,
  punishment_type VARCHAR(8) NOT NULL,
  player_uuid BINARY(16) NOT NULL,
  player_name VARCHAR(32) NOT NULL,
  issuer_uuid BINARY(16) NULL,
  issuer_name VARCHAR(32) NULL,
  appeal_text VARCHAR(600) NOT NULL,
  status VARCHAR(10) NOT NULL,
  handler_uuid BINARY(16) NULL,
  handler_name VARCHAR(32) NULL,
  response VARCHAR(255) NULL,
  old_expires BIGINT NULL,
  new_expires BIGINT NULL,
  created BIGINT NOT NULL,
  handled BIGINT NULL,
  player_notified BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_appeals_punishment ON appeals (punishment_id, status);

CREATE INDEX IF NOT EXISTS idx_appeals_status ON appeals (status, created);

CREATE INDEX IF NOT EXISTS idx_appeals_player ON appeals (player_uuid, player_notified);

CREATE INDEX IF NOT EXISTS idx_appeals_handler ON appeals (handler_uuid, handled);

CREATE INDEX IF NOT EXISTS idx_appeals_issuer ON appeals (issuer_uuid, handled);

-- Phase P (P3): evidence, staff notes, warning acknowledgement, warn weights, suggestion API. Never edit V001-V003.
-- Re-runnable: a first try on MariaDB stopped at the evidence table (`lines` is a reserved word), so every ALTER uses IF NOT EXISTS.

-- 12.7 warn weight: NULL = 1 (rows from before P3). One decimal (0.5, 1, 2).
ALTER TABLE punishments ADD COLUMN IF NOT EXISTS weight DECIMAL(5,1) NULL;

-- 12.6 acknowledgement: when the player clicked [I understand] or typed /ack (NULL = not yet).
ALTER TABLE punishments ADD COLUMN IF NOT EXISTS acknowledged_at BIGINT NULL;

-- 12.6: the one reminder on the next join was sent.
ALTER TABLE punishments ADD COLUMN IF NOT EXISTS ack_join_sent BOOLEAN NOT NULL DEFAULT FALSE;

-- 12.7: the weighted warn count when the rule fired, in tenths (15 = 1.5). NULL for rows from before P3 (use warn_count).
ALTER TABLE punishment_suggestions ADD COLUMN IF NOT EXISTS warn_tenths INT NULL;

-- 12.8: suggestions from detectors (kind 'api'): the reason id of punishments.yml and what was detected.
ALTER TABLE punishment_suggestions ADD COLUMN IF NOT EXISTS reason_id VARCHAR(48) NULL;

ALTER TABLE punishment_suggestions ADD COLUMN IF NOT EXISTS details VARCHAR(255) NULL;

CREATE INDEX IF NOT EXISTS idx_punishment_suggestions_reason ON punishment_suggestions (target_uuid, kind, reason_id, status);

-- 12.4 evidence: chat lines saved with a mute/warn. Deleted after extras.evidence.retention (180d). Never IP addresses.
CREATE TABLE IF NOT EXISTS punishment_evidence (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  punishment_id BIGINT NOT NULL,
  created BIGINT NOT NULL,
  line_count INT NOT NULL DEFAULT 0,
  chat_lines TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_punishment_evidence_punishment ON punishment_evidence (punishment_id);

CREATE INDEX IF NOT EXISTS idx_punishment_evidence_created ON punishment_evidence (created);

-- 12.5 staff notes: private, staff only. Removing keeps the row (removed_at / removed_by).
CREATE TABLE IF NOT EXISTS staff_notes (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  target_uuid BINARY(16) NOT NULL,
  target_name VARCHAR(32) NOT NULL,
  author_uuid BINARY(16) NULL,
  author_name VARCHAR(32) NOT NULL,
  note VARCHAR(255) NOT NULL,
  created BIGINT NOT NULL,
  removed BOOLEAN NOT NULL DEFAULT FALSE,
  removed_at BIGINT NULL,
  removed_by_uuid BINARY(16) NULL,
  removed_by_name VARCHAR(32) NULL
);

CREATE INDEX IF NOT EXISTS idx_staff_notes_target ON staff_notes (target_uuid, removed, created);

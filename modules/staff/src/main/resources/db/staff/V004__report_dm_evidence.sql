-- STAFF-R3 (R3-9a): /report attaches the last private messages between the reporter and the reported player (copied from
-- the audit log, category dm, which keeps DMs only 30 days). Only staff who may read DMs see them. Rows are deleted
-- reports.dm-evidence.keep-days (default 180) after stored_at by the staff module's purge job.
CREATE TABLE IF NOT EXISTS report_dm_evidence (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  report_id BIGINT NOT NULL,
  stored_at BIGINT NOT NULL,
  sent_at BIGINT NOT NULL,
  sender_uuid BINARY(16),
  sender_name VARCHAR(32) NOT NULL,
  recipient_name VARCHAR(32) NOT NULL,
  body VARCHAR(1024) NOT NULL,
  was_ignored BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_report_dm_report ON report_dm_evidence (report_id);

CREATE INDEX IF NOT EXISTS idx_report_dm_stored ON report_dm_evidence (stored_at);

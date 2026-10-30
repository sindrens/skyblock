-- Phase P (P8): rule numbers (14.2, config only, no column), notification settings (14.9), training mode (14.6).
-- Never edit V001-V008. Re-runnable: IF NOT EXISTS everywhere.

-- 14.6: the request was made by a trainee (skyblock.staff.punish.trainee). Shown as "Alex (trainee) requests ...".
ALTER TABLE punishment_requests ADD COLUMN IF NOT EXISTS requester_trainee BOOLEAN NOT NULL DEFAULT FALSE;

-- 14.9: per staff member (account-wide) which alert types they switched on or off with /pnotify.
-- No row = on (the default). Types in extras.notify.always (guard) are on whatever is stored here.
CREATE TABLE IF NOT EXISTS staff_notify_settings (
  staff_uuid BINARY(16) NOT NULL,
  notify_type VARCHAR(16) NOT NULL,
  notify_on BOOLEAN NOT NULL,
  updated BIGINT NOT NULL,
  PRIMARY KEY (staff_uuid, notify_type)
);

-- 14.6: dry runs (/p <player> <reason> -dry and the Dry run toggle of /p). A log of what WOULD have happened: nothing
-- here is ever applied, and no other table changes. verdict: APPLY, REQUEST, BLOCKED, SKIPPED. action_text: "mute 1d".
CREATE TABLE IF NOT EXISTS punish_dry_runs (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  staff_uuid BINARY(16) NULL,
  staff_name VARCHAR(32) NOT NULL,
  trainee BOOLEAN NOT NULL DEFAULT FALSE,
  target_uuid BINARY(16) NULL,
  target_name VARCHAR(32) NOT NULL,
  reason_key VARCHAR(48) NULL,
  verdict VARCHAR(12) NOT NULL,
  action_text VARCHAR(64) NULL,
  created BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_punish_dry_runs_staff ON punish_dry_runs (staff_uuid, created);

-- Phase P (P10): monthly report (14.8) and the "Honorable" title (14.11, clean streak).
-- Never edit V001-V010. Re-runnable: IF NOT EXISTS everywhere; the backfills only touch rows that are still NULL.

-- 14.8: how a punishment was reversed, so the report can split "reversed %" into undo / pardon / appeal / rollback.
-- UNDO ([Undo] button), PARDON (/unban, /unmute, /unwarn), APPEAL (accepted appeal), ROLLBACK (/prollback).
-- NULL while not reversed. Rows reversed before P10: rollback and appeal are known (below); the rest count as PARDON.
ALTER TABLE punishments ADD COLUMN IF NOT EXISTS removed_kind VARCHAR(10) NULL;

UPDATE punishments SET removed_kind = 'ROLLBACK' WHERE removed_kind IS NULL AND removed_at IS NOT NULL AND rollback_id IS NOT NULL;

UPDATE punishments SET removed_kind = 'APPEAL' WHERE removed_kind IS NULL AND removed_at IS NOT NULL
  AND id IN (SELECT punishment_id FROM appeals WHERE status = 'ACCEPTED');

-- 14.8: the stored monthly reports. report_month = 'YYYY-MM' in extras.stats.timezone (Europe/Oslo). report_json =
-- aggregated numbers only (no player names, no IPs, no evidence, no notes; staff names only in the staff section).
-- notified = the "report is ready" line and the Discord embed were sent (once, by the scheduled run).
CREATE TABLE IF NOT EXISTS punish_reports (
  report_month VARCHAR(7) NOT NULL PRIMARY KEY,
  generated_at BIGINT NOT NULL,
  generated_by VARCHAR(32) NOT NULL,
  report_json MEDIUMTEXT NOT NULL,
  notified BOOLEAN NOT NULL DEFAULT FALSE
);

-- 14.8: the report reads one month at a time; these keep every query on an index.
CREATE INDEX IF NOT EXISTS idx_appeals_created ON appeals (created);

CREATE INDEX IF NOT EXISTS idx_appeals_handled ON appeals (handled);

CREATE INDEX IF NOT EXISTS idx_punishment_requests_created ON punishment_requests (created);

CREATE INDEX IF NOT EXISTS idx_punish_dry_runs_created ON punish_dry_runs (created);

CREATE INDEX IF NOT EXISTS idx_sanctions_created ON sanctions (created);

-- 14.11: clean streak per player (account-wide). streak_state: EARNED (owns the title), LOST (a new warn, mute, ban or
-- kick hid it; earned again after another clean period). Never deleted: times_earned keeps the history.
CREATE TABLE IF NOT EXISTS punish_clean_streak (
  player_uuid BINARY(16) NOT NULL PRIMARY KEY,
  streak_state VARCHAR(8) NOT NULL,
  earned_at BIGINT NULL,
  lost_at BIGINT NULL,
  times_earned INT NOT NULL DEFAULT 0,
  updated BIGINT NOT NULL
);

-- 14.11: the player's total playtime (players.playtime_ms) at the last check of a day (play_day = epoch day in
-- extras.stats.timezone). "Played in the last 60 days" = now - the sample at the start of that period. Older samples
-- than that start are deleted.
CREATE TABLE IF NOT EXISTS punish_playtime_days (
  player_uuid BINARY(16) NOT NULL,
  play_day INT NOT NULL,
  played_total_ms BIGINT NOT NULL,
  PRIMARY KEY (player_uuid, play_day)
);

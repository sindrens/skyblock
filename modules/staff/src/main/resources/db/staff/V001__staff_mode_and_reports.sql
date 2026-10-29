-- Staff mode: the staff member's own inventory while they are in staff mode (crash-safe restore on next join).
CREATE TABLE IF NOT EXISTS staff_mode (
  uuid BINARY(16) NOT NULL PRIMARY KEY,
  contents MEDIUMBLOB NOT NULL,
  game_mode VARCHAR(16) NOT NULL,
  allow_flight BOOLEAN NOT NULL,
  flying BOOLEAN NOT NULL,
  was_vanished BOOLEAN NOT NULL,
  saved_at BIGINT NOT NULL
);

-- Player reports (/report, /reports). Not season-bound: kept across seasons for staff history.
CREATE TABLE IF NOT EXISTS reports (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  created_at BIGINT NOT NULL,
  reporter BINARY(16) NOT NULL,
  reporter_name VARCHAR(32) NOT NULL,
  target BINARY(16) NOT NULL,
  target_name VARCHAR(32) NOT NULL,
  reason VARCHAR(32) NOT NULL,
  details VARCHAR(255) NOT NULL DEFAULT '',
  world VARCHAR(64),
  x INT,
  y INT,
  z INT,
  status VARCHAR(16) NOT NULL DEFAULT 'open',
  claimed_by BINARY(16),
  claimed_by_name VARCHAR(32),
  claimed_at BIGINT,
  closed_by BINARY(16),
  closed_by_name VARCHAR(32),
  closed_at BIGINT,
  outcome VARCHAR(32),
  reporter_notified BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_reports_status ON reports (status, created_at);
CREATE INDEX idx_reports_target ON reports (target, created_at);
CREATE INDEX idx_reports_reporter ON reports (reporter, created_at);

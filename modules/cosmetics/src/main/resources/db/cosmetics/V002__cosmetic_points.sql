-- Cosmetic points (owner 7 Nov 2026, plans/STORE_FULL_PLAN.md 2): one balance per account, and a log whose primary key is
-- the change's unique key (a replay of the same key changes nothing). Times in epoch millis. MariaDB 11.8 and H2.
CREATE TABLE IF NOT EXISTS cosmetic_points (
  account_uuid BINARY(16) NOT NULL,
  points BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (account_uuid)
);
CREATE TABLE IF NOT EXISTS cosmetic_points_log (
  change_key VARCHAR(96) NOT NULL,
  account_uuid BINARY(16) NOT NULL,
  amount BIGINT NOT NULL,
  reason VARCHAR(32) NOT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (change_key)
);
CREATE INDEX IF NOT EXISTS idx_cosmetic_points_log_account ON cosmetic_points_log (account_uuid, created_at);

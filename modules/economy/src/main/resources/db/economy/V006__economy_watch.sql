-- Economy dashboard + anomaly alerts (M3.8). Summary tables filled by an incremental job that reads the ledger in id
-- order (never the whole ledger at once, never on the main thread). The job's progress (last processed ledger id) is
-- saved in the SAME transaction as the sums, so every ledger row is counted exactly once, also after a crash.

-- Progress of the background jobs (job 'ledger' = last ledger id that is in the sums below).
CREATE TABLE IF NOT EXISTS eco_watch_state (
  job VARCHAR(32) NOT NULL,
  last_id BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (job)
);

-- Money created / removed / moved per hour (bucket_hour = epoch millis / 3,600,000, UTC), currency, ledger reason.
-- flow: created (from nobody), removed (to nobody), moved (between two accounts).
CREATE TABLE IF NOT EXISTS eco_watch_flows (
  bucket_hour BIGINT NOT NULL,
  currency VARCHAR(16) NOT NULL,
  reason VARCHAR(32) NOT NULL,
  flow VARCHAR(8) NOT NULL,
  amount_total BIGINT NOT NULL,
  tx_count BIGINT NOT NULL,
  PRIMARY KEY (bucket_hour, currency, reason, flow)
);

-- Per profile (player account) and hour: what came in (earned) and went out (spent), without the reasons in
-- economy-watch.yml -> earnings.ignore-reasons (own bank moves, staff changes). Top earners and the earn-rate alert.
CREATE TABLE IF NOT EXISTS eco_watch_accounts (
  bucket_hour BIGINT NOT NULL,
  owner_id BINARY(16) NOT NULL,
  currency VARCHAR(16) NOT NULL,
  earned BIGINT NOT NULL,
  spent BIGINT NOT NULL,
  tx_count BIGINT NOT NULL,
  PRIMARY KEY (bucket_hour, owner_id, currency)
);

CREATE INDEX IF NOT EXISTS idx_eco_watch_accounts_cur ON eco_watch_accounts (currency, bucket_hour);

-- Money supply per hour: everything held in accounts (players, islands, banks) plus money notes not cashed yet.
CREATE TABLE IF NOT EXISTS eco_watch_supply (
  bucket_hour BIGINT NOT NULL,
  currency VARCHAR(16) NOT NULL,
  account_total BIGINT NOT NULL,
  note_total BIGINT NOT NULL,
  taken_at BIGINT NOT NULL,
  PRIMARY KEY (bucket_hour, currency)
);

-- Anomaly alerts. Never punish by themselves: staff open the evidence, review or dismiss, and may file a suggestion.
-- subject_id: the profile key (player account) or NULL for server-wide alerts, other_id: the other side (transfers).
-- bucket_hour: the hour the alert is about. ledger_ids: comma list of evidence rows. alert_state: open | reviewed | dismissed.
CREATE TABLE IF NOT EXISTS eco_watch_alerts (
  id BIGINT NOT NULL AUTO_INCREMENT,
  created_at BIGINT NOT NULL,
  bucket_hour BIGINT NOT NULL,
  rule_id VARCHAR(24) NOT NULL,
  severity VARCHAR(8) NOT NULL,
  currency VARCHAR(16) NOT NULL,
  subject_id BINARY(16) NULL,
  other_id BINARY(16) NULL,
  amount BIGINT NOT NULL,
  expected BIGINT NOT NULL,
  hit_count INT NOT NULL,
  ledger_ids VARCHAR(512) NOT NULL,
  alert_state VARCHAR(12) NOT NULL,
  reviewed_by VARCHAR(32) NULL,
  reviewed_at BIGINT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_eco_watch_alerts_state ON eco_watch_alerts (alert_state, id);
CREATE INDEX IF NOT EXISTS idx_eco_watch_alerts_subject ON eco_watch_alerts (subject_id, id);
-- Biggest transactions of a day: the ledger already has (created_at); this index lets the dashboard sort one day
-- by amount without touching older rows.
CREATE INDEX IF NOT EXISTS idx_eco_ledger_cur_time ON eco_ledger (currency, created_at, amount);

-- Voting (PHASE-10-REWARDS.md M10.5). Times in epoch milliseconds; dates as epoch days in the server zone
-- (Europe/Oslo), so a vote day starts at 00:00 Oslo time. Column names avoid MariaDB reserved words.

-- Every vote that arrived. vote_id = hash of service + name + the site's timestamp: the same packet twice (retry,
-- replay) is stored once and paid once. account_id is empty while the name is unknown (waits unknown-name-days).
-- vote_state: queued (waits for the player) -> paid | duplicate (same site within 24 h) | limited (address limit)
-- | expired. address_hash = SHA-256 of the voter address (never the address itself).
CREATE TABLE IF NOT EXISTS vote_log (
  vote_id VARCHAR(40) NOT NULL,
  site_id VARCHAR(32) NOT NULL,
  service_name VARCHAR(64) NOT NULL,
  username VARCHAR(36) NOT NULL,
  name_key VARCHAR(36) NOT NULL,
  account_id BINARY(16) NULL,
  address_hash VARCHAR(64) NOT NULL,
  protocol INT NOT NULL,
  site_time BIGINT NOT NULL,
  received_at BIGINT NOT NULL,
  vote_date INT NOT NULL,
  vote_state VARCHAR(12) NOT NULL,
  paid_profile BINARY(16) NULL,
  paid_at BIGINT NOT NULL,
  PRIMARY KEY (vote_id)
);

CREATE INDEX IF NOT EXISTS idx_vote_log_account ON vote_log (account_id, site_id, vote_state);
CREATE INDEX IF NOT EXISTS idx_vote_log_name ON vote_log (name_key, vote_state);
CREATE INDEX IF NOT EXISTS idx_vote_log_state ON vote_log (vote_state, received_at);
CREATE INDEX IF NOT EXISTS idx_vote_log_address ON vote_log (address_hash, vote_date);

-- The vote streak of an account: days in a row with at least one paid vote.
CREATE TABLE IF NOT EXISTS vote_streaks (
  account_id BINARY(16) NOT NULL,
  streak_days INT NOT NULL,
  last_vote_date INT NOT NULL,
  best_streak INT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (account_id)
);

-- The server-wide vote party counter (one row, party_key = 1). parties_total counts every party so far.
CREATE TABLE IF NOT EXISTS vote_party (
  party_key INT NOT NULL,
  vote_count INT NOT NULL,
  parties_total INT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (party_key)
);

-- One row per filled party: 'filled' when the counter fills (same transaction), 'paid' when the rewards went out.
-- A party still 'filled' at the next start was interrupted by a stop and is marked 'review' for staff.
CREATE TABLE IF NOT EXISTS vote_parties (
  party_no INT NOT NULL,
  filled_at BIGINT NOT NULL,
  party_state VARCHAR(12) NOT NULL,
  rewarded INT NOT NULL,
  PRIMARY KEY (party_no)
);

-- Who got the reward of a party: the primary key makes a second reward for the same account impossible.
CREATE TABLE IF NOT EXISTS vote_party_rewards (
  party_no INT NOT NULL,
  account_id BINARY(16) NOT NULL,
  profile_key BINARY(16) NOT NULL,
  paid_at BIGINT NOT NULL,
  PRIMARY KEY (party_no, account_id)
);

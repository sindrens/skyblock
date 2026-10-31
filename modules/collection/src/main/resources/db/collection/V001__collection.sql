-- Collection log (PHASE-10-REWARDS.md M10.10), per profile (owner 130). Times in epoch milliseconds.

-- One row per entry a profile found itself. The primary key makes a second "first find" impossible.
CREATE TABLE IF NOT EXISTS collection_entries (
  profile_key BINARY(16) NOT NULL,
  entry_id VARCHAR(64) NOT NULL,
  first_at BIGINT NOT NULL,
  found_source VARCHAR(24) NOT NULL,
  PRIMARY KEY (profile_key, entry_id)
);

-- Milestones a profile reached: '<page>:<percent>' (25/50/75/100; each is one island level point) and 'all' (the whole
-- log). Written in the same transaction as their reward rows, so a milestone is paid exactly once.
CREATE TABLE IF NOT EXISTS collection_milestones (
  profile_key BINARY(16) NOT NULL,
  milestone_id VARCHAR(32) NOT NULL,
  player_uuid BINARY(16) NOT NULL,
  reached_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key, milestone_id)
);

-- Hand-outs of milestone rewards (keys, titles, set tokens). Written 'pending' with the milestone; 'sending' right before
-- the hand-out and 'delivered' after it. A crash while 'sending' moves the row to 'review' at the next start (staff check
-- it), so a reward can be lost but never doubled. Titles are idempotent and are simply granted again.
CREATE TABLE IF NOT EXISTS collection_grants (
  grant_id BINARY(16) NOT NULL,
  profile_key BINARY(16) NOT NULL,
  player_uuid BINARY(16) NOT NULL,
  milestone_id VARCHAR(32) NOT NULL,
  grant_kind VARCHAR(16) NOT NULL,
  grant_ref VARCHAR(64) NOT NULL,
  amount BIGINT NOT NULL,
  grant_state VARCHAR(12) NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (grant_id)
);

CREATE INDEX IF NOT EXISTS idx_collection_grants_state ON collection_grants (grant_state, created_at);

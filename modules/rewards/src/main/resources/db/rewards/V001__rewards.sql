-- Rewards (PHASE-10-REWARDS.md M10.6): daily calendar + playtime, per profile (owner 128, R6). Times in epoch
-- milliseconds; dates as epoch days in the server zone (Europe/Oslo), so a day starts at 00:00 Oslo time.

-- The daily calendar of a profile. calendar_day = the last day claimed (0 = none yet, 1..30); it only moves on a claim,
-- so missing a day never sends you back. streak_days = days in a row with a claim. When a streak breaks, the lost
-- streak and the day it broke are kept for the streak save (100 store credits within 2 days, R5; store in Phase 11).
CREATE TABLE IF NOT EXISTS daily_calendar (
  profile_key BINARY(16) NOT NULL,
  calendar_day INT NOT NULL,
  last_claim_date INT NOT NULL,
  streak_days INT NOT NULL,
  best_streak INT NOT NULL,
  lost_streak INT NOT NULL,
  streak_broken_on INT NOT NULL,
  total_claims INT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key)
);

-- One row per claim: the primary key makes a second claim of the same profile on the same day impossible, even when
-- the calendar row check was somehow passed twice (idempotency).
CREATE TABLE IF NOT EXISTS daily_claims (
  profile_key BINARY(16) NOT NULL,
  claim_date INT NOT NULL,
  calendar_day INT NOT NULL,
  streak_after INT NOT NULL,
  player_uuid BINARY(16) NOT NULL,
  claimed_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key, claim_date)
);

-- Active playtime of a profile (not AFK, not in the AFK pool) and the milestones already paid: hours_paid hourly
-- rewards, tens_paid 10-hour keys, title_hours = the highest playtime title threshold granted.
CREATE TABLE IF NOT EXISTS playtime_rewards (
  profile_key BINARY(16) NOT NULL,
  active_seconds BIGINT NOT NULL,
  hours_paid INT NOT NULL,
  tens_paid INT NOT NULL,
  title_hours INT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key)
);

-- Hand-outs that happen after a claim's transaction (keys, items, fuel, titles, pass XP). Written in the claim's
-- transaction as 'pending'; moved to 'sending' right before the hand-out and to 'delivered' after it. A crash while
-- 'sending' moves the row to 'review' at the next start (staff check it), so a reward can be lost but never doubled.
-- Titles are idempotent and are simply granted again.
CREATE TABLE IF NOT EXISTS reward_grants (
  grant_id BINARY(16) NOT NULL,
  profile_key BINARY(16) NOT NULL,
  player_uuid BINARY(16) NOT NULL,
  grant_source VARCHAR(16) NOT NULL,
  source_ref VARCHAR(64) NOT NULL,
  grant_kind VARCHAR(16) NOT NULL,
  grant_ref VARCHAR(64) NOT NULL,
  amount BIGINT NOT NULL,
  grant_state VARCHAR(12) NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (grant_id)
);

CREATE INDEX IF NOT EXISTS idx_reward_grants_state ON reward_grants (grant_state, profile_key);

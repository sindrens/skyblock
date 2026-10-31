-- Quests (PHASE-10-REWARDS.md M10.7). Times in epoch milliseconds; windows as epoch days in the server zone
-- (Europe/Oslo): a daily window is its day, a weekly window (weekly quests, island missions) the day of its Monday.

-- One board in one window: a profile's daily or weekly set (owner_key = profile key) or an island's missions of the week
-- (owner_key = island id). rerolls_used = free swaps used (daily); bonus_paid = 1 once the all-done key was written, so
-- it is paid once per set even if the last two quests finish at the same moment.
CREATE TABLE IF NOT EXISTS quest_sets (
  owner_key BINARY(16) NOT NULL,
  quest_kind VARCHAR(8) NOT NULL,
  period_start INT NOT NULL,
  rerolls_used INT NOT NULL,
  bonus_paid INT NOT NULL,
  roll_tier INT NOT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (owner_key, quest_kind, period_start)
);

-- The quests of a set. quest_uid is new for every quest instance (also after a swap): payouts use it as idempotency key,
-- so a quest pays at most once. done_at = 0 while open. Rewards are fixed when the quest is rolled (shown in /quests).
CREATE TABLE IF NOT EXISTS quest_progress (
  owner_key BINARY(16) NOT NULL,
  quest_kind VARCHAR(8) NOT NULL,
  period_start INT NOT NULL,
  slot_index INT NOT NULL,
  quest_uid BINARY(16) NOT NULL,
  quest_type VARCHAR(16) NOT NULL,
  tier_from INT NOT NULL,
  target BIGINT NOT NULL,
  progress BIGINT NOT NULL,
  done_at BIGINT NOT NULL,
  reward_money BIGINT NOT NULL,
  reward_crystals BIGINT NOT NULL,
  reward_xp INT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (owner_key, quest_kind, period_start, slot_index)
);

CREATE INDEX IF NOT EXISTS idx_quest_progress_uid ON quest_progress (quest_uid);

-- Island missions: what each member (profile) added, and when their share was paid (0 = not paid).
CREATE TABLE IF NOT EXISTS quest_helpers (
  quest_uid BINARY(16) NOT NULL,
  profile_key BINARY(16) NOT NULL,
  player_uuid BINARY(16) NOT NULL,
  contributed BIGINT NOT NULL,
  paid_at BIGINT NOT NULL,
  PRIMARY KEY (quest_uid, profile_key)
);

-- Hand-outs after a payout's transaction (keys, pass XP). Written in that transaction as 'pending'; moved to 'sending'
-- right before the hand-out and to 'delivered' after it. A crash while 'sending' moves the row to 'review' at the next
-- start (staff check it), so a reward can be lost but never doubled.
CREATE TABLE IF NOT EXISTS quest_grants (
  grant_id BINARY(16) NOT NULL,
  profile_key BINARY(16) NOT NULL,
  player_uuid BINARY(16) NOT NULL,
  source_ref VARCHAR(64) NOT NULL,
  grant_kind VARCHAR(16) NOT NULL,
  grant_ref VARCHAR(32) NOT NULL,
  amount BIGINT NOT NULL,
  grant_state VARCHAR(12) NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (grant_id)
);

CREATE INDEX IF NOT EXISTS idx_quest_grants_state ON quest_grants (grant_state, profile_key);

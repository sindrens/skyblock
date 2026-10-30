-- Banks (M3.3). Balances stay in eco_accounts (owner_type 'bank' = a profile's personal bank, 'island' = an island bank);
-- every change is a ledger transaction. These tables only hold the interest state and limits.

-- Interest state per bank account: personal tier, the lowest balance of the current period, whether a withdrawal with a
-- fee forfeited it, and the last period whose interest was decided (payout idempotency key bank_interest:<account>:<period>).
CREATE TABLE IF NOT EXISTS eco_bank_state (
  owner_type VARCHAR(8) NOT NULL,
  owner_id BINARY(16) NOT NULL,
  bank_tier INT NOT NULL DEFAULT 1,
  cycle_no BIGINT NOT NULL,
  min_balance BIGINT NOT NULL DEFAULT 0,
  forfeited BOOLEAN NOT NULL DEFAULT FALSE,
  settled_cycle BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (owner_type, owner_id)
);

CREATE INDEX IF NOT EXISTS idx_eco_bank_state_due ON eco_bank_state (settled_cycle);

-- Island bank withdrawals per member and day (Europe/Oslo day number), for the daily limit per role.
CREATE TABLE IF NOT EXISTS eco_bank_withdrawn (
  island_id BINARY(16) NOT NULL,
  member_id BINARY(16) NOT NULL,
  day_no INT NOT NULL,
  amount BIGINT NOT NULL,
  PRIMARY KEY (island_id, member_id, day_no)
);

-- Minutes played per profile and day, for the interest activity rule (owner #38: 2 h in the last 7 days).
CREATE TABLE IF NOT EXISTS eco_bank_activity (
  profile_id BINARY(16) NOT NULL,
  day_no INT NOT NULL,
  minutes INT NOT NULL,
  PRIMARY KEY (profile_id, day_no)
);

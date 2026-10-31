-- Black Market (PHASE-10-REWARDS.md M10.11). Times in epoch milliseconds.

-- The rotating offers of each rotation (rotation_id = epoch day * 100 + window index). Written once by the first server
-- that needs the rotation, so a restart or a config edit never re-rolls a running rotation. stock_left is the server-wide
-- stock of the headline (-1 = no shared stock); a buy lowers it in its own transaction.
CREATE TABLE IF NOT EXISTS black_market_rotation (
  rotation_id BIGINT NOT NULL,
  slot_index INT NOT NULL,
  offer_id VARCHAR(48) NOT NULL,
  price_shards BIGINT NOT NULL,
  stock_total INT NOT NULL,
  stock_left INT NOT NULL,
  starts_at BIGINT NOT NULL,
  PRIMARY KEY (rotation_id, slot_index)
);

-- Limit counters: scope_id is an account (player UUID text) or 'island:<uuid>' (Island Charter steps), period_key is
-- 'r:<rotation>', 'd:<day>', 'm:<yyyymm>' or 's:<season>'. A buy raises it only while it stays within the limit (one
-- conditional UPDATE inside the buy transaction), so two clicks at once can never pass a limit.
CREATE TABLE IF NOT EXISTS black_market_counts (
  scope_id VARCHAR(48) NOT NULL,
  offer_id VARCHAR(48) NOT NULL,
  period_key VARCHAR(24) NOT NULL,
  bought INT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (scope_id, offer_id, period_key)
);

-- Every buy (and staff give). Written 'pending' in the same transaction as the payment; 'sending' right before the
-- hand-out and 'delivered' after it. A hand-out that can't happen is refunded ('refunded'); a crash while 'sending' moves
-- the row to 'review' at the next start (staff check it), so an item can be lost but never doubled.
CREATE TABLE IF NOT EXISTS black_market_buys (
  buy_id BINARY(16) NOT NULL,
  account_id BINARY(16) NOT NULL,
  profile_key BINARY(16) NOT NULL,
  rotation_id BIGINT NOT NULL,
  offer_id VARCHAR(48) NOT NULL,
  reward_kind VARCHAR(16) NOT NULL,
  reward_ref VARCHAR(64) NOT NULL,
  amount INT NOT NULL,
  pay_currency VARCHAR(12) NOT NULL,
  price BIGINT NOT NULL,
  island_id BINARY(16) NULL,
  buy_state VARCHAR(12) NOT NULL,
  bought_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (buy_id)
);

CREATE INDEX IF NOT EXISTS idx_black_market_buys_state ON black_market_buys (buy_state, bought_at);
CREATE INDEX IF NOT EXISTS idx_black_market_buys_account ON black_market_buys (account_id, bought_at);

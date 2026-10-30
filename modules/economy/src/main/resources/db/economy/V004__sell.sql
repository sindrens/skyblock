-- Selling and dynamic prices (M3.4).
-- sell_prices: the dynamic state of every item (multiplier 1.0 = base price). Kept in memory by the plugin and saved
-- every few minutes and on shutdown; updated_at is the time of the last interval step, so a restart applies the steps
-- that were missed while the server was off. volume = items sold per hour of the last 24 h ("hour:count;...").
CREATE TABLE IF NOT EXISTS sell_prices (
  item_key VARCHAR(80) NOT NULL,
  multiplier DOUBLE NOT NULL,
  impact DOUBLE NOT NULL,
  last_sale_at BIGINT NOT NULL,
  hour_ago DOUBLE NOT NULL,
  volume VARCHAR(512) NOT NULL,
  sold_total BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (item_key)
);

-- One row per sale, written in the SAME database transaction as the payment (EconomyService.executeGuarded, ledger
-- reason 'sell', idempotency key 'sell:<id>'), so a sale record exists exactly when the money was paid.
-- sale_lines: JSON array [{"item","amount","base","multiplier","hot","value"}].
CREATE TABLE IF NOT EXISTS sell_sales (
  id BINARY(16) NOT NULL,
  created_at BIGINT NOT NULL,
  profile_id BINARY(16) NOT NULL,
  player_id BINARY(16) NOT NULL,
  player_name VARCHAR(32) NOT NULL,
  source VARCHAR(16) NOT NULL,
  item_count BIGINT NOT NULL,
  subtotal BIGINT NOT NULL,
  bonus_percent DOUBLE NOT NULL,
  total BIGINT NOT NULL,
  sale_lines TEXT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT chk_sell_sales_total CHECK (total > 0)
);

CREATE INDEX IF NOT EXISTS idx_sell_sales_profile ON sell_sales (profile_id, created_at);
CREATE INDEX IF NOT EXISTS idx_sell_sales_player ON sell_sales (player_id, created_at);

-- Items of a failed sale that could not be handed back because the player had left (or switched profile) by then.
-- Given back on the next join / switch to that profile: DELETE ... WHERE id = ? must hit exactly one row before the
-- item is handed out, so a refund is delivered once.
CREATE TABLE IF NOT EXISTS sell_refunds (
  id BIGINT NOT NULL AUTO_INCREMENT,
  owner BINARY(16) NOT NULL,
  profile_id BINARY(16) NOT NULL,
  item MEDIUMBLOB NOT NULL,
  material VARCHAR(64) NOT NULL,
  amount INT NOT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_sell_refunds_owner ON sell_refunds (owner, id);

-- Builder shop (M3.5). One row per purchase, written in the SAME database transaction as the payment
-- (EconomyService.executeGuarded), so a paid purchase always has its row and a row always has its payment.
-- state: delivering (paid, being handed over right now) -> delivered
--        delivering -> pending (the player left or switched profile before the items were handed over)
--        pending -> delivering (claimed exactly once on join / profile switch) -> delivered
-- Rows still 'delivering' at startup (crash between payment and hand-over) become 'pending' and are handed over again.
CREATE TABLE IF NOT EXISTS shop_purchases (
  id BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  player_uuid BINARY(16) NOT NULL,
  profile_key BINARY(16) NOT NULL,
  player_name VARCHAR(32) NOT NULL,
  item_id VARCHAR(64) NOT NULL,
  category_id VARCHAR(32) NOT NULL,
  quantity INT NOT NULL,
  unit_price BIGINT NOT NULL,
  total_price BIGINT NOT NULL,
  currency_key VARCHAR(16) NOT NULL,
  state VARCHAR(12) NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_shop_purchases_profile ON shop_purchases (profile_key, state);
CREATE INDEX IF NOT EXISTS idx_shop_purchases_state ON shop_purchases (state);

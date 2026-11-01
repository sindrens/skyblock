-- Credits sold on Tebex (card C1, store grant; build spec CREDIT-SHOP 4.2). One row per Tebex transaction and package; the
-- primary key is the receipt, so a resend by Tebex can never pay twice (the ledger idempotency key guards it too).
-- state: granted | chargeback | refunded. taken = bought credits taken back by /credit chargeback. Times in epoch millis.
-- Valid on MariaDB 11.8 and H2. Note: the selling lane's sell_hot table uses V011.
CREATE TABLE IF NOT EXISTS store_purchases (
  tx_id VARCHAR(64) NOT NULL,
  package_id VARCHAR(32) NOT NULL,
  player_uuid BINARY(16) NOT NULL,
  credits BIGINT NOT NULL,
  usd_cents INT NOT NULL,
  state VARCHAR(12) NOT NULL,
  taken BIGINT NOT NULL DEFAULT 0,
  granted_at BIGINT NOT NULL,
  closed_at BIGINT NULL,
  PRIMARY KEY (tx_id, package_id)
);
CREATE INDEX IF NOT EXISTS idx_store_purchases_player ON store_purchases (player_uuid, granted_at);

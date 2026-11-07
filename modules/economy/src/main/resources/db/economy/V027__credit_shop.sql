-- Credit shop (owner 7 Nov 2026, plans/STORE_FULL_PLAN.md 1). One row per purchase: who paid, who got it, what, the price,
-- state paid | delivered | refunded | revoked (revoked = taken back after a chargeback). Times in epoch millis.
-- credit_shop_owned: small per-account counters the shop keeps for modules (island rename tokens, extra /pv vaults ...).
-- Valid on MariaDB 11.8 and H2.
CREATE TABLE IF NOT EXISTS credit_shop_purchases (
  id VARCHAR(64) NOT NULL,
  buyer_uuid BINARY(16) NOT NULL,
  receiver_uuid BINARY(16) NOT NULL,
  offer_id VARCHAR(48) NOT NULL,
  price BIGINT NOT NULL,
  bought_only BOOLEAN NOT NULL,
  state VARCHAR(12) NOT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_credit_shop_buyer ON credit_shop_purchases (buyer_uuid, created_at);
CREATE INDEX IF NOT EXISTS idx_credit_shop_receiver ON credit_shop_purchases (receiver_uuid, offer_id);
CREATE TABLE IF NOT EXISTS credit_shop_owned (
  account_uuid BINARY(16) NOT NULL,
  thing VARCHAR(48) NOT NULL,
  amount INT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (account_uuid, thing)
);

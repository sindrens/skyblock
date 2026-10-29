-- Money notes (M3.2): one row per note item. The item carries the id (= its unique item UUID); the amount and currency
-- are read from here on redeem, never from the item. Redeeming locks the row (SELECT ... FOR UPDATE), sets status
-- 'redeemed' and deposits in ONE database transaction (EconomyStore.Guard), so a copied note can never be cashed twice.
-- status: active (can be redeemed) | redeemed | void (never handed out, e.g. the target left before /eco note finished).
-- kind: withdraw (/withdraw, money was taken) | admin (/eco note, created by staff).
CREATE TABLE IF NOT EXISTS money_notes (
  id BINARY(16) NOT NULL,
  amount BIGINT NOT NULL,
  currency VARCHAR(16) NOT NULL,
  kind VARCHAR(8) NOT NULL,
  created_by BINARY(16) NULL,
  created_name VARCHAR(32) NOT NULL,
  created_at BIGINT NOT NULL,
  note VARCHAR(64) NULL,
  redeemed_by BINARY(16) NULL,
  redeemed_name VARCHAR(32) NULL,
  redeemed_at BIGINT NULL,
  status VARCHAR(8) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT chk_money_notes_amount CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_money_notes_created_by ON money_notes (created_by, created_at);
CREATE INDEX IF NOT EXISTS idx_money_notes_redeemed_by ON money_notes (redeemed_by, redeemed_at);

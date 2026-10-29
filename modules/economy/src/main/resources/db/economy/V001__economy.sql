-- Economy (M3.1): one balance per owner (player or island) and currency, plus the ledger of every change.
-- Balances only change through atomic UPDATEs inside a transaction (EconomyStore); never read-modify-write.
-- Accounts are global; the season of every change is in the ledger (season reset rules: docs/OPEN_QUESTIONS.md).
CREATE TABLE IF NOT EXISTS eco_accounts (
  owner_type VARCHAR(8) NOT NULL,
  owner_id BINARY(16) NOT NULL,
  currency VARCHAR(16) NOT NULL,
  balance BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (owner_type, owner_id, currency),
  CONSTRAINT chk_eco_accounts_balance CHECK (balance >= 0)
);

CREATE INDEX IF NOT EXISTS idx_eco_accounts_top ON eco_accounts (currency, owner_type, balance);

-- One row per change. from_* is NULL when currency was created (rewards, admin give), to_* NULL when it was removed.
-- idem_key: unique key of a change (anti-dupe); NULL for changes that can't be replayed.
CREATE TABLE IF NOT EXISTS eco_ledger (
  id BIGINT NOT NULL AUTO_INCREMENT,
  created_at BIGINT NOT NULL,
  season_id INT NOT NULL,
  currency VARCHAR(16) NOT NULL,
  from_type VARCHAR(8) NULL,
  from_id BINARY(16) NULL,
  to_type VARCHAR(8) NULL,
  to_id BINARY(16) NULL,
  amount BIGINT NOT NULL,
  from_balance BIGINT NULL,
  to_balance BIGINT NULL,
  reason VARCHAR(32) NOT NULL,
  reference VARCHAR(64) NULL,
  idem_key VARCHAR(96) NULL,
  actor_uuid BINARY(16) NULL,
  actor_name VARCHAR(32) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uq_eco_ledger_idem UNIQUE (idem_key)
);

CREATE INDEX IF NOT EXISTS idx_eco_ledger_from ON eco_ledger (from_id, id);
CREATE INDEX IF NOT EXISTS idx_eco_ledger_to ON eco_ledger (to_id, id);
CREATE INDEX IF NOT EXISTS idx_eco_ledger_time ON eco_ledger (created_at);
CREATE INDEX IF NOT EXISTS idx_eco_ledger_reason ON eco_ledger (reason, created_at);

-- /pv private vaults (owner 7 Nov 2026, plans/STORE_FULL_PLAN.md 2): account-wide, one row per used slot; a vault is
-- written as a whole in one transaction (its rows deleted, then inserted). Times in epoch millis.
CREATE TABLE IF NOT EXISTS perks_vaults (
  account_uuid BINARY(16) NOT NULL,
  vault_no INT NOT NULL,
  slot INT NOT NULL,
  item MEDIUMBLOB NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (account_uuid, vault_no, slot)
);

-- Island Vault (batch 4, 7 Nov 2026): /is storage, one chest shared by the island's members. One row per used slot; the
-- whole vault is written in one transaction (rows of the island deleted, then inserted), the same transaction as a task
-- hand-in from the storage. Rows stay when an island is disbanded (staff can still give the items back).
CREATE TABLE IF NOT EXISTS island_vault (
  island_id BINARY(16) NOT NULL,
  slot INT NOT NULL,
  item MEDIUMBLOB NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, slot)
);

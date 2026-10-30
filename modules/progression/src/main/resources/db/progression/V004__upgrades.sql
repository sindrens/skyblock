-- Island upgrades (progression module, PHASE-5 M5.4). One row per island, season and upgrade with the bought level.
-- Written as the guard of the island bank payment (ledger reason 'island_upgrade'), so the level and the money change in
-- one database transaction: a level exists exactly when it was paid, and the payment's idempotency key stops a second
-- payment for the same level. Size, member slots and warp slots also stay in the islands table (raised from here).
CREATE TABLE IF NOT EXISTS island_upgrades (
  island_id BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  upgrade_id VARCHAR(32) NOT NULL,
  upgrade_level INT NOT NULL,
  bought_by BINARY(16) NULL,
  bought_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, season_id, upgrade_id)
);

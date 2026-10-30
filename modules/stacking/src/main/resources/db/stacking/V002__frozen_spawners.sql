-- SECFIX1 F-07 / R3-13: a spawner row whose block is gone (crash before the chunk was saved, world edit, island reset)
-- is no longer deleted. It moves here in one transaction, frozen: it never spawns and can't be taken, so it can't be
-- duplicated, and staff can give the spawners back by hand from this table and the audit log (spawner_frozen).
-- state: frozen (waiting for staff) | restored | dismissed (set by hand once staff decided).
CREATE TABLE IF NOT EXISTS stacking_spawners_frozen (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  world VARCHAR(64) NOT NULL,
  x INT NOT NULL,
  y INT NOT NULL,
  z INT NOT NULL,
  entity_type VARCHAR(64) NOT NULL,
  amount INT NOT NULL,
  island_id BINARY(16) NULL,
  placed_by BINARY(16) NULL,
  placed_at BIGINT NOT NULL,
  reason VARCHAR(32) NOT NULL,
  state VARCHAR(16) NOT NULL,
  frozen_at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_stacking_spawners_frozen_island ON stacking_spawners_frozen (island_id);
CREATE INDEX IF NOT EXISTS idx_stacking_spawners_frozen_state ON stacking_spawners_frozen (state);

-- Island production (PHASE-7-PRODUCTION.md). M7.1 Island Fuel. Times and fuel in milliseconds.

-- One row per island that ever had fuel. The production module keeps every row in memory (it is the authority while it
-- runs) and writes changes back every fuel.save-seconds and on stop. Grants (AFK pool, staff) and Fuel Cells write the
-- current value and then add with one conditional, capped UPDATE in the same transaction.
CREATE TABLE IF NOT EXISTS island_fuel (
  island_id BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  fuel_ms BIGINT NOT NULL,
  tank_level INT NOT NULL,
  last_state VARCHAR(8) NOT NULL,
  state_since BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id)
);

-- Every Fuel Cell ever used (its unique id): the same cell can never add fuel twice, even when a copy or a crash
-- rollback brings the item back.
CREATE TABLE IF NOT EXISTS production_used_cells (
  item_uuid BINARY(16) NOT NULL,
  profile_key BINARY(16) NOT NULL,
  island_id BINARY(16) NOT NULL,
  cell_minutes INT NOT NULL,
  used_at BIGINT NOT NULL,
  PRIMARY KEY (item_uuid)
);

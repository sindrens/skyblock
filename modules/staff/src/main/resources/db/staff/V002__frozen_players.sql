-- Frozen players (/freeze). A row means "frozen until a staff member unfreezes them": kept across logout and restarts.
-- allow_flight/flying: the player's own flight state before the freeze, restored on unfreeze (the freeze makes them hover).
CREATE TABLE IF NOT EXISTS staff_frozen (
  uuid BINARY(16) NOT NULL PRIMARY KEY,
  name VARCHAR(32) NOT NULL,
  frozen_by VARCHAR(32) NOT NULL,
  frozen_by_uuid BINARY(16),
  frozen_at BIGINT NOT NULL,
  allow_flight BOOLEAN NOT NULL,
  flying BOOLEAN NOT NULL
);

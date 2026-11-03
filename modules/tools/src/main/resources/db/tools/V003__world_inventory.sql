-- Owner 3 Nov 2026: the Depths has its own inventory; the island and spawn share one. One row per profile and side
-- ('surface' or 'depths'): the full inventory (storage, armor, offhand) of the side that is NOT live right now, written
-- when the player leaves that side. The live side's row is stale and always overwritten before it is read again.
-- Valid on MariaDB 11.8 and H2.
CREATE TABLE IF NOT EXISTS world_inventory (
  profile_key BINARY(16) NOT NULL,
  side VARCHAR(16) NOT NULL,
  items MEDIUMBLOB NOT NULL,
  saved_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key, side)
);

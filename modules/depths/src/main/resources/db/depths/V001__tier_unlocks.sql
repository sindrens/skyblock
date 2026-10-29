-- Depths tier unlocks per player and season (depths module, M4.6). A row exists once the tier was unlocked (skill level
-- reached) or first visited; first_visit_at is set on the first walk/teleport into the zone (title + sound once).
-- Access itself is always derived from the live skill level; this table only remembers the "first" moments.
CREATE TABLE IF NOT EXISTS depths_tier_unlocks (
  player BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  zone VARCHAR(48) NOT NULL,
  unlocked_at BIGINT NOT NULL,
  first_visit_at BIGINT NULL,
  PRIMARY KEY (player, season_id, zone)
);

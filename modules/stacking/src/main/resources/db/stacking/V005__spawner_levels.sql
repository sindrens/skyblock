-- Spawners v2 (owner plan 5 Nov 2026, FULL_PLAN.md section 5): a level per island and mob type from kills of mobs from
-- the island's own spawners on the island. kills = kills in the current prestige cycle; prestige 0-10 (+5% drops each).
CREATE TABLE IF NOT EXISTS stacking_spawner_levels (
  island_id BINARY(16) NOT NULL,
  mob VARCHAR(48) NOT NULL,
  kills BIGINT NOT NULL DEFAULT 0,
  prestige INT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, mob)
);

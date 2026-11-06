-- Owner 3 Nov 2026: island tools are SHARED. Tier and upgrades belong to the island (every member's copy shows them; a new
-- member gets them). The per-profile rows of V001 keep the item id, the slot and the stored flag. An island row starts
-- from the highest levels its members had (merged as they log in, never down).
CREATE TABLE IF NOT EXISTS island_tool_levels (
  island_id BINARY(16) NOT NULL,
  kind VARCHAR(10) NOT NULL,
  tier INT NOT NULL,
  upgrades VARCHAR(400) NOT NULL DEFAULT '',
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, kind)
);

-- The island's task toward the next upgrade of a tool: relevant actions with that tool by any member since the last
-- upgrade (mine ores with the pickaxe, chop logs with the axe, harvest ripe crops with the hoe, kill mobs with the sword).
CREATE TABLE IF NOT EXISTS island_tool_tasks (
  island_id BINARY(16) NOT NULL,
  kind VARCHAR(10) NOT NULL,
  progress BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (island_id, kind)
);

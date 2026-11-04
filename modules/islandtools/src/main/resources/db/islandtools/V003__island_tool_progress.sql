-- Owner 4 Nov 2026 (island tools rework): the hoe and pickaxe belong to the ISLAND. One row per island and tool kind:
-- xp = tool XP in this prestige (the level follows from it), prestige, the perk levels ('a=3,b=1') and a version that every
-- perk purchase and prestige raises (the guard against a double purchase by two members at once). XP is added in batches
-- (xp = xp + ?) and never touches the version. Seeded on first use from island_tool_levels (old levels carried over
-- proportionally, the tier dropped: every tool is diamond). The old tables stay for a rollback; axe and sword rows are
-- simply ignored.
CREATE TABLE IF NOT EXISTS island_tool_progress (
  island_id BINARY(16) NOT NULL,
  kind VARCHAR(10) NOT NULL,
  xp BIGINT NOT NULL DEFAULT 0,
  prestige INT NOT NULL DEFAULT 0,
  upgrades VARCHAR(600) NOT NULL DEFAULT '',
  version BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, kind)
);

-- Owner 6-7 Nov 2026 (plans/TOOLS_V3.md, "personal tools"): tool level, XP, prestige and rebirth belong to the PLAYER
-- (one row per profile and tool kind). The island keeps the shared perks (island_tool_progress.upgrades). A new row is
-- seeded once from the island tool the player stood on (its XP and prestige), so nobody loses a level. version guards a
-- prestige against a double click or a second device; XP is added in batches (xp = xp + ?) and never touches it.
CREATE TABLE IF NOT EXISTS island_tool_personal (
  profile_id BINARY(16) NOT NULL,
  kind VARCHAR(10) NOT NULL,
  xp BIGINT NOT NULL DEFAULT 0,
  prestige INT NOT NULL DEFAULT 0,
  rebirth INT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (profile_id, kind)
);

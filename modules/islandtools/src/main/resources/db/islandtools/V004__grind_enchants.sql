-- Owner 6 Nov 2026 (plans/TOOLS_V3.md): personal grind enchants. One row per profile, tool kind and enchant. They are the
-- player's own (bought with their own shards) and work only at /farm and /mine; the island's perks stay shared.
CREATE TABLE IF NOT EXISTS island_tool_enchants (
  profile_id BINARY(16) NOT NULL,
  kind VARCHAR(10) NOT NULL,
  enchant VARCHAR(32) NOT NULL,
  level INT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (profile_id, kind, enchant)
);

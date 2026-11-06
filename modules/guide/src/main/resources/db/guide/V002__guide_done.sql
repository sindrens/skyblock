-- Kindle's quests and trades (docs/planning/ISLAND_GUIDE.md): one row per hand-in. Story quests: scope = island,
-- period = 'story'. Errands and finds: scope = profile, period = the rotation number. The primary key makes every quest
-- and trade pay once per rotation, even with two clicks at the same moment.
CREATE TABLE IF NOT EXISTS guide_done (
  scope_id BINARY(16) NOT NULL,
  kind VARCHAR(8) NOT NULL,
  period VARCHAR(24) NOT NULL,
  entry_id VARCHAR(40) NOT NULL,
  done_by BINARY(16) NOT NULL,
  done_at BIGINT NOT NULL,
  PRIMARY KEY (scope_id, kind, period, entry_id)
);

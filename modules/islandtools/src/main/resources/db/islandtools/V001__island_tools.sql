-- Island tools (Complete Developer Spec D1-D12, T1-T13): one row per profile and tool kind. item_id = the only valid copy
-- (a new copy makes older ones useless); stored = put away in the Depths (T1), slot = where it goes back.
CREATE TABLE IF NOT EXISTS island_tools (
  profile_key BINARY(16) NOT NULL,
  kind VARCHAR(10) NOT NULL,
  tier INT NOT NULL,
  upgrades VARCHAR(400) NOT NULL DEFAULT '',
  item_id VARCHAR(36) NOT NULL,
  slot INT NOT NULL DEFAULT -1,
  stored BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key, kind)
);

-- T9: the hoe's storage, per profile and item.
CREATE TABLE IF NOT EXISTS island_tool_storage (
  profile_key BINARY(16) NOT NULL,
  item VARCHAR(48) NOT NULL,
  amount BIGINT NOT NULL,
  PRIMARY KEY (profile_key, item)
);

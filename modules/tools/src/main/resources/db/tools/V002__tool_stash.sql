-- Owner 3 Nov 2026: Depths tools exist only in the Depths. Outside it they wait here (the item as bytes, its slot) and come
-- back in the same slot when the profile enters the Depths again. One row per tool; restoring skips a tool that is
-- already in the inventory (a crash between the two writes can never make a second copy). Valid on MariaDB 11.8 and H2.
CREATE TABLE IF NOT EXISTS tool_stash (
  profile_key BINARY(16) NOT NULL,
  tool_id BINARY(16) NOT NULL,
  slot INT NOT NULL,
  item MEDIUMBLOB NOT NULL,
  stashed_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key, tool_id)
);

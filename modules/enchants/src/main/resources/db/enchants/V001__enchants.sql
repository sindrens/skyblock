-- Enchants (Phase E, ENCHANTS §9 and §13.1). One row per enchant book or Disenchant Scroll (id = the item's unique id):
-- an item whose row is not 'active' any more is a copy (consumed/void) and is confiscated when used.
-- state: active | consumed | void (minted but never handed out, or rolled back). reason: the operation that changed it.
-- profile_id stays NULL until the profiles module exists (books are profile items: they live in a profile's inventory).
CREATE TABLE IF NOT EXISTS enchant_books (
  id BINARY(16) NOT NULL PRIMARY KEY,
  kind VARCHAR(8) NOT NULL,
  enchant_id VARCHAR(48) NULL,
  level INT NOT NULL DEFAULT 0,
  state VARCHAR(12) NOT NULL,
  season_id INT NOT NULL,
  origin VARCHAR(64) NOT NULL,
  created_for BINARY(16) NULL,
  profile_id BINARY(16) NULL,
  created_at BIGINT NOT NULL,
  changed_at BIGINT NULL,
  changed_by BINARY(16) NULL,
  reason VARCHAR(64) NULL
);

CREATE INDEX IF NOT EXISTS idx_enchant_books_created_for ON enchant_books (created_for);
CREATE INDEX IF NOT EXISTS idx_enchant_books_state ON enchant_books (state);

-- The enchant collection per season ("found this season" in the guide; Mythics show ??? until seen).
CREATE TABLE IF NOT EXISTS enchant_seen (
  player BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  enchant_id VARCHAR(48) NOT NULL,
  first_at BIGINT NOT NULL,
  PRIMARY KEY (player, season_id, enchant_id)
);

-- Book shop rolls per player and day (Europe/Oslo date, yyyy-MM-dd).
CREATE TABLE IF NOT EXISTS enchant_daily (
  player BINARY(16) NOT NULL,
  roll_day VARCHAR(10) NOT NULL,
  roll VARCHAR(16) NOT NULL,
  used INT NOT NULL,
  PRIMARY KEY (player, roll_day, roll)
);

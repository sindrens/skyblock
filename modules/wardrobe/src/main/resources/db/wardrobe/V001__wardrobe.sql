-- Wardrobe (Phase W, docs/plans/WARDROBE.md §8.2). Everything per profile uses the PROFILE KEY (ProfileService#profileKey);
-- account data (Sigils) uses the player UUID. Every change is audited by the module.

-- One row per set a profile owns (the set is the row, worn pieces are only a view of it, WD19).
-- state: active | merged | removed. active_key = set_id while the row is active and not merged, NULL otherwise, so the
-- unique key below allows exactly one active copy per set per profile (WD6) on MariaDB and H2 alike (NULLs never clash).
-- origin: money | crystals | gems | gems_claim | token | merge | staff. level/xp/version are used by W2 (set levels).
CREATE TABLE IF NOT EXISTS wardrobe_sets (
  entry_id BINARY(16) NOT NULL PRIMARY KEY,
  profile_key BINARY(16) NOT NULL,
  set_id VARCHAR(32) NOT NULL,
  merged_set_id VARCHAR(32) NULL,
  look_set_id VARCHAR(32) NULL,
  active_key VARCHAR(32) NULL,
  level INT NOT NULL DEFAULT 0,
  xp DOUBLE NOT NULL DEFAULT 0,
  state VARCHAR(12) NOT NULL,
  origin VARCHAR(16) NOT NULL,
  origin_ref VARCHAR(64) NULL,
  season_id INT NOT NULL,
  created_at BIGINT NOT NULL,
  changed_at BIGINT NULL,
  changed_by BINARY(16) NULL,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT uq_wardrobe_sets_active UNIQUE (profile_key, active_key)
);

CREATE INDEX IF NOT EXISTS idx_wardrobe_sets_profile ON wardrobe_sets (profile_key, state);

-- Per-piece data of an entry (armor enchants, E5). The item only shows it.
CREATE TABLE IF NOT EXISTS wardrobe_pieces (
  entry_id BINARY(16) NOT NULL,
  piece VARCHAR(8) NOT NULL,
  enchants VARCHAR(2048) NOT NULL,
  PRIMARY KEY (entry_id, piece)
);

-- The truth for worn pieces: a worn item is valid only in its owner's matching armor slot with this nonce (§4.2).
CREATE TABLE IF NOT EXISTS wardrobe_equipped (
  profile_key BINARY(16) NOT NULL,
  slot VARCHAR(8) NOT NULL,
  entry_id BINARY(16) NOT NULL,
  nonce BINARY(16) NOT NULL,
  equipped_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key, slot)
);

-- The Wardrobe Chest: unique, tracked items only (tools, enchant books, scrolls, Set Tokens, tomes), with the auction
-- house's escrow rules (§4.4). state: stored -> releasing (plan_id, item being handed out) -> released.
-- A 'releasing' row whose item is not in the player's inventory is given again on join; an inventory copy of a 'stored'
-- item is a stale copy and is removed (the stored copy wins). item_uuid is the item's unique id, so a double is visible.
CREATE TABLE IF NOT EXISTS wardrobe_storage (
  item_uuid BINARY(16) NOT NULL PRIMARY KEY,
  profile_key BINARY(16) NOT NULL,
  kind VARCHAR(16) NOT NULL,
  item_id VARCHAR(64) NOT NULL,
  item_name VARCHAR(128) NOT NULL,
  item MEDIUMBLOB NOT NULL,
  state VARCHAR(12) NOT NULL,
  plan_id BINARY(16) NULL,
  stored_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_wardrobe_storage_profile ON wardrobe_storage (profile_key, state);

-- Account-wide gem purchases (WD5): kept across seasons; each profile may claim the set once per season.
CREATE TABLE IF NOT EXISTS wardrobe_sigils (
  player_uuid BINARY(16) NOT NULL,
  set_id VARCHAR(32) NOT NULL,
  gems BIGINT NOT NULL,
  bought_at BIGINT NOT NULL,
  bought_profile BINARY(16) NULL,
  PRIMARY KEY (player_uuid, set_id)
);

CREATE TABLE IF NOT EXISTS wardrobe_claims (
  profile_key BINARY(16) NOT NULL,
  set_id VARCHAR(32) NOT NULL,
  season_id INT NOT NULL,
  entry_id BINARY(16) NOT NULL,
  claimed_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key, set_id, season_id)
);

-- Set Tokens: unique items like enchant books (§2.4). A token item whose row is not 'active' is a copy and is taken.
-- state: active | redeemed | void (never handed out) | confiscated.
CREATE TABLE IF NOT EXISTS set_tokens (
  token_uuid BINARY(16) NOT NULL PRIMARY KEY,
  set_id VARCHAR(32) NOT NULL,
  state VARCHAR(12) NOT NULL,
  origin VARCHAR(64) NOT NULL,
  created_for BINARY(16) NULL,
  created_at BIGINT NOT NULL,
  changed_at BIGINT NULL,
  changed_by BINARY(16) NULL,
  redeemed_profile BINARY(16) NULL,
  reason VARCHAR(64) NULL
);

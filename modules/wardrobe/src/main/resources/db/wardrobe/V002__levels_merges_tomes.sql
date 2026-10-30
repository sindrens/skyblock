-- Wardrobe W2 (docs/plans/WARDROBE.md §3, §5): set levels, merging, Wardrobe Tomes.
-- wardrobe_sets.xp holds the entry's TOTAL set XP; wardrobe_sets.level follows from it (level curve in wardrobe.yml).
-- A merged entry has merged_set_id set, look_set_id = the set whose look its pieces use, active_key NULL (so the
-- profile may buy or redeem either input set again, WD6) and origin 'merge'; its inputs are state 'merged'.

-- One row per merge: the new entry and the two entries it consumed (kept forever, for staff and audit).
CREATE TABLE IF NOT EXISTS wardrobe_merges (
  merge_id BINARY(16) NOT NULL PRIMARY KEY,
  profile_key BINARY(16) NOT NULL,
  entry_a BINARY(16) NOT NULL,
  entry_b BINARY(16) NOT NULL,
  set_a VARCHAR(32) NOT NULL,
  set_b VARCHAR(32) NOT NULL,
  look_set_id VARCHAR(32) NOT NULL,
  fee BIGINT NOT NULL,
  merged_at BIGINT NOT NULL,
  merged_by BINARY(16) NULL,
  CONSTRAINT uq_wardrobe_merges_a UNIQUE (entry_a),
  CONSTRAINT uq_wardrobe_merges_b UNIQUE (entry_b)
);

CREATE INDEX IF NOT EXISTS idx_wardrobe_merges_profile ON wardrobe_merges (profile_key);

-- Wardrobe Tomes (WD13): unique consumable items like Set Tokens. A tome item whose row is not 'active' is a copy and
-- is taken. state: active | redeemed (used on a set) | void (never handed out) | confiscated. xp_amount is the truth (the item only shows it).
CREATE TABLE IF NOT EXISTS wardrobe_tomes (
  tome_uuid BINARY(16) NOT NULL PRIMARY KEY,
  kind VARCHAR(16) NOT NULL,
  xp_amount BIGINT NOT NULL,
  state VARCHAR(12) NOT NULL,
  origin VARCHAR(64) NOT NULL,
  created_for BINARY(16) NULL,
  created_at BIGINT NOT NULL,
  changed_at BIGINT NULL,
  changed_by BINARY(16) NULL,
  used_entry BINARY(16) NULL,
  reason VARCHAR(64) NULL
);

-- Key items (PHASE-10 M10.4): a tradeable key withdrawn from /crates becomes a unique item (item_uuid = the item's
-- unique id, seen by the DupeDetector). The row is written in the SAME transaction that takes the key from the profile,
-- and redeeming flips item_state active -> redeemed in the SAME transaction that adds the key back, so a key item is
-- worth exactly one key, once. A copy of a redeemed item finds no active row and is refused.
--   item_state: active (item exists, not used) | redeemed (put back on a profile) | refunded (never handed out: the
--               player left or switched profile first, the key went back to the profile it came from)
-- account_uuid + profile_key = who withdrew it: owner decision P4, it can't be redeemed on another profile of the same
-- account (other players can redeem it).
CREATE TABLE IF NOT EXISTS crate_key_items (
  item_uuid BINARY(16) NOT NULL,
  crate_id VARCHAR(32) NOT NULL,
  account_uuid BINARY(16) NOT NULL,
  account_name VARCHAR(16) NOT NULL,
  profile_key BINARY(16) NOT NULL,
  item_state VARCHAR(12) NOT NULL,
  created_at BIGINT NOT NULL,
  redeemed_profile BINARY(16) NULL,
  redeemed_by BINARY(16) NULL,
  redeemed_name VARCHAR(16) NULL,
  redeemed_at BIGINT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (item_uuid)
);

CREATE INDEX IF NOT EXISTS idx_crate_key_items_profile ON crate_key_items (profile_key, created_at);

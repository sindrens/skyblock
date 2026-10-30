-- Phase P (P7): economy/item sanctions (spec 13.1) and island punishments (spec 13.2).
-- Never edit V001-V007. Re-runnable: IF NOT EXISTS everywhere; MODIFY only widens a column.

-- Island punishment types (ISLAND_KICK, ISLAND_BAN, COOP_REMOVE, COOP_BLOCK) are longer than 8 characters.
ALTER TABLE punishments MODIFY COLUMN type VARCHAR(16) NOT NULL;

ALTER TABLE punishment_requests MODIFY COLUMN type VARCHAR(16) NOT NULL;

-- One row per sanction. Account-wide: target_uuid is the player (every profile); profile_key is the profile money was
-- removed from / the item was taken from (NULL for freeze and block trading).
-- kind: FREEZE (freeze balance), TRADE (block trading), MONEY (remove money), ITEM (confiscate item).
-- state: ACTIVE (in force / held), PENDING (item: row written, not yet taken from the player; never restorable),
--        LIFTED (freeze/trade ended by staff), EXPIRED (freeze/trade ran out), RESTORED (money/item given back),
--        FAILED (item: could not be taken, nothing happened).
-- expires: freeze/trade end (NULL = until lifted). amount/currency: money removed. item_text: shown summary of the item.
-- handled_*: who lifted/restored it and why. approved_*: reserved for approval of sanctions (spec 13.4).
CREATE TABLE IF NOT EXISTS sanctions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  kind VARCHAR(8) NOT NULL,
  target_uuid BINARY(16) NOT NULL,
  target_name VARCHAR(32) NOT NULL,
  profile_key BINARY(16) NULL,
  issuer_uuid BINARY(16) NULL,
  issuer_name VARCHAR(32) NOT NULL,
  reason VARCHAR(255) NOT NULL,
  created BIGINT NOT NULL,
  expires BIGINT NULL,
  amount BIGINT NULL,
  currency VARCHAR(16) NULL,
  item_text VARCHAR(255) NULL,
  state VARCHAR(10) NOT NULL,
  handled_by_uuid BINARY(16) NULL,
  handled_by_name VARCHAR(32) NULL,
  handled_at BIGINT NULL,
  handled_reason VARCHAR(255) NULL,
  approved_by_uuid BINARY(16) NULL,
  approved_by_name VARCHAR(32) NULL
);

CREATE INDEX IF NOT EXISTS idx_sanctions_target ON sanctions (target_uuid, state);

CREATE INDEX IF NOT EXISTS idx_sanctions_issuer ON sanctions (issuer_uuid, created);

CREATE INDEX IF NOT EXISTS idx_sanctions_state ON sanctions (state, kind);

-- The confiscated item itself (ItemStack#serializeAsBytes), kept until restored (and after, as history).
-- source: inventory | ender (where it was taken from), slot: the slot number there.
CREATE TABLE IF NOT EXISTS confiscated_items (
  sanction_id BIGINT NOT NULL PRIMARY KEY,
  item MEDIUMBLOB NOT NULL,
  source VARCHAR(10) NOT NULL,
  slot INT NOT NULL
);

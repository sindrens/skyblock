-- Item mailbox /box (owner answer 7): items that did not fit an inventory wait here, per profile, until taken.
-- Every delivery inserts all its rows in one transaction. Taking is DELETE ... WHERE entry_id = ? AND profile_key = ?
-- that must hit exactly one row before the item is given (remove-then-give; anything not given is inserted again).
-- delivery_ref: idempotency key of spooled deliveries (imported again after a crash without doubling); NULL otherwise.
CREATE TABLE IF NOT EXISTS box_items (
  entry_id BIGINT NOT NULL AUTO_INCREMENT,
  profile_key BINARY(16) NOT NULL,
  item_data MEDIUMBLOB NOT NULL,
  material VARCHAR(64) NOT NULL,
  amount INT NOT NULL,
  item_name VARCHAR(128) NOT NULL,
  source_key VARCHAR(32) NOT NULL,
  created_at BIGINT NOT NULL,
  delivery_ref VARCHAR(48) NULL,
  PRIMARY KEY (entry_id)
);

CREATE INDEX IF NOT EXISTS idx_box_items_profile ON box_items (profile_key, entry_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_box_items_ref ON box_items (delivery_ref);

-- Entries an admin cleared or removed (kept so they can be restored by hand; never deleted by the plugin).
CREATE TABLE IF NOT EXISTS box_cleared (
  entry_id BIGINT NOT NULL,
  profile_key BINARY(16) NOT NULL,
  item_data MEDIUMBLOB NOT NULL,
  material VARCHAR(64) NOT NULL,
  amount INT NOT NULL,
  item_name VARCHAR(128) NOT NULL,
  source_key VARCHAR(32) NOT NULL,
  created_at BIGINT NOT NULL,
  cleared_at BIGINT NOT NULL,
  cleared_by VARCHAR(36) NULL,
  cleared_by_name VARCHAR(32) NOT NULL,
  PRIMARY KEY (entry_id)
);

CREATE INDEX IF NOT EXISTS idx_box_cleared_profile ON box_cleared (profile_key, cleared_at);

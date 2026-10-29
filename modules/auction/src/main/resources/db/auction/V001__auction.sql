-- Auction house (M3.6). The listed item is stored serialized (ItemStack#serializeAsBytes) in the same transaction that
-- creates the listing row, and every state change is a conditional UPDATE (... WHERE status = 'active'), so a listing can
-- never be sold twice, or sold and cancelled. Payments go through EconomyService with idempotency keys ah_fee:<id>,
-- ah_buy:<id> and ah_sale:<id>; listings left in 'pending' or 'buying' by a crash are resolved on startup.
-- status: pending (listing fee not confirmed) -> active -> buying (a buyer is paying) -> sold
--         active -> cancelled / expired, pending -> failed (fee could not be paid, item to the collection bin)
CREATE TABLE IF NOT EXISTS ah_listings (
  id BIGINT NOT NULL AUTO_INCREMENT,
  season_id INT NOT NULL,
  seller BINARY(16) NOT NULL,
  seller_name VARCHAR(32) NOT NULL,
  currency VARCHAR(16) NOT NULL,
  price BIGINT NOT NULL,
  fee BIGINT NOT NULL,
  tax BIGINT NOT NULL DEFAULT 0,
  item MEDIUMBLOB NOT NULL,
  item_key VARCHAR(80) NOT NULL,
  item_uuid VARCHAR(36) NULL,
  material VARCHAR(64) NOT NULL,
  amount INT NOT NULL,
  item_name VARCHAR(128) NOT NULL,
  category VARCHAR(16) NOT NULL,
  status VARCHAR(12) NOT NULL,
  created_at BIGINT NOT NULL,
  expires_at BIGINT NOT NULL,
  buyer BINARY(16) NULL,
  buyer_name VARCHAR(32) NULL,
  buy_started_at BIGINT NULL,
  ended_at BIGINT NULL,
  seller_notice BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (id),
  CONSTRAINT chk_ah_listings_price CHECK (price > 0 AND fee >= 0 AND tax >= 0)
);

CREATE INDEX IF NOT EXISTS idx_ah_listings_status ON ah_listings (status, expires_at);
CREATE INDEX IF NOT EXISTS idx_ah_listings_seller ON ah_listings (seller, status);
CREATE INDEX IF NOT EXISTS idx_ah_listings_recent ON ah_listings (seller, created_at);
CREATE INDEX IF NOT EXISTS idx_ah_listings_notice ON ah_listings (seller, seller_notice);

-- The collection bin: items waiting for their owner (expired, cancelled, bought with a full inventory, fee not paid).
-- One row per listing at most (uq_ah_collection_listing); rows put back after a partial delivery have no listing.
-- Taking an item out is a DELETE ... WHERE id = ? AND owner = ? that must hit exactly one row.
CREATE TABLE IF NOT EXISTS ah_collection (
  id BIGINT NOT NULL AUTO_INCREMENT,
  owner BINARY(16) NOT NULL,
  listing_id BIGINT NULL,
  source VARCHAR(12) NOT NULL,
  item MEDIUMBLOB NOT NULL,
  material VARCHAR(64) NOT NULL,
  amount INT NOT NULL,
  item_name VARCHAR(128) NOT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uq_ah_collection_listing UNIQUE (listing_id)
);

CREATE INDEX IF NOT EXISTS idx_ah_collection_owner ON ah_collection (owner, id);

-- Secure trade (M3.7). A trade session row is the lock every change takes first (SELECT ... FOR UPDATE), so adding an
-- item, taking one back, completing and cancelling can never interleave.
-- status: open -> completed | cancelled (both final). Sessions still open after a crash are cancelled on startup.
CREATE TABLE IF NOT EXISTS trade_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  player_a BINARY(16) NOT NULL,
  profile_a BINARY(16) NOT NULL,
  name_a VARCHAR(32) NOT NULL,
  player_b BINARY(16) NOT NULL,
  profile_b BINARY(16) NOT NULL,
  name_b VARCHAR(32) NOT NULL,
  status VARCHAR(12) NOT NULL,
  created_at BIGINT NOT NULL,
  ended_at BIGINT NULL,
  end_reason VARCHAR(32) NULL,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_trade_sessions_status ON trade_sessions (status);

-- Item escrow: an offered item leaves the inventory and lives only here until it is delivered.
-- state: held (in an open trade) -> pending (waiting for owner_profile: returned on cancel, or the other side's after
-- completion) -> delivered. Completion switches owner_* of every held row and sets them pending in the same database
-- transaction as the money (EconomyService.executeBatch). Delivery is UPDATE ... WHERE state = 'pending' (exactly once).
-- write_token: a random id per offer, so after a database error the module can tell whether the row was written
-- (then it is in the trade) or not (then the item goes back), never both.
CREATE TABLE IF NOT EXISTS trade_escrow (
  id BIGINT NOT NULL AUTO_INCREMENT,
  trade_id BIGINT NOT NULL,
  origin_player BINARY(16) NOT NULL,
  origin_profile BINARY(16) NOT NULL,
  owner_player BINARY(16) NOT NULL,
  owner_profile BINARY(16) NOT NULL,
  state VARCHAR(12) NOT NULL,
  item MEDIUMBLOB NOT NULL,
  item_key VARCHAR(80) NOT NULL,
  item_uuid VARCHAR(36) NULL,
  material VARCHAR(64) NOT NULL,
  amount INT NOT NULL,
  item_name VARCHAR(128) NOT NULL,
  write_token VARCHAR(36) NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uq_trade_escrow_token UNIQUE (write_token)
);

CREATE INDEX IF NOT EXISTS idx_trade_escrow_trade ON trade_escrow (trade_id, state);
CREATE INDEX IF NOT EXISTS idx_trade_escrow_owner ON trade_escrow (owner_profile, state);

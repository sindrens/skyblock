-- test migration
CREATE TABLE IF NOT EXISTS test_items (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  owner BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  amount BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_test_items_owner ON test_items (owner);

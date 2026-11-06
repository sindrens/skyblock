-- Friends (owner 4 Nov 2026, plan B section E): per account (player UUID), so a profile wipe keeps them.
-- One row per pair, the smaller UUID (as bytes) first.
CREATE TABLE IF NOT EXISTS social_friends (
  a BINARY(16) NOT NULL,
  b BINARY(16) NOT NULL,
  since BIGINT NOT NULL,
  PRIMARY KEY (a, b)
);

CREATE TABLE IF NOT EXISTS social_friend_requests (
  sender BINARY(16) NOT NULL,
  receiver BINARY(16) NOT NULL,
  created BIGINT NOT NULL,
  PRIMARY KEY (sender, receiver)
);

CREATE INDEX IF NOT EXISTS idx_social_friend_requests_receiver ON social_friend_requests (receiver);

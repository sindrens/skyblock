-- Island level v2 (owner 7 Nov 2026, plans/TOOLS_V5.md 3): the highest island level whose rewards were paid, per season.
-- Rewards of a level are paid once (the ledger idempotency key guards it too). Valid on MariaDB 11.8 and H2.
CREATE TABLE IF NOT EXISTS island_xp_rewards (
  island_id BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  paid_level INT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, season_id)
);

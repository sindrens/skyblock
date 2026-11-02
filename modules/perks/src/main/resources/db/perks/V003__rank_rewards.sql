-- Rank rewards (card F2, RANKS-V2). Weekly kit per profile, monthly cosmetic per account. Times in epoch milliseconds.
CREATE TABLE IF NOT EXISTS perks_rank_kit (
  profile_key BINARY(16) NOT NULL,
  week_key VARCHAR(10) NOT NULL,
  player_uuid BINARY(16) NOT NULL,
  kit_size VARCHAR(2) NOT NULL,
  state VARCHAR(10) NOT NULL,
  claimed_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key, week_key)
);
CREATE INDEX IF NOT EXISTS idx_perks_rank_kit_state ON perks_rank_kit (state, claimed_at);
CREATE TABLE IF NOT EXISTS perks_rank_monthly (
  player_uuid BINARY(16) NOT NULL,
  month_key VARCHAR(7) NOT NULL,
  cosmetic_id VARCHAR(64) NOT NULL,
  claimed_at BIGINT NOT NULL,
  PRIMARY KEY (player_uuid, month_key)
);

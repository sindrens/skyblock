-- Reward Core claims (spec "Complete Developer Spec" 4.4): one row per claim of a periodic or one-time reward. The unique
-- key (reward_source, entry_id, scope_id, period_key) makes a second claim of the same period impossible; the claim's
-- guard checks it first, the constraint is the backstop. Weekly / Monthly Reward use scope PROFILE and period keys
-- "2026-W45" / "2026-11" (Europe/Oslo). Daily, vote and pass keep their own tables. Valid on MariaDB 11.8 and H2.
CREATE TABLE IF NOT EXISTS reward_claims (
  claim_id BIGINT NOT NULL AUTO_INCREMENT,
  reward_source VARCHAR(16) NOT NULL,
  entry_id VARCHAR(64) NOT NULL,
  scope_type VARCHAR(8) NOT NULL,
  scope_id BINARY(16) NOT NULL,
  period_key VARCHAR(16) NOT NULL,
  claimed_by_profile BINARY(16) NOT NULL,
  claimed_by_player BINARY(16) NOT NULL,
  claimed_at BIGINT NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  PRIMARY KEY (claim_id),
  CONSTRAINT uq_reward_claims_key UNIQUE (idempotency_key),
  CONSTRAINT uq_reward_claims_entry UNIQUE (reward_source, entry_id, scope_id, period_key)
);
CREATE INDEX IF NOT EXISTS idx_reward_claims_scope ON reward_claims (scope_id, reward_source, period_key);

-- Active play per profile and week (same rule as the /daily Playtime tab: acted in the last idle-minutes, not AFK, not in
-- the AFK pool). Written with the playtime save; the Weekly Reward needs weekly.required-minutes of it on that profile.
CREATE TABLE IF NOT EXISTS profile_active_week (
  profile_key BINARY(16) NOT NULL,
  week_key VARCHAR(10) NOT NULL,
  active_seconds BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (profile_key, week_key)
);

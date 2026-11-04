-- Owner 4 Nov 2026 (PLAN_4NOV phase 1, anti-boosting): money a NEW profile (less than anti-boost.playtime-minutes of its
-- own playtime) received from and sent to other players in total (/pay, /trade, auction sales, notes of other players).
-- One row per profile; rows are locked (FOR UPDATE) inside the money transaction, so the caps can't be passed by parallel
-- transfers. A profile id is never reused (a wipe gives a new key), and once a profile played the threshold its row is
-- ignored. Valid on MariaDB 11.8 and H2.
CREATE TABLE IF NOT EXISTS eco_anti_boost (
  profile_id BINARY(16) NOT NULL,
  received BIGINT NOT NULL,
  sent BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (profile_id)
);

-- Island boosters (owner 7 Nov 2026, plans/STORE_FULL_PLAN.md 3): one per island and type, for every member everywhere.
-- They run in real time (ends_at, epoch millis), also while nobody is online. started_by = the profile that started it
-- (or a system source such as koth). Valid on MariaDB 11.8 and H2.
CREATE TABLE IF NOT EXISTS booster_island (
  island_id BINARY(16) NOT NULL,
  booster_type VARCHAR(16) NOT NULL,
  bonus_percent INT NOT NULL,
  ends_at BIGINT NOT NULL,
  started_by VARCHAR(48) NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, booster_type)
);

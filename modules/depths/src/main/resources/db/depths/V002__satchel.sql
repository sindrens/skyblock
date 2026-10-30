-- Depths satchel (M4.4): virtual storage per PROFILE (profile key, PROF P2). One row per item; amount never below 0.
-- Writes are batched (adds) or atomic (take-out, sale via the economy's guarded transaction, Forge use).
CREATE TABLE IF NOT EXISTS depths_satchel_items (
  profile BINARY(16) NOT NULL,
  item VARCHAR(64) NOT NULL,
  amount BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (profile, item)
);

-- Bought satchel upgrades per profile (capacity steps from depths.yml satchel.upgrades).
CREATE TABLE IF NOT EXISTS depths_satchel_upgrades (
  profile BINARY(16) NOT NULL,
  upgrade_level INT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (profile)
);

-- E2 book drops (ENCHANTS §3.4, EN5). Counters live in memory while a player plays and are written in short batches
-- (never per block); a book row and its counter changes are written in one transaction.

-- Pity: actions (blocks, logs, kills; area blocks at their yield) since the last book of an activity, per profile key.
CREATE TABLE IF NOT EXISTS enchant_pity (
  profile_id BINARY(16) NOT NULL,
  activity VARCHAR(16) NOT NULL,
  since_book DOUBLE NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (profile_id, activity)
);

-- First finds: actions per account (player UUID, not profile: new profiles can't farm them), season and activity, and how
-- many first-find milestones (100th action = Common, 1,000th = Uncommon) were given out.
CREATE TABLE IF NOT EXISTS enchant_first_finds (
  account BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  activity VARCHAR(16) NOT NULL,
  actions DOUBLE NOT NULL,
  granted INT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (account, season_id, activity)
);

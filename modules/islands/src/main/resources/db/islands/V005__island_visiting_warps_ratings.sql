-- Islands module M2.4: visiting, lock, bans, warps (with categories and sign warps), visit counters, ratings and likes.
-- Days (visit_day) are epoch days in islands.yml visiting.timezone (Europe/Oslo), so "once per day" resets at local midnight.

-- Locked islands: only members and coop players can enter (staff with skyblock.island.visit.bypass too).
ALTER TABLE islands ADD COLUMN IF NOT EXISTS locked BOOLEAN NOT NULL DEFAULT FALSE;
-- Warp slots of this island: NULL = islands.yml warps.start-slots (3); raised by a later upgrade up to warps.max-slots.
ALTER TABLE islands ADD COLUMN IF NOT EXISTS warp_slots INT NULL;

-- Players banned from an island: they can't enter, teleport or pearl in; banned while inside = sent to spawn.
-- Members must be kicked first. Removed together with the island (M2.7 disband/delete/reset).
CREATE TABLE IF NOT EXISTS island_bans (
  island_id BINARY(16) NOT NULL,
  uuid BINARY(16) NOT NULL,
  banned_by BINARY(16) NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, uuid)
);

CREATE INDEX IF NOT EXISTS idx_island_bans_uuid ON island_bans (uuid);

-- Island warps (warps.start-slots per island; sign warps count too). name_key = lowercase name, unique per island.
-- category: shops | farms | parkour | builds | other. icon: item id (warps.icons). is_public = false: members and coop only.
-- sign_*: the [warp] sign of a sign warp (NULL for /is setwarp warps); breaking the sign deletes the warp.
-- visits: unique visitors per day who used the warp (members never count).
CREATE TABLE IF NOT EXISTS island_warps (
  id BIGINT NOT NULL AUTO_INCREMENT,
  island_id BINARY(16) NOT NULL,
  name VARCHAR(32) NOT NULL,
  name_key VARCHAR(32) NOT NULL,
  category VARCHAR(16) NOT NULL,
  icon VARCHAR(64) NOT NULL,
  is_public BOOLEAN NOT NULL DEFAULT TRUE,
  x DOUBLE NOT NULL,
  y DOUBLE NOT NULL,
  z DOUBLE NOT NULL,
  yaw FLOAT NOT NULL,
  pitch FLOAT NOT NULL,
  sign_x INT NULL,
  sign_y INT NULL,
  sign_z INT NULL,
  visits INT NOT NULL DEFAULT 0,
  created_by BINARY(16) NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_island_warps_name ON island_warps (island_id, name_key);

-- One row per warp, visitor and day: the warp visit counter only goes up once per visitor per day.
CREATE TABLE IF NOT EXISTS island_warp_visits (
  warp_id BIGINT NOT NULL,
  uuid BINARY(16) NOT NULL,
  visit_day INT NOT NULL,
  PRIMARY KEY (warp_id, uuid, visit_day)
);

-- Unique visitors per island and day (members and coop players never count). Kept after a disband for history.
CREATE TABLE IF NOT EXISTS island_visits (
  island_id BINARY(16) NOT NULL,
  uuid BINARY(16) NOT NULL,
  visit_day INT NOT NULL,
  season_id INT NOT NULL,
  PRIMARY KEY (island_id, uuid, visit_day)
);

CREATE INDEX IF NOT EXISTS idx_island_visits_day ON island_visits (visit_day, island_id);
CREATE INDEX IF NOT EXISTS idx_island_visits_uuid ON island_visits (uuid, island_id);

-- Ratings 1-5: one per player per island per season, changeable; never on your own island.
CREATE TABLE IF NOT EXISTS island_ratings (
  island_id BINARY(16) NOT NULL,
  uuid BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  stars INT NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, uuid, season_id)
);

CREATE INDEX IF NOT EXISTS idx_island_ratings_season ON island_ratings (season_id, island_id);

-- Likes (owner decision O7): one per PLAYER (account, not profile) per island per day, separate from ratings.
CREATE TABLE IF NOT EXISTS island_likes (
  island_id BINARY(16) NOT NULL,
  uuid BINARY(16) NOT NULL,
  visit_day INT NOT NULL,
  season_id INT NOT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, uuid, visit_day)
);

CREATE INDEX IF NOT EXISTS idx_island_likes_season ON island_likes (season_id, island_id);

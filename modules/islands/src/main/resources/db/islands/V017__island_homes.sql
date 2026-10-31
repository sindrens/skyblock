-- PHASE-5 M5.5 Homestead (owner P5-21 / R3-17): shared island homes. No personal homes (answer 247): every stored member
-- can go to these (/is home <name>); members with the island permission "homes" set and delete them. The island home
-- (islands.home_*) is the first, free home; each Homestead mastery rank allows one named home more. Removed with the
-- island (disband, staff delete, reset). home_name is lowercase a-z, 0-9 and _ (1-16 characters).
CREATE TABLE IF NOT EXISTS island_homes (
  island_id BINARY(16) NOT NULL,
  home_name VARCHAR(16) NOT NULL,
  x DOUBLE NOT NULL,
  y DOUBLE NOT NULL,
  z DOUBLE NOT NULL,
  yaw FLOAT NOT NULL,
  pitch FLOAT NOT NULL,
  set_by BINARY(16) NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, home_name)
);

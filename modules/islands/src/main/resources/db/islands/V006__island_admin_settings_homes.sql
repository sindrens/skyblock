-- Islands module ISL2 (islands round 2): Admin role, /is settings, personal homes.

-- island_members.role (V001, VARCHAR(16)) now also takes 'admin' (between leader and mod). Roles are stored as text, so
-- no column change is needed; rows with an unknown role (none expected) become plain members, the safest choice.
-- New transfers make the old leader 'admin' (MemberStore.transfer); earlier transfers stay 'mod'.
UPDATE island_members SET role = 'member' WHERE role NOT IN ('leader', 'admin', 'mod', 'member');

-- island_role_perms.role (V002) now also takes 'admin'; perm also takes 'hurt-monsters' and 'settings' (no change needed).

-- /is settings values an island changed (only differences from islands.yml flags.defaults.*).
-- flag: mob-spawning | animal-spawning | fire-spread | explosions | leaf-decay | crop-trampling | notifications |
-- visitor-fly | weather | time. value: on | off, or the choice (weather: sunny | rain | storm; time: day | noon | sunset |
-- night | real). visitor-pickup is the visitor row's 'pickup' permission (island_role_perms), never stored here.
CREATE TABLE IF NOT EXISTS island_settings (
  island_id BINARY(16) NOT NULL,
  flag VARCHAR(32) NOT NULL,
  flag_value VARCHAR(16) NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, flag)
);

-- Personal homes of stored members (/is setmyhome): homes.per-member (1) each, more from masteries later.
-- Removed when the member leaves or is removed, and with the island (disband, staff delete, reset).
CREATE TABLE IF NOT EXISTS island_member_homes (
  island_id BINARY(16) NOT NULL,
  uuid BINARY(16) NOT NULL,
  name VARCHAR(16) NOT NULL,
  x DOUBLE NOT NULL,
  y DOUBLE NOT NULL,
  z DOUBLE NOT NULL,
  yaw FLOAT NOT NULL,
  pitch FLOAT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, uuid, name)
);

CREATE INDEX IF NOT EXISTS idx_island_member_homes_uuid ON island_member_homes (uuid);

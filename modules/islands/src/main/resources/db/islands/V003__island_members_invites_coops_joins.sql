-- Islands module M2.3: members and roles, invites, coop, join limits (REVIEW A4 coop, A5 island hopping, B1 team size).
-- island_members (V001) already holds the stored roles: leader | mod | member. Coop is temporary and lives in island_coops.

-- Member Slots (REVIEW B1): NULL = islands.yml members.start-slots (4); the Phase 5 Member Slots upgrade raises it (max 8).
-- Counts every stored member including the leader; coop players never take a slot.
ALTER TABLE islands ADD COLUMN IF NOT EXISTS member_slots INT NULL;

-- Who invited a member (history for /is members and moderation). NULL for leaders and rows from before M2.3.
ALTER TABLE island_members ADD COLUMN IF NOT EXISTS invited_by BINARY(16) NULL;

-- Pending invites (one per island and target). Expire after members.invite-expiry-seconds (5 min); expired rows are
-- removed by the sweep and ignored by /is accept even before that.
CREATE TABLE IF NOT EXISTS island_invites (
  island_id BINARY(16) NOT NULL,
  target_uuid BINARY(16) NOT NULL,
  inviter_uuid BINARY(16) NOT NULL,
  created_at BIGINT NOT NULL,
  expires_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, target_uuid)
);

CREATE INDEX IF NOT EXISTS idx_island_invites_target ON island_invites (target_uuid);
CREATE INDEX IF NOT EXISTS idx_island_invites_expires ON island_invites (expires_at);

-- Coop players (temporary trust, REVIEW A4): build/break with the coop role permissions only, no XP, no bank, never a
-- member slot, never turns a solo island into a team island. Removed as soon as no leader or mod of the island is online.
CREATE TABLE IF NOT EXISTS island_coops (
  island_id BINARY(16) NOT NULL,
  uuid BINARY(16) NOT NULL,
  granted_by BINARY(16) NOT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, uuid)
);

CREATE INDEX IF NOT EXISTS idx_island_coops_uuid ON island_coops (uuid);

-- Every time a player joined an island by accepting an invite (REVIEW A5): max members.max-joins-per-season per season,
-- members.join-cooldown-hours after the last join or leave/kick. left_reason: leave | kick | removed | disband (M2.7).
-- counted = false for rows that only record a leave (e.g. a founder who transferred the island and left later): they
-- start the cooldown but never count as a join.
CREATE TABLE IF NOT EXISTS player_island_joins (
  id BIGINT NOT NULL AUTO_INCREMENT,
  uuid BINARY(16) NOT NULL,
  island_id BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  joined_at BIGINT NOT NULL,
  left_at BIGINT NULL,
  left_reason VARCHAR(16) NULL,
  counted BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_player_island_joins_uuid ON player_island_joins (uuid, season_id);
CREATE INDEX IF NOT EXISTS idx_player_island_joins_island ON player_island_joins (island_id, uuid);

-- Staff/moderation block on joining islands and being cooped (Phase P7 island punishments). until = epoch millis.
CREATE TABLE IF NOT EXISTS island_join_blocks (
  uuid BINARY(16) NOT NULL,
  until_at BIGINT NOT NULL,
  reason VARCHAR(255) NULL,
  actor VARCHAR(64) NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (uuid)
);

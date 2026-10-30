-- Island profiles (PROF). Island membership, ownership, coop trust and personal homes are stored per PROFILE KEY
-- (island_members.uuid, islands.owner_uuid, island_coops.uuid, island_member_homes.uuid, island_invites.target_uuid).
-- The first profile of every player uses the player UUID as its key, so every existing row already belongs to profile 1.
-- player_island_joins stays per PLAYER (join limits, owner P11) and now also remembers the profile that joined, so two
-- profiles of one player can never be on the same island, also not one after the other (owner P3).
-- profile_id NULL = a row from before profiles = profile 1 (key = uuid).
ALTER TABLE player_island_joins ADD COLUMN IF NOT EXISTS profile_id BINARY(16) NULL;

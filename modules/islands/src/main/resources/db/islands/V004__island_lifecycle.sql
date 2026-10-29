-- Islands module M2.7: island lifecycle (disband, staff delete, reset) and the slot clearer.
-- Islands are never deleted: deleted_at (V001) marks the end, the row keeps its season_id, owner and slot for history.
-- deleted_reason: disband (the leader, /is disband) | delete (staff, /isadmin delete) | reset (staff, /isadmin reset).
-- Only "disband" (and rows from before M2.7, NULL) starts the creation cooldown (creation.cooldown-hours).
-- deleted_by: the player who did it (NULL = console/system).
ALTER TABLE islands ADD COLUMN IF NOT EXISTS deleted_reason VARCHAR(16) NULL;
ALTER TABLE islands ADD COLUMN IF NOT EXISTS deleted_by BINARY(16) NULL;

-- island_slots.state (V001): used -> clearing (island removed, region being emptied by the clearer; keeps island_id)
-- -> free (emptied, island_id NULL, reused before a new spiral slot). Clearing slots are resumed after a restart.
-- player_island_joins.left_reason gets "disband" for everyone on a removed island; such a leave never starts the join
-- cooldown (members.join-cooldown-hours): nobody chose to leave.

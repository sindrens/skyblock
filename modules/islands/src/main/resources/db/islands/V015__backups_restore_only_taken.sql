-- SECFIX1 F-02: a backup gives back only what was really taken, once.
-- money_taken: what the disband/reset actually withdrew from the profile (NULL = nothing taken or not confirmed); a
-- restore deposits exactly this, never a "top-up" to the old balance. Kick backups never take money or items (a kick
-- removes only the membership), so they restore only the membership.
-- Items of a reset are saved as PENDING first (inventory set, items_captured FALSE); only after the inventory was emptied
-- AND the player file saved is items_captured set TRUE. A crash in between leaves a pending capture for staff to check by
-- hand, never a backup that could be restored next to items the player still has.
ALTER TABLE island_kick_backups ADD COLUMN IF NOT EXISTS money_taken BIGINT NULL;

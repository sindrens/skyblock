-- Owner answer 591 (round 2): a disband or staff reset resets every member profile of the island and deletes the
-- island bank money, but staff can restore it: each member gets a backup row in island_kick_backups (removal
-- island_disband / island_reset) and the leader's row also keeps the island bank money (NULL = none or unknown).
ALTER TABLE island_kick_backups ADD COLUMN IF NOT EXISTS bank_money BIGINT NULL;

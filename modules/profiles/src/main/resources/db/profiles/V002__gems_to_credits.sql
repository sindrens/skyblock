-- Card C1 (C1-R1-03): gems were renamed credits. Profile slots bought with gems are now 'credits' slots; they stay kept
-- at a season reset (owner P8; ProfileStore#seasonReset keeps source 'credits'). Fits source VARCHAR(8).
-- Safe to re-run (only rows that still say 'gems'). Valid on MariaDB 11.8 and H2.
UPDATE profile_slots SET source = 'credits' WHERE source = 'gems';

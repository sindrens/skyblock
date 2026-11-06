-- Owner 2 Nov 2026: staff mode takes the player's XP away and gives it back on exit (and after a crash).
ALTER TABLE staff_mode ADD COLUMN xp_level INT NULL;
ALTER TABLE staff_mode ADD COLUMN xp_progress DOUBLE NULL;

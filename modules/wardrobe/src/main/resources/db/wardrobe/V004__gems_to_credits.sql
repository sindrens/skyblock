-- Card C1 (C1-R1-03): gems were renamed credits. Set entries bought with gems ('gems') or claimed from a gem Sigil
-- ('gems_claim') get the new origins 'credits' / 'credits_claim' (VARCHAR(16)). Origins are only shown to staff.
-- Safe to re-run (only rows that still say 'gems' / 'gems_claim'). Valid on MariaDB 11.8 and H2.
UPDATE wardrobe_sets SET origin = 'credits' WHERE origin = 'gems';
UPDATE wardrobe_sets SET origin = 'credits_claim' WHERE origin = 'gems_claim';

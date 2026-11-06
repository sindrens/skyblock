-- Card C1 (C1-R1-03): gems were renamed credits; old gems are earned credits. A sanction that removed gems gives them back
-- (/psanction restore) as earned credits, the same ledger the economy migration moved the gem balances to.
-- Safe to re-run (only rows that still say 'gems'). Valid on MariaDB 11.8 and H2.
UPDATE sanctions SET currency = 'credits_earned' WHERE currency = 'gems';

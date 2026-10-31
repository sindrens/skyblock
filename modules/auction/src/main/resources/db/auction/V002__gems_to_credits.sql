-- Card C1 (C1-R1-03): gems were renamed credits; old gems are earned credits ('credits_earned', as in the economy ledger).
-- The auction house lists in money only, so this normally changes nothing; it is here so no 'gems' key is left anywhere.
-- Safe to re-run (only rows that still say 'gems'). Valid on MariaDB 11.8 and H2.
UPDATE ah_listings SET currency = 'credits_earned' WHERE currency = 'gems';

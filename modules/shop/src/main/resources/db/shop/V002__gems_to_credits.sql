-- Card C1 (C1-R1-03): gems were renamed credits; old gems are earned credits ('credits_earned', as in the economy ledger).
-- shop_purchases.currency_key is a record of what was paid; the builder shop sells for money only, so this normally
-- changes nothing. Safe to re-run (only rows that still say 'gems'). Valid on MariaDB 11.8 and H2.
UPDATE shop_purchases SET currency_key = 'credits_earned' WHERE currency_key = 'gems';

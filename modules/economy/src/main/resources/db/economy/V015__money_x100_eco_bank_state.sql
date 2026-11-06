-- Money x100 (batch 10, docs/plans/ECONOMY_SCALE.md): one table per file, so each runs exactly once.
UPDATE eco_bank_state SET min_balance = CASE WHEN min_balance > 92233720368547758 THEN 9223372036854775807 ELSE min_balance * 100 END;

-- Money x100 (batch 10, docs/plans/ECONOMY_SCALE.md): one table per file, so each runs exactly once.
UPDATE eco_accounts SET balance = CASE WHEN balance > 92233720368547758 THEN 9223372036854775807 ELSE balance * 100 END WHERE currency = 'money';

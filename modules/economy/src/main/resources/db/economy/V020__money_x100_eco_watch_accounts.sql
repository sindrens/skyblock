-- Money x100 (batch 10, docs/plans/ECONOMY_SCALE.md): one table per file, so each runs exactly once.
UPDATE eco_watch_accounts SET earned = CASE WHEN earned > 92233720368547758 THEN 9223372036854775807 ELSE earned * 100 END, spent = CASE WHEN spent > 92233720368547758 THEN 9223372036854775807 ELSE spent * 100 END WHERE currency = 'money';

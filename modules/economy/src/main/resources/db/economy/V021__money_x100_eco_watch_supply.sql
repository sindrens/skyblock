-- Money x100 (batch 10, docs/plans/ECONOMY_SCALE.md): one table per file, so each runs exactly once.
UPDATE eco_watch_supply SET account_total = CASE WHEN account_total > 92233720368547758 THEN 9223372036854775807 ELSE account_total * 100 END, note_total = CASE WHEN note_total > 92233720368547758 THEN 9223372036854775807 ELSE note_total * 100 END WHERE currency = 'money';

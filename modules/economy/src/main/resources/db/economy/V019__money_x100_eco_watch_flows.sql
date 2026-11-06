-- Money x100 (batch 10, docs/plans/ECONOMY_SCALE.md): one table per file, so each runs exactly once.
UPDATE eco_watch_flows SET amount_total = CASE WHEN amount_total > 92233720368547758 THEN 9223372036854775807 ELSE amount_total * 100 END WHERE currency = 'money';

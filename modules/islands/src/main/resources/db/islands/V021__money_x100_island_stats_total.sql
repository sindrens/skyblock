-- Money x100 (batch 10, docs/plans/ECONOMY_SCALE.md): one table per file, so each runs exactly once.
UPDATE island_stats_total SET amount = CASE WHEN amount > 92233720368547758 THEN 9223372036854775807 ELSE amount * 100 END WHERE stat_key = 'money_earned';

-- Money x100 (batch 10, docs/plans/ECONOMY_SCALE.md): one table per file, so each runs exactly once.
UPDATE crate_opens SET reward_amount = CASE WHEN reward_amount > 92233720368547758 THEN 9223372036854775807 ELSE reward_amount * 100 END WHERE reward_kind = 'money';

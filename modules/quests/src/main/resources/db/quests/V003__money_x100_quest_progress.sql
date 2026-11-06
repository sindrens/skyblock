-- Money x100 (batch 10, docs/plans/ECONOMY_SCALE.md): one table per file, so each runs exactly once.
UPDATE quest_progress SET reward_money = CASE WHEN reward_money > 92233720368547758 THEN 9223372036854775807 ELSE reward_money * 100 END WHERE quest_type <> 'sell';

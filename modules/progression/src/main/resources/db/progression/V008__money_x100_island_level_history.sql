-- Money x100 (batch 10, docs/plans/ECONOMY_SCALE.md): one table per file, so each runs exactly once.
UPDATE island_level_history SET block_worth = CASE WHEN block_worth > 92233720368547758 THEN 9223372036854775807 ELSE block_worth * 100 END;

-- Money x100 (batch 10, docs/plans/ECONOMY_SCALE.md): one table per file, so each runs exactly once.
UPDATE island_block_worth SET blocks_worth = CASE WHEN blocks_worth > 92233720368547758 THEN 9223372036854775807 ELSE blocks_worth * 100 END, spawners_worth = CASE WHEN spawners_worth > 92233720368547758 THEN 9223372036854775807 ELSE spawners_worth * 100 END;

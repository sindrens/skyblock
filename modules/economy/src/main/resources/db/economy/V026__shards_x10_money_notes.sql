-- Shards x10 (owner 7 Nov 2026, docs/plans/SHARDS_X10_BREAKTHROUGHS.md): bigger numbers, same value. One table per file, so each runs once.
UPDATE money_notes SET amount = CASE WHEN amount > 922337203685477580 THEN 9223372036854775807 ELSE amount * 10 END WHERE currency IN ('shards', 'mining_shards', 'fishing_shards', 'combat_shards');

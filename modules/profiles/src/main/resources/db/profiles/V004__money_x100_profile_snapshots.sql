-- Money x100 (batch 10, docs/plans/ECONOMY_SCALE.md): one table per file, so each runs exactly once.
UPDATE profile_snapshots SET money = CASE WHEN money > 92233720368547758 THEN 9223372036854775807 ELSE money * 100 END;

-- Money x100 (batch 10, docs/plans/ECONOMY_SCALE.md): one table per file, so each runs exactly once.
UPDATE eco_send_unlocked SET earned = CASE WHEN earned > 92233720368547758 THEN 9223372036854775807 ELSE earned * 100 END;

-- TQ6: everyone who played before the tutorial existed counts as finished ("legacy"): no locks, no reward.
-- Runs exactly once, when the tutorial module first starts on a server. Players who join later start as "new".
INSERT INTO tutorial_progress (player_uuid, progress_state, step_id, step_started_at, started_at, finished_at, replays, flow_version, pack_given, rewarded)
SELECT p.uuid, 'legacy', NULL, 0, NULL, 0, 0, 1, TRUE, TRUE
FROM players p
WHERE NOT EXISTS (SELECT 1 FROM tutorial_progress t WHERE t.player_uuid = p.uuid);

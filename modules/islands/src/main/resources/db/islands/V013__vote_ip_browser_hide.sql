-- Owner answer 292 (round 2): at most 3 accounts from the same address may rate or like the same island. ip_hash =
-- SHA-256 hex of the voter's address at the vote (never the address itself); NULL for votes from before V013.
ALTER TABLE island_ratings ADD COLUMN IF NOT EXISTS ip_hash VARCHAR(64) NULL;
ALTER TABLE island_likes ADD COLUMN IF NOT EXISTS ip_hash VARCHAR(64) NULL;

CREATE INDEX IF NOT EXISTS idx_island_ratings_ip ON island_ratings (island_id, ip_hash);

CREATE INDEX IF NOT EXISTS idx_island_likes_ip ON island_likes (island_id, ip_hash);

-- Owner answer 293: staff hide an island from the visit browser with a note (/isadmin hide). The island can still be
-- visited by name; only the browser leaves it out.
ALTER TABLE islands ADD COLUMN IF NOT EXISTS browser_hidden BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE islands ADD COLUMN IF NOT EXISTS browser_hidden_note VARCHAR(200) NULL;
ALTER TABLE islands ADD COLUMN IF NOT EXISTS browser_hidden_by VARCHAR(32) NULL;

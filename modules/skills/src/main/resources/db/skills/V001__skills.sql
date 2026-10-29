-- Skill XP per player, skill and season (skills module, M4.5). xp = total XP (authoritative), level = derived from the
-- level curve in skills.yml (stored for leaderboards and queries; recomputed when the curve changes).
CREATE TABLE IF NOT EXISTS skills (
  player BINARY(16) NOT NULL,
  season_id INT NOT NULL,
  skill VARCHAR(16) NOT NULL,
  xp DOUBLE NOT NULL DEFAULT 0,
  level INT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (player, season_id, skill)
);

CREATE INDEX IF NOT EXISTS idx_skills_rank ON skills (season_id, skill, xp);

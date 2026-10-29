-- Islands module M2.2: role permissions per island (the matrix GUI comes in M2.3, visitor settings in M2.5).
-- Defaults per role live in islands.yml (permissions.defaults.*): leader = everything (fixed), mod/member = nearly
-- everything, coop = building and farming, visitor = nothing (walk and look only). This table only holds the
-- permissions an island changed, so a changed default reaches every island that never touched it.
-- role: mod | member | coop | visitor (the leader is never stored). perm: IslandPerm key (break, place, interact,
-- containers, redstone, animals, villagers, pickup, drop, armor-stands, vehicles, fire, ender-pearls, spawners).
CREATE TABLE IF NOT EXISTS island_role_perms (
  island_id BINARY(16) NOT NULL,
  role VARCHAR(16) NOT NULL,
  perm VARCHAR(32) NOT NULL,
  allowed BOOLEAN NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, role, perm)
);

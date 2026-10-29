# Islands module (`modules/islands`): M2.1, M2.2

Grid, creation, home and per-player borders. Config `islands.yml`, texts `messages/islands.yml`, schematics in
`plugins/PhoenixSkyblock/schematics/` (`<creation.schematic>.schem` + optional `<name>.yml` metadata with `home`, `chest`,
`sign` offsets and `home.yaw`; missing file or no FastAsyncWorldEdit = the built-in "Ember Meadow" placeholder islet).

| Command | Description | Permission | Status |
|---|---|---|---|
| `/is` (`/island`) | No island yet: "Create your island" confirm menu, then the starter islet is built (FAWE async or placeholder), the starter chest filled and you are teleported with a title, sound and your first objective. Has an island: teleport home | `skyblock.island` (everyone) | ✅ |
| `/is home` | Teleport to your island home (safe-spot check: unsafe homes move to the nearest safe spot; warmup `home.warmup-seconds`) | `skyblock.island` | ✅ |
| `/is go` | Same as `/is home` for now (member islands come with M2.3) | `skyblock.island` | ✅ |
| `/is sethome` | Set the home where you stand: leader only (role permission in M2.3), inside your border, safe spot, Y 0-256 | `skyblock.island` | ✅ |
| `/is bypass` | Staff: toggle ignoring island protection (build, containers, everything; also walk out of borders). Off by default and again on quit; every toggle is logged (`staff`: `island_bypass_on` / `island_bypass_off`) | `skyblock.island.bypass` (admin) | ✅ |

Rules: one island per player (database-enforced), creation cooldown after a disband `creation.cooldown-hours` (24 h),
start size 3x3 chunks (48x48), grows by chunk rings up to 15x15 (`IslandService.setSize`, Phase 5 / M2.7). Only players
on an island see its border. Void falls return you to the island home without damage.
Everything that changes data is logged (category `island`: `island_create`, `island_build`, `island_build_failed`,
`island_sethome`, `island_size`, `island_rescue_block`, `island_perm`).

## Protection (M2.2)
Inside an island every action needs the permission of your role: **Leader** (always everything), **Mod**, **Member**,
**Coop**, or **visitor** (not on the island). Permissions: `break`, `place`, `interact` (doors, trapdoors, gates,
buttons, levers, pressure plates, bells, beds, workstations), `containers`, `redstone` (repeaters, comparators, daylight
detectors, note blocks), `animals`, `villagers` (trade), `pickup`, `drop`, `armor-stands` (also item frames, paintings),
`vehicles`, `fire` (flint and steel, fire charges), `ender-pearls` (pearls and chorus fruit onto the island), `spawners`.
Defaults in `islands.yml → permissions.defaults.*`: Mod everything; Member everything except spawners; Coop building,
farming, containers and redstone but no fire/spawners; visitors nothing (walk and look). Per-island changes are stored in
`island_role_perms` (only differences from the defaults); the permission matrix GUI comes in M2.3, visitor settings in M2.5.

- Nothing can be built outside an island border; blocks only between `build.min-y` and `build.max-y` (Y 0-256).
- Cross-border: liquids, pistons, falling blocks, explosions (TNT, creepers, crystals, beds, anchors), tree/mushroom
  growth, fire, spreading (grass, vines, sculk …), forming and dispensers never change a block outside the island they
  come from (or the void between slots). Explosions can't hurt entities in another island.
- Mobs, animals, items and empty vehicles pushed out of a border are put back (sweep every
  `protection.return-entities.interval-ticks` of the chunk ring around occupied islands); a player inside a border can't
  be pushed or walk out of it (`protection.keep-players-inside`; spectator/creative and bypass excepted).
- PvP is off in the islands world (`protection.pvp`): hits, arrows, harmful potions, fishing rods, fire.
- Nether and end portals can't be built or used in the islands world (`protection.disable-portals`).
- Denials show a short action-bar message, at most one per `protection.message-cooldown-ms` (stepping on plates and item
  pickup are silent).

Service for other modules: `core.services().optional(IslandService.class)` (island at location, island of player,
members/roles, size, home, slot bounds, `teleportHome`, `setSize`). Placeholders: `<ph:island_name>`, `<ph:island_level>`
(`-` until Phase 5), `<ph:island_rank>` (empty until the island top exists).

Permission definitions: `modules/islands/src/main/resources/permissions/islands.yml`. Rank grants: `server/permissions/islands.txt`.

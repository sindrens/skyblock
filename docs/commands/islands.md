# Islands module (`modules/islands`): M2.1

Grid, creation, home and per-player borders. Config `islands.yml`, texts `messages/islands.yml`, schematics in
`plugins/PhoenixSkyblock/schematics/` (`<creation.schematic>.schem` + optional `<name>.yml` metadata with `home`, `chest`,
`sign` offsets and `home.yaw`; missing file or no FastAsyncWorldEdit = the built-in "Ember Meadow" placeholder islet).

| Command | Description | Permission | Status |
|---|---|---|---|
| `/is` (`/island`) | No island yet: "Create your island" confirm menu, then the starter islet is built (FAWE async or placeholder), the starter chest filled and you are teleported with a title, sound and your first objective. Has an island: teleport home | `skyblock.island` (everyone) | ✅ |
| `/is home` | Teleport to your island home (safe-spot check: unsafe homes move to the nearest safe spot; warmup `home.warmup-seconds`) | `skyblock.island` | ✅ |
| `/is go` | Same as `/is home` for now (member islands come with M2.3) | `skyblock.island` | ✅ |
| `/is sethome` | Set the home where you stand: leader only (role permission in M2.3), inside your border, safe spot, Y 0-256 | `skyblock.island` | ✅ |

Rules: one island per player (database-enforced), creation cooldown after a disband `creation.cooldown-hours` (24 h),
start size 3x3 chunks (48x48), grows by chunk rings up to 15x15 (`IslandService.setSize`, Phase 5 / M2.7). Only players
on an island see its border. Basic protection until M2.2: only members break/place/use buckets inside the border, nobody
builds outside a border; staff bypass `skyblock.island.bypass`. Void falls return you to the island home without damage.
Everything that changes data is logged (category `island`: `island_create`, `island_build`, `island_build_failed`,
`island_sethome`, `island_size`, `island_rescue_block`).

Service for other modules: `core.services().optional(IslandService.class)` (island at location, island of player,
members/roles, size, home, slot bounds, `teleportHome`, `setSize`). Placeholders: `<ph:island_name>`, `<ph:island_level>`
(`-` until Phase 5), `<ph:island_rank>` (empty until the island top exists).

Permission definitions: `modules/islands/src/main/resources/permissions/islands.yml`. Rank grants: `server/permissions/islands.txt`.

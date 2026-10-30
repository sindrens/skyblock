# Worlds (`modules/worlds`): worlds and finished builds

`/skyblock worlds` and `/skyblock tp` are core commands (see [core](core.md)). The worlds module adds `/skyblock builds`:
the finished builds from the builders (`BUILDER_BRIEF.md` §3.5/3.6: one Sponge `.schem` + `.yml` per build, plus
`depths_layout.yml`), tracked in `server/builds/` and deployed to `<server>/builds/` (`worlds.yml → builds.folder`).

| Command | Description | Permission | Status |
|---|---|---|---|
| `/skyblock builds` / `/skyblock builds list` | Every build with world, block count, state (new / pasted / applied) and error count; Depths layout status | `skyblock.admin.builds` | ✅ |
| `/skyblock builds info <id>` | Schematic, world, origin, paste area + chunks, blocks, air/biomes, player spawn, spots and regions per category, Depths role, pasted/applied by whom, every problem | `skyblock.admin.builds` | ✅ |
| `/skyblock builds paste <id> [confirm]` | Preview (world, origin, area, blocks, backup reminder) and [Confirm]; then loads the chunks, pastes with FastAsyncWorldEdit at the yml origin (`paste_air`, `paste_biomes` from the yml, no entities, no undo), progress + time, audit `world/build_paste`. Only valid builds of non-template worlds are suggested | `skyblock.admin.builds` | ✅ |
| `/skyblock builds paste-all [confirm]` | Preview of the order (`builds.paste-order`: spawn base, districts, Depths gate, zones), then pastes one after the other, stops at the first failure | `skyblock.admin.builds` | ✅ |
| `/skyblock builds apply <id>` | Wires the metadata: spots/regions → `build-points.yml` (BuildPoints API), `player_spawn` → the world's spawn (worlds.yml; `/spawn` and first join), Depths builds → `zones.yml` merged with the layout (backup `zones.yml.bak-<time>`, depths reloads), the island template → the `/is create` schematic (`schematics/<id>.schem`, `islands.yml → creation.schematic`). Audit `world/build_apply` | `skyblock.admin.builds` | ✅ |

The confirm step works from the console too (type the command with `confirm` within `builds.confirm-seconds`).

Safety: a build is pasted only into the world its yml names; a missing/unreadable schematic, bounds outside the
schematic's area, a newer Minecraft data version, or an area outside the world border are errors (no paste, no apply);
builds of `builds.template-worlds` (islands, outposts) are never pasted. One paste at a time; FAWE works off the main
thread. Back up the world folder before pasting.

Permission: `skyblock.admin.builds` (default op; LuckPerms: admin, owner inherits; `server/permissions/worlds.txt`).

## For developers: BuildPoints

`core.services().optional(BuildPoints.class)` (common `net.skybornmc.skyblock.common.builds`) returns the spots of
every applied build: `points("npcs")`, `point("npcs", "bank")`, `points(build, "holograms")`, `regions("portals")`,
`region("regions", "spawn_protected")`. Categories are the yml list names (npcs, holograms, leaderboards, crates,
particles, tutorial, viewpoints, treasure_spots, secrets, signs, key_spots, arrival, mob_spawns, ambient_spawns...).
Extra keys (facing, size, text, type, hint...) are in `extra`.

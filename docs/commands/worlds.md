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

## Build portals (no command)

Portals have no command: players step into a portal trigger of an applied build (`portals:` in the build yml, stored
in `build-points.yml` by `/skyblock builds apply`, which now prints each portal and its target). Settings:
`worlds.yml → portals.*`, texts: `messages/worlds.yml → portals.*`.

| Portal (build) | Target | What happens |
|---|---|---|
| `to_island` (spawn_gatehouse_v1) | `island` | **The island portal** (owner answer 159): no island on the active profile → the island is created right away (normal `/is create` flow, starter schematic, join-block check, no confirm menu, "Creating your island…" title) and the player is brought there; with an island → instant teleport home. Fires `IslandPortalEnteredEvent(firstTime)` on arrival (the tutorial hooks it). |
| `to_depths` (spawn_gatehouse_v1) | `depths` | Lands on the Depths arrival pad (landing of the Depths `to_spawn` portal). |
| `to_spawn` (depths_gate_v1) | `spawn` | Lands in front of the gatehouse Depths portal (its `landing_tp`, facing the gate passage). |
| `to_warzone` (spawn_gatehouse_v1) | `warzone` | Refused ("isn't built yet") until a warzone build is applied (`portals.need-build`); then the warzone spawn. |
| `to_outposts`, `to_events` (spawn_gatehouse_v1) | `outposts`, `events` | Closed (`status: closed`, iron bars): "This portal isn't open yet". Opening the gate + `portals.force-open` still says not open until those features exist. |

Rules:
- Instant. A combat-tagged player, or anyone in the warzone, waits `portals.combat-delay-seconds` (3) inside the
  portal (owner principle 1); leaving the portal cancels it. The teleport bypass (`skyblock.staff.teleport.bypass`) skips the wait.
- Frozen players can't use portals. Refused players are pushed back out (`portals.push-back`), one message per
  `portals.refusal-cooldown-millis`.
- After a portal teleport portals ignore the player for `portals.grace-millis` (1 s), so nobody bounces back.
- Warzone (owner answer 413): teleports into the warzone from another world (`/spawn`, `/tpa`, homes...) are refused
  unless they are the warzone portal's own; staff `/tp` (teleport bypass) still works (`portals.warzone-portal-only`).
- Particles: the build's `particles` entry `portal_<id>` (or the portal's `text` id, or a `portal*` particle at the
  trigger) puffs every `portals.particle-ticks`; `enabled: false` particles stay off.
- Targets: `island`, `spawn`, `depths`, `warzone` (`portals.world-targets`), `world:<name>`, `point:<category>:<id>`
  (a build point). A world target lands in front of that world's portal leading back, else at the world spawn.
- Overrides without editing the build: `portals.island-portal` (which portal is the island portal; empty = target
  `island`, or an id containing `island`/`start`), `portals.target-overrides` (`portal=target`), `portals.force-open`,
  `portals.disabled`. Changes apply with `/skyblock reload worlds`; a new `/skyblock builds apply` is picked up at once.

## For developers: BuildPoints

`core.services().optional(BuildPoints.class)` (common `net.skybornmc.skyblock.common.builds`) returns the spots of
every applied build: `points("npcs")`, `point("npcs", "bank")`, `points(build, "holograms")`, `regions("portals")`,
`region("regions", "spawn_protected")`. Categories are the yml list names (npcs, holograms, leaderboards, crates,
particles, tutorial, viewpoints, treasure_spots, secrets, signs, key_spots, arrival, mob_spawns, ambient_spawns...).
Extra keys (facing, size, text, type, hint...) are in `extra`.

Island portal hook: `IslandService.enterFromPortal(player, firstTime -> ...)` (islands module) and the Bukkit event
`net.skybornmc.skyblock.common.island.IslandPortalEnteredEvent` (`firstTime()`, `portal()`), fired by the worlds module
once the player is on the island. Portal teleports use `TeleportReason.PORTAL` (always instant, no teleport.yml section).

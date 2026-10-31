# NPCs and holograms (`modules/npcs`)

NPCs, text holograms and leaderboard holograms at the points of the applied builds (NPCHOLO). Positions come from
`build-points.yml` through the worlds module's `BuildPoints` service (written by `/skyblock builds apply`): lists
`npcs`, `holograms`, `leaderboards` of each build's metadata (`BUILDER_BRIEF.md` §3.6). Settings: `npcs.yml` (all editable
in `/admin`); texts: `messages/npcs.yml`. Reload (re-reads the points and respawns everything): `/skyblock reload npcs`.
Help: `/help npcs`. No new player command: everything is a click (right or left; a tap on Bedrock).

| Command | Description | Permission | Status |
|---|---|---|---|
| `/skyblock npcs` / `/skyblock npcs list` | Players: menu of every NPC, hologram and leaderboard (kind, id, role, position, spawned or chunk not loaded); click one to teleport in front of it. Console: the same as a chat list. Also in the `/staff` hub (NPCs & Holograms) | `skyblock.admin.npcs` | ✅ |
| `/skyblock npcs tp <id>` | Teleport in front of an NPC, hologram or leaderboard (ids tab-complete; ids used by several builds are `id@build`, e.g. `mine_foreman@depths_mine_t01_coal_v1`). Players only. Audit `staff/npc_teleport` | `skyblock.admin.npcs` | ✅ |
| `/skyblock npcs respawn` | Re-reads the build points and `npcs.yml`, respawns everything and refreshes the leaderboards (after applying a build). Console-safe. Audit `config/npcs_respawn` | `skyblock.admin.npcs` | ✅ |

## NPCs
- **Body:** a Paper mannequin (player-shaped, `npcs.body: mannequin`) or a villager without AI (`villager`). No Citizens. Invulnerable, silent, no gravity, not collidable, never saved (`persistent=false`): spawned when the chunk's entities load, removed when they unload and on disable; left-overs with the module's tag are removed on load. Optional skin (`npcs.skins`, a Minecraft name, default none) and held item (`npcs.held-items`).
- **Position:** the pad's feet cell from the metadata, centred; if that block is solid (the pad itself was given) the NPC stands on top of it. Facing = the metadata `yaw`. Heads turn to the nearest player within `npcs.look-radius` every `npcs.look-ticks` (no per-tick work; a rotation is only sent when it changed by 3° or more).
- **Name text:** a text display above the head: name (`npc-names.<id>`, else `roles.<role>.name`) + one hint line (`Click` / `Click for help`).
- **Role:** the metadata's `role` key, else `npcs.roles` (`id: role`), else the id. Defaults: quartermaster/blacksmith/depths_forge → forge, help → info, ah → auction, depths_sell → sell, depths_guide → dungeon, mine_foreman → foreman.
- **Click** (`npcs.actions`): `role: command <cmd>` runs the command as the player (the command checks its own permission); sneak + click shows the role's help instead (`npcs.sneak-for-help`). `role: help [cmd]` shows the help with an `[Open]` button for the command. Roles without a line show their help. Right-click and hitting both act (`npcs.left-click-acts`, Bedrock touch = tap). UX3 root cause of "only left-click works": Paper 26.x fires only `PlayerInteractAtEntityEvent` for a right-click (one interact packet with the hit position), and the listener used to wait for a plain `PlayerInteractEntityEvent` that never comes; now every main-hand interact acts (off-hand copy ignored). One click per `npcs.click-cooldown-ms` (removes the second packet of the same click). No trading, no damage, no fire, mobs ignore them.
- **Help:** `roles.<role>.help` (a few short lines; owner answer 44: explained where it is, lores stay clean). Java: chat lines + clickable `[Open]`; Bedrock: a 3-row menu with the text on a book and a big Open button.

| Role | Action | NPCs (default builds) |
|---|---|---|
| guide | help + [Open] `/menu` | guide |
| info | help + [Open] `/help` | help |
| forge | `/forge` | quartermaster, blacksmith, depths_forge |
| enchanter | `/enchanter` | enchanter |
| bank / shop / sell | `/bank` / `/shop` / `/sell` | bank, shop, sell, depths_sell |
| auction | `/ah` | ah |
| dungeon | `/dungeon` | depths_guide |
| foreman | help + [Open] `/dungeon` | mine_foreman (every mine tier) |
| wardrobe | `/wardrobe` | (none yet) |
| crates, black_market, quests, daily, vote | help (coming soon) | black_market, quests, daily, vote |

## Holograms
- Text displays at each `holograms` point: text `holograms.<id>` in messages/npcs.yml (a list = lines), else the metadata's `text` (made Bedrock-safe: `→` becomes `»`), else not shown (logged once). Ids in `holograms.skip` are left to other modules (default: `miniboss_timer`, `event_timer_board`, `rush_banner`).
- Boards with a `facing` are fixed to the wall (pulled `holograms.wall-offset` from the point toward the wall); without one they turn to the viewer. The text is scaled to fill `holograms.fill` of the board's `size` and centred vertically (`holograms.line-height`, `min-scale`, `max-scale`). `holograms.yaw-offset: 180` if boards show their back.
- **Clickable:** holograms (and leaderboards) with `hologram-help.<id>` get an invisible Interaction box (at most `holograms.click-max-width` wide) that shows that help.

## Leaderboards
- `leaderboards` points show the top `leaderboards.size` (10) with V2 colors: #1 `top1`, #2 `top2`, #3 `top3`, then `place`; empty places are padded so a board never changes height. Title `leaderboard.titles.<id>`, footer "Updates every 5 min".
- Data (`leaderboards.sources`, `id: source`): `money` / `shards` / `credits` (`EconomyService#top`; crystals are never public and show an empty board, C1-R1-28; the old `gems` = credits, the `/baltop` query: best live profile per player), `skill <type>` (`SkillService#top`, level shown, ranked by XP), `island <stat> [period] [board]` (`IslandStatsService#top`), `none` (Coming soon). Defaults: the five skills on their boards; island top solo/team, weekly gains and boss damage are Coming soon until island value (Phase 5) exists; `baltop`/`money` points show money.
- Refreshed every `leaderboards.refresh-minutes` (5): one database read per distinct source, off the main thread; texts are set on the main thread.

## For other modules
- `NpcDirectory` service (common `npc`): `npcs()`, `npcLocation(id)`, `npcByRole(role)`, `hologramLocation(id)`, `npcId(entity)`. The tutorial points its arrow at an NPC with it.
- `NpcClickEvent` (cancellable, before the action/help runs): the tutorial or quests can replace an NPC's click.
- These entities live in spawn and the Depths only, so they never count toward island entity limits (`stacking` counts on islands only).

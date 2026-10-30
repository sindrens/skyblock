# Performance and Lag Audit

Date: 2026-10-30. Target: 300 players on one Paper 26.3 server, Java and Bedrock (Geyser).
Scope: lag spikes, redstone, farms, config, network. Written as a read-only audit; QUICK1 applied the safe parts (section 0).

## How to read this

- Every number here is an estimate from reading code. Nothing was measured.
- "Verify" means check with spark before you spend time on it.
- Paper config key names were checked against the generated files in `server/`. Keys marked "verify key" were not seen in the repo.
- Impact levels: Critical = can stall or crash the server. High = causes visible lag spikes. Medium = adds steady cost. Low = small or only a risk later.
- Owner decisions respected: no block, hopper, piston or observer count limits (O1, O2, WR2). Only lag-machine detection. Nether and End stay off. Villager trading stays on.

Known fact from the logs: the slow-task warnings (`achievements-load`, `achievements-unlock`, `moderation:ack`, `depths-combat`, `trade:deliver`) all come from the test VPS (2 vCPU, 4 GB). Some are cold JIT. Re-test on production hardware before judging them.

---

## 0. QUICK1 status (30 Oct): what is applied

Config keys (tracked files under `server/`, reasons in `server/CONFIG_NOTES.md`):

| File | Key | Value | Status |
|---|---|---|---|
| paper-global.yml | `misc.max-joins-per-tick` | 3 -> 2 | APPLIED |
| paper-global.yml | `misc.region-file-cache-size` | 256 -> 512 | APPLIED |
| paper-global.yml | `player-auto-save.rate` / `max-per-tick` | -1 / -1 -> 600 / 10 | APPLIED |
| paper-global.yml | `block-updates.disable-tripwire-updates` | true | APPLIED |
| paper-global.yml | `block-updates.disable-chorus-plant-updates` / `disable-mushroom-block-updates` | true / true | APPLIED |
| paper-global.yml | `spam-limiter.tab-spam-limit` | 500 -> 200 | APPLIED |
| paper-world-defaults.yml | `misc.update-pathfinding-on-block-update` | true -> false | APPLIED |
| paper-world-defaults.yml | `fixes.falling-block-height-nerf` / `tnt-entity-height-nerf` | disabled -> 320 | APPLIED |
| paper-world-defaults.yml | `fixes.prevent-tnt-from-moving-in-water` | true | APPLIED |
| paper-world-defaults.yml | `chunks.prevent-moving-into-unloaded-chunks` | true | APPLIED |
| paper-world-defaults.yml | `tick-rates.behavior.villager.acquirepoi` | 60 (new) | APPLIED |
| paper-world-defaults.yml | `tick-rates.sensor.villager.nearestbedsensor` / `villagerbabiessensor` | 80 / 40 (new) | APPLIED |
| paper-world-defaults.yml | `hopper.disable-move-event` | false | KEPT (our listener is gone, see P-13) |
| start.sh / setup-vps.sh | JVM heap and GC flags | unchanged | NOT CHANGED: hardware unknown (P-04) |
| bukkit.yml | `ticks-per.monster-spawns` | 10 | NOT CHANGED: changes mob farms, waits for Q2 |
| spigot.yml / server.properties | view, simulation, tracking ranges | unchanged | NOT CHANGED: owner question (per-world distances) |
| plugin core.yml | DB `pool-size` | 10 | NOT CHANGED: hardware unknown (P-26) |

Code: P-05, P-10, P-13, P-20, P-34 FIXED; P-03, P-12 partly (see the rows below).

PERF2 (30 Oct): the slow tasks `stacking-menu-refresh`, `island-stats-flush`, `crates:admin` and `wardrobe-trail` are
fixed (root causes in the PERF2 report and the rows). FIXED: P-06, P-12, P-18, P-19, P-25, P-35, P-36, P-37. PARTLY
FIXED: P-08, P-17, P-21, P-22, P-24, P-29. Left on purpose: P-14 (skipping view-only chunks changes when far crops grow:
owner question PERF2 a), P-23 and P-28 (not safe local fixes without testing on the server), P-38 (the shutdown waits
protect data), P-01, P-02, P-04, P-07, P-09, P-11, P-15, P-16, P-26, P-31 (owner decisions). The slow-task warnings named on the
test server are handled: `island-showcase-stats` (reads start async), `npcs:board-skill` / `board-money` (formatted on the
database thread), `achievements-load` / `achievements-unlock`, `moderation:ack`, `depths-combat`.

## 1. Top 15 risks, ranked

1. **P-01 Villagers have no AI cap.** 20 per island x 150 islands = 3,000 active brains. Paper has no lobotomy. This alone can break 20 TPS.
2. **P-02 No lag-machine guard exists.** The owner promised detection. Nothing is built. One clock can freeze a chunk, and a chunk freezes the server.
3. **P-03 Join and restart storm.** 300 players log in within a minute after a restart. Five pre-login handlers wait on the DB one after another. 40 join handlers run on the main thread.
4. **P-04 Heap and GC setup is wrong for production.** The setup script gives the JVM RAM minus 1.5 GB (about 62 GB on a 64 GB box). The flags are for small heaps. There is no GC log.
5. **P-05 Chat renders a full message per viewer.** 300 viewers means 600 MiniMessage parses and 300 regex compiles per public message.
6. **P-06 DupeDetector scans every inventory.** A full scan runs every 60 s, and again on join, inventory open and close. It builds a string for every slot.
7. **P-07 Tab and nametag teams grow as N squared.** Every player has their own scoreboard. Every board gets a team for every player. About 90,000 team jobs at 300 players.
8. **P-08 HUD re-parses 15 lines per player per second.** About 4,800 MiniMessage parses per second with no cache.
9. **P-09 Nightly backup runs on the same box.** `save-all flush`, then tar of the whole server, then mysqldump at 04:30. Disk and CPU spike at once.
10. **P-10 Depths combat spawn scan.** `standY()` walks down block by block, up to 8 attempts per spawn. This is the `depths-combat` warning.
11. **P-11 Depths ambient mobs are real AI mobs.** Up to about 600 mobs with full AI, hidden per viewer.
12. **P-12 Many tasks fire on the same tick.** TierGates, ArmorEffects, DupeDetector, achievements and others all loop over every player on ticks 20, 40, 200, 600 and 1200.
13. **P-13 Hopper event listener on every transfer.** `MenuService.onMove` fires for every hopper move in every island. It does nothing useful.
14. **P-14 Crop engine works on chunks that do not tick.** It also rewrites PDC data on every plant.
15. **P-15 Natural spawning is on in the islands world.** The spawn loop runs for every ticking chunk, then most spawns are cancelled by a flag.

Next in line: view and simulation distance, void chunk files on disk, Bedrock chunk translation (P-16).

---

## 2. Findings table

Effort: S = under half a day. M = 1 to 3 days. L = more than 3 days.

### Critical

| id | area | problem | where | impact at 300 players | fix | effort |
|---|---|---|---|---|---|---|
| P-01 | Villagers | No cap on active villager AI. 20 villagers per island is allowed. Trading is allowed. Paper has no lobotomy. Pathfinding, POI search and sensors run for each one. | Owner limit in `docs/DECISIONS_2026-10-30.md`; no code exists | Up to 3,000+ brains. Could use 20 to 60 ms per tick. Biggest theoretical risk. | Add a villager pause. When a villager is enclosed or idle, call `setAware(false)`. Wake it when a player opens its trade. Call `restock()` on a timer. Add `tick-rates` villager keys (see section 3). Set `update-pathfinding-on-block-update: false`. Test that trading still works. | M |
| P-02 | Redstone | No redstone, hopper or piston logic exists. Only the `redstone` role permission and the border piston check exist. | grep found nothing. `BorderProtection` has piston and flow checks only. | One fast clock or a piston loop can take over a chunk. Alternate Current helps but does not stop abuse. | Build a `lagguard` module. Count redstone, piston and dispenser events per chunk. Warn, then freeze, then break the top part. Add a heat meter in the `/is` menu. See section 4. | L |
| P-03 | Login | Five `AsyncPlayerPreLoginEvent` handlers wait on the DB one after another. Pool is 10 connections. 40 `PlayerJoinEvent` handlers run on the main thread. | `PlayerService.java:149`, `SkillManager.java:425`, `ModerationModule.java:1786` and `1807`, `ProfileServiceImpl.java:174`, `DatabaseConfig.java:38-43` | After a restart, 300 logins in about a minute. Login time can reach tens of seconds and the main thread can spike on joins. | Load all profile data for a player in one DB call. Run the pre-login handlers in parallel with one `CompletableFuture.allOf`. Raise pool to 20. Set `max-joins-per-tick: 2`. Spread the join handlers (HUD, tab, achievements) over several ticks with a join queue. Add a login queue message. | M **PARTLY FIXED (QUICK1):** moderation ban/mute/alt reads run in parallel; skills reads the profile key from the pre-login account (one DB trip less); `max-joins-per-tick: 2` APPLIED. Pool size, join queue: open. **PERF2:** join handlers audited: the heavy ones are already deferred (bank, sell refunds, shop, auction, notes, tools, visits) and `max-joins-per-tick: 2` spreads them; the dupe scan on join now reads only the joining player. The rest must run at join (profile attach, freeze, vanish, staff mode, menu-item cleanup) or is a single async DB call. Join queue: open (owner Q12). |
| P-04 | JVM | Heap is RAM minus 1.5 GB. `start.sh` uses small-heap Aikar flags. No GC log. No heap dump on OOM. | `scripts/setup-vps.sh:11-14`, `server/start.sh:16-22` | A 62 GB heap gives very long G1 pauses and starves the OS page cache and MariaDB. Pauses show up as lag spikes with no cause in the logs. | Set `MEMORY=20G` by hand. Use the large-heap flags in section 3. Add GC log and heap dump flags. Add `LimitNOFILE`. | S **NOT CHANGED (QUICK1):** hardware unknown; heap and GC flags wait for the owner (OPEN_QUESTIONS QUICK1 e). |

### High

| id | area | problem | where | impact at 300 players | fix | effort |
|---|---|---|---|---|---|---|
| P-05 | Chat | The renderer is viewer-aware. Per viewer it builds a hover card (about 10 placeholders), parses the format, calls `highlight()` which compiles a Pattern, and `mentions()` compiles a Pattern per online player. | `ChatModule.java:559-585`, `:607`, `:611`, `:622` | About 300 viewers x 2 parses per public message. At 5 messages per second this is 3,000 parses per second. Verify with spark whether Paper runs the renderer on the main thread. | Render once per viewer class (Java or Bedrock). Cache the hover card per sender. Precompile Patterns. Run mention matching once per message, not per viewer. | M **FIXED (QUICK1):** mention Patterns compiled once per name (dropped on quit), name + hover card built once per viewer kind (Java/Bedrock) per message. Message parse per viewer remains (needed for Bedrock colors and the viewer's own mention highlight). |
| P-06 | Items | Full scan of every online player's inventory, ender chest and open container every 60 s. Also on join, inventory open and inventory close (limited to once per 40 ticks). `collect()` builds a string for every slot, even empty ones. | `DupeDetector.java:113-173`, `ItemsConfig.java:13,18` | About 20,000 string concats per full scan. Spikes at 60 s and during menu use. | Scan only players marked dirty. Skip empty slots. Build the string only when a duplicate is found. Spread players over the 60 s with hash buckets. | M **FIXED (PERF2):** rolling scan: each player is read once per `scan-seconds` (one slice per second); a join or inventory open/close reads only that player; remembered copies count only while the item is still in that slot (no false alarms); text is built only for unique items; shared containers compare by container. |
| P-07 | Tab | Each player has a personal scoreboard. Each board holds a team for every player. A join queues 300 jobs for the new board and 300 for the new player. Quit has O(N) and O(jobs) scans. Budget is 400 us per tick. | `TabService.java:105`, `:142-183`, `:226-240`, `:357-389`, `Sidebar.java` | About 90,000 jobs after a mass join. Tab and nametags settle slowly for minutes. Many team packets per player. | Sort the tab list with `Player#setPlayerListOrder` instead of teams. Make nametag teams only for nearby or visible players. Raise the budget during a join storm. Check Geyser `add-team-suggestions`. | L |
| P-08 | HUD | Each player re-renders every 20 ticks. Up to 15 lines of MiniMessage parse with no cache. `online()` does an O(N) `canSee` loop per render. | `HudService.java:216`, `:283`, `:317`, `:359` | About 4,800 parses per second. 300 x 300 `canSee` checks per second. | Skip the parse when the resolved text is unchanged. Cache the online count for 1 s. Cache components by resolved string. | M **PARTLY FIXED (PERF2):** the sidebar already sends only changed lines (checked); every line parse got cheaper through the cached style resolver (P-17) and the dash/objective texts come from `Messages.fixed`. Per-line parse skipping and the online-count loop are not changed. |
| P-09 | Backup | 04:30 job: `save-all flush`, sleep 10, `tar -czf` of the whole server, then `mysqldump`, all on the game host. | `scripts/setup-vps.sh:106-115` | Disk I/O and a CPU core are used for minutes. World saves stall. Night players see lag. | Use `save-all` without flush plus `save-off`/`save-on`. Run tar with `nice` and `ionice`. Use `zstd -T2`. Dump DB from a replica or with `--single-transaction`. Move off-site copy to a quiet window. | S |
| P-10 | Depths | `cycle` runs every 40 ticks. `spot()` tries up to 8 spots. `standY()` scans down one block at a time with `getBlockAt`, `isPassable`, `getRelative`. | `CombatZones.java:190`, `:338`, `:367`; `DepthsConfig.java:197-206` | Matches the `depths-combat` slow warning. With many occupied zones, one tick can run 5 to 20 ms. | Precompute valid spawn points per zone once (at zone build). Pick from the list. Process a few zones per tick, not all. | M **FIXED (QUICK1):** standing height cached per arena column (`combat.spawn-column-cache-seconds`, 300 s), mob names parsed once. |
| P-11 | Depths | Ambient mobs are real mobs with AI. Hidden per viewer with `setVisibleByDefault(false)`. | `AmbientMobs.java:103`, `:168`; `MobFactory.java:72-80`; `DepthsConfig.java:244-259` | Up to about 600 AI mobs in one world. Entity tick and tracking cost. | Call `setAware(false)` on ambient mobs. Or lower `ambient-per-player` and `max-total`. Longer term, use packet-only entities. | S |
| P-12 | Scheduling | Per-player loops all fire on the same shared ticks. TierGates checks all players x all zones in one tick. ArmorEffects refreshes all players in one tick. Achievements poll all players every 30 s. | `TierGates.java:248`, `ArmorEffects.java:135`, `Tracker.java:112-117`, `:330+` | Several 3 to 8 ms jobs land on the same tick every 1 to 30 s. This makes periodic spikes. | Use a shared helper: split players into buckets by UUID hash and run one bucket per tick. Make TierGates and ArmorEffects event-driven (skill level up, gear change). | M **PARTLY FIXED (QUICK1):** achievements gauge poll runs one player slice per second (same 30 s per player). TierGates/ArmorEffects: open. **FIXED (PERF2):** TierGates and ArmorEffects run every tick on a per-player slice (`common/util/Stagger`), each player still once per period. |
| P-13 | Hoppers | `InventoryMoveItemEvent` listener fires on every hopper transfer. It is the only such listener in the code. Menus are virtual and cannot be hopper-connected. | `MenuService.java:525` | Every hopper move calls into the plugin. With hundreds of hopper farms this is thousands of calls per second. | Delete the listener. Set `hopper.disable-move-event: true`. Check that CoreProtect (when installed) does not need it. | S **FIXED (QUICK1):** listener deleted; with no listener Paper skips the event. `disable-move-event` kept false (CoreProtect later). |
| P-14 | Farms | Crop engine works on every loaded chunk, including view-only chunks that do not tick. Budget is 2,000 crops per tick. It rewrites a PDC `int[]` on every plant. Each grow fires `BlockGrowEvent`, gets the state and updates. | `CropGrowth.java:125`, `:159`, `:198`; `StackingConfig` (`crops.per-tick=2000`) | About 3 ms per tick when farms are big. Wasted work on view-only chunks. | Skip chunks where `Chunk#getLoadLevel` is not entity-ticking. Use a nanoTime budget (for example 1 ms) instead of a count. Batch PDC writes. | M |
| P-15 | Spawning | `islands` world has natural spawning on. A flag listener cancels spawns after the server did the work. `ticks-per.monster-spawns` is 10. Wandering traders are on. | `IslandFlagListener.java:75`; `worlds.yml:58-62`; `bukkit.yml` | Spawn attempts run for every ticking chunk near a player, then are cancelled. Steady 1 to 3 ms. | Ask the owner if the per-island mob toggle is needed. If not, turn off the gamerules. If yes, set `ticks-per.monster-spawns: 40` and turn off wandering traders. | S |
| P-16 | World | View distance 8, simulation 5 for all worlds. Each loaded void chunk makes a 4 KB region sector. Bedrock players need chunk translation on each world change. | `server.properties`; `spigot.yml` | About 289 chunks per island at view 8. More disk, more packets, more RAM. Bedrock join and teleport cost is higher. | Set view and simulation per world (islands 6 / 4, spawn 6 / 3). Use `Player#setSendViewDistance(6)` for Bedrock. Pregenerate spawn and depths. | M |

### Medium

| id | area | problem | where | impact at 300 players | fix | effort |
|---|---|---|---|---|---|---|
| P-17 | Text | `Messages.parse` builds new TagResolvers on every call and caches nothing. | `common/.../text/Messages.java` (about lines 190-205) | Cross-cutting. Feeds P-05, P-08, P-20, P-29. | Cache parsed Components by (template, resolved args). Cache static resolvers. | M **PARTLY FIXED (PERF2):** the style resolver (with the parsed `<prefix>`) is cached per viewer type until style.yml reloads; it was rebuilt and the prefix parsed again for every message parse. `Messages.fixed` caches placeholder-free texts per viewer type. A general (template, args) cache is not built. |
| P-18 | Cosmetics | Trail effects loop trail owners x all players in the world, every 6 ticks. | `wardrobe/.../Leveling.java:467-507` | Crowd at spawn or depths gives thousands of distance checks per tick. | Use `getNearbyPlayers` around the trail owner. Cap viewers per trail. | S **FIXED (PERF2, `wardrobe-trail`):** viewers from `getNearbyPlayers` within the view distance, the "moving" check and the settings lookup ordered cheapest first, particle parsed once, worn map built once, cap `trail.max-viewers` (60). |
| P-19 | Enchants | `refreshAll` updates all players on the same tick every 20 ticks. | `ArmorEffects.java:135` | Part of P-12. Spiky. | Event-driven on armor change. Stagger the rest. | S **FIXED (PERF2):** staggered per player (see P-12). Event-driven refresh not built (the period refresh also drives Second Wind). |
| P-20 | Achievements | `evaluateAll` and `pollGauges` run on the main thread at join. Unlock does audit and title grant on the main thread. Gauge poll reads hotbar PDC for every player every 30 s. | `Tracker.java:202-230`, `:330+`, `:498` | These match the `achievements-load` and `achievements-unlock` warnings. Join spike. | Load async, apply on main thread in one small step. Poll hotbar only on change. Stagger the poll by player. Move audit write async. | M **FIXED (QUICK1):** unlock audit written from the DB thread (title grant only on main), join gauge poll deferred and spread over 20 ticks, periodic poll sliced. |
| P-21 | Tutorial | 5-tick task per tutorial player. `trail()` re-parses `TargetSpec` for every node on every particle tick. Bossbar MiniMessage parse every 5 ticks. About 50 particle packets per second per player. A real hologram entity per player. | `tutorial/.../Guides.java` (about :70, :89, :264, :405-452) | Burst at launch when many new players start together. Holograms count toward the island display limit of 30. | Parse the trail once and reuse. Lower particle rate. Use a packet-only hologram or a text display visible to that player only. Check the display limit. | M **PARTLY FIXED (PERF2):** trail nodes parsed once per config list. Particle rate and the per-player hologram unchanged (visible behaviour). |
| P-22 | Menus | Live menus rebuild the whole inventory every 10 to 40 ticks. | `Menu.java:222`, `CosmeticsMenu.java:142`, `IslandMenu.java:87`, `MenuService.java:89` | Many players with menus open means many full redraws per second. | Redraw only changed slots. Raise the refresh period. Pause refresh for players not looking at the menu. | M **PARTLY FIXED (PERF2):** framework: frame panes cached per reload, fixed buttons (back/info/close/help) per opening, already-tagged items are not cloned again; the spawner menu redraws only the info item each second (buttons only on change); the staff crates view draws only its crate slots when data arrives. Other live menus still rebuild their content on their 20-40 tick timer. |
| P-23 | Enchants | Area break sends one block change packet per block. Expiry sends single packets too. Normal break sends about 3 block packets. | `DepthsEnchants.java:366-384`, `DepthsEngine.java:575-590`, `:140-160` | Up to 80 packets per proc. Network and Netty cost in crowds. | Batch with `sendMultiBlockChange`. Merge restore packets. | M |
| P-24 | NPC | `onEntitiesLoad` iterates entities in all worlds and calls `getScoreboardTags()` (allocates). `look()` runs `getNearbyPlayers` per NPC every 10 ticks. NPC bodies are real entities seen by everyone. | `npc/.../Placer.java:130`, `:353` | Small steady cost. Crowd at spawn adds rotation packets. | Check world first. Cache tag lookup. Use a longer look interval. Move to packet NPCs later. | S **PARTLY FIXED (PERF2):** the entity-load cleanup only reads tags of villager/mannequin/text display/interaction entities. Look interval unchanged. |
| P-25 | Border | Sweep calls `chunk.getEntities()` for ring chunks of every occupied island every 40 ticks. | `BorderProtection.java` (about 380-420), `IslandsConfig` (about 97-104) | About 150 islands x ring chunks. Several ms per sweep. | Event-driven (entity move and spawn near border). Or spread over the 40 ticks. | M **FIXED (PERF2):** the sweep runs every tick and handles each occupied island once per interval in its own slot. |
| P-26 | Database | Pool size 10. No MariaDB tuning. Virtual-thread executor is unbounded. | `DatabaseConfig.java:38-43`; `setup-vps.sh` | Pool waits under join storm and during saves. | Pool 20. Tune MariaDB (section 3). Turn on the slow query log. Bound the executor or log queue depth. | S |
| P-27 | Storage | Region file cache is 256. About 300 islands each have a region file. Compression is deflate. | `paper-global.yml`; `server.properties` | Region file open and close churn. Slower chunk saves. | `region-file-cache-size: 512`. Use `lz4` on production. | S **PARTLY APPLIED (QUICK1):** `region-file-cache-size: 512`. lz4 waits for production. |
| P-28 | Items | `ToolItems.read(getItemInMainHand())` has 29 call sites and is called several times per break or hit. | `ToolsListener.java:149,190`, `DepthsHooks`, enchants, wardrobe | PDC read repeated 3 to 5 times per break. Depths miners break many blocks per second. | Memoize per event (a small per-tick cache keyed by player and item). | S |
| P-29 | Skills | Action bar flush does MiniMessage parse. | `SkillManager.java:347-377` | Adds to the parse load. | Cache by value. Flush less often. | S **PARTLY FIXED (PERF2):** separator and skill names come from `Messages.fixed` (parsed once per viewer type); the gain line itself is still parsed (it holds numbers). |
| P-30 | Phase 7 (future) | Offline production, collectors, auto-sell chests and fuel are not built. Today offline spawners and crops do nothing (chunks unloaded; `hasIslandFuel` is a stub). | `docs/phases/PHASE-7-PRODUCTION.md`; REVIEW B4 | If built by loading chunks, 150 offline islands would load at once. | Keep the decision: simulate arithmetically, never load chunks. Run the sim in a bucketed task with a time budget. | L |
| P-31 | Network | Geyser team suggestions and command suggestions are on. `netty-threads: 4`. Geyser `max-players: 100` may be a hard cap. | Geyser config; `spigot.yml` | Extra packets for Bedrock. Possible refusal of Bedrock players over 100. | Turn off team suggestions. `netty-threads: 8` on production. Verify the Geyser cap. | S |
| P-32 | Observability | No per-handler timing. Only scheduled tasks are timed (threshold 5 ms, one warning per minute per name). Event handlers are not timed except Stacking `Perf` and `/depths debug`. | `Scheduler.repeat`, `SlowOperationTimer` | Lag spikes from event handlers cannot be traced without spark. | Add a small perf registry (count, total, max) used by handlers. Add `/skyblock perf`. | M |
| P-33 | OS | systemd unit has no `LimitNOFILE`. | `scripts/setup-vps.sh` | Region files, sockets and DB connections can hit the default limit. | `LimitNOFILE=65536`. | S |

### Low

| id | area | problem | where | impact at 300 players | fix | effort |
|---|---|---|---|---|---|---|
| P-34 | Moderation | `moderation:ack` 20-tick task shows slow warnings. Likely cold JIT on the small VPS. | `PunishExtras.java:344` | Probably none. | Re-test on production. Fix only if it repeats. | S **FIXED (QUICK1):** the 1 s timer returns at once when no reminder is waiting. |
| P-35 | Trade | `deliverySweep` runs a DB `cancelOrphans` and a player loop. | `TradeModule.java:156-158`, `:1018` | Probably none on real hardware. | Move DB call async. Re-test. | S **FIXED (PERF2):** the orphan check was already async; the online-player loop is skipped when nothing waits. |
| P-36 | MOTD | Gradient is rendered on every ping, no frame cache. | `MotdModule.java:79` | Ping floods are cheap to trigger. | Cache the rendered frame for 1 s. | S **FIXED (PERF2):** rendered frames cached per (lines, phase, center), cleared on reload and above 512 entries. |
| P-37 | Leaks | `Cooldowns.clear(UUID)` is never called by `MoneyNotes:85`, `ReportService:41`, `TeleportCommands:45`. | `common/.../util/Cooldowns.java` | Tiny leak per player ever seen. | Call `clear` on quit. | S **FIXED (PERF2):** `Cooldowns` prunes expired entries every 256 writes (`prune()`); running cooldowns are kept so a relog cannot skip them. |
| P-38 | Reload | `DepthsDrops.stop()` waits up to 3 s. Achievements `shutdown` waits up to 10 s. | `DepthsDrops.java:105`, achievements shutdown | Stalls on reload or stop. | Lower waits. Do not reload on a live server. | S |
| P-39 | Docs | `CONTEXT.md:62` says no natural spawning on islands, but `worlds.yml` has it on. `WORLD.md` section 6 says per-chunk piston and observer limits, which the owner rejected. | docs | Wrong assumptions in later work. | Fix both docs after the owner answers Q2 and Q1. | S |
| P-40 | Stacking | `SpawnerManager.active()` calls `getNearbyPlayers` per due spawner. Fine at `due-per-tick=100`. | `SpawnerManager` | Fine today. | Watch it when spawner count grows past 2,000. | S |

### Checked and fine

- Chunk tickets are released (`IslandBuilder:97`, `BuildsService:444`, `IslandClearer:393`, `ZoneGenerator:193`).
- `AuditLog` is async and batched.
- All `PlayerMoveEvent` handlers exit early unless the block changes.
- Economy, bank and trade DB calls are async.
- Most `Map<UUID, ...>` fields are cleaned on quit.
- Stacking uses a priority queue and a mob index. Spawner mobs are `setAware(false)`.

### Count by impact

| Impact | Count | Ids |
|---|---|---|
| Critical | 4 | P-01 to P-04 |
| High | 12 | P-05 to P-16 |
| Medium | 17 | P-17 to P-33 |
| Low | 7 | P-34 to P-40 |
| Total | 40 | |

---

## 3. Recommended server config

Current values were read from `server/`. Treat the recommended values as a first guess. Tune after the load test (section 5).

### server.properties

| Key | Now | Set to | Why |
|---|---|---|---|
| `view-distance` | 8 | 8 (default for all worlds) | Per-world overrides below handle the heavy worlds. |
| `simulation-distance` | 5 | 5 | Keep. Per-world overrides below. |
| `network-compression-threshold` | 256 | 256 | Good balance. Keep. |
| `region-file-compression` | deflate | `lz4` on production | Faster saves. Larger files. Disk is cheap. |
| `max-chained-neighbor-updates` | 1000000 | 100000 | Stops very long update chains. Optional. |
| `max-players` | 350 | 350 | Leaves room above 300. |

### spigot.yml (per world under `world-settings.<world>`; verify key names against the generated file)

| Key | Now | Set to | Why |
|---|---|---|---|
| `world-settings.islands.view-distance` | default | 6 | Void islands are small. Saves chunks and packets. |
| `world-settings.islands.simulation-distance` | default | 4 | Fewer ticking chunks near each player. |
| `world-settings.spawn.simulation-distance` | default | 3 | Spawn is crowded and mostly static. |
| `world-settings.depths.simulation-distance` | default | 5 | Keep. Mobs need it. |
| `mob-spawn-range` | 6 | 4 | Fewer chunks checked for spawns. |
| `entity-activation-range.animals` | 16 | 12 | Animals do less work far away. |
| `entity-activation-range.monsters` | 24 | 16 | Same. |
| `entity-activation-range.raiders` | 48 | 16 | Raids are not used. |
| `entity-activation-range.flying-monsters` | 32 | 16 | Fewer phantoms ticking. |
| `entity-tracking-range.players` | 96 | 64 | Fewer player packets in crowds. |
| `entity-tracking-range.animals / monsters` | 48 | 32 | Fewer entity packets. |
| `entity-tracking-range.misc` | 32 | 24 | Items, minecarts. |
| `entity-tracking-range.display` | 96 | 64 | Holograms rarely need 96. |
| `entity-tracking-range.other` | 48 | 32 | |
| `netty-threads` | 4 | 8 on production | More network throughput with Geyser. |
| `tick-inactive-villagers` | false | false | Keep. |

### paper-global.yml

| Key | Now | Set to | Why |
|---|---|---|---|
| `misc.max-joins-per-tick` (verify key path) | 3 | 2 | Smooths join storms. |
| `chunk-loading-basic` / region cache (`region-file-cache-size`) | 256 | 512 | About 300 island region files. |
| `chunk-system.worker-threads` | auto | 4 | Test value. Measure on production. |
| `chunk-system.io-threads` | auto | 2 | Test value. |

### paper-world-defaults.yml

| Key | Now | Set to | Why |
|---|---|---|---|
| `misc.redstone-implementation` | ALTERNATE_CURRENT | ALTERNATE_CURRENT | Keep. Biggest redstone win. |
| `hopper.disable-move-event` | false | true | After P-13. Big saving for hopper farms. |
| `hopper.cooldown-when-full` | true | true | Keep. |
| `hopper-check` / `hopper-transfer` (tick-rates) | 8 / 8 | 8 / 8 | Keep. Raise to 10 / 10 if hoppers show in spark. |
| `misc.update-pathfinding-on-block-update` | true | false | Stops mobs re-pathing on every block change (farms, villagers). |
| `environment.max-block-ticks` and `max-fluid-ticks` (verify key) | default | 32768 | Optional cap on scheduled ticks per chunk. |
| `collisions.allow-vehicle-collisions` | true | false (optional) | Cheaper minecart and boat crowds. Ask owner. |
| `maps.item-frame-cursor-update-interval` | default | 20 | Fewer map packets. |
| `tick-rates.*` villager sensors and behaviors (`acquirepoi`, `validatenearbypoi`, `secondarypoisensor`, `nearestbedsensor`, `villagerbabiessensor`, `playersensor`, `nearestlivingentitysensor`) (verify key names) | default | 40 to 80 ticks | Cuts villager brain work without plugin code. Test that trading and restock still work. |

### bukkit.yml

| Key | Now | Set to | Why |
|---|---|---|---|
| `ticks-per.monster-spawns` | 10 | 40 | Fewer spawn passes. Depths spawns are plugin-made. |
| `spawn-limits.monsters` | 20 | 20 | Keep. |

### worlds.yml (plugin)

| Key | Now | Set to | Why |
|---|---|---|---|
| islands `spawn_wandering_traders` | true | false | No use on islands. |
| islands `spawn_mobs` / `spawn_monsters` | true / true | depends on Q2 | If the per-island toggle is not needed, set false. |
| islands `random_tick_speed` | 3 | 3 | Keep. Crop engine handles growth. |
| islands `fire_spread_radius_around_player` | 128 | 32 | Fire near players only. Verify key. |

### Geyser and ViaVersion

| Setting | Now | Set to | Why |
|---|---|---|---|
| Geyser `add-team-suggestions` | true | false | Cuts team packets (P-07). |
| Geyser `scoreboard-packet-threshold` | 20 | 20 | Keep. |
| Geyser `bedrock.compression-level` | 6 | 3 (optional) | Less CPU per Bedrock packet. |
| Geyser `max-players` | 100 | verify | Check whether it is a hard cap. Set 350 if it is. |
| ViaVersion packet limiter | 800 / 200 | keep | Fine. |
| Per Bedrock player | none | `setSendViewDistance(6)` | Less chunk translation. |

### JVM (20 GB heap on a 64 GB box; change if hardware differs)

Replace the `MEMORY` formula with an explicit value. Use:

```
-Xms20G -Xmx20G
-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200
-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch
-XX:G1NewSizePercent=40 -XX:G1MaxNewSizePercent=50 -XX:G1HeapRegionSize=16M
-XX:G1ReservePercent=15 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4
-XX:InitiatingHeapOccupancyPercent=20 -XX:G1MixedGCLiveThresholdPercent=90
-XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32
-XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1
-Xlog:gc*,safepoint:file=logs/gc.log:time,uptime:filecount=5,filesize=10M
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=logs/
```

Also test `-XX:+UseCompactObjectHeaders` on JDK 25 (less memory per entity). Keep it only if spark shows no regression.

### MariaDB (add a `/etc/mysql/mariadb.conf.d/phoenix.cnf`)

```
innodb_buffer_pool_size = 3G
innodb_log_file_size = 512M
innodb_flush_log_at_trx_commit = 1      # see Q6
skip_name_resolve = 1
max_connections = 100
slow_query_log = 1
long_query_time = 0.2
```

Plugin `pool-size`: 10 to 20 on production.

### systemd and backup

- Add `LimitNOFILE=65536` to the unit.
- Backup: `nice -n 19 ionice -c3 tar ... | zstd -T2`. Use `save-all` (no flush) between `save-off` and `save-on`. Dump the DB with `--single-transaction`. Prefer a replica or a second host.

---

## 4. Redstone and farm rules for islands

Rule: the owner chose no count limits on blocks, hoppers, pistons or observers. These rules limit lag, not building. Players should always be able to see where they stand.

### 4.1 Limits players can see

These already exist. Show them in the `/is` menu and in `/is limits`.

| Thing | Limit per island | Message when blocked |
|---|---|---|
| Armor stands | 20 | `You have reached the limit of 20 armor stands on this island.` |
| Item frames | 60 | `You have reached the limit of 60 item frames on this island.` |
| Minecarts | 20 | `You have reached the limit of 20 minecarts on this island.` |
| Boats | 10 | `You have reached the limit of 10 boats on this island.` |
| Villagers | 20 | `You have reached the limit of 20 villagers on this island.` |
| Display entities | 30 | `You have reached the limit of 30 displays on this island.` |
| Mob stacks | 8 stacks per type per chunk, 1024 per stack | `This chunk is full of that mob. Kill some to make room.` |
| Spawner stack | 8 to 512 | `That spawner stack is full.` |

The limits get +50% with the upgrade. Show both numbers in the menu: `Villagers 14 / 20`.

### 4.2 Redstone heat meter

- Add a "Redstone heat" line to the `/is` menu. It shows Cool, Warm, Hot for the worst chunk on the island.
- Heat score per chunk per second = redstone events + 5 x piston moves + 2 x dispenser or dropper fires + observer pulses.
- Starting thresholds (tune in the load test): Warm at 300 per second, Hot at 600 per second. Warn at Hot for 10 s. Freeze at 1,500 for 10 s.

### 4.3 Lag-machine detector: three stages

Stage 1, warning. Tell all online island members. Show the chunk coordinates.

- `Redstone in chunk 12, 4 is running very fast. Slow it down or it will be paused.`

Stage 2, freeze for 60 s. Stop the signals in that chunk by holding the old current (`BlockRedstoneEvent#setNewCurrent(oldCurrent)`). Pistons in the chunk stop moving. Redstone elsewhere still works.

- `Redstone in chunk 12, 4 is paused for 60 seconds because it was causing lag.`

Stage 3, repeat offense (3 freezes in 10 minutes). Break the top offending part and return the item to the owner. Log it for staff.

- `We removed a clock at 120, 64, 88 to protect the server. The item is back in your inventory.`

Staff tools: `/lagguard top` (worst chunks now), `/lagguard island <name>`, `/lagguard unfreeze`. The owner must approve stage 3 (Q10).

### 4.4 Soft counters (information only)

- Count hoppers, pistons, observers and dispensers per island on place and break.
- Show them in the island menu: `Hoppers 214`. No limit and no block.
- Staff see the same list to find problem islands.
- Optional safety cap: 64 hoppers per chunk (REVIEW3 R3-27). Only if the owner agrees (Q1). Message: `This chunk has 64 hoppers. Place the next one in another chunk.`

### 4.5 Farm rules

- Crops grow only in loaded, ticking chunks. Message in the island menu: `Crops grow while you or a member are on the island.` (offline growth rules come with Phase 7).
- Villagers pause when enclosed and idle. Trading wakes them. Restock runs on a timer. Message when a paused villager is hit: none. Players should not see it. Show in help: `Villagers rest when nobody is trading. Right-click one to wake it.`
- Item farms: stacking merges items. A farm hopper chain cannot hold more than normal.
- Spawner farms: spawners stack. Mobs stack. Kill zones should drop to a collector, not float.
- Mob cramming stays at the Minecraft default (24).

---

## 5. Profiling and load test plan

### 5.1 Tools (use these first)

- spark: `/spark profiler start --timeout 300` for general view. `/spark profiler start --only-ticks-over 60` to catch only spikes. `/spark tickmonitor --threshold 100` to log slow ticks. `/spark health` for TPS, CPU, GC, memory.
- `/mspt` for quick checks.
- Timings v2 is gone or deprecated. Do not rely on it.
- GC log: `logs/gc.log` (section 3). Check the pause p99.
- JFR for 10-minute recordings on the load test host: `-XX:StartFlightRecording=duration=600s,filename=logs/load.jfr`.
- MariaDB slow query log (0.2 s). HikariCP pool wait time (add metrics to logs).
- Add the perf registry (P-32) before the load test so handlers show up by name.

### 5.2 Staging server

The owner rule is: never start a test server without permission (Q13). Get permission first. Use a copy of production data on a second box. Settings: `online-mode=false`, whitelist off, Grim relaxed or exempt for bots, Geyser on.

### 5.3 Bot load

- Java bots: mineflayer or a similar bot library. Ramp to 300. Each bot walks, chats, opens menus, breaks blocks.
- Bedrock bots: about 30 through Geyser (about 10% of players; adjust to Q7).
- Seed data: 150 islands. Each has 4 spawners, 2 collectors, a 9x9 farm, 10 villagers, 100 hoppers.

### 5.4 Scenarios

| # | Scenario | What it tests |
|---|---|---|
| 1 | Join storm: 300 logins in 2 minutes after restart | P-03, P-07, P-20 |
| 2 | Spawn crowd: 300 at spawn for 10 minutes | P-05, P-07, P-08, P-18, P-24 |
| 3 | Depths: 150 miners with area enchants | P-10, P-11, P-23, P-28 |
| 4 | 100 island farms running | P-01, P-14, P-15, P-13 |
| 5 | Chat: 5 messages per second | P-05 |
| 6 | Menu storm: 100 players opening menus and `/sell` | P-22, P-06 |
| 7 | Redstone clock bomb on 3 islands | P-02 and section 4.3 |
| 8 | Boss fight: 150 players | Entity and packet cost |
| 9 | Nightly backup during load | P-09 |
| 10 | Tutorial burst: 50 new players at once | P-21 |

### 5.5 Acceptance targets

- TPS 20 for 95% of the time.
- MSPT p95 under 35 ms. p99 under 50 ms.
- GC pause p99 under 100 ms.
- DB wait p95 under 50 ms.
- Login to ready under 10 s with 300 online.
- No scheduled task over its warning threshold more than once an hour.

### 5.6 Order of work

1. P-04, P-13, P-09, P-33 (quick config wins).
2. P-32 (perf registry), so later fixes can be measured.
3. P-02 lagguard and P-01 villager pause.
4. P-03 login path, P-07 tab, P-05 chat, P-06, P-08.
5. P-10, P-11, P-12, P-14, P-15, P-16.
6. Load test round 1. Fix what spark shows. Round 2.
7. Medium and low items.

---

## 6. Questions for the owner

Keys: `q` question, `s` suggested answer, `why` why it matters, `p` priority (1 = most urgent).

```json
[
  {"q": "Do you want a high safety cap on hoppers and pistons per chunk (for example 64 hoppers)?", "s": "No cap for now. Use soft counters and the lag detector. Add a cap only if tests show a problem.", "why": "Keeps your no-limits decision (O1, O2, WR2) but covers an extreme case (REVIEW3 R3-27).", "p": 1},
  {"q": "Is the per-island 'mob spawning on/off' toggle needed?", "s": "No. Turn natural spawning off in the islands world with gamerules.", "why": "Natural spawn work runs on every ticking chunk even when cancelled later. Saves 1 to 3 ms per tick.", "p": 1},
  {"q": "May villagers go to sleep (stop AI) when enclosed and idle, and wake when a player trades?", "s": "Yes. Restock every few minutes so trading feels normal.", "why": "Without it, 3,000 villagers can break the tick rate.", "p": 1},
  {"q": "May the lag detector remove the worst redstone part after three freezes, and return the item?", "s": "Yes, with a staff log and the message in section 4.3.", "why": "Freezing alone does not stop players who rebuild the same machine.", "p": 1},
  {"q": "What is the production hardware (CPU, cores, RAM, disk) and the heap size?", "s": "At least 8 fast cores, 64 GB RAM, NVMe disk, 20 GB heap.", "why": "All GC and thread settings depend on it. docs/CONTEXT.md describes the test VPS only.", "p": 1},
  {"q": "Can I start a staging server and run bot load tests?", "s": "Yes, on a second box with a copy of data and no public access.", "why": "Project rule says never start test servers without permission. Without it, nothing here can be measured.", "p": 1},
  {"q": "Are shorter view and simulation distances per world OK (islands 6 / 4, spawn 3 sim)?", "s": "Yes. Keep spawn and depths at 8.", "why": "Biggest cheap win for chunks, RAM, disk and packets. Players may notice a closer fog in the void.", "p": 2},
  {"q": "May Bedrock players get a send view distance of 6?", "s": "Yes.", "why": "Geyser translates every chunk. Fewer chunks means less CPU and faster teleports.", "p": 2},
  {"q": "What share of players will be Bedrock, and is Geyser max-players 100 a hard limit?", "s": "Plan for 15%. Check the Geyser limit and set it to 350 if it is a hard cap.", "why": "Bedrock costs more CPU per player. A cap of 100 could refuse players.", "p": 2},
  {"q": "Is a tab list sorted by `setPlayerListOrder`, with nametag teams only for nearby players, acceptable?", "s": "Yes, if names and colors look the same.", "why": "Removes the N squared team cost (P-07), the largest scoreboard cost.", "p": 2},
  {"q": "Should the ambient Depths mobs have no AI, and should combat caps go lower?", "s": "No AI for ambient mobs. Keep combat.max-total-mobs at 300 until tests say otherwise.", "why": "Ambient mobs are decoration. AI on 600 mobs costs a lot.", "p": 2},
  {"q": "What do you want to happen after a restart when 300 players rejoin at once?", "s": "Soft queue: 2 joins per tick, a short 'Connecting, please wait' message.", "why": "Protects the main thread and the DB during the first minute.", "p": 2},
  {"q": "Should the DB use innodb_flush_log_at_trx_commit = 2 for speed?", "s": "No. Keep 1 for money safety. Use a faster disk.", "why": "Value 2 can lose up to 1 second of writes in a crash.", "p": 3},
  {"q": "What backup window and off-site location do you want?", "s": "04:30 local time to a second host. Run with nice and ionice.", "why": "Backup load is a nightly spike (P-09).", "p": 3},
  {"q": "May vehicle collisions be turned off (minecarts, boats)?", "s": "Yes, unless you want boat bumping.", "why": "Cheaper crowds. Small gameplay change.", "p": 3},
  {"q": "May I fix the docs that disagree with the owner decisions (CONTEXT.md line 62, WORLD.md section 6)?", "s": "Yes, after Q1 and Q2 are answered.", "why": "Wrong docs cause wrong code later.", "p": 3}
]
```

---

## PERF3 (startup slow operations on update101)

Measured by reading the code paths (no test server). What each warning was and what changed:

| Task (update101) | Root cause | Fix |
|---|---|---|
| `npcs:start` 1065 ms | One tick did everything: parse roles/skins/held items/build points/leaderboard sources, build every hologram's MiniMessage text once just to check that it exists, walk every entity of every world for left-overs (tag-set copy per entity), then spawn every NPC, hologram and leaderboard in the loaded chunks (mannequin + text display + click box, MiniMessage parse, first-time Adventure-to-vanilla conversion) in that same tick. No chunk was loaded. | Parsing runs off the main thread (`NpcPlan`, applied on the main thread through `LatestLoad`; a newer reload/respawn drops older results). The "has a text" check reads the raw message only. Left-over purge only looks at villagers, mannequins, text displays and interactions. Spawns go through `SpawnQueue`: at most `spawning.per-tick` (8) sets or `spawning.budget-ms` (2 ms) per tick, always at least one; chunk-load spawns use the same queue (one task instead of one task per chunk). Chunks are still never loaded. |
| `tutorial:start` 14.5 ms | `Cosmetics.defineTitle` (the tutorial reward title) wrote `tags-titles.yml` on the main thread (load + save of the whole YAML file). The same path ran once per new achievement title. | `CosmeticRegistry.putLater`: the title is active at once, the file is written off the main thread, a burst of definitions shares one write. A reload before the write writes it first. |
| `sell-stat-sources` 7.3 ms | Two set inserts; the time was first-tick JVM warm-up (class loading, lambda bootstrap, safepoints while every module's async start-up load runs). | Registered directly in `enable()` (islands is enabled before economy); the one-tick task is only a fallback. |
| `afk-start` 7.2 ms | Same as above: area lookup in memory and two timers. The banked booster (boosters module, M10-2) does its database work async; boosters has no start task. | `AfkTracker.start()` runs in `enable()`; no separate first-tick task. |

What to look for in the next startup log: no `npcs:start`, `tutorial:start`, `sell-stat-sources` or `afk-start` warnings; the `[npcs] … from the builds; spawning N in loaded chunks over the next ticks` line; at most one `npcs:spawn` warning (the very first spawn pays the cold-start cost) and `npcs:purge` / `npcs:apply` warnings only if those steps are still slow (they now time themselves).

---

## PERF4 (slow operations after update102, one player online)

Measured by reading the code paths (no test server). Warn limit 5 ms. "Warm-up" = a one-off first run (class loading, lambda bootstrap, first MiniMessage parse, first entity of a type, time-zone rules loading, GC pause); it should not repeat once the server has run for a minute.

| Task | Verdict | Root cause / fix |
|---|---|---|
| `chat:tab` 46×/min, worst 32.5 ms | **Real** | The tab list was already diff-based, but the round-robin slice was `max(1, ceil(online / update-ticks))`: with fewer players than `tab.update-ticks` (40) every player was re-checked **every tick**, i.e. 20×/s instead of once per 2 s. Each check ran 5+ LuckPerms node scans (`prefix`, `rankColor` (twice through `primaryGroup`), `weight`), the title/tag placeholders (MiniMessage) and rebuilt the staff TPS footer. Fix: `TabPacing` (fractional carry; every player once per `update-ticks`, 300 players = 7–8 per tick; a joined player is still checked on the next tick). Rank parts are read **off the main thread** into a `Display.RankView` snapshot (each check and each rank-change event); the tick only compares snapshots; a rank change forces a rebuild when its snapshot arrives. |
| `tutorial-guides` 57.3 ms | Mostly warm-up, plus real repeated work | First run spawns the hologram and Pip (first text display / parrot) and parses every guide message. Real: the farm-spot (~1,000 blocks) and ore (~1,300 blocks) scans every 2 s re-stripped and lowercased every `mine-blocks` rule per block and sorted with a comparator that built two `Location`s per comparison. Fix: `MarkerScan` (rules compiled once per config list, one rule check per material per scan, distances computed once, stable sort = same order); block types read with `World#getType`. |
| `tutorial-rejoin` 28 ms | Warm-up | One teleport request on join (guard chain, async chunk load scheduling, first use of the teleport path). Nothing repeats; the NPC lookup it uses (`npcByRole`) no longer builds every NPC's info. |
| `bank:join` 13.7 ms | Warm-up | `settleFor` only reads in-memory island data and starts async DB futures (virtual-thread executor); the first run pays class loading / lambda bootstrap of the settle chain. |
| `npcs:spawn` 13.8 ms | Warm-up, budget tightened | A single spawn (first mannequin with skin profile, first label MiniMessage); one spawn per tick is always allowed. The budget was honoured; now a tick also stops when the previous spawn shows the next would end past `spawning.budget-ms`. |
| `npcs:look` 5.2 ms | Real (small) | One nearby-players entity query plus two eye `Location`s per NPC every 10 ticks. Fix: `NearestGrid`, one pass over each world's players per run, each NPC checks the 3×3 cells around it. |
| `wardrobe-trail` 11.8 ms | Warm-up / GC | Re-checked after PERF2: with one player a pass is a handful of map lookups and one particle packet. |
| `enchants-armor` 7.4 ms | Warm-up | Staggered since PERF2; one refresh is in-memory wardrobe cache + attribute check. |
| `teleport-arrived` 5.7 ms | Warm-up | Runs the caller's arrival callback once. |
| `island-stats-playtime` 7.2 ms | Warm-up | First run builds the `StatsClock` day cache (time-zone rules); later runs are map lookups. |
| `island-environment` 8.1 ms | Warm-up, small fix | First "real" clock refresh loads the time-zone rules. The `ZoneId` is now parsed once per config value instead of per player per refresh. |
| `portal-particles` 6.2 ms | Warm-up, small fix | Each world is now looked up (and its player list copied) once per run instead of once per puff. |

What to look for in the next log (one player online): `chat:tab` should be gone (a check runs once per 2 s per player and only builds components when something changed; a new `chat:tab-rank` task applies the async rank snapshots and should never warn). `npcs:look` and `tutorial-guides` should not repeat; at most one warning each for the warm-up tasks above in the first minute after a start or join. Any of them repeating after that is real and worth a new item.

---

## PERF5 (progression, achievements and npcs slow operations, 0-1 players online)

Measured by reading the code paths (no test server). Warn limit 5 ms. Important for reading the log: `Scheduler.ensureMain` runs **inline** when it is already on the main thread, and a `CompletableFuture` that is already complete runs its `thenApply`/`whenComplete` inline too. So a nested name is timed inside its parent: `progression-level` (41.6 ms) **includes** `progression-level-apply` (20 ms), `progression-bank-cache` was a continuation of an already-complete balance read, and `progression-blocks` (8.7 ms) included the `progression-scan` copies of chunks that were already loaded.

| Task | Verdict | Root cause / fix |
|---|---|---|
| `progression-task-check` 27-28 ms every 30 s | **Real (repeating)** | Every 30 s, in one tick, every island with an online member ran `readyAll`: all 180 tasks, the live value of every reach task read even for tasks already done, `SkillService` looked up and every online member's skill read once **per skill task**, and a task whose completion save failed was claimed again from scratch. Fix: `TaskCheckPlan`. The check runs every second for one slice of the islands (by island id), so each island is still checked once per `check-seconds` but never all in one tick. An island gets the full check only once after the load, after a reload (catalog change) and after a failed completion save; every later check reads only the not-done reach tasks (`TaskProgress.readyReach`); counter tasks already complete the moment they are counted (`readyFor`/`release`). Online members and best skill levels are read once per check and only when a skill task still needs them. Exactly-once completion is unchanged (same claim → save → finish path, same idempotency keys). |
| `progression-level` 41.6 ms (incl. `progression-level-apply` 20 ms) | Mostly warm-up, plus a real burst | First calculations after a start pay class loading (`LevelMath`, EnumMaps, the time-zone rules for the daily history row, the first `IslandLevelUpEvent` handler list, the first audit row). Real: after start every island with achievement points asks for a level update (achievements `hook()`), and up to `recalc.per-tick` (4) islands with all inputs cached were calculated **and applied** inside one tick. Fix: new `recalc.budget-ms` (2): after the first island of a tick, no more start once the tick has used its budget; the rest stay due for the next ticks. |
| `progression-bank-cache` 6 ms, `progression-skill-cache` | Warm-up (a single map put) | They no longer exist as separate main-thread tasks: the balance and offline XP read from the database are cached in the apply step, which already runs on the main thread. |
| `progression-scan` 11.4 ms, `progression-blocks` 8.7 ms | **Real** | A rescan chunk copy took `chunk.getTileEntities()`: a **snapshot copy of every block entity** of the chunk (chests, hoppers, shulkers with all their items) just to find the spawners. And when the chunk was already loaded, `getChunkAtAsync` completed inside the `progression-blocks` tick, so up to `chunks-per-tick` copies ran in that tick. Fix: `getTileEntities(block is SPAWNER, no snapshot)`. Copies follow a new per-tick budget `blocks.copy-budget-ms` (2, `TickBudget`, at least one per tick). A loaded chunk over the budget waits in the job's `ready` queue for the next tick. If it unloaded meanwhile it is loaded again and copied as soon as it arrives, so it never waits forever. No chunk tickets are used, because they are per plugin and other modules share them. |
| `achievements-save` 6 ms | **Real (O(islands))** | Every save walked **every island of the season** (all are kept in memory): each `drain()` cloned `IslandMetric.values()` and allocated a list, and each changed island got its own queued database task. Fix: `IslandProgress` keeps a `pending` flag (clean islands drain in O(1), `values()` cached). All changed islands are written in **one** transaction, all or nothing: a failure gives every island its rows back. Before, a partly written island could add its counter delta twice on retry. |
| `achievements-islands` 7.4 ms | **Real** | `IslandStatsService.snapshot` was started on the main thread for every island with an online member. Starting it copies the island's unwritten counters out of the stats buffer under the lock that every block/kill counter and the stats flush also take (and `copyOf` walks every pending counter of every island). Fix: the snapshots are started off the main thread (the service is synchronized and documented to complete off the main thread); the values still land on the main thread, one `achievements-island-stats` task per island. |
| `achievements-join-gauges` 5.3 ms | Warm-up / one-off | Runs once per join (spread by player). It is the same work as the per-second gauge slice (which never warns): skills, wardrobe, cached balance, 9 hotbar items. The first poll after a load can unlock tiers (toast MiniMessage, audit row, title grant), which is real but one-off work. `Metric.values()` is no longer cloned per drain. |
| `npcs:apply` 27 ms after a reload | **Real (reload only)** | The apply removed every placed NPC, hologram and leaderboard (up to 3 entities each, with their remove packets) in the reload tick and walked every world's entities for left-overs. Fix: on a reload the old sets go to a `retiring` queue that the spawn task empties first, `spawning.removals-per-tick` (24) within `spawning.budget-ms`, and only then do the new sets spawn, so nothing shows twice. The left-over walk only runs on the first placement of an enable (on a reload every entity is tracked). Disable still removes everything at once. |

Not changed (outside this item or not flagged): `progression-top` (every 5 min) builds the island boards from memory, but `displayName` → `Bukkit.getOfflinePlayer(uuid).getName()` can read a player data file for an island without a name whose owner is not in the profile cache. Worth caching names if `progression-top` ever warns.

What to look for in the next log (0-1 players): `progression-task-check` should not warn any more (a run is one slice; an island's check is a handful of map lookups). After a start there can be one `progression-level` / `progression-level-apply` warm-up warning in the first minute, and no `progression-bank-cache` / `progression-skill-cache` lines at all. A Recalculate or nightly rescan should give at most an occasional `progression-scan` warning (one slow chunk per tick is still allowed). `achievements-save` and `achievements-islands` should be gone. `npcs:apply` after `/skyblock reload npcs` should be well under 5 ms, followed by a few `npcs:spawn` ticks.

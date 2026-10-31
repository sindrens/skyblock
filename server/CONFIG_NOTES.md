# Server config notes

Why every non-default value is set. All other values are **Paper/vanilla defaults** for Minecraft 26.3.
When Paper updates and adds new options, keep their defaults unless there's a reason to change them, and document the reason here.

## server.properties
| Key | Value | Why |
|---|---|---|
| `level-name` | `spawn` | Main world is the spawn hub. Islands, outposts, Depths and warzone are created by our plugin |
| `level-type` / `generator-settings` | flat, void (`the_void` biome, 1 air layer) | Empty world for builders; no terrain generation cost |
| `generate-structures` | false | Void world |
| `online-mode` | true | Java accounts are verified. Bedrock players are verified by Floodgate |
| `enforce-secure-profile` | false | Needed for custom chat formatting and Geyser players; disables Mojang chat reporting |
| `white-list` / `enforce-whitelist` | true | **Test server:** only invited testers. Set false at public launch |
| `allow-flight` | true | `/fly` perk and knockback/jump effects would otherwise kick players. Grim handles fly cheats |
| `difficulty` | normal | Combat zones need real mob damage (scaled by our plugin) |
| `spawn-protection` | 0 | Our plugin handles protection |
| `max-players` | 350 | Target 300 + headroom for staff |
| `view-distance` | 8 | Performance at 300 players. Islands are small, so this is plenty |
| `simulation-distance` | 5 | Ticks fewer chunks; farms/redstone work within 5 chunks of players |
| `sync-chunk-writes` | false | Faster saving (Paper recommendation) |
| `player-idle-timeout` | 0 | AFK handled by our plugin (AFK only kicks at spawn) |
| `broadcast-console/rcon-to-ops` | false | Less chat noise for staff |
| `management-server-secret` | empty | Never commit secrets; the server generates its own |
| `motd` | `SKYBORNMC » Phoenix Skyblock` + season line | Network name + gamemode in phoenix red; update the second line per season. Moves to the proxy when the network gets one |

## bukkit.yml
| Key | Value | Why |
|---|---|---|
| `allow-end` | false | Vanilla End disabled (owner plans a custom use later) |
| `spawn-limits` | lowered | Natural spawning is minimal; mobs come from spawners and the Depths |
| `ticks-per.*-spawns` | 10 / 400 | Fewer natural spawn attempts (performance) |
| `chunk-gc.period-in-ticks` | 400 | Unload unused chunks faster |
| `shutdown-message` | friendly | Player-facing text |

## spigot.yml
| Key | Value | Why |
|---|---|---|
| `messages.*` | friendly wording | Points players to `/help` and `/menu` |
| `restart-on-crash` | false | The VPS service (systemd) restarts the server instead |
| `advancements.disabled` | `'*'` | Vanilla advancements off; we have custom achievements. Also saves performance |
| `merge-radius` | item 2.5 / exp 4.0 | Fewer item/XP entities |
| `mob-spawn-range` | 6 | Matches simulation distance |
| `arrow-despawn-rate` | 300 | Fewer lingering arrows |
| `nerf-spawner-mobs` | true | Spawner mobs have no AI: big performance win for spawner farms |
| `enable-zombie-pigmen-portal-spawns` | false | No portals |
| `entity-activation-range` | lowered | Entities far from players tick less |
| `tick-inactive-villagers` | false | Villagers are laggy; trading still works |
| `entity-tracking-range` | lowered | Less network traffic; players still visible at 96 blocks |
| `hopper-check` | 8 | Hoppers check less often (big win with many hoppers) |
| `hopper-transfer` | 8 | One move every 8 ticks (vanilla speed); moving every tick would add work, not save it (F12) |
| `hopper-amount` | 64 | F12 (owner 30 Oct): a hopper moves a whole stack per move, so 64 items every 8 ticks (~160 items/s, 64x vanilla) with 64x fewer move operations. Side effect: vanilla item sorters (one item per move) no longer work; collectors and storage do the sorting |
| `max-tnt-per-tick` | 50 | Lag protection |
| `send-namespaced` | false | Hides `plugin:command` entries from tab completion |
| `log-villager-deaths` | false | Log spam |

## commands.yml
Removed the `icanhasbukkit` alias.

## config/paper-global.yml
| Key | Value | Why |
|---|---|---|
| `collisions.enable-player-collisions` | false | No pushing at spawn/KOTH crowds, less CPU |
| `messages.no-permission` | friendly MiniMessage text | Consistent with UI_STYLE.md |
| `misc.enable-nether` | false | Vanilla Nether disabled (owner plans a custom use later) |
| `misc.max-joins-per-tick` | 2 | Smooths join spikes after restarts (QUICK1: 3 -> 2, PERFORMANCE P-03) |
| `misc.region-file-cache-size` | 512 | About 300 island region files stay open (QUICK1, P-27) |
| `player-auto-save.rate` / `max-per-tick` | 600 ticks / 10 | Player data saved every 30 s, spread over ticks: shrinks the crash-rollback window (QUICK1, SECURITY F-01) |
| `block-updates.disable-tripwire-updates` | true | Stops string/tripwire-hook dupers (QUICK1). `skip-tripwire-hook-placement-validation` stays false |
| `block-updates.disable-chorus-plant-updates` / `disable-mushroom-block-updates` | true | End is off, mushroom block updates are a lag source (QUICK1) |
| `spam-limiter.tab-spam-limit` | 200 | Tab-complete spam limit (was 500); normal tab use never gets near it (QUICK1) |

Kept on purpose (QUICK1): `item-validation` book/lore limits stay at the defaults (island notes use books; our own gradient lores
can exceed 1,024 bytes per line); `chunk-system` threads stay automatic and JVM flags in `start.sh` are unchanged until the
production hardware is known (PERFORMANCE P-04).

spark is built into Paper. Use `/spark profiler` and `/spark tps`.

## config/paper-world-defaults.yml
| Key | Value | Why |
|---|---|---|
| `chunks.entity-per-chunk-save-limit` | arrows 16, pearls 8, orbs 16, … | Prevents entity build-up and chunk bloat |
| `collisions.max-entity-collisions` | 2 | Mob farms don't melt the CPU |
| `collisions.fix-climbing-bypassing-cramming-rule` | true | Exploit fix |
| `entities.armor-stands.tick` / `do-collision-entity-lookups` | false | Decorative armor stands cost nothing |
| `entities.behavior.disable-chest-cat-detection` | true | Performance |
| `entities.behavior.pillager-patrols.disable` | true | No random patrols on islands |
| `entities.spawning.alt-item-despawn-rate` | cobblestone/deepslate/netherrack 15s, dirt/flesh 30s | Generator spam despawns fast |
| `entities.spawning.disable-mob-spawner-spawn-egg-transformation` | true | **Economy:** players can't change spawner type with a spawn egg |
| `environment.optimize-explosions` | true | Performance |
| `fixes.disable-unloaded-chunk-enderpearl-exploit` | true | Exploit fix |
| `fixes.fix-items-merging-through-walls` | true | Exploit fix |
| `fixes.falling-block-height-nerf` / `tnt-entity-height-nerf` | 320 | Removes sand/gravel/TNT entities launched above the build limit (duper and cannon tricks). Normal falling blocks are never higher (QUICK1) |
| `fixes.prevent-tnt-from-moving-in-water` | true | Stops water-carried TNT dupers and cannons (QUICK1) |
| `chunks.prevent-moving-into-unloaded-chunks` | true | Players (boats, minecarts) can't ride into unloaded chunks (QUICK1) |
| `misc.update-pathfinding-on-block-update` | false | Mobs don't re-path on every block change: lag-machine defence, cheaper farms (QUICK1) |
| `tick-rates.behavior.villager.acquirepoi` | 60 | Villagers look for job sites/beds every 3 s instead of every tick (QUICK1; trading unaffected) |
| `tick-rates.sensor.villager.nearestbedsensor` / `villagerbabiessensor` | 80 / 40 | Fewer villager brain scans (QUICK1) |
| `hopper.ignore-occluding-blocks` | true | Performance |
| `hopper.cooldown-when-full` (paper-world-defaults) | true | A hopper whose target is full waits a full cooldown instead of retrying every tick (F12) |
| `hopper.disable-move-event` (paper-world-defaults) | false | Keep false: other plugins may listen to `InventoryMoveItemEvent`. Our plugin registers no listener for it (MenuService QUICK1, ItemStacker uses `InventoryPickupItemEvent`), so Paper skips building the event for hopper moves anyway (F12) |
| `misc.redstone-implementation` | ALTERNATE_CURRENT | Much faster redstone, same behavior for normal builds |
| `tick-rates.behavior.villager.validatenearbypoi` | 60 | Villager performance |
| `tick-rates.sensor.villager.secondarypoisensor` | 80 | Villager performance |
| `tick-rates.grass-spread` | 4 | Performance |
| `tick-rates.mob-spawner` | 2 | Spawners tick every 2 ticks (no visible difference) |

## Plugins
| File | Change |
|---|---|
| `Geyser-Spigot/config.yml` | `auth-type: floodgate` (Bedrock players join without a Java account), MOTD "SkybornMC / Phoenix Skyblock", server name "SkybornMC". Bedrock port **19132/UDP** must be open |
| `floodgate/config.yml` | Defaults. Bedrock names get the `.` prefix. `key.pem` is secret and never committed |
| `LuckPerms/config.yml` | `server: skyblock`. Storage: H2 locally, **MariaDB on the VPS** (the setup script switches it) |
| `GrimAC/database.yml` | `server-name: skyblock-test` |
| `FastAsyncWorldEdit/config.yml` | Update notifications off |
| `ViaVersion/config.yml` | `block-versions: '<1.21.5'`: players on **1.21.5 up to 26.3** can join. Friendly kick message for older versions. Update checks off, supported versions shown in the server list. Why 1.21.5 and not 1.21.4: ViaVersion warns that the 1.20.5–1.21.4 protocol is easily exploitable |
| `ViaBackwards/config.yml` | Defaults (needed for clients older than the server). Emulated-menu colors changed from blue to the Phoenix red/gold palette. Grim warns that vehicles (boats/minecarts) can glitch for older clients with ViaBackwards; acceptable since vehicles are not core gameplay |
| `PhoenixSkyblock/worlds.yml` | Our worlds: spawn (peaceful, fixed day, no fall damage, no random ticks), islands + outposts (normal difficulty for spawner mobs, time moves, no weather, natural spawning off), depths (fixed night, mobs from the plugin), warzone (PvP on, dusk). Shared defaults: keep inventory, no advancement/death messages, no phantoms/patrols/raids/wardens, no mob griefing, no fire spread, command blocks off |
| `PhoenixSkyblock/motd.yml` | Animated hex-color MOTDs (4 designs), rotation, centering, hover info. Replaces the `server.properties` MOTD for Java clients |
| CoreProtect | **Not yet released for 26.3.** The newest version (24.1) checks the Minecraft version and **refuses to start** above 26.2, so it can't be used yet. `fetch.sh` downloads it automatically once a 26.3 build exists. Config will use MariaDB |

## GrimAC → punishment suggestions (Phase P, spec 13.3)
Grim never punishes by itself on this server. When a check flags often enough, Grim runs our console command
`/psuggest <player> <reason-id> [details]`, which only **suggests**: staff with `skyblock.staff.notify` get
`Steve (Grim: Reach vl 12). Suggested: Hacked client, ban 30d [Apply] [Dismiss] [Punish menu]` and decide (lag and ping
spikes cause false flags, which is why it stays suggestion-only; `extras.never-auto` in `punishments.yml` lists the
reasons that may never be applied without a click). One waiting suggestion per player + reason: further flags are quiet
and only add their details to it (newest 5 lines), so Grim can call it as often as it likes.

No Grim code is used (no API dependency); it is plain Grim config. In `server/plugins/GrimAC/punishments.yml`, add one line
to the `commands:` list of each group you want suggestions from. Format `"<threshold>:<interval> <command>"`; Grim's
placeholders are `%player%`, `%check_name%`, `%vl%`:

```yaml
  Reach:
    commands:
      - "1:1 [alert]"
      - "1:1 [log]"
      - "10:10 psuggest %player% hacking Grim: %check_name% vl %vl%"
  Autoclicker:
    commands:
      - "20:40 [alert]"
      - "1:1 [log]"
      - "40:40 psuggest %player% macro Grim: %check_name% vl %vl%"
```

- The reason id (`hacking`, `macro`, ...) must exist in `punishments.yml` (Tab after `/psuggest <player>` lists them); its
  ladder decides the suggested punishment (next step for that player).
- **The thresholds are the owner's choice** (not set yet, so nothing calls `/psuggest` today). A safe start: only the
  groups with few false flags (Reach, Hitboxes, Knockback, Timer in Simulation) at a high threshold, e.g. `10:10`.
  `remove-violations-after: 300` means the count is per 5 minutes.
- Grim runs the command as the console, which has `skyblock.staff.punish.suggest`. Each call is audited
  (`suggestion_api` / `suggestion_api_refresh`, actor CONSOLE).
- `[proxy]` lines can stay: they do nothing without BungeeCord/Velocity.

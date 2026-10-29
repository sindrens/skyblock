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
| `misc.max-joins-per-tick` | 3 | Smooths join spikes after restarts |

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
| `hopper.ignore-occluding-blocks` | true | Performance |
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

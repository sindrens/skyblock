# Project Context (read before every task)

The facts every task depends on. If something here is out of date, fix it first: a wrong version or assumption produces wrong code and configs.

---

## 1. Tech stack (exact)

| Layer | Choice | Version / notes |
|---|---|---|
| Minecraft (Java) | Latest stable, follows releases | **26.3** (server). Clients **1.21.5 – 26.3** via ViaVersion + ViaBackwards |
| Bedrock | Geyser + Floodgate | Geyser 2.12.x (Bedrock 26.40–26.60), Floodgate 2.2.5. Bedrock names start with `.` |
| Server software | **Paper** | 26.3 (latest build). Not Purpur, not Folia, not Spigot |
| Java | **25** (Temurin locally, OpenJDK 25 on the VPS) | Paper 26.3 requires Java 25 |
| Build | Gradle **9.8.2** (wrapper), Kotlin DSL, shadow 9.6.1, run-paper 3.1.0 | `./gradlew build -x test` |
| Plugin API | `io.papermc.paper:paper-api:26.3.build.<n>-stable` | Paper Brigadier commands, Adventure + MiniMessage, registry-based APIs (`RegistryAccess`), new snake_case game rules (`advance_time`, `pvp`, …) |
| Our code | One plugin jar **PhoenixSkyblock**, package `net.skybornmc.skyblock` | `common` (framework) + `modules/<feature>` + `plugin` (entrypoint) |
| Database | **MariaDB 11.8** on the VPS | Databases: `skyblock`, `luckperms`, `coreprotect`. Local dev: H2 (MySQL mode) |
| OS | Ubuntu 26.04 (test VPS) | systemd service `skyblock`, tmux console, ufw, fail2ban, daily backups |

**Never** produce configs or code for older versions (e.g. 1.20 `config.yml` formats, `GameRule.DO_DAYLIGHT_CYCLE`, legacy `&` colors in our configs, Bukkit command executors). When unsure about an API, check the Paper 26.3 javadoc/jar.

## 2. External plugins (the only ones)

| Plugin | Version | Role | Integration |
|---|---|---|---|
| Geyser-Spigot | 2.12.x | Bedrock crossplay | `auth-type: floodgate` |
| Floodgate | 2.2.5 | Bedrock auth | Floodgate API: detect Bedrock players (`FloodgateApi.isFloodgatePlayer`) |
| LuckPerms | 5.5.x | Ranks/permissions | **LuckPerms API** for prefix, weight, group (softdepend). Staff ranks in `server/permissions/luckperms.txt` |
| GrimAC | 2.3.x | Anticheat | Alerts permission `grim.alerts` |
| FastAsyncWorldEdit | 2.16.x | Builders + **schematic pasting** for islands | FAWE API (softdepend) |
| ViaVersion / ViaBackwards | 5.12.x | Older clients 1.21.5+ | Nothing to do in code |
| Axiom Paper Plugin | 6.0.x (for 26.3) | Builder tool (owner request) for spawn/Depths/warzone builds. Needs the **Axiom client mod** (Java/Fabric only) | Nothing to do in code. Builder group has `axiom.default` + entities + blueprints; islands/outposts blocked in its config |
| CoreProtect | pending 26.3 | Block logging | `fetch.sh` adds it when released |
| spark | built into Paper | Profiling | `/spark` |
| PlaceholderAPI | *optional* | Not installed | Our plugin registers a `%phoenix_…%` expansion **only if** PlaceholderAPI is present |

**Not used (we build our own):** Vault (our EconomyService instead), EssentialsX, DiscordSRV, LiteBans, Citizens, Multiverse, island plugins (BentoBox/SuperiorSkyblock), crate/shop/menu plugins.

## 3. Economy meta (how money is made)

Decided 2026-10-29:

| Source | Share of income | Notes |
|---|---|---|
| **The Depths** (mine, forest, combat drops → satchel → sell) | **~50%** | Main, active income. Rewards playing over AFK |
| **Island production** (farms, spawners, cobble generators, collectors, offline fuel) | **~35%** | Steady secondary income, partly offline |
| **Quests, events, KOTH, envoys, bosses** | **~15%** | Bursts and variety |

- **Big numbers:** start with **$1,000**; mid-game millions; late-game billions. Display compact (`$1.2M`, `$3.4B`).
- Money is stored as a whole-dollar `long` (max ≈ 9.2 quintillion). Tokens, gems and shards are `long` too.
- Dynamic global sell prices (floor 40%, ceiling 150% of base), price floors and interest caps control inflation. No extra money sinks besides gameplay costs (owner decision).
- A **balance sheet** (target earnings per hour/day/week per tier) is made and approved by the owner **before** prices are coded (Phase 3).

## 4. Hardware and performance limits

| Server | Specs | Java heap | Purpose |
|---|---|---|---|
| **Test VPS** (now) | OVH VPS-1, 2 vCPU, 4 GB RAM, 40 GB, Frankfurt | ~2.5 GB (auto) | Development + a few testers |
| **Production** (before beta) | Dedicated, high single-core CPU (Ryzen 9 class), 32–64 GB RAM | 16–24 GB | 300 players |

- Target: **20 TPS, MSPT < 40** at 300 players.
- Paper/Spigot configs are already tuned (`server/CONFIG_NOTES.md`): view 8 / simulation 5, entity limits, hopper-check 8, alternate-current redstone, spawner mobs without AI, no natural spawning on islands.
- Code rules: no blocking I/O on the main thread; async DB; cache; batch writes; no per-tick loops over all players unless required; offline islands are **simulated**, never kept loaded.

## 5. Who does what

| Who | Does |
|---|---|
| **Claude (developer)** | Writes all Java code, configs, scripts and docs. Builds with `./gradlew build -x test`. Commits, pushes to GitHub and releases to the test VPS (`server/scripts/deploy.sh`). **Never runs tests or test servers without the owner's permission.** Writes unit tests so they are ready when the owner asks |
| **Owner (Sindre / SlickyMatt, in-game: Pouched)** | Decides design, tests every milestone in-game with the checklist, approves balance sheets and builds |
| **Builders** (later) | Replace placeholder builds (spawn, Depths, warzone, island schematics) using the builder brief |

- **No Skript**, no config-only plugin stacks: everything is Java in our plugin.
- **Releases:** after **every milestone**, with a short test checklist for the owner.
- **Debugging:** paste the full console error (stack trace) + the related config. The developer fixes root causes.

## 6. Naming and branding

- Network: **SkybornMC** (`play.skybornmc.net`). Gamemode: **Phoenix Skyblock** (phoenix theme, mainly red; see `UI_STYLE.md`).
- Chat prefix: **Phoenix »**. Chat line: `[Title] [RANK] Name » message` (hover: island, level, playtime, Discord; click: `/msg`).
- Placeholders: internal keys like `island_level`; PlaceholderAPI form `%phoenix_island_level%`.

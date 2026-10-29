# Roadmap

**One phase at a time. One milestone at a time.** Order decided 2026-10-29: Islands → Economy → Depths. Detailed plans: [`phases/`](phases/README.md). A phase starts only when the previous one meets the Definition of Done ([`STANDARDS.md`](STANDARDS.md) §4). Order is chosen so every phase builds on a tested foundation.

Target: everything below is in season 1. Launch is in ~3 years. There is time to do it properly.

| Phase | Name | Contents | Status |
|---|---|---|---|
| 0 | **Foundation** ([plan](phases/PHASE-0-FOUNDATION.md)) | Config, style/messages, database (MariaDB/H2, migrations), player data, commands, menus, items, services/placeholders (+ optional PlaceholderAPI), **audit log** | 🟡 Repo, deploy, worlds, MOTD, `/unban`, M0.1–M0.7 done |
| 1 | **Core player experience** ([plan](phases/PHASE-1-CORE.md)) | Spawn/join flow, teleports, chat + ranks everywhere (chat/tab/nametags), scoreboard, tab, `/menu`, `/help`, `/settings`, custom punishments, staff mode, **`/logs` viewer**, reports, `/admin` | ⬜ |
| 2 | **Islands** ([plan](phases/PHASE-2-ISLANDS.md)) | Grid, creation (placeholder schematic), protection, roles + permission GUI, solo → team, visiting/warps/ratings, island menu, stats tracking, admin tools | ⬜ |
| 3 | **Economy** ([plan](phases/PHASE-3-ECONOMY.md)) | Balance sheet first, currencies + ledger, `/pay`, `/transactions`, notes, banks + interest, dynamic `/sell`, builder shop, auction house, secure `/trade`, economy dashboard | ⬜ |
| 4 | **Tools and The Depths** ([plan](phases/PHASE-4-DEPTHS.md)) | Placeholder Depths build, starter kit + Forge + living tools + blueprints + skins, per-player blocks, satchel, skill core (XP/levels), tiers + `/dungeon`, forest, combat zones, tokens | ⬜ |
| 5 | **Progression core** | 5 pillars + island level, `/is upgrade` masteries with tasks, leaderboards (solo/team/weekly) | ⬜ |
| 6 | **Skills, sets, enchants** | 5 skills, `/sets`, `/enchanter` custom enchants, tokens economy tuning | ⬜ |
| 7 | **Island production** | Custom generators, custom crops, spawners + stacking, mob stacking, collectors/auto-sell (T1–5 merge), Island Fuel, outposts + biomes + fusion | ⬜ |
| 8 | **PvE** | Custom mobs, world/summon bosses + damage leaderboard, pets, fishing | ⬜ |
| 9 | **Conquest and events** | KOTH, envoys, PvP zones, duels, automatic event engine | ⬜ |
| 10 | **Rewards and retention** | Crates + keys, voting, daily rewards, quests, battle pass, achievements, collection log, Black Market, tutorial | ⬜ |
| 11 | **Store, ranks, Discord** | Tebex credits/gems, 5 ranks + perks, Discord linking/announcements/tickets | ⬜ |
| 12 | **Seasons** | Season reset tool, pre-season mode, carry-over (titles, cosmetics, ranks), Final Week | ⬜ |
| 13 | **Balance and launch prep** | Full balance pass, bot load test to 300+ players, closed beta, bugfix | ⬜ |

## Phase 0 detail (current)

- [x] Repository, `.gitignore`, docs, CI workflow
- [x] Gradle multi-module skeleton (`common`, `plugin`, `modules/`)
- [x] `SkyblockModule` interface + module registry
- [x] Minecraft 26.3 / Java 25 / Gradle 9 toolchain
- [x] Test server: tuned Paper configs (`server/`, see `server/CONFIG_NOTES.md`), external plugins via `server/scripts/fetch.sh`, start script, VPS setup script, deploy-to-test workflow
- [ ] CoreProtect: add once it releases a 26.3 build (`fetch.sh` picks it up automatically)
- [x] ViaVersion + ViaBackwards (Java 1.21.5 to 26.3)
- [x] Worlds module: spawn, islands, outposts, depths, warzone as configured void worlds (`worlds.yml`), `/skyblock worlds`, `/skyblock tp`
- [x] MOTD module: animated hex-gradient MOTDs, rotation, centering, hover info (`motd.yml`)
- [x] Staff ranks + permissions (LuckPerms, applied automatically on update/deploy)
- [x] Deploy tooling: `install-on-vps.sh`, `update-vps.sh`, `setup-github-deploy.sh` (push to main = deploy to test)
- [x] Config framework (typed config objects, reload, validation)
- [x] Messages framework (MiniMessage, placeholders, prefix, style.yml palette, sounds, Bedrock fallbacks)
- [x] Database layer (MariaDB/H2, HikariCP, virtual-thread executor, migrations)
- [x] GUI framework (frame, paged lists, confirm, number/player picker, chat input, click protection)
- [x] Command framework (Brigadier, enforced tab completion, cooldowns, confirm, typed args, help index)
- [x] Player data cache (load before join, autosave, save on quit/shutdown, name history)
- [x] Item framework (registry, PDC id/UUID, lore builder, dupe detection)
- [ ] Local test server workflow (`./gradlew runServer`) documented

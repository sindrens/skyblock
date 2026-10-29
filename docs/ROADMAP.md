# Roadmap

**One phase at a time. One feature at a time.** A phase starts only when the previous one meets the Definition of Done ([`STANDARDS.md`](STANDARDS.md) §4). Order is chosen so every phase builds on a tested foundation.

Target: everything below is in season 1. Launch is in ~3 years. There is time to do it properly.

| Phase | Name | Contents | Status |
|---|---|---|---|
| 0 | **Foundation** | Repo, Gradle, CI, module system, config + messages framework, database layer (HikariCP, migrations), GUI framework (Bedrock-safe), command framework, player data cache, scheduler utils, test harness | 🟡 Repo + skeleton done |
| 1 | **Core player experience** | Spawn, `/menu`, `/help`, teleports (`/spawn`, `/tpa`, `/back`, `/warp`), scoreboard/tab/chat format, `/staff` mode, `/admin` config GUI base, audit log, LuckPerms integration | ⬜ |
| 2 | **Economy** | Currencies (money, tokens, gems, shards), `/bal`, `/pay`, notes, banks + interest, dynamic `/sell`, auction house, player shops | ⬜ |
| 3 | **Islands** | Chunk grid, schematic creation, roles + permission GUI, solo → team conversion, visit/lock/notifications, warps, island bank hookup, `/is stats` tracking | ⬜ |
| 4 | **Omnitool and The Depths** | Omnitool, per-player packet blocks, mine tiers, forest tiers, combat zones, `/dungeon` tier GUI, visibility setting | ⬜ |
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
- [ ] Config framework (typed config objects, reload, validation)
- [ ] Messages framework (MiniMessage, placeholders, prefix)
- [ ] Database layer (HikariCP, async executor, migrations)
- [ ] GUI framework (paged menus, back/close, Bedrock-safe)
- [ ] Command framework (subcommands, tab completion, permissions, usage help) (evaluate Paper Brigadier API)
- [ ] Player data cache (load on join, async save, save on quit/shutdown)
- [ ] Local test server workflow (`./gradlew runServer`) documented

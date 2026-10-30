# Phase Plans

Each phase has its own detailed plan. A phase is split into **milestones**; every milestone is released to the test server with a test checklist for the owner (`CONTEXT.md` §5).

| Phase | Plan | Summary |
|---|---|---|
| 0 | [PHASE-0-FOUNDATION.md](PHASE-0-FOUNDATION.md) | Framework every feature uses: config, messages/style, database, player data, commands, menus, items, services, **logging (audit) system** |
| 1 | [PHASE-1-CORE.md](PHASE-1-CORE.md) | The network feel: spawn, teleports, chat + ranks everywhere, scoreboard, tab, nametags, `/menu`, `/help`, `/settings`, punishments, staff mode, **staff log viewer**, reports, `/admin` |
| 2 | [PHASE-2-ISLANDS.md](PHASE-2-ISLANDS.md) | Islands: grid, creation, protection, members/roles, visiting, warps, island menu, stats tracking, admin tools |
| 3 | [PHASE-3-ECONOMY.md](PHASE-3-ECONOMY.md) | Currencies + ledger, `/pay`, notes, banks + interest, selling + dynamic prices, builder shop, auction house, secure trade, economy dashboard |
| 4 | [PHASE-4-DEPTHS.md](PHASE-4-DEPTHS.md) | Tools (starter kit, Forge, living tools, blueprints, skins), per-player ore/log blocks, satchel, mine/forest/combat tiers, skill core (XP/levels), crystals, `/dungeon` |
| 5 | [PHASE-5-PROGRESSION.md](PHASE-5-PROGRESSION.md) | Pillars, dynamic island level (8 sources), task engine, `/is upgrade` upgrades + masteries + Ascension, paid sizes above 9x9, leaderboards (solo/team/weekly, Hall of Heroes), staff tools |
| 7 | [PHASE-7-PRODUCTION.md](PHASE-7-PRODUCTION.md) | Island Fuel (active time, AFK pool, Fuel Cells), custom generators, 11 crop tiers + Farming skill, harvester hoe + rod tiers, chunk collectors + auto-sell chests (T1-5 merge), offline simulation, spawner additions, basic fishing, outposts + biomes + fusion; unblocks E6 |
| 10 | [PHASE-10-REWARDS.md](PHASE-10-REWARDS.md) | Crates + keys (7 crates, tables, pity), boosters, voting + vote party, 30-day daily calendar, playtime rewards, quests (daily/weekly/island), battle pass, achievements (link), collection log, Black Market (shards), tutorial (link) |

Later phases (6+): skills perks/sets/enchants, island production + outposts, PvE/bosses/pets, conquest/events, rewards/crates/quests, store/ranks/Discord, seasons, balance + launch. They get the same level of detail before they start.

## Rules for every phase
1. **Read first:** `CONTEXT.md`, `DESIGN.md`, `STANDARDS.md`, `UI_STYLE.md`, `REVIEW.md`, and the phase plan.
2. **No guessing.** Anything unclear goes to the owner as a question *before* coding. Unanswered questions are listed at the end of each plan with the default that applies if the owner doesn't object.
3. **Every action that changes data is logged** through the audit log (Phase 0 M0.8), so staff can always see who did what.
4. **Every feature has:** GUI + command, `/help` entry, configurable messages/values, permissions in the module's `permissions/<module>.yml` + `docs/commands/<module>.md`, Bedrock-safe menus, audit events, and a test checklist.
5. **Build:** `./gradlew build -x test`. Tests are written but only run when the owner allows it.

# Work queue (rolling)

Rules: max 3 agents at once; an item starts when its dependencies are merged and its lane does not overlap a running agent. Merge each finished agent immediately (build `-x test` + `compileTestJava` on its branch first), update STATUS.md + this file, release, start the next item.
Status: ⬜ todo · 🔄 running · ✅ merged · ⏸ parked

## Phase 1 follow-ups
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| F1 | Hook remaining `/settings` toggles: join-messages, tpa-requests (essentials), scoreboard (hud), ui-sounds | Phase 1 | modules/essentials, modules/hud | ⬜ |
| F2 | Per-module permission files instead of shared `plugin.yml`/`luckperms.txt`/`COMMANDS.md` | — | common/command, plugin, server/permissions, scripts | ✅ |
| F3 | Achievements system (progress tracking → `Cosmetics.grantTitle`) | Phase 2+ stats | new modules/achievements | ⬜ |

## Phase 2: Islands (docs/phases/PHASE-2-ISLANDS.md) — one lane, sequential
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| M2.1 | Grid, creation (FAWE placeholder schematic), 3×3 chunks, per-player border, home | Phase 1 | modules/islands | 🔄 |
| M2.2 | Protection (role perms, Y 0–256, cross-border) | M2.1 | modules/islands | ⬜ |
| M2.3 | Members & roles, invites (solo→team warning), coop, limits | M2.2 | modules/islands | ⬜ |
| M2.4 | Visiting, lock, ban, warps, ratings | M2.3 | modules/islands | ⬜ |
| M2.5 | Island menu + settings, `/is chat` | M2.3 | modules/islands | ⬜ |
| M2.6 | Stats tracking (daily/weekly buckets) | M2.3 | modules/islands | ⬜ |
| M2.7 | Lifecycle (disband/reset) + `/isadmin` | M2.3 | modules/islands | ⬜ |

## Phase 3: Economy (docs/phases/PHASE-3-ECONOMY.md)
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| M3.0 | Balance sheet `docs/ECONOMY_BALANCE.md` (owner approves before prices go live) | — | docs only | 🔄 |
| M3.1 | Currencies + ledger, EconomyService, `/bal /baltop /pay /transactions /eco` | Phase 1 | modules/economy | ⬜ |
| M3.2 | Money notes (`/withdraw`, `/eco note`) | M3.1 | modules/economy | ⬜ |
| M3.4 | Selling + dynamic prices (`/sell`) | M3.1 | modules/economy (sell pkg) | ⬜ |
| M3.5 | Builder shop `/shop` | M3.1 | modules/shop | ⬜ |
| M3.6 | Auction house `/ah` | M3.1 | modules/auction | ⬜ |
| M3.7 | Secure trade `/trade` | M3.1 | modules/trade | ⬜ |
| M3.3 | Banks + interest (personal + island) | M3.1, M2.3 | modules/economy (bank pkg) | ⬜ |
| M3.8 | Economy dashboard + anomaly alerts | M3.1–M3.7 | modules/economy (admin pkg) | ⬜ |

## Phase 4: Tools + The Depths (docs/phases/PHASE-4-DEPTHS.md)
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| M4.3 | Per-player blocks engine (depleted state per player, 10 s respawn, timing wheel) | Phase 1 | modules/depths (engine pkg) | 🔄 |
| M4.1 | Depths layout from `depths.yml` (placeholder zones by code, U-shaped wings) | M4.3 | modules/depths (zones pkg) | ⬜ |
| M4.5 | Skill core (XP/levels, `/skills`) | Phase 1 | modules/skills | ⬜ |
| M4.4 | Satchel | M4.3, M3.4 | modules/depths (satchel pkg) | ⬜ |
| M4.6 | Tiers, unlocks, `/dungeon` | M4.1, M4.5 | modules/depths | ⬜ |
| M4.2 | Tools: starter kit (24 h), Forge, living tools (perk slots 5/15/30/45 + prestige), blueprints, skins; `/tool give`, `/blueprint give`, `/skin give` | M3.1, M4.5 | modules/tools | ⬜ |
| M4.7 | Forest | M4.3, M4.1 | modules/depths | ⬜ |
| M4.8 | Combat zones (shared mobs) | M4.1 | modules/depths (combat pkg) | ⬜ |
| M4.9 | Drops + tokens | M4.4, M3.1 | modules/depths | ⬜ |

## Later (not planned in detail yet)
Crates (`/crate give`), quests, progression (pillars, island level, `/is upgrade`), enchants/sets, production/outposts, PvE/bosses/pets, conquest/events, store/ranks/Discord, seasons.

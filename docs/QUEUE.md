# Work queue (rolling)

Rules: max 3 agents at once; an item starts when its dependencies are merged and its lane does not overlap a running agent. Merge each finished agent immediately (build `-x test` + `compileTestJava` on its branch first), update STATUS.md + this file, release, start the next item.
Status: ⬜ todo · 🔄 running · ✅ merged · ⏸ parked

## Phase 1 follow-ups
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| F1 | Hook remaining `/settings` toggles: join-messages, tpa-requests (essentials), scoreboard (hud), ui-sounds | Phase 1 | modules/essentials, modules/hud | ⬜ |
| F2 | Per-module permission files instead of shared `plugin.yml`/`luckperms.txt`/`COMMANDS.md` | — | common/command, plugin, server/permissions, scripts | ✅ |
| F4 | Move a TeleportService interface (warmup, guards) into common so /dungeon, /spawn, /tpa, /is home share one warmup | — | common, essentials, depths | ⬜ |
| F3 | Achievements system (progress tracking → `Cosmetics.grantTitle`) | Phase 2+ stats | new modules/achievements | ⬜ |

## UX polish (owner, 2026-10-30) — top priority, starts at the next free slot
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| UX1 | Rank change shows on scoreboard/tab instantly; `/baltop` fast + async (short cache, refresh after big changes); all messages short, direct, normal words, one consistent color scheme and format (UI_STYLE 3-role palette); tag after the name in parentheses-style; cleaner scoreboard + tab with better colors | — | modules/hud, chat, cosmetics, economy (baltop), social, essentials, perks, staff, logs, menus, auction + their messages; style.yml; UI_STYLE §3/§6 | 🔄 |
| UX2 | Same message cleanup for moderation, depths, islands messages (after P1, M4.6, M2.7 merge) | UX1, P1, M4.6, M2.7 | their messages files | ⬜ |
| FI | Owner answers `docs/FEATURE_IDEAS.md` (yes/no) → yes-items get queued | owner | docs | ⬜ |

## Phase P: Punishment system v2 (owner spec `docs/specs/PUNISH_SPEC.md` + `PUNISH_SPEC_NOTES.md`) — one lane (modules/moderation), sequential, priority
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| P0 | Bugs: freeze doesn't stop movement (and after relog), frozen player must not break/chat/do anything; staff-mode vanish item placeholder name; spyglass tool zooms; YAML boolean keys (fixed in main) | — | modules/staff, modules/essentials (vanish) | ✅ |
| P1 | Spec §1–11: `/p` single-screen menu from YAML, warn counter + rules (notify/auto), `/warns`, validation + safe reload + build test of tracked YAML; release script pulls owner's GitHub edits | P0 | modules/moderation, server/scripts | ✅ |
| P2 | §12.1 undo, §12.2 double-punish, §12.3 offence expiry, §12.10 `/pstats` | P1 | modules/moderation | 🔄 |
| P3 | §12.4 evidence, 12.5 notes, 12.6 ack, 12.7 weights, 12.8 suggestion API, 12.9 report link, 12.11 Discord sink, 12.12 `/mypunishments` | P2 | modules/moderation, modules/staff (report API), common (DiscordSink) | ⬜ |
| P4 | §13.4 approval, 13.13 mute ended, 13.7 probation, 13.5 watchlist | P3 | modules/moderation | ⬜ |
| P5 | §13.8 bulk, 13.11 punish from logs, 13.10 appeals (in-game + webhook) | P4 | modules/moderation, modules/logs (API) | ⬜ |
| P6 | §13.9 raid lockdown, 13.3 anticheat suggestions (`/psuggest`, Grim config notes), 13.12 network-ready | P5 | modules/moderation, server/CONFIG_NOTES.md | ⬜ |
| P7 | §13.1 sanctions (economy SanctionProvider), 13.2 island punishments | P6, M2.3 | modules/moderation, modules/economy, modules/islands | ⬜ |
| P8 | §14.2 rule numbers, 14.9 notification settings, 14.6 training mode | P7 | modules/moderation | ⬜ |
| P9 | §14.1 guard + rollback, 14.5 false reports, 14.10 purge | P8 | modules/moderation, modules/staff | ⬜ |
| P10 | §14.8 monthly report, 14.11 "Honorable" title | P9 | modules/moderation, modules/cosmetics | ⬜ |

## Phase 2: Islands (docs/phases/PHASE-2-ISLANDS.md) — one lane, sequential
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| M2.1 | Grid, creation (FAWE placeholder schematic), 3×3 chunks, per-player border, home | Phase 1 | modules/islands | ✅ |
| M2.2 | Protection (role perms, Y 0–256, cross-border) | M2.1 | modules/islands | ✅ |
| M2.3 | Members & roles, invites (solo→team warning), coop, limits | M2.2 | modules/islands | ✅ |
| M2.4 | Visiting, lock, ban, warps, ratings | M2.3 | modules/islands | ⬜ |
| M2.5 | Island menu + settings, `/is chat` | M2.3 | modules/islands | ⬜ |
| M2.6 | Stats tracking (daily/weekly buckets) | M2.3 | modules/islands | ⬜ |
| M2.7 | Lifecycle (disband/reset) + `/isadmin` | M2.3 | modules/islands | 🔄 |

## Phase 3: Economy (docs/phases/PHASE-3-ECONOMY.md)
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| M3.0 | Balance sheet `docs/ECONOMY_BALANCE.md` (owner approves before prices go live) | — | docs only | ✅ (draft, owner approval pending) |
| M3.1 | Currencies + ledger, EconomyService, `/bal /baltop /pay /transactions /eco` | Phase 1 | modules/economy | ✅ |
| M3.2 | Money notes (`/withdraw`, `/eco note`) | M3.1 | modules/economy | ✅ |
| M3.4 | Selling + dynamic prices (`/sell`) | M3.1 | modules/economy (sell pkg) | ⬜ |
| M3.5 | Builder shop `/shop` | M3.1 | modules/shop | ⬜ |
| M3.6 | Auction house `/ah` | M3.1 | modules/auction | ✅ |
| M3.7 | Secure trade `/trade` | M3.1 | modules/trade | ⬜ |
| M3.3 | Banks + interest (personal + island) | M3.1, M2.3 | modules/economy (bank pkg) | ⬜ |
| M3.8 | Economy dashboard + anomaly alerts | M3.1–M3.7 | modules/economy (admin pkg) | ⬜ |

## Phase 4: Tools + The Depths (docs/phases/PHASE-4-DEPTHS.md)
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| M4.3 | Per-player blocks engine (depleted state per player, 10 s respawn, timing wheel) | Phase 1 | modules/depths (engine pkg) | ✅ |
| M4.1 | Depths layout from `depths.yml` (placeholder zones by code, U-shaped wings) | M4.3 | modules/depths (zones pkg) | ✅ |
| M4.5 | Skill core (XP/levels, `/skills`) | Phase 1 | modules/skills | ✅ |
| M4.4 | Satchel | M4.3, M3.4 | modules/depths (satchel pkg) | ⬜ |
| M4.6 | Tiers, unlocks, `/dungeon` | M4.1, M4.5 | modules/depths | ✅ |
| M4.2 | Tools: starter kit (24 h), Forge, living tools (perk slots 5/15/30/45 + prestige), blueprints, skins; `/tool give`, `/blueprint give`, `/skin give` | M3.1, M4.5 | modules/tools | ⬜ |
| M4.7 | Forest | M4.3, M4.1 | modules/depths | ⬜ |
| M4.8 | Combat zones (shared mobs) | M4.1 | modules/depths (combat pkg) | ⬜ |
| M4.9 | Drops + tokens | M4.4, M3.1 | modules/depths | ⬜ |

## Later (not planned in detail yet)
Crates (`/crate give`), quests, progression (pillars, island level, `/is upgrade`), enchants/sets, production/outposts, PvE/bosses/pets, conquest/events, store/ranks/Discord, seasons.

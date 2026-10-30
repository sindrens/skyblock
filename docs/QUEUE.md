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
| UX1 | Rank change shows on scoreboard/tab instantly; `/baltop` fast + async (short cache, refresh after big changes); all messages short, direct, normal words, one consistent color scheme and format (UI_STYLE 3-role palette); tag after the name in parentheses-style; cleaner scoreboard + tab with better colors | — | modules/hud, chat, cosmetics, economy (baltop), social, essentials, perks, staff, logs, menus, auction + their messages; style.yml; UI_STYLE §3/§6 | ✅ |
| UX1b | Tab (owner 2026-10-30): remove the `/menu /help /discord /store` footer line; nicer TPS + `play.skybornmc.net`; the lines beside SKYBORN at the top match the colors (one long gradient, or left line white and right line the last letter's color) | UX1 | modules/chat (tab) | ✅ (done inside UX1) |
| UX2 | Same message cleanup for moderation, depths, islands messages (after P1, M4.6, M2.7 merge) | UX1, P1, M4.6, M2.7 | their messages files | ⬜ |
| FI | Owner answered `docs/FEATURE_IDEAS.md` (2026-10-30); follow-up questions in `docs/QUESTIONS_ROUND2.md` → then queue the yes-items | owner | docs | 🔄 waiting for owner |
| PROF | **Island profiles** per docs/DECISIONS_2026-10-30.md (P1–P12): profile layer in common (ProfileService, active profile per player, `/profiles`), migrate every module's per-player data to (player, profile) keys, inventory/ender chest/armor/XP swap, zero transfer between own profiles | — | common + all modules (one agent, careful, big) | ✅ |
| PROF-ADOPT | Modules merged after PROF (tools M4.2, stacking, enchants, notes…) store per-player data per profile via ProfileService | PROF | each new module | ✅ tools (Forge money, refunds, starter kit) + stacking (item merges keep profile tags) done at merge; enchants (E1 built with profile keys) |
| CRY | Replace tokens with **island crystals** everywhere (currency enum, messages, HUD, docs) | — | common/economy, modules/economy, hud, docs | ✅ |
| R2B | Economy part of round 2: new-account limit 30 min for /pay + /withdraw (economy.yml notes/pay), `/eco` for admins via permission, sell multiplier cap config 300% | — | modules/economy | ✅ (in R2A) |
| R2A | Round-2 quick decisions: account-age 30 min (pay/withdraw/AH), `/eco` for admin permission, remove amount suggestions (10k/10m/10b) in tab completion, sell cap 300% (config), freeze cleared by a ban, Inspect tool = blaze rod, public announcements unless -s, timezone Europe/Oslo, skill cap 150, staff `/skills set|add` (admin+owner), `/freerank` (30 min playtime) | — | economy, auction, common/command, moderation, staff, skills | ✅ |
| ISL2 | Islands round 2: role Admin (+ old leader → Admin on transfer), hurt-monsters role permission, join cooldown from last join only, `/is settings` flags, island fly for everyone (perm/mastery), likes per player, member homes (1 + masteries), invites menu | M2.4 | modules/islands | ✅ |
| STACK | Stacking module: mobs (1024, weapon-based kills, no AI for spawner mobs, breeding by food, max 8 stacks/chunk/type, no despawn), items, spawners (8→512, menu, ignore conditions, crops grow without light) | answered (round 2+3) | new modules/stacking | ✅ |
| NOTES | `/is notes` (N1–N9: shared to-do list, temporary book, pinned holograms) | M2.4 | modules/islands | ✅ |
| SB | Per-place scoreboards (island, Depths, spawn, warzone) | UX1 | modules/hud | ⬜ |
| BAL2 | Re-run the balance model with the +300% sell cap and the owner's progression wish (#61: fast start, progressively harder, ups and downs); update ECONOMY_BALANCE | R2B | docs + tools/economy_model.py | ✅ |
| PLAN-ENCH | Plan doc: full custom enchant list (levels, effects, how each is obtained: mob kills, mining, chopping, dungeons, crates, /enchanter, crystals), synergy with tools/sets/stacking kills | — | docs/plans/ENCHANTS.md | ✅ docs/plans/ENCHANTS.md |
| PLAN-SETS | Plan doc: `/wardrobe` (was /sets) + `/loadout` presets swapping armor + pet in one action; (gem/crate/currency sets priced by strength, set levels 0→50 with multipliers, merging two sets resets level) | — | docs/plans/SETS.md | ✅ docs/plans/WARDROBE.md |
| STORE2 | Island storage: mastery delivery tasks take items from storage; daily take-out limit per role; staff-only recall of items taken by kicked members | islands storage built | modules/islands | ⬜ |
| PLAN-RP | Plan doc: resource pack (items, borders, crates, menus, font icons, sounds; Java + Geyser) | PLAN-ENCH | docs/plans/RESOURCE_PACK.md | ⬜ |
| FREERANK | `/freerank`: lowest rank, cosmetic color, small perks, pick 1 of 3 tags, forever (after 30 min playtime) | — | modules/perks or cosmetics | ✅ (in R2A) |
| AFK | AFK pool (fuel + money + keys, better loot over time, max 8 h/day) | economy, islands fuel | new module | ⬜ (needs balance numbers) |

## Phase P: Punishment system v2 (owner spec `docs/specs/PUNISH_SPEC.md` + `PUNISH_SPEC_NOTES.md`) — one lane (modules/moderation), sequential, priority
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| P0 | Bugs: freeze doesn't stop movement (and after relog), frozen player must not break/chat/do anything; staff-mode vanish item placeholder name; spyglass tool zooms; YAML boolean keys (fixed in main) | — | modules/staff, modules/essentials (vanish) | ✅ |
| P1 | Spec §1–11: `/p` single-screen menu from YAML, warn counter + rules (notify/auto), `/warns`, validation + safe reload + build test of tracked YAML; release script pulls owner's GitHub edits | P0 | modules/moderation, server/scripts | ✅ |
| P2 | §12.1 undo, §12.2 double-punish, §12.3 offence expiry, §12.10 `/pstats` | P1 | modules/moderation | ✅ |
| P3 | §12.4 evidence, 12.5 notes, 12.6 ack, 12.7 weights, 12.8 suggestion API, 12.9 report link, 12.11 Discord sink, 12.12 `/mypunishments` | P2 | modules/moderation, modules/staff (report API), common (DiscordSink) | 🔄 |
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
| M2.4 | Visiting, lock, ban, warps, ratings | M2.3 | modules/islands | 🔄 |
| M2.5 | Island menu + settings, `/is chat` | M2.3 | modules/islands | ⬜ |
| M2.6 | Stats tracking (daily/weekly buckets) | M2.3 | modules/islands | ⬜ |
| M2.7 | Lifecycle (disband/reset) + `/isadmin` | M2.3 | modules/islands | ✅ |

## Phase 3: Economy (docs/phases/PHASE-3-ECONOMY.md)
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| M3.0 | Balance sheet `docs/ECONOMY_BALANCE.md` (owner approves before prices go live) | — | docs only | ✅ (draft, owner approval pending) |
| M3.1 | Currencies + ledger, EconomyService, `/bal /baltop /pay /transactions /eco` | Phase 1 | modules/economy | ✅ |
| M3.2 | Money notes (`/withdraw`, `/eco note`) | M3.1 | modules/economy | ✅ |
| M3.4 | Selling + dynamic prices (`/sell`) | M3.1 | modules/economy (sell pkg) | 🔄 |
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
| M4.2 | Tools: starter kit (24 h), Forge, living tools (perk slots 5/15/30/45 + prestige), blueprints, skins; `/tool give`, `/blueprint give`, `/skin give` | M3.1, M4.5 | modules/tools | ✅ |
| M4.7 | Forest | M4.3, M4.1 | modules/depths | ⬜ |
| M4.8 | Combat zones (shared mobs) | M4.1 | modules/depths (combat pkg) | ⬜ |
| M4.9 | Drops + tokens | M4.4, M3.1 | modules/depths | ⬜ |

## Phase E: Enchants (docs/plans/ENCHANTS.md)
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| E1 | Registry (enchants.yml, 48 defs + validation test), unique DB books + Disenchant Scrolls, `/enchanter` GUI (apply/upgrade/combine/remove/rolls/guide), slot/conflict/level rules, crystal costs, lore, EnchantService + BonusService in common | M4.2, CRY | modules/enchants, common/enchant, common/bonus | ✅ |
| E2 | Book drops from Depths mine/forest/combat, gathering + area effects via the block engine, Depths sword effects | E1, M4.7, M4.8, M4.9 | modules/enchants + depths hooks | ⬜ |
| E3 | Stacked-mob kill enchants (Slaughter etc.), batch kill API | E1, STACK | modules/enchants + stacking API | ⬜ |
| E4 | Tuning: enchant factors in economy model, family caps, drop rates | E2, E3, BAL2 | docs, tools, configs | ⬜ |
| E5 | Armor enchants on wardrobe set pieces | E1, W2 | modules/enchants + wardrobe | ⬜ |
| E6 | Hoe + rod effects, farming/fishing pools | E1, Phase 7 | modules/enchants | ⬜ |

## Phase W: Wardrobe + loadout (docs/plans/WARDROBE.md)
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| W1 | Wardrobe core: sets.yml (16 sets), per-profile tables, virtual entries + locked real armor, Wardrobe Chest with escrow, buy with money/crystals/gems (Sigils), Set Tokens, /wardrobe GUI, bonuses via BonusService | PROF, CRY, E1 (common/bonus) | modules/wardrobe, common/wardrobe | 🔄 |
| W2 | Set levels, merging, warzone normalization | W1, M4.5 | modules/wardrobe | ⬜ |
| W3 | /loadout presets (2 free, rank/credit slots), atomic apply, guards, 2 s cooldown, pet hook | W1, M4.2 | modules/wardrobe, modules/tools | ⬜ |
| W4 | Balance: set factors in economy model | W2, W3, BAL2 | docs, tools | ⬜ |

## Later (not planned in detail yet)
Crates (`/crate give`), quests, progression (pillars, island level, `/is upgrade`), enchants/sets, production/outposts, PvE/bosses/pets, conquest/events, store/ranks/Discord, seasons.

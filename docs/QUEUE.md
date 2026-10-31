# Work queue (rolling)

Rules: max 3 agents at once; an item starts when its dependencies are merged and its lane does not overlap a running agent. Merge each finished agent immediately (build `-x test` + `compileTestJava` on its branch first), update STATUS.md + this file, release, start the next item.
Status: ⬜ todo · 🔄 running · ✅ merged · ⏸ parked

## GUI round 1 (design session 31 Oct; files in docs/design/, decisions page https://claude.ai/artifact/Jk4hUDpho4iWzH9kzHFsHp db answers/gui1)
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| GUI-BUGS ✅ | Safe fixes from docs/design/UX_REVIEW.md §5 + §3 that need no decision: /trade Info/Help/Close must not cancel the trade (Back instead of Cancel at 45, same in money picker); /trash confirm + Close button; /p: permanent ban (and any ban) needs a confirm screen, add frame; /freerank pick + booster use + satchel upgrade/sell-all + spawner Take all get a confirm with the details on the middle item; daily screen stops overwriting its border; wardrobe staff-entry duplicate "Set to Lv 40"; staff outcome lore says Invalid counts as a false report; hide placeholder tiles that do nothing (Masteries, Weekly gain, Pillars); stale NPC texts ("opens soon" for crates/daily); tutorial Help step wording + Menu step completes on /menu; one welcome on first join | GUI-1 (+ GUI-1 handoff in docs/design/MENU_FIXES.md: moderation 3-row double Back, economy WatchMenu frame, TransactionsMenu Refresh on 52, perm ban twoStep, hand-made state items in economy/bank/moderation, DailyMenu tabs reopen) | trade, perks, moderation, rewards, boosters, depths, stacking, wardrobe, staff, progression, npcs, tutorial, essentials | ⬜ |
| GUI-1 ✅ | Menu framework in common/menu (GUI_DESIGN §9.1): bottom bar 45 Back / 48 ? / 49 Close / 50 Main menu / 52-53 pages, 3- and 4-row standards, title rule `Feature » Screen`, locked/empty/loading/error items, confirm details on 13, tabs refresh in place (answers: docs/OWNER_ANSWERS_GUI1.md) | — | common/menu | ⬜ |
| GUI-2 ✅ | /menu hub 15 buttons in 4 groups + You menu + Travel menu (replaces UX3 Warps), Back-to-hub from every screen opened from the hub | GUI-1 | menus | ⬜ |
| GUI-3 ✅ | Shop v2 (docs/design/SHOP_DESIGN.md): 9 tabs, buy page (1/16/64/fill/type), catalog migration ~253 items, undo + big-buy rule (F14, toggle) | GUI-1 | shop | ⬜ |
| GUI-4 ✅ | Per-menu fixes from docs/design/MENU_FIXES.md, module by module (staff/moderation last) | GUI-1 | many | ⬜ |
| GUI-5 ✅ | Bedrock popups instead of clickable chat (TPA, island invites, store/vote/Discord links, [Tutorial]) | GUI-1 | essentials, islands, social, tutorial | ⬜ |
| GUI-6 | Pack art on the final slots (RP1–RP3) | GUI-4, art | resource pack | ⬜ |

## REVIEW3 + owner feedback 30 Oct (top priority; answers in docs/OWNER_ANSWERS_REVIEW3.md, details in docs/review/REVIEW3_DEVELOPER_BRIEF.md)
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| REV3-FIX ✅ | Brief §3 fixes: combat drops + Depths mining on a full satchel → inventory → /box (hard cap 5,000, warn every 3 s); helpers can't read DMs in /logs (mods+ keep it); frozen spawners on disbanded/reset islands cleared (IslandRemovedEvent) + staff restore button; code defaults for 641 / R3-15 / N2-1; remove dead keys (30-min keys, islands disband-prompt-length + homes.*, shop buy.confirm-above) + dead-key test; decision ledger docs/decisions.yml + test + a one-time config migration so answers reach live configs; stale docs (ECONOMY_BALANCE 30-min, DECISIONS #40, islands.md typed-word disband, art brief rank names) | — | depths, logs, stacking, islands, economy, auction, shop, docs | ⬜ |
| FEATCAT | ✅ done by the owner's planning agent: docs/FEATURES.md (102 features, card loop; developer builds Locked cards). Was: Feature catalog (owner F2 IMPORTANT): docs/features/ one short md per feature with today's logic + real numbers from code/live config, commands, how players get it, open points; README index; copy to the Mac skyborn-owner/features/ | — | docs/features only | ⬜ |
| UX3 ✅ | Owner in-game feedback 30 Oct: texts say "Right-click" but only left-click works (NPCs, holograms, everything) → root cause, make both clicks work, wording "Click"; bank tier prices weird (tier 10 = $195.3B) → rebalance against ECONOMY_BALANCE and show them in /is upgrade; Warps button in /menu opens a small warps menu (spawn, crates, Depths, …); /menu title shows "Large Chest" (check) | M5-4 (bank tiers in /is upgrade) | npcs, menus, bank | ⬜ |
| PERF5 ✅ | progression slow ops: progression-task-check 28 ms every 30 s, progression-level 42 ms + level-apply 20 ms, progression-scan 11 ms, progression-blocks 9 ms, progression-bank-cache 6 ms; achievements-islands 7 ms, achievements-save 6 ms, achievements-join-gauges 5 ms; npcs:apply 27 ms | M5-4 | progression, achievements | ⬜ |
| PERF6 ✅ | Slow ops after update105 (one player joined): progression-upgrade-bank 80.6 ms (!), boosters:tick 16.7 ms, rewards:playtime-tick 9.3 ms, auction:join 9.7 ms, crates:view 7.3 ms, tutorial-rejoin 55 ms (again), bank:join 29 ms (again: PERF4 called it warm-up, it repeats), enchants-armor 11.7 ms | PERF5, UX3 | progression (upgrade bank), boosters, rewards, auction, crates, tutorial, bank | ⬜ |
| PERF7 ✅ | Still repeating after update109 with one player: chat:tab 8 slow runs/min worst 28 ms (PERF4 pacing not enough — profile the rebuild itself: nametag teams, header/footer components), tutorial-guides 7 slow runs/min worst 23 ms, progression-level 14 ms on join | GUI-BUGS | chat, tutorial, progression | ⬜ |
| PERF8 ✅ | After update110 startup: boosters:tick 30 ms (again after PERF6), npcs:apply 13 ms, islands:banned-leaders 7 ms, social:auto-afk 6 ms, depths-combat 6.5 ms | PERF7 | boosters, npcs, islands, social, depths | ⬜ |
| TUT-FIX ✅ | Owner 31 Oct: tutorial broken in play: intro camera flies through buildings and looks off; moving during a camera leaves the camera inside the player and he can't move (needs a relog); the starter chest open isn't detected; plus a sprint/escape glitch hunt over every step | — | tutorial | ✅ |
| M5-5 ✅ | Phase 5 M5.5 masteries + Treasury + Wings + Homestead shared homes (R3-17/P5-21) + Ascension/Market Contacts (/is masteries) | M5-4 | progression | ✅ |
| M10-3 ✅ | Phase 10 M10.3 physical crates at the crate yard (pedestal blocks, holograms, in-world reward float, spinner, ★ wins in chat) | M10-1 | crates | ✅ |
| M10-4 ✅ | Phase 10 M10.4 key items: withdraw tradeable keys as items (unique id, dupe detection), redeem by click, not on another profile of the same account | M10-3 | crates | ✅ |
| PERF9 ✅ | npcs:apply 31 ms (apply-place 12.6, apply-track 5.9) and npcs:start 13.8 ms at startup after update112 | PERF8 | npcs | ✅ |
| M5-7 ✅ | Phase 5 M5.7 leaderboards (solo, team, weekly, per pillar; Hall of Heroes boards) | M5-5 | progression, npcs boards via service | ✅ |
| M10-5 ✅ | Phase 10 M10.5 voting (/vote menu, Votifier receiver v1+v2, vote streak, vote party, offline queue, Bedrock names) | M10-6 | rewards | ✅ |
| M10-7 ✅ | Phase 10 M10.7 quests (/quests, daily/weekly/island missions, Quest Master NPC) | M10-6 | modules/quests | ✅ |
| M10-10 ✅ | Phase 10 M10.10 collection log (/collection) | M10-7 | modules/collection | ✅ |
| PERF10 ✅ | Startup after update114: crates:yard-start 32.6 ms (M10-3 pedestal setup), npcs:apply 11.8 ms (still, after PERF9), boosters:tick 9.2 ms; all one-time at start | M10-10 | crates, npcs, boosters | ⬜ |
| M10-8 🔄 | Phase 10 M10.8 battle pass (/pass, free track now, premium cosmetic-only hook for the store; R3-44 no premium reroll) | M10-7 | modules/pass | 🔄 |
| M10-11 🔄 | Phase 10 M10.11 Black Market (/blackmarket, rotating shard offers, island size above 9x9 for shards or gems, NPC) | M10-10 | modules/blackmarket | 🔄 |
| SPAWN-F2 🔄 | F2 spawner economy (owner liked it; feature card D2): fixed /shop price (already), diminishing output per island and tier 1-16 100% / 17-32 60% / 33-64 35% / 65-128 20% / 129+ 10%, counted across blocks and outposts; drop the +10% per owned spawner (642); spawner menu + shop lore 'next works at X%' and payback | GUI-3 | stacking, shop lore via service | 🔄 |
| ECON-R3 ✅ | R3-5b crystals hard non-transferable + shard notes; R3-6 /eco up to $1T applies + flagged, above waits for owner/admin Allow in the alert; R3-39 /eco not on self or same IP; R3-16/40 notes soft limit 20/min + purge cashed notes after 90 d; R3-41 bank: fee only (free-minutes 0, no interest forfeit); R3-25 AFK-pool minutes don't count for the interest activity rule; R3-38 trade value hints; R3-28 auto 24 h trade block on detected unique-item dupes; F1 money send needs $10k self-earned except to own island members | REV3-FIX | economy, trade, moderation, bank | ⬜ |
| ISL-R3 ✅ | VIS follow-up: VisitManager canSee → Visibility.knows (/is visit by name, visitor notice, tab lists); R3-30 two-step disband (use GUI-1 confirm standard) with real amounts; R3-33 top rated needs 5 IPs + Bayesian average; R3-26 40 mob stacks per chunk total; F10 freed member slot locked 48 h before a new invite (clear message); R3-17 groundwork for shared homes (Homestead in M5.5) | M5-4, GUI-1 | islands, stacking | ⬜ |
| STAFF-R3 ✅ | R3-9a /report attaches last 10 DMs (180 d); R3-31 report reasons Scamming/Threats/Griefing; R3-32 /msg through link/IP/phone filter + DMs to players before tutorial end only from staff or people they messaged; F15 creative in /s for owner/admin on islands with auto-bypass, block log, staff-tagged items | REV3-FIX | staff, moderation, social, essentials, islands protection | ⬜ |
| VIS ✅ | Shared visibility service in common (D4): hidden = vanished OR not allowed; modes Everyone / Friends+island / Nobody + pin; replaces direct hide/show calls (vanish, ambient mobs); then R3-36 AFK pool own area with players hidden, R3-35 teleport non-earners home after 60 s, R3-3 staff family whitelist + Floodgate real-IP check | — | common, staff, afk, depths | ⬜ |
| HOPPER ✅ | F12 hoppers 64 items / 8 ticks (spigot.yml, Paper hopper settings checked, sorters noted); F13 per-chunk hopper counter (cached, no tick scans) + staff alert above 64 | — | server configs, stacking | ⬜ |
| SHOP-R3 | → folded into GUI-3 (shop v2 has undo + big-buy rule). F14 shop: "Undo last buy" 15 s (full refund while items are still in the inventory) + second click over 50% of money; both toggleable in /settings | — | shop | ⬜ |
| DEPTHS-R3 ✅ | R3-10 mini-boss scaling (+25% HP per extra eligible player up to 10; eligibility rules); R3-18 3 fragments = 1 Phoenix key, fragments from mine/forest tiers 11+ (chance = open question); R3-24 combat XP only in Depths combat zones | — | depths, skills, crates | ⬜ |
| PLAN-DV2 | ✅ plan v1 from the owner's planning agent; my review questions on https://claude.ai/artifact/4SnGEs7TkRt9H6dccD2rfF (DV-1..10). Was: Write docs/phases/PHASE-4-DEPTHS-V2.md (D1–D4: 5 zones per mine/forest/combat + boss hall + world boss arena, tier palette, lower-tier choice with per-tier rewards (D2), layered mobs from the start (D3, be critical about cost), visibility); fact-check against code; questions on a clickable page | — | docs/phases only | ⬜ |
| PLAN-R3 | Plan files for the owner (one at a time, fact-checked, "Today:" lines): spawner economy (F2), gems model (F3–F6: bought tradeable, earned bound, claim-to-bind items), rank perks as a start-to-end "battle pass" grind (F7), contribution on kick + top-contributor rewards (F9–F11) | — | docs/planning (new files only) | ⬜ |
| OPS-R3 | Release tooling: release2.sh finds deploy_key or claude_deploy and either mount layout; STATUS.md header = current state only; keep last 5 bundles/tarballs on the Mac (S4, needs delete permission once) | — | tools, docs | ⬜ |

## Phase 1 follow-ups
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| F1 | Hook remaining `/settings` toggles: join-messages, tpa-requests (essentials), scoreboard (hud), ui-sounds | Phase 1 | modules/essentials, modules/hud | ✅ |
| F2 | Per-module permission files instead of shared `plugin.yml`/`luckperms.txt`/`COMMANDS.md` | — | common/command, plugin, server/permissions, scripts | ✅ |
| F4 | Move a TeleportService interface (warmup, guards) into common so /dungeon, /spawn, /tpa, /is home share one warmup | — | common, essentials, depths | ✅ |
| F3 | Achievements system (progress tracking → `Cosmetics.grantTitle`) | Phase 2+ stats | new modules/achievements | ✅ |

## UX polish (owner, 2026-10-30) — top priority, starts at the next free slot
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| UX1 | Rank change shows on scoreboard/tab instantly; `/baltop` fast + async (short cache, refresh after big changes); all messages short, direct, normal words, one consistent color scheme and format (UI_STYLE 3-role palette); tag after the name in parentheses-style; cleaner scoreboard + tab with better colors | — | modules/hud, chat, cosmetics, economy (baltop), social, essentials, perks, staff, logs, menus, auction + their messages; style.yml; UI_STYLE §3/§6 | ✅ |
| UX1b | Tab (owner 2026-10-30): remove the `/menu /help /discord /store` footer line; nicer TPS + `play.skybornmc.net`; the lines beside SKYBORN at the top match the colors (one long gradient, or left line white and right line the last letter's color) | UX1 | modules/chat (tab) | ✅ (done inside UX1) |
| UX2 | Same message cleanup for moderation, depths, islands messages (after P1, M4.6, M2.7 merge) | UX1, P1, M4.6, M2.7 | their messages files | ✅ |
| FI | Owner answered `docs/FEATURE_IDEAS.md` (2026-10-30); follow-up questions in `docs/QUESTIONS_ROUND2.md` → then queue the yes-items | owner | docs | 🔄 waiting for owner |
| PROF | **Island profiles** per docs/DECISIONS_2026-10-30.md (P1–P12): profile layer in common (ProfileService, active profile per player, `/profiles`), migrate every module's per-player data to (player, profile) keys, inventory/ender chest/armor/XP swap, zero transfer between own profiles | — | common + all modules (one agent, careful, big) | ✅ |
| PROF-ADOPT | Modules merged after PROF (tools M4.2, stacking, enchants, notes…) store per-player data per profile via ProfileService | PROF | each new module | ✅ tools (Forge money, refunds, starter kit) + stacking (item merges keep profile tags) done at merge; enchants (E1 built with profile keys) |
| CRY | Replace tokens with **island crystals** everywhere (currency enum, messages, HUD, docs) | — | common/economy, modules/economy, hud, docs | ✅ |
| R2B | Economy part of round 2: new-account limit 30 min for /pay + /withdraw (economy.yml notes/pay), `/eco` for admins via permission, sell multiplier cap config 300% | — | modules/economy | ✅ (in R2A) |
| R2A | Round-2 quick decisions: account-age 30 min (pay/withdraw/AH), `/eco` for admin permission, remove amount suggestions (10k/10m/10b) in tab completion, sell cap 300% (config), freeze cleared by a ban, Inspect tool = blaze rod, public announcements unless -s, timezone Europe/Oslo, skill cap 150, staff `/skills set|add` (admin+owner), `/freerank` (30 min playtime) | — | economy, auction, common/command, moderation, staff, skills | ✅ |
| ISL2 | Islands round 2: role Admin (+ old leader → Admin on transfer), hurt-monsters role permission, join cooldown from last join only, `/is settings` flags, island fly for everyone (perm/mastery), likes per player, member homes (1 + masteries), invites menu | M2.4 | modules/islands | ✅ |
| STACK | Stacking module: mobs (1024, weapon-based kills, no AI for spawner mobs, breeding by food, max 8 stacks/chunk/type, no despawn), items, spawners (8→512, menu, ignore conditions, crops grow without light) | answered (round 2+3) | new modules/stacking | ✅ |
| NOTES | `/is notes` (N1–N9: shared to-do list, temporary book, pinned holograms) | M2.4 | modules/islands | ✅ |
| SB | Per-place scoreboards (island, Depths, spawn, warzone) | UX1 | modules/hud | ✅ |
| BAL2 | Re-run the balance model with the +300% sell cap and the owner's progression wish (#61: fast start, progressively harder, ups and downs); update ECONOMY_BALANCE | R2B | docs + tools/economy_model.py | ✅ |
| PLAN-ENCH | Plan doc: full custom enchant list (levels, effects, how each is obtained: mob kills, mining, chopping, dungeons, crates, /enchanter, crystals), synergy with tools/sets/stacking kills | — | docs/plans/ENCHANTS.md | ✅ docs/plans/ENCHANTS.md |
| PLAN-SETS | Plan doc: `/wardrobe` (was /sets) + `/loadout` presets swapping armor + pet in one action; (gem/crate/currency sets priced by strength, set levels 0→50 with multipliers, merging two sets resets level) | — | docs/plans/SETS.md | ✅ docs/plans/WARDROBE.md |
| STORE2 | Island storage: mastery delivery tasks take items from storage; daily take-out limit per role; staff-only recall of items taken by kicked members | islands storage built | modules/islands | ⬜ |
| PLAN-RP | Plan doc: resource pack (items, borders, crates, menus, font icons, sounds; Java + Geyser) | PLAN-ENCH | docs/plans/RESOURCE_PACK.md | ✅ docs/plans/RESOURCE_PACK.md |
| FREERANK | `/freerank`: lowest rank, cosmetic color, small perks, pick 1 of 3 tags, forever (after 30 min playtime) | — | modules/perks or cosmetics | ✅ (in R2A) |
| BUILDS | Owner 2026-10-30: paste all finished builds (server/builds: spawn base + forge quarter + market square, Depths gate + mine T1/T2 + forest T1 + graveyard T1, starter island) on the test server with FAWE (backup first) and wire their metadata (spawn point, portals, holograms, NPC spots, zones, regions); starter island = /is schematic | — | modules/worlds (+ islands/depths/essentials hooks) | ✅ pasted + applied on the test server (backup ~/backups/pre-builds-*.tgz) |
| SELL2 | `/sell` becomes a chest you put items into + Sell button (items saved while inside, crash-safe), Sell inventory button; quick-buy `/shop` grid with Bedrock amount screen; Sell button in `/shop` | DESIGN approved | modules/economy (sell), modules/shop | ✅ |
| STYLE2 | Brighter v2 palette in style.yml (Bedrock fallbacks kept) + one lore builder for every item: bold rarity name, red `\| Header` sections, `\| Buy Price` green / `\| Sell Price` red, bold `» RIGHT-CLICK` action lines, crate rewards `★ name (xN) \| chance%`, quest `Progress` + `[+]` rewards; same style on scoreboards, holograms, leaderboards, chat prefix gradient | owner inspiration 30 Oct | common/text + all lore call sites + hud + holograms | ✅ |
| NPCHOLO | NPC + hologram module: spawn the NPC pads, holograms and leaderboards stored in build-points.yml (BuildPoints API); right-click NPCs/holograms explain things (owner 44); leaderboard holograms with #1/#2/#3 colors | builds applied | new module | ✅ |
| PORTALS | Build portals teleport (step in → target); spawn portal that creates your island on first use and always takes you to it (owner 159) | builds applied | worlds + islands | ✅ |
| DEPTHSPROT | Protect the Depths outside ore/log fields; safe zones and dev flags from the builds (no PvE in arrival/exit, no pearls/wind charges in combat zones, no anvils/containers in the Forge Quarter) | builds applied | depths + worlds | ✅ |
| PLAN5 | Plan Phase 5 (pillars, dynamic island level, masteries with tasks incl. bank fee discount, leaderboards) + questions to the Decisions page | — | docs | ✅ |
| PLAN10 | Plan Phase 10 (crates/keys, voting, daily rewards per profile, quests, battle pass, collection log, Black Market, tutorial) + questions | — | docs | ✅ docs/phases/PHASE-10-REWARDS.md |
| PLAN7 | Plan Phase 7 (island production, Island Fuel, collectors, outposts) + questions; unblocks E6 | — | docs | ✅ docs/phases/PHASE-7-PRODUCTION.md (questions go on the page after GUIDE2) |
| PLAN8 | Plan Phase 8 PvE (custom mobs, world/summon bosses, pets; fishing moved to Phase 7) | — | docs | ✅ |
| PLAN9 / PLAN11 / PLAN12 / PLAN13 | Plans for PvE, conquest/events, store/ranks/Discord, seasons, launch prep | — | docs | ⬜ |
| GUIDE2 | TEST_GUIDE refresh for everything built today + new open questions onto the Decisions page | — | docs + tools/owner_sheet | ✅ |
| FIXES1 | Tester feedback (fly, /s, /p, menu item, armor, tab ranks, scoreboard line, report book, shop confirm, usage errors) | owner/Maarten 30 Oct | several | ✅ |
| PERF3 | Startup slow ops on update101 (npcs:start 1065 ms, tutorial:start 14 ms, sell-stat-sources 7 ms, afk-start 7 ms) | PERF2 | npcs, tutorial, economy, afk | ✅ |
| M5-4 | Phase 5 M5.4 island upgrades (/is upgrade, gated by TaskService, paid from island bank) | M5-3 | progression, islands | ✅ |
| PERF4 | Slow ops after update102 with a player online: chat:tab (46 slow runs/min, worst 32 ms), progression-task-check 28 ms (every 30 s), progression-level 42 ms + level-apply 20 ms, progression-scan 11 ms, tutorial-guides 57 ms, tutorial-rejoin 28 ms, bank:join 14 ms, npcs:spawn 14 ms, wardrobe-trail 12 ms, enchants-armor 7 ms | PERF3 | chat, tutorial, bank, npcs, wardrobe, enchants (progression-* ops wait for M5-4 → PERF5) | ✅ |
| M10-6 | Phase 10 M10.6 daily calendar + playtime rewards (/daily, new modules/rewards) | M10-2 | modules/rewards | ✅ |
| AC1-2 | Achievements AC1+AC2 from docs/plans/RANKS_TITLES_ACHIEVEMENTS.md (/achievements tabs, personal boosts with caps, island achievements) | RTA plan | achievements | ✅ |
| PERF2 | Slow main-thread tasks seen on the server (stacking-menu-refresh 15 ms, island-stats-flush 7 ms, wardrobe-trail 6 ms, crates:admin 11 ms) + safe PERFORMANCE.md findings without owner decisions | audits | stacking, islands, wardrobe, crates, chat, hud | ✅ |
| M10-2 | Phase 10 M10.2 boosters (personal items + global, /boosters, /thanks, BonusService wiring, AFK booster moved here) | M10-1 | modules/boosters, afk | ✅ |
| SMALL2 | /crates admin give|take|view (owner approved P10-41) + join-time slow tasks (box:join, menus:menu-item, clean-streak-join) off the main thread | M10-1 | crates, box, menus, moderation | ✅ |
| M5-3 | Phase 5 M5.3 task engine (challenge book, shared island progress, deliver tasks) | M5-1 | progression | ✅ |
| M5-2 | Phase 5 M5.2 dynamic island level + /is level and /is top (owner approved P5-25 Suggested) | M5-1 | progression, islands | ✅ |
| TUT2 | Tutorial TU5+ (Pip companion, NPC lines, cameras, optional steps, stats) | TUT1 | tutorial | ✅ |
| M10-1 | Phase 10 M10.1 crates core (+ bound items from bound keys, R3-2) | PLAN10 | modules/crates | ✅ |
| M5-1 | Phase 5 M5.1 pillars | PLAN5 | modules/progression | ✅ |
| QUICK1 | QoL quick wins (sell protection, drop protection, island auto-pickup), server config hardening, safe performance fixes | audits | several | ✅ |
| SECFIX1 | High security findings without owner decision (crash-rollback dupes, kick-restore dupe, frozen spawners, box caps, depletion persisted, shop recovery, sanctions fail closed) | audit | economy, auction, islands, stacking, box | ✅ |
| ANS3 | Skipped from ANS2: /ignore blocks tpa/trade/invites + hides chat (shared ignore service), staff powers (creative only builders/admins in spawn/Depths/builder areas, staff item tag unsellable, god/fly/heal only in staff/build mode, staff play normally with transfers flagged), /ec and /craft blocked in warzone/boss/10 s after combat, banned player's island locked for visitors | owner round 2 | several | ✅ |
| TUT1 | Build the tutorial TU1-TU4 from docs/plans/TUTORIAL.md with the suggested answers (portal hook, steps engine, guides, locks via TutorialGate, starter chest) | PLANTUT, PORTALS | new module tutorial | ✅ TU5+ (cameras, Pip, analytics) left |
| ANS2 | Owner answers round 2 (teleports instant except combat, visiting, chat filter EN, /trash admin, ban screen, silent -s, collection bin, reset backups) | owner 30 Oct | several | ✅ |
| QFIX | Questions reworked from the owner's answers (Decisions page v2) | owner | docs | ✅ |
| ANS1 | Owner answers round 1: islands/profiles rules (no cooldowns, start money per profile, no 30-min limit, team 4+leader, no setmyhome, kick backup + staff restore, no disband with members, bank deleted on disband, respawn at spawn, hide level line, size cap 9x9, profile delete removes listings, no buying own-profile listings) | owner 30 Oct | islands, profiles, hud | ✅ |
| BOX | `/box` item mailbox: nothing is ever dropped; every overflow (sell chest, shop, crates, gifts, staff gives) goes to the box | owner answer 7 | common + economy | ✅ |
| BANK2 | Bank interest higher, every 8 h; 2% withdrawal fee lowered by island masteries | owner answer 169 | economy | ✅ |
| TUTORIAL | (plan done: docs/plans/TUTORIAL.md, 32 questions on the page; build TU1-TU8 next) Join flow: portal at spawn creates the island and always teleports to it; guided tutorial on the island (camera/animation, arrow guide, step-by-step), permissions unlock per step, full perms when done; help NPCs/holograms everywhere | owner answers 43, 44, 131, 159 | needs a plan + sketches first | ⬜ plan |
| STARTER | Custom starter chest designed for this server (proposal to owner) | owner answer 158 | islands | ⬜ |
| ISLAND-LEVEL | Plan: dynamic island level from many sources, not only block value | owner answer 168 | plan | ⬜ |
| RANKS | Plan ranks, titles, achievements (PLANRANKS: 7 themed donor ranks, free-rank rename, season ranks, perks, titles/tags catalog, personal + island achievements) | owner answers 243, 355, 375, 386-390, 398 | docs/plans | ✅ plan written, 46 questions on the page |
| RP1 | Forced resource pack (prompt before joining) once the owner's art arrives (docs/plans/RESOURCE_PACK_ART_BRIEF.md) | owner answers 33, 77 | pack | ⬜ waits for art |
| DESIGN | Owner 2026-10-30: sketches for GUIs, chat, /shop (quick-buy grid), lores, prices, /sell chest GUI (put items in + click sell; sell also in satchel and shop), /forge etc.; vanilla now + resource-pack version later; owner reviews before implementation | — | docs/design + artifact | 🔄 main session |
| AFK | AFK pool (fuel + money + keys, better loot over time, max 8 h/day) | economy, islands fuel | new module | ✅ |

## Phase P: Punishment system v2 (owner spec `docs/specs/PUNISH_SPEC.md` + `PUNISH_SPEC_NOTES.md`) — one lane (modules/moderation), sequential, priority
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| P0 | Bugs: freeze doesn't stop movement (and after relog), frozen player must not break/chat/do anything; staff-mode vanish item placeholder name; spyglass tool zooms; YAML boolean keys (fixed in main) | — | modules/staff, modules/essentials (vanish) | ✅ |
| P1 | Spec §1–11: `/p` single-screen menu from YAML, warn counter + rules (notify/auto), `/warns`, validation + safe reload + build test of tracked YAML; release script pulls owner's GitHub edits | P0 | modules/moderation, server/scripts | ✅ |
| P2 | §12.1 undo, §12.2 double-punish, §12.3 offence expiry, §12.10 `/pstats` | P1 | modules/moderation | ✅ |
| P3 | §12.4 evidence, 12.5 notes, 12.6 ack, 12.7 weights, 12.8 suggestion API, 12.9 report link, 12.11 Discord sink, 12.12 `/mypunishments` | P2 | modules/moderation, modules/staff (report API), common (DiscordSink) | ✅ |
| P4 | §13.4 approval, 13.13 mute ended, 13.7 probation, 13.5 watchlist | P3 | modules/moderation | ✅ |
| P5 | §13.8 bulk, 13.11 punish from logs, 13.10 appeals (in-game + webhook) | P4 | modules/moderation, modules/logs (API) | ✅ |
| P6 | §13.9 raid lockdown, 13.3 anticheat suggestions (`/psuggest`, Grim config notes), 13.12 network-ready | P5 | modules/moderation, server/CONFIG_NOTES.md | ✅ |
| P7 | §13.1 sanctions (economy SanctionProvider), 13.2 island punishments | P6, M2.3 | modules/moderation, modules/economy, modules/islands | ✅ |
| P8 | §14.2 rule numbers, 14.9 notification settings, 14.6 training mode | P7 | modules/moderation | ✅ |
| P9 | §14.1 guard + rollback, 14.5 false reports, 14.10 purge | P8 | modules/moderation, modules/staff | ✅ |
| P10 | §14.8 monthly report, 14.11 "Honorable" title | P9 | modules/moderation, modules/cosmetics | ✅ |

## Phase 2: Islands (docs/phases/PHASE-2-ISLANDS.md) — one lane, sequential
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| M2.1 | Grid, creation (FAWE placeholder schematic), 3×3 chunks, per-player border, home | Phase 1 | modules/islands | ✅ |
| M2.2 | Protection (role perms, Y 0–256, cross-border) | M2.1 | modules/islands | ✅ |
| M2.3 | Members & roles, invites (solo→team warning), coop, limits | M2.2 | modules/islands | ✅ |
| M2.4 | Visiting, lock, ban, warps, ratings | M2.3 | modules/islands | ✅ |
| M2.5 | Island menu + settings, `/is chat` | M2.3 | modules/islands | ✅ |
| M2.6 | Stats tracking (daily/weekly buckets) | M2.3 | modules/islands | ✅ |
| M2.7 | Lifecycle (disband/reset) + `/isadmin` | M2.3 | modules/islands | ✅ |

## Phase 3: Economy (docs/phases/PHASE-3-ECONOMY.md)
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| M3.0 | Balance sheet `docs/ECONOMY_BALANCE.md` (owner approves before prices go live) | — | docs only | ✅ (draft, owner approval pending) |
| M3.1 | Currencies + ledger, EconomyService, `/bal /baltop /pay /transactions /eco` | Phase 1 | modules/economy | ✅ |
| M3.2 | Money notes (`/withdraw`, `/eco note`) | M3.1 | modules/economy | ✅ |
| M3.4 | Selling + dynamic prices (`/sell`) | M3.1 | modules/economy (sell pkg) | ✅ |
| M3.5 | Builder shop `/shop` | M3.1 | modules/shop | ✅ |
| M3.6 | Auction house `/ah` | M3.1 | modules/auction | ✅ |
| M3.7 | Secure trade `/trade` | M3.1 | modules/trade | ✅ |
| M3.3 | Banks + interest (personal + island) | M3.1, M2.3 | modules/economy (bank pkg) | ✅ |
| M3.8 | Economy dashboard + anomaly alerts | M3.1–M3.7 | modules/economy (admin pkg) | ✅ |

## Phase 4: Tools + The Depths (docs/phases/PHASE-4-DEPTHS.md)
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| M4.3 | Per-player blocks engine (depleted state per player, 10 s respawn, timing wheel) | Phase 1 | modules/depths (engine pkg) | ✅ |
| M4.1 | Depths layout from `depths.yml` (placeholder zones by code, U-shaped wings) | M4.3 | modules/depths (zones pkg) | ✅ |
| M4.5 | Skill core (XP/levels, `/skills`) | Phase 1 | modules/skills | ✅ |
| M4.4 | Satchel | M4.3, M3.4 | modules/depths (satchel pkg) | ✅ (with M4.9) |
| M4.6 | Tiers, unlocks, `/dungeon` | M4.1, M4.5 | modules/depths | ✅ |
| M4.2 | Tools: starter kit (24 h), Forge, living tools (perk slots 5/15/30/45 + prestige), blueprints, skins; `/tool give`, `/blueprint give`, `/skin give` | M3.1, M4.5 | modules/tools | ✅ |
| M4.7 | Forest | M4.3, M4.1 | modules/depths | ✅ (with M4.8) |
| M4.8 | Combat zones (shared mobs) | M4.1 | modules/depths (combat pkg) | ✅ (with M4.7) |
| M4.9 | Drops + tokens | M4.4, M3.1 | modules/depths | ✅ (with M4.4) |

## Phase E: Enchants (docs/plans/ENCHANTS.md)
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| E1 | Registry (enchants.yml, 48 defs + validation test), unique DB books + Disenchant Scrolls, `/enchanter` GUI (apply/upgrade/combine/remove/rolls/guide), slot/conflict/level rules, crystal costs, lore, EnchantService + BonusService in common | M4.2, CRY | modules/enchants, common/enchant, common/bonus | ✅ |
| E2 | Book drops from Depths mine/forest/combat, gathering + area effects via the block engine, Depths sword effects | E1, M4.7, M4.8, M4.9 | modules/enchants + depths hooks | ✅ |
| E3 | Stacked-mob kill enchants (Slaughter etc.), batch kill API | E1, STACK | modules/enchants + stacking API | ✅ |
| E4 | Tuning: enchant factors in economy model, family caps, drop rates | E2, E3, BAL2 | docs, tools, configs | ✅ |
| E5 | Armor enchants on wardrobe set pieces | E1, W2 | modules/enchants + wardrobe | ✅ |
| E6 | Hoe + rod effects, farming/fishing pools | E1, Phase 7 | modules/enchants | ⏸ Phase 7 (farming/fishing) not planned yet |

## Phase W: Wardrobe + loadout (docs/plans/WARDROBE.md)
| # | Item | Depends on | Lane | Status |
|---|---|---|---|---|
| W1 | Wardrobe core: sets.yml (16 sets), per-profile tables, virtual entries + locked real armor, Wardrobe Chest with escrow, buy with money/crystals/gems (Sigils), Set Tokens, /wardrobe GUI, bonuses via BonusService | PROF, CRY, E1 (common/bonus) | modules/wardrobe, common/wardrobe | ✅ |
| W2 | Set levels, merging, warzone normalization | W1, M4.5 | modules/wardrobe | ✅ |
| W3 | /loadout presets (2 free, rank/credit slots), atomic apply, guards, 2 s cooldown, pet hook | W1, M4.2 | modules/wardrobe, modules/tools | ✅ |
| W4 | Balance: set factors in economy model | W2, W3, BAL2 | docs, tools | ✅ |

## Later (not planned in detail yet)
Crates (`/crate give`), quests, progression (pillars, island level, `/is upgrade`), enchants/sets, production/outposts, PvE/bosses/pets, conquest/events, store/ranks/Discord, seasons.

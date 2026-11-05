# Master plan (6 Nov 2026)

One file that says what is done, what is broken, what must be planned and in which order. It replaces the "current state" parts of STATUS, QUEUE, PLANNING and ROADMAP until those are rewritten (cleanup D-01).

## 0. Rules for this phase (owner, 6 Nov)
- **Plan first, build after.** Nothing is implemented until the plans below are approved ("100% done and just as I want it").
- **No coding agents.** Sub-agents (Sonnet) only read and plan. The lead session writes every line of code itself.
- **Fixes are pre-approved** ("fix everything we find"): section 2 is built without asking. Design changes go to the owner as yes/no (section 5).
- Principle 0 (fun, easy, less is more) beats every other plan.
- Release in batches.

## 1. Where the code is
| Line | What | Verdict |
|---|---|---|
| `q/release-v4` | main (update120) + 279 commits from 3 to 5 Nov: Rowan start, Depths/dungeon/old tutorial removed, island = profile, tools v2, /farm and /mine, tutorial quest book, Contracts, /friends, daily streak, Sell Wand, Auto-Sell Chest, Chunk Collector. **Runs on the test server.** | The real line. Becomes `main` once the plans are approved (Q-50). |
| `main` | update120 + 1 symbol fix (7967169d) | Fix goes in FX-10; then main = release-v4. |
| `q/specs-v3` | spec texts (RANKS-V2, CREDIT-SHOP, MENUS-V3, GUI-ICONS-FINAL, ECON-*), 68 ledger lines | Archive; ranks and credit shop feed P-13. |
| `wip/q/start-safety` | paused anti-boost + start gate (not on v4) | Archive; idea feeds P-08. |
| `q/M7-5` (local) | old production collectors | Archive; v4 has its own Chunk Collector. |
| `q/econ-v2`, `docs/full-spec`, `docs/specs`, `design/gui-round1` | already in v4, or junk (full-spec holds only a pasted shell command) | Delete branch (Q-51). |

## 2. Fix list (pre-approved, build first)
Severity order. Paths are on `q/release-v4`.

| Id | Problem | Where | Fix |
|---|---|---|---|
| FX-01 | Chunk Collector keeps 1 item of a stacked kill drop and deletes the rest (1,024 cows = 1 beef) | `stacking/ChunkCollectors.java:88-111` vs `ItemStacker.java:165-170` | Insert `ItemStacker.count(item)` items; leftovers stay as a stacked item. Test. |
| FX-02 | Auto-Sell Chest and Chunk Collector only handle `BlockBreakEvent`: after TNT, fire or island reset the row stays and the limit is used up forever | `AutoSellChests.java:154`, `ChunkCollectors.java:158` | Same handlers as `ShardBlockManager` (explode, burn, piston, island removed) + a staff command to clear. |
| FX-03 | Spawner labels count against the island display limit (30): big islands lose labels and retry every 10 s | `SpawnerManager.java:396`, `EntityLimits.java:62,121` | Exclude plugin-tagged displays from the limit. |
| FX-04 | Sell Wand charges whatever is in the hand after the async sale: swap the item = free sale | `SellWandListener.java:121-124` | Find the wand by its id/UUID, charge before paying. |
| FX-05 | Wand and Auto-Sell take items before the money is saved (crash dupe, low) | `SellWandListener.java:~86`, `AutoSellChests.java:177-200` | Same reserve/commit order as `/sell`. |
| FX-06 | Combat XP can never be earned (only from Depths, which is gone) | `skills/SkillsConfig.java:45-49`, `SkillManager.java:329` | Depends on Q-20. |
| FX-07 | `/farm` view refresh reads 3,721 block states per player every 10 s (300 players = lag) | `essentials/GrindAreas.java:171-180, 676-698` | Cache per crop tier; send only on join/unlock. |
| FX-08 | `/farm` and `/mine` bonus money lives only in memory, result unchecked, no `TxReason` | `GrindAreas.java:556-575` | Flush on quit/stop, real reason, check result. |
| FX-09 | Crop unlock numbers and world layout hardcoded; tutorial hint hardcodes "3,000" | `GrindAreas.java:54-80, 410-420`, `tutorial.yml:323-327` | Move to config; hint uses a placeholder. |
| FX-10 | Non-Bedrock-safe symbols: em dash in `islands.yml:410,493`; `→` in the `notice` prefix (`style.yml:11`) and `guide.yml:197-198` | messages | Replace; add a build test that scans every messages file. |
| FX-11 | NPCs still run `/dungeon` (command gone); Depths Guide NPC and board in `depths_gate_v1` | `npcs/NpcsConfig.java:53-70`, `messages/npcs.yml:78-88`, `server/builds/depths_*` | Remove roles and builds (with C-01). |
| FX-12 | Contract Board: default pools start with `mine`, `chop`, `kill` that are never offered, so island contracts show 2 instead of 3 | `QuestsModule.java:342-346`, `QuestsSettings.java:55,83,110` | New pools from P-10; until then drop the dead types. |
| FX-13 | `skyblock.tutorial.admin` and `skyblock.islandtools.admin` are granted to nobody | `server/permissions/` | Add `tutorial.txt`, `islandtools.txt`. |
| FX-14 | "Tutorial stats" has no tile in the staff hub | `menu.yml:114-126`, `navigation.yml:531` | Add tile. |
| FX-15 | Profile switching is blocked in "depths" (no such world) but allowed in the grind world | `profiles/ProfilesConfig.java:24` | Block in the `/farm`/`/mine` world. |
| FX-16 | `/is gho` prints the full sub-command list | `common/command/CommandService.java:287`, `messages/commands.yml:9` | "Did you mean /is go?" + short usage. |
| FX-17 | Placed-block memory is RAM only (200k per world): place and break ore after a restart = free XP and shards | `common/PlacedBlocks.java`, `islandtools/ToolListener.java:76` | Store the "placed" mark in chunk data (persistent, no cap). |
| FX-18 | `/help` is wrong: Black Market "pay with shards" (now crystals), "Depths drops", sell topic misses Sell Wand / Auto-Sell Chest / Chunk Collector; Auto-Sell lore hardcodes "every 30 s" | `help.yml:555,1198,1359-1366`, `economy.yml:1230` | Rewrite those topics; placeholder for the interval. |
| FX-19 | `/quest` opens the tutorial but `/help quest` shows Contracts | `TutorialCommands.java:46`, `help.yml:1548` | Help topic named Contracts; alias follows Q-12. |
| FX-20 | Collection log Mine/Forest/Combat pages built from removed `depths_*` items (warnings on every start) | `collection/CollectionSettings.java:25-40` | New pages from farm/mine items (with C-01). |
| FX-21 | NPCs `farm_merchant`, `mine_merchant`, `tinker`, `sage` have no placement in `server/builds` | builds | Check the live world; add placements. |

## 3. Cleanup (after the fixes)
**C-01 Depths, dungeon and combat-zone leftovers in code and config** (no player sees a dead button):
worlds (`DepthsWiring`, `worlds.yml:83-218`, messages), hud (`places.depths`, `Place.DEPTHS`), economy (`sell.yml` ~45 `depths_*` lines, `economy.yml:400`), production (`depths-xp=` syntax), enchants (sword text), progression (Depths upgrade tab, "Depths progress" tile that always shows 0, mine-fortune text), quests messages, skills messages, help (Depths/Forge/Combat & Events topics), collection, `/tags` titles that cannot be earned, `GrindAreas` dungeon code, `menu.yml` warp ids and `highlights`, box `depths` source, `SkyblockPlugin.java:335` comment.
**C-02 Old `tools` module** (Forge, `/forge`, `/tool`, `/blueprint`, axes, swords, Depths recipes, Depths inventory swap) next to `islandtools`: remove (Q-05).
**C-03 Docs** (move to `docs/archive/`, nothing deleted):
- Depths: `phases/PHASE-4-DEPTHS*.md`, `DV2_SPIKE_TEST.md`, `PHASE-8-PVE.md`, `plans/TUTORIAL.md`.
- Superseded plans: `planning/V5_PLAN.md`, `FEEDBACK_3NOV.md` (open questions moved out first), `SELL_PRICES.md` (replaced by P-03), `SHOP.md`, `MENU_GUI.md`, `TAB_LIST.md`, `MESSAGES_AND_COMMANDS.md`, `BANK_UX.md`, `MORNING_REPORT.md`, pack-v1 art docs (`plans/RESOURCE_PACK*.md`, `PACK_BUILD*.md`).
- `TOOLS_AREAS_REDESIGN.md`: keep only the owner decisions; archive the rest.
- Merge `PLAN_4NOV.md` + `_B` + `_C` into one file with done/open per phase; `NEXT_BATCH.md` into QUEUE.
- Merge `OWNER_ANSWERS_ROUND1-3.md`, `DECISIONS_2026-10-30.md`, `OWNER_ANSWERS_PACK1.md` into `OWNER_DECISIONS.md` (`decisions.yml` stays).
- `OPEN_QUESTIONS.md` (248 KB, 133 entries): live ones to a short "Open now" list, the rest archived.
- Rewrite `STATUS.md`, `QUEUE.md` (open items only), `ROADMAP.md`, `PLANNING.md`, `BACKLOG.md`.
**C-04 VPS:** remove `~/node`, `~/bot`, `~/pack.yml.bak` (old test leftovers).

## 4. Plans to write (each its own file in `docs/plans/`, owner approves each)
| Id | Plan | Status / waiting on |
|---|---|---|
| P-01 | **Spawners v2** (10 s timer, kill levels, sqrt formula B = 100/250/600/1500, limits 4 to 64, delay upgrade, radius 3, prestige +5% x10, premium tier 5 limit 3, one simple menu) | Needs the full Plan Pack text (not saved anywhere) |
| P-02 | **Spawner payback** (100 h to 150 h, value per mob $2.78 / $5.95 / $10.42 / $18.52, never shown). Today tier 1 pays back in ~9 h, 10x too fast (vanilla loot at farm prices) | Q-01 |
| P-03 | **Dynamic prices v2** (per-island saturation + weekly global drift max +/-10%) replacing today's global model | Q-03 |
| P-04 | **Island upgrades done on the island** (I1: farm 1,000 wheat ...) | The research result (RESEARCH_PROMPT.md) |
| P-05 | **Tool enchants v2** (T2, more enchants; remove axe/sword enchants, dedupe) | The research result |
| P-06 | **Grind inventories** (farm, mine and hoe storage) | Q-30 |
| P-07 | **/mailbox** | Q-31 |
| P-08 | **Playtime pay limits** (daily limit on money you receive from /pay, trades and sales, rising at 6 h / 12 h / 24 h playtime; island members exempt; /paytoggle). No $10k rule | Ready to plan (Q-32, `planning/INSPIRATION_6NOV.md`) |
| P-09 | **Quest line v2** (no tutorial: `/quest` shows the next step, 5-10 quests per location, most from spawn NPCs, some on the island; old players can do it too; no invite step) | Ready to plan (Q-10 to Q-15) |
| P-10 | **Contracts targets v2** (/mine ores, island mining, spawner kills) | Q-14 |
| P-11 | **Menu and help coverage pass** (A2 + every feature in /menu and /help) | Q-15, Q-16 |
| P-12 | **Combat after the Depths** (skill, achievements, titles) | Q-20, Q-21 |
| P-13 | **Ranks v2 + credit shop** (from `q/specs-v3`) | Q-40 |
| P-14 | **Wardrobe Tome + Tool Skin Blueprint** (FEEDBACK_5NOV ⬜) | Q-41 |
| P-15 | **Shop pass** (Q9 missing items, Mob Drops tab, ECON-B1) | Q-42 |
| P-16 | Seasons, events, store, launch (phases 9, 11-13) | Later |
| P-17 | **PvE** (where the Combat skill, combat achievements and titles come from) | Research big servers first (Q-20) |
| P-18 | **/fish** (third grind area next to /farm and /mine; also feeds quests and contracts) | Research + inspiration (Q-12) |
| P-19 | **Resource pack v2** (owner dislikes the current look; only the /collection icons are decent) + GUI design pass | Q-42, Q-43 |
The owner's own "14 plans still needed" list is not saved in the repo; it is matched against this table when he re-sends it.

## 5. Owner questions
Yes/no page: https://claude.ai/artifact/DPYQjLegVypWe3tKqcm3MU (db collection `answers6nov`). Answers are copied to `docs/OWNER_ANSWERS_6NOV.md` once given.

## 6. Order of work after approval
1. FX-01 to FX-21 (one batch, one release).
2. C-01, C-02 cleanup + C-03 docs (one batch).
3. Merge `q/release-v4` into `main`, delete the dead branches.
4. Plans in this order: P-01/P-02/P-03 (one economy batch), P-09/P-10/P-11/P-12 (new player batch), P-06/P-07/P-08, P-04/P-05 (after research), P-13 to P-15.

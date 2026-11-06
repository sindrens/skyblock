# Morning report, 7 Nov 2026

Written during the night while you slept. Newest at the top of each section.

## In short
- The full plan is written: `docs/plans/FULL_PLAN.md` (everything: economy, /farm /mine /fish /pve, tools with 4 sets, island v2, spawners v2, quests, UI, other features, build order). It follows all your answers; nothing in it is waiting for you.
- Building happens in batches, each released to the test server. Status of each batch is below.

## Batches
| Batch | What | Status |
|---|---|---|
| 1 | Fixes and cleanup | in progress |
| 2 | Economy (money x100, playtime pay limits, island-only shard pay, prices v2, spawner payback) | next |
| 3 | Tools v4 core | |
| 4 | Island v2 | |
| 5 | Spawners v2 | |
| 6 | Quest line v2, menus, help, mailbox | |
| 7 | /fish | |
| 8 | /pve | |
| 9 | Polish (crates, collection, AH, shop) | |
| 10 | Pack v2, ranks v2 | |

## Batch 1: fixes
- **The release-v4 branch did not compile** (the old session pushed a broken state: the tutorial module and 11 failing tests in 8 modules). Fixed first.
- FX-01 Chunk Collector: keeps every item of a stacked mob drop (was 1 of 1,024), only catches drops from its own island (your Q-18).
- FX-02 Chunk Collector and Auto-Sell Chest survive explosions, and a removed island takes them with it (before, the island's slot was blocked forever).
- FX-03 Spawner labels no longer use the island's hologram limit.
- FX-04 Sell Wand: the use is charged before the sale (switching items no longer gave free sales); nothing sold = the use comes back.
- Sell Wand and Auto-Sell Chest never drop items on the ground any more: inventory, then the mailbox.
- FX-07 /farm and /mine view: about 10x cheaper per player (no block copies, refresh every 30 s instead of 10 s).
- FX-08 /farm and /mine bonus money still waiting is paid when the server stops.
- FX-10 Symbols that show as boxes on Bedrock removed; a new build test blocks them for good.
- FX-11 No NPC runs /dungeon or /forge any more (the Forge NPC opens the tool menu).
- FX-13 Admins can use `/tutorial admin` and `/islandtools` staff commands without OP.
- FX-14 "Tutorial stats" tile in the staff hub.
- FX-16 A mistyped sub-command says "Did you mean /is go?" instead of the whole option list.
- FX-18 /help: Black Market says crystals, selling explains the Sell Wand / Auto-Sell Chest / Chunk Collector, the Forge pages became Island tools pages, no Depths texts, the empty "Combat & Events" category is gone.

## Moved to a later batch (on purpose)
- Removing the old tools module (C-02) moves to batch 3: the crates still give tool enchant books that only work on those tools, so crates and tools change together.
- Contract pools (FX-12) move to batch 6 (new task types for /mine, /fish and /pve).
- Hardcoded /farm and /mine numbers (FX-09) move to batch 3 (the areas get 8 crops and 10 ores there).
- Island Depths tab and level tile (Q-17) move to batch 4 (island upgrade menu rebuild).
- FX-05 (a crash in the half second of a Sell Wand sale could duplicate): needs a server crash at exactly that moment; noted, fixed with the storage work in batch 3.

## Things you should know
- Two sessions deploy to the same test server. From tonight only this one releases. Please stop the old session (it works in `skyblock-wt-tutfix`) so it does not overwrite these releases.

## Waiting for you
(nothing yet)

# Morning report, 7 Nov 2026

Written during the night while you slept. Newest at the top of each section.

## In short
- The full plan is written: `docs/plans/FULL_PLAN.md` (everything: economy, /farm /mine /fish /pve, tools with 4 sets, island v2, spawners v2, quests, UI, other features, build order). It follows all your answers; nothing in it is waiting for you.
- Building happens in batches, each released to the test server. Status of each batch is below.

## Batches
| Batch | What | Status |
|---|---|---|
| 1 | Fixes and cleanup | **released** (update121, 02:14) |
| 2 | Economy: playtime pay limits, island-only shards, spawner payback **released** (update121); events, contracts next release; money x100 and prices v2 planned (`plans/ECONOMY_SCALE.md`) | in progress |
| 3 | Tools v4 core | |
| 4 | Island v2 | |
| 5 | Spawners v2 | |
| 6 | Quest line v2, menus, help, mailbox | |
| 7 | /fish | |
| 8 | /pve | |
| 9 | Polish (crates, collection, AH, shop) | |
| 10 | Pack v2, ranks v2 | |

## Released tonight
- **update121 (02:14):** batch 1 + the pay limit + spawner payback. GitHub `main` is now the release-v4 line plus tonight's work (Q-50 done). Jar md5 0a62117c.
- Live fixes on the test server (backups in `~/cfg-backup-7nov/`): the Prospector achievement was skipped live (text too long for Bedrock), and 5 shop items cost less than they sell for (bucket, water/lava bucket, bread, white wool): fixed and reloaded.

## Pay limit by playtime (your Q-32)
- `/paylimit` shows the ladder: passed steps, "you are here" with what you sent today, and the playtime to the next step.
- Money **sent** to other players per day (/pay, trades, auction buys, notes) by the sender's playtime: 0 h $20k, 3 h $100k, 6 h $500k, 12 h $2.5M, 24 h no limit. Island members never count. Resets at midnight (Oslo).
- I put the limit on the **sender** (that is what stops alts feeding a main account). The ColorGens picture limits what you **receive**. If you want it that way round, say so; it is one setting.
- Shards can only be paid to members of your own island (no shard notes, no shard trades with others). The old $10k rule is off.

## Spawner payback (your Q-01, "round numbers")
- Mob drop prices: tier 1 items $1, tier 2 $5, tier 3 $10, tier 4 $36 (about $3 / $6 / $10 / $18 per mob). A spawner now pays back in about 100 h (tier 1) to 150 h (tier 4) before upgrades; before tonight tier 1 paid back in about 9 h.

## Events at /farm and /mine (your 6-event idea)
- 6 events: Double XP, Double Drops, Double Shards, Instant Regrow, Proc Frenzy (Sweep/Vein chance x2), Golden Blocks (the money bonus always comes and is worth 5x). 2-4 run at once for 30 s, then a 30-60 s break. A boss bar per event, a chat line and sound when a round starts, and an "Events:" line on the /farm and /mine scoreboard. All numbers in `essentials.yml` → `grind.events`.

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
- Contract pools (FX-12): done. Contracts now offer /farm crops and /mine ores (new "ore" quest), island missions show 3 again.
- Hardcoded /farm and /mine numbers (FX-09) move to batch 3 (the areas get 8 crops and 10 ores there).
- Island Depths tab and level tile (Q-17): done (the Depths tab and the always-0 tiles are gone).
- FX-05 (a crash in the half second of a Sell Wand sale could duplicate): needs a server crash at exactly that moment; noted, fixed with the storage work in batch 3.

## Things you should know
- Two sessions deploy to the same test server. From tonight only this one releases. Please stop the old session (it works in `skyblock-wt-tutfix`) so it does not overwrite these releases.

## Waiting for you
(nothing yet)

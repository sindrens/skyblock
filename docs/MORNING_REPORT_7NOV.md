# Morning report, 7 Nov 2026

Written during the night while you slept. Newest at the top of each section.

## In short
- The full plan is written: `docs/plans/FULL_PLAN.md` (everything: economy, /farm /mine /fish /pve, tools with 4 sets, island v2, spawners v2, quests, UI, other features, build order). It follows all your answers; nothing in it is waiting for you.
- Building happens in batches, each released to the test server. Status of each batch is below.

## ⚠ Test server not updated since update128
Since about 08:00 SSH to the test server doesn't work: port 22 accepts the connection but sshd never answers (banner timeout); the Minecraft server itself is up (port 25565 answers). Likely sshd is overloaded or rate-limiting (for example the old Claude session still connecting in a loop), or the firewall throttles the Mac's IP. update129, 130 and 131 are **on GitHub `main`** but **not on the test server**. To deploy: when SSH works again, run `~/Development/.skyborn-claude/release2.sh 131` on the Mac (it deploys the newest jar; 131 includes 129 and 130). After that, two live config edits are needed (I'll do them myself once I can connect): in `upgrades.yml` the new Spawner Speed values and hiding Spawner Stack Limit, and in `tutorial.yml` the two new chapters.

## Batches
| Batch | What | Status |
|---|---|---|
| 1 | Fixes and cleanup | **released** (update121, 02:14) |
| 2 | Economy: playtime pay limits, island-only shards, spawner payback **released** (update121); events, contracts next release; money x100 and prices v2 planned (`plans/ECONOMY_SCALE.md`) | in progress |
| 3 | Tools v4 core: enchants, personal prestige + rebirths (update125), storage (update126), old tools module removed (update128) | in progress |
| 4 | Island v2: Farm and Mine Mastery (island ↔ area synergy) **released (update127)**; upgrade menu tabs, island stats next | in progress |
| 5 | Spawners v2: levels per mob type, limit 4 x level, prestige, 10 s to 5 s speed, no efficiency curve (update129) | built |
| 6 | Quest line v2: two new quest book chapters (Bramble's Farmhand, Flint's Apprentice), /mailbox, /menu → You gets Quest Book, Mailbox, Friends, Milestones (update130) | built |
| 7 | /fish: pond, 6 waters, island rod, Fishing Shards, rod enchants, storage, anti-AFK (update131) | built |
| 8 | /pve | |
| 9 | Polish (crates, collection, AH, shop) | |
| 10 | Pack v2, ranks v2 | |

## Tools v4 (batch 3): what you get in update125 (released 03:22)
- **Your own enchants:** right-click the island pickaxe or hoe, then **Your enchants** (middle of the menu). 17 per tool, levels 1-1000, paid with your own shards, working only at /mine (pickaxe) and /farm (hoe). Rows: area (Blast, Lightning Strike, Meteor, Nuke / Explode, Row Reaper, Tornado, Meteor Shower), shards (Shard Burst, Shard Echo, Compound Interest, Shard Jackpot), loot (Rich Vein/Bountiful, Cash Vein/Cash Crop, Quick Study, Motherlode/Golden Crop, Geode/Harvest Basket), boosts (Scholar, Combo, Lucky Streak, Keyfinder).
- A "Buy 1 / 10 / 100" button so Bedrock players can buy many levels too (Java: shift = 10, right-click = 100).
- Area enchants break the blocks around you as **drops only**: no shards, money bonus, quests, contracts, skill XP or unlock progress from those blocks (so they can't multiply everything). Big ones (Nuke, 100 blocks) are spread over a few ticks.
- Keyfinder is rare: 0.001% a block at level 1, 0.02% at level 1000 (about one key per 5,000 blocks at max), never doubled by Proc Frenzy.
- **Your own prestige:** the tool level, prestige (up to 50) and rebirths (no end) are now yours, not the island's. Prestige resets only the level: enchants, perks and shards stay. Each prestige: +5% XP and shards and +20 enchant levels (cap 20 at prestige 0, 1000 at prestige 50). Each rebirth: +10% for good, needs 10% more XP. Everyone starts from their island tool's old level, so nobody loses anything.
- Levels are much faster now: the first prestige takes about 15 minutes, prestige 49 about 50 minutes; the first rebirth about 25-30 hours (tune after a playtest).
- The island perks (Efficiency, Fortune, Haste, Sweep/Vein, Auto-Sell, storage ...) stay shared by the island.

### Changed from the plan (on purpose)
- **Collector** (pickaxe #15, "+collection progress") became **Geode** (one of every ore you have unlocked): the collection log only records the first find, there is no progress to boost. Collection rework is batch 9.
- Prestige is no longer owner/co-owner only and no longer takes everyone's shards (it is personal now).

## Storage and the fifth crop (update126, released)
- At /farm and /mine every drop goes into the tool's storage first (then your bag). When the storage is full, breaking pauses with an action bar line ("Storage full! Right-click the tool » Storage » Sell all"), so nothing is lost. The scoreboard has a "Storage: 1,200/5,000 (24%)" line.
- /farm has a fifth crop: **nether wart** at 60,000 harvests (sells for more than beetroot). The crop thresholds are now in `essentials.yml → grind.farm-unlocks` (FX-09).

## Cleanup done (your Q-50-52)
- GitHub: the 4 dead branches (`q/econ-v2`, `docs/full-spec`, `docs/specs`, `design/gui-round1`) are deleted. Each was saved first as a tag `archive/<name>` on GitHub, so nothing is lost.
- Old docs moved to `docs/archive/` (Depths, old PvE plan, join tutorial, pack v1 art briefs, superseded plans, the old morning report). Nothing was deleted. Still to do: merging the owner-answer files and shortening OPEN_QUESTIONS.md.

## Farm and Mine Mastery (batch 4 start, update127, released)
- Two new island masteries in `/is masteries`: **Farm Mastery** and **Mine Mastery**, 10 ranks each, paid from the island bank. Each rank needs the island's farming (or mining) pillar level and an island level, so the island has to grow to push the areas.
- Each rank gives every member **+100 enchant levels** on that tool (cap = 20 + 20 per own prestige + 100 per mastery rank, max 1000). So a player can push enchants either by prestiging or by helping the island.
- Not done yet (on purpose): Mastery gates on the higher crop and ore tiers. Gating them now would take tiers away from players who already have them; it comes with the 8 crops / 10 ores (Q-70).

## Old tools module removed (C-02, your Q-05, update128, released)
- The old `tools` module is gone: `/forge`, `/blueprint`, `/skin give`, the custom axes/swords/pickaxes, the Forge menu and the Depths inventory swap. `/tool` now always opens the island tool menu.
- Kept on purpose: enchant books still work on wardrobe armor (the enchants module stays), the Blacksmith NPC opens the tool menu, its help texts were rewritten.
- "Gain tool levels" quests and the Toolsmith achievements now count the island hoe and pickaxe levels.
- Nothing was deleted from the database: the old tool tables (and any Depths inventories stashed on 3 Nov) are still there, unused, in case we need to give something back. Old tool items still in inventories become plain tools with old lore.

## Spawners v2 (batch 5, update129)
- Every island has a **level per mob type** (1-16) from kills of mobs from its own spawners on the island: level n needs 100 / 250 / 600 / 1,500 x n² kills (tier 1-4). Tier 1 level 2 = 400 kills.
- An island can place **4 spawners of a type per level**, up to 64 (premium tier 5: 3). The old "32 per tier" cap and the efficiency curve (100/60/35/20/10%) are gone: every spawner works at 100%.
- At level 16 the type can **prestige** in the spawner menu: back to level 1, **+5% drops** for good (up to 10), and the limit stays at 64.
- Every spawner spawns every **10 s**; the **Spawner Speed** island upgrade (was "Spawner Rate", which did nothing before) takes it down to **5 s** over 10 levels. Spawn radius 3.
- Spawner menu: the info item shows level, a progress bar, kills, placed/limit, speed; the middle button is the prestige.
- Chat line for the island when a type levels up.

### Spawner questions (Q-72, defaults in place)
1. Islands that already have more than 4 spawners of a type keep them (they work), but can't add more until the type levels up. OK? (default: yes; the alternative is giving existing islands a starting level.)
2. The old "Spawner Stack Limit" island upgrade no longer does anything (the limit is per type now), so it is hidden from the menu (nobody can pay for nothing). Refund islands that bought it? (default: no refund until you answer.)
3. Add/Take buttons stay in the spawner menu (the plan said "nothing else"). Remove them? (default: keep, they're handy.)

## Quest line v2, mailbox, menu (batch 6, update130)
- Two new quest book chapters after "What's Next": **Bramble's Farmhand** (buy a hoe enchant, hoe level 10, get Explode, prestige the hoe) and **Flint's Apprentice** (buy a pickaxe enchant, pickaxe level 10, reach iron, get Blast, prestige the pickaxe). Each pays 500 shards and an Ember key.
- New quest step type "tool level" (counts your own hoe/pickaxe level).
- `/mailbox` opens the item box (`/box` still works).
- `/menu` → You now also has Quest Book, Mailbox, Friends and Milestones.
- Not done from the plan's quest line: the NPC chains for /fish and /pve (those areas don't exist yet) and the Kindle island chain (spawner, island size, bank) — the island chapter already covers chest, seeds, generator, Kindle and island level.

## /fish (batch 7, update131)
- `/fish` (and Travel → The Pond) takes you to a new pond in the grind world. Six waters unlock by catches: Shallows 0, River 500, Lake 2,000, Reef 6,000, Deep 15,000, Abyss 35,000. Each water has its own fish.
- **Every catch pays money** ($150 in the Shallows up to $450 in the Abyss, Golden Blocks x5). Vanilla fish don't get a big sell price, so AFK fish farms on islands can't print money.
- **Island Rod**: every island member gets a third island tool. With it a catch gives tool XP, **Fishing Shards** (a new personal shard type, island-only like the others), rod perks (Shard Finder, Lure, Luck of the Sea, Extra Drops, Storage, Auto-Sell ...) and 12 rod enchants (Shard Burst, Big Catch, Golden Fish, Treasure Chest, Keyfinder ...). Sneak + right-click opens its menu (a right-click casts).
- Catches go into the rod's storage (full = fishing pauses, nothing lost). The scoreboard at the pond shows your water, progress, money earned, storage, events and Fishing level. Fishing and Farming now show in `/skills`.
- **Anti-exploit**: eight casts in a row from one spot without looking around give nothing, and at most 20 catches a minute count. Bites come in 3-8 seconds; Instant Regrow events make them three times faster (Bite Frenzy).
- Not yet: an NPC "Marla" at the pond and her quest chain, a Fish Mastery, a rod picture in the pack.

## Released tonight
- **update129:** Spawners v2 (levels per mob type, limit 4 x level, prestige +5% drops, Spawner Speed 10 s to 5 s, no efficiency curve). Server copies of the island tool, progression and help texts were stale and are synced now (so the personal-prestige texts are really live).
- **update128:** old tools module removed (C-02); Sell Wand, Auto-Sell Chest and Chunk Collector off the Black Market until you approve (Q-71).
- **update127:** Farm and Mine Mastery (island masteries that raise the members' enchant caps by 100 a rank; 20 new island tasks: harvest island wheat + mine island cobblestone).
- **update126:** tool storage first at /farm and /mine (full = pause, no loss), storage on the scoreboard, nether wart as the fifth crop, farm thresholds in the config.
- **update125 (03:22):** Tools v4: 17 personal grind enchants per tool, personal prestige 50 + rebirths, faster tool levels. Jar md5 b4f1700c.
- **update121 (02:14):** batch 1 + the pay limit + spawner payback. GitHub `main` is now the release-v4 line plus tonight's work (Q-50 done). Jar md5 0a62117c.
- **update122 (02:30):** grind events, contracts, no Depths tab/tiles. **update123/124 (02:38):** the quest book (tutorial) did not work live: the test server still had the OLD tutorial's config and database tables, so `tutorial.yml` had no chapters and the quest-book tables were never created (every step would have failed). Fixed: the bundled `tutorial.yml` is now live (old one in `~/cfg-backup-7nov/tutorial-old.yml`) and a V004 migration creates the tables. Jar md5 b886b85b.
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
- **Q-71, Sell Wand, Auto-Sell Chest, Chunk Collector:** you're right that you never had them in the game. The previous Claude session built simple versions on 5 Nov (from the 4 Nov plan, `docs/planning/PLAN_4NOV.md`), but they were only sold in random Black Market rotations, and the crates still say "coming soon". My batch 1 "fixes" were bug fixes in that code, not new items. **Since update128 they are off the Black Market** (the code stays, nothing deleted). Say "on" (I put them back on the Black Market so you can try them), "off for good", or "redo them" (the planned 5 wand types in `docs/planning/SELL_WANDS.md`).
- **Q-70, /mine and /farm tiers (8 crops, 10 ores):** I stopped at 5 crops and the 7 ores because the next vanilla items sell for LESS than the tier before (sweet berries $6, melon slice $2, pumpkin $10 after beetroot $20; diamond $61 a block is below lapis $180 and redstone $132 a block; emerald, the top ore, is not sellable at all because of the villager loop). Options: (a) custom grind items with their own prices ("Golden Wheat", "Ember Ore" ...) in the economy pass (my pick), (b) change the vanilla sell prices (also changes island farms), (c) keep 5 and 7. Until you answer, the top /mine tier gives emeralds that can't be sold: (a) would fix that too.

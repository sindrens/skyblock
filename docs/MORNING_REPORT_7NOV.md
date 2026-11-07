# Morning report, 7 Nov 2026

Written during the night while you slept. Newest at the top of each section.

## In short
- The full plan is written: `docs/plans/FULL_PLAN.md` (everything: economy, /farm /mine /fish /pve, tools with 4 sets, island v2, spawners v2, quests, UI, other features, build order). It follows all your answers; nothing in it is waiting for you.
- Building happens in batches, each released to the test server. Status of each batch is below.

## ⚠ Test server not updated since update128
Since about 08:00 SSH to the test server doesn't work: port 22 accepts the connection but sshd never answers (banner timeout); the Minecraft server itself is up (port 25565 answers). Likely sshd is overloaded or rate-limiting (for example the old Claude session still connecting in a loop), or the firewall throttles the Mac's IP. update129-143 are **on GitHub `main`** (or in `~/Development/.skyborn-claude/` as bundles if the Mac was offline when I pushed) but **not on the test server**. To deploy: when SSH works again, **back up the database first** (money x100 in update139 changes every balance once), then run `~/Development/.skyborn-claude/release2.sh 143` on the Mac (it deploys the newest jar; 143 includes everything before it). After that, two live config edits are needed (I'll do them myself once I can connect): in `upgrades.yml` the new Spawner Speed values and hiding Spawner Stack Limit, and in `tutorial.yml` the two new chapters.

## Batches
| Batch | What | Status |
|---|---|---|
| 1 | Fixes and cleanup | **released** (update121, 02:14) |
| 2 | Economy: playtime pay limits, island-only shards, spawner payback (update121), events and contracts (update122), money x100 (update139), dynamic prices v2 (update141) | built |
| 3 | Tools v4 core: enchants, personal prestige + rebirths (update125), storage (update126), old tools module removed (update128) | in progress |
| 4 | Island v2: Farm and Mine Mastery (island ↔ area synergy) **released (update127)**; upgrade menu tabs, island stats next | in progress |
| 5 | Spawners v2: levels per mob type, limit 4 x level, prestige, 10 s to 5 s speed, no efficiency curve (update129) | built |
| 6 | Quest line v2: two new quest book chapters (Bramble's Farmhand, Flint's Apprentice), /mailbox, /menu → You gets Quest Book, Mailbox, Friends, Milestones (update130) | built |
| 7 | /fish: pond, 6 waters, island rod, Fishing Shards, rod enchants, storage, anti-AFK (update131) | built |
| 8 | /pve: arena with 10 rings (update132), Island Sword and Combat Shards (update133), bosses and the last masteries (update134), Warden Hale (update135) | built |
| 9 | Polish: crates, collection, AH, shop (update136), GUI pass (update140), small fixes (update142-143) | built |
| 10 | Ranks v2 leftovers and Black Market items (update138) built; pack v2 waits for your art direction | in progress |

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

## /pve v1 (batch 8, update132)
- `/pve` takes you to a new arena in the grind world: **ten square rings**, ring 1 outside (zombies) to ring 10 in the middle (ravagers): zombie, husk, skeleton, spider, stray, drowned, piglin brute, blaze, wither skeleton, ravager. Inner mobs have more health and hit harder.
- **Kills open the next ring** (200, 600, 1,500, 3,000, 6,000, 10,000, 16,000, 25,000, 40,000 kills in the ring before); a locked ring can't be entered.
- Every kill pays money ($20 to $350, Golden Blocks x5), gives **Combat XP** (until now there was no way to get Combat XP: it was Depths-only) and counts for events, quests and the scoreboard. Drops go straight to your bag.
- Six mobs per ring while someone is in the arena; they stay in their ring, don't burn in the sun and don't fight each other.
- **No PvP; dying in the arena costs nothing** (items and levels kept, back at the start).
- **Island Sword (update133)**: a fourth island tool for the arena. Kills with it give tool XP, **Combat Shards** (new personal shard type), sword perks (Shard Finder, Sharpness, Looting, Wisdom, Sweeping Edge, Lucky) and 8 sword enchants (Shard Burst, Scholar, Combo, Keyfinder ...). Tool menu `/is tools` now shows four tools.
- Not yet: the personal bosses with Boss Sigils, Combat Mastery gates, the NPC "Warden Hale" and his quest chain.

## Still to do (in this order)
1. ~~Dynamic prices v2~~ **built** (update141, defaults in Q-77). ~~Money x100~~ **built** (update139).
2. Pack v2: waits for your art direction (what you like / don't like about the current look); tool skins wait for Q-74.
3. Small fix FX-05 (Sell Wand / Auto-Sell: a server crash in the half second of a sale could lose or double it): needs a test on the live server, so it waits for SSH.
4. 5-row menus (help topics, guide tasks) and the pane bars in the sell chest / trash / trade (Q-76): your call.

## Bosses and the last two masteries (update134, plan: `docs/plans/BATCH8_BOSSES.md`)
- **Boss Sigils**: every 200 kills in a ring give one. `/pve boss` opens the boss menu: click a ring with a Sigil to summon its boss (Rotting King ... Ravager Alpha). Only you see and hit it, 3 minutes, a boss bar with its health and time.
- A boss pays 60x the ring's kill money (about 30% on top of the 200 kills that earned the Sigil, so bosses are a bonus, not the main income), five mobs' worth of loot, 25 kills of Combat XP and sword progress, and a crate key 10% of the time (Ember for rings 1-5, Cinder for 6-10; every 10th boss without a key gives one for sure).
- **Fish Mastery and Combat Mastery**: the last two Area Masteries (+100 rod / sword enchant levels a rank), same prices and island tasks as Farm and Mine Mastery.

## NPCs in the four areas and two new quest chapters (update135)
- Bramble (/farm), Flint (/mine), **Marla** (/fish) and **Warden Hale** (/pve) now stand at the entrance of their area. The areas are built by code, not from a build, so their spots are a new setting `npcs.extra-spots` (one line per NPC: id, role, world, x y z, facing). Move one there and `/skyblock reload npcs`.
- Marla buys fish (opens /sell), Warden Hale opens the boss menu (/pve boss). Both have their own skin, name, help lines and idle chat lines.
- Two new chapters in /tutorial: **Marla's Deckhand** (meet Marla, 50 fish, a rod enchant, rod level 10, Golden Fish; 500 Fishing Shards + Ember key) and **Hale's Recruit** (meet Hale, 100 kills, a sword enchant, sword level 10, a boss kill; 500 Combat Shards + Ember key).
- Boss kills are now in the audit log (`economy / pve_boss`, with ring and key), so staff can see them.
- Still open (FX-21): **Tinker and Sage** have no spot at spawn. I can't see the spawn build from here, so I didn't guess; stand where you want them and tell me the coordinates (or add a line to `npcs.extra-spots`).

## Batch 9: crates, collection, shop, auction house (update136, includes FX-17 and FX-19, plan: `docs/plans/BATCH9_POLISH.md`)
- **Crates**: the spin takes 2 s (was 3 s, still skippable). The crate tile and preview show a **luck bar** and "Top reward in N opens" (pity numbers unchanged: 3-4x more top rewards would flood the server). Every **"coming soon" row is gone**: Sell Wand, Auto-Sell Chest, Chunk Collector, Tool Skin, Tome, Blueprint and the rest don't exist yet (your Q-71 point). They already paid crystals, so their chance went to crystals: plain rows into the Crystal Pouch, star rows into a new star **Crystal Jackpot**. Players get the same value, and pity keeps its odds. A live `crates.yml` is changed once (backup `crates.yml.before-v3`). Key Fragments are off (their Depths drop is gone). Money pouches now grow with your best area skill (Farming, Mining, Fishing, Combat).
- **Collection**: the Depths pages (which warned on every start and could never fill) are replaced by **Farm** (5 crops), **Ores** (7), **Fish** (9) and **Mobs** (10 ring mobs + their 10 bosses), plus Crates. Every entry now counts **how many** you found ("Found: 3,412"). Every 25% of a page also gives **5 crystals** (on top of the island point; a full page is still a title and a Blaze key).
- **Shop** (your Q-42 "the 49 back"): the 34 items that were still missing are back: 12 planks, 15 bricks/polished stones, coarse dirt, packed and blue ice, moss carpet, baked potato, chiseled bookshelf, white banner, at their old prices. New rows: Planks, Bricks and polished stone, Ice, Brewing. Mob Drops adds arrows, wither skeleton skull, ink and glow ink sacs, nautilus shell, magma cream, phantom membrane and ghast tear (335 items, every price still passes the "can't buy and sell for profit" check). A live `shop.yml` is fixed once: worth-only items move back into their tab.
- **Auction house**: a **"Similar to your hand"** button shows only listings of the exact item you hold. On a new server (too few sales for a typical price) the sell page shows **"Cheapest now"** from other players' listings.

### Two fixes in the same update
- **FX-17 (exploit):** a block you place is now remembered in the chunk itself, so placing ores and breaking them after a restart no longer gives tool XP and shards. Pistons carry the mark along; explosions and cobblestone generators clear it.
- **FX-19:** `/help quest` now shows the quest line (the same as `/quest`); the daily and weekly topic is called Contracts.

### Shop question (Q-73, default in place)
The full plan says "one click buy, shift for 64", but your GUI-1 answer was "a click opens the buy page". I kept your GUI-1 answer: a click opens the buy page and shift-click buys a stack. Do you want a plain click to buy 1 instead?

### Auction house note
If a buy would go over the seller's daily receive limit, it is refused with a message (nobody loses money). The plan said the extra should wait in the mailbox. I kept the refusal because it is safer; tell me if you want the mailbox way.

## Your 3 Nov feedback list, checked again (update137)
- Invite a new player who already has their starter island: **works** since 3 Nov (fix 11a0a969). Only a player on a team island (2+ members) gets "already has an island". Accepting asks them to replace their solo island.
- Joining an island empties the inventory: **works** (joining wipes the profile; the fresh profile starts empty).
- The right tools after joining: **fixed now**. The new island's hoe, pickaxe, rod and sword used to come only at the next login; now they are handed out right after the join.
- Start with $1,000 after joining: **works** (`lifecycle.profile-reset.join-money: 1000`).
- `/sell all` sells the hotbar too: **works** (the "skip hotbar" switch is off by default since 3 Nov).
- Bone meal on island crops, one stage every 3 s, no hologram over crops, faster with upgrades: **works**. Bone meal adds 1-3 stages. The Crop Growth island upgrade and the hoe's Growth perk make crops grow faster. Island level itself does not change the speed; the upgrades do.

## Black Market items back (update138, plan: `docs/plans/BATCH10_PLAN.md`)
- Correction to my Q-71 answer: **Sell Wand, Auto-Sell Chest and Chunk Collector were already built** (on 4 Nov). They were only taken off the Black Market. I fixed two bugs and put them back on sale at their old prices (25 / 120 / 100 crystals):
  - Breaking an Auto-Sell Chest gave back an **unbound** copy (it could be traded away). It now stays bound.
  - Breaking a Chunk Collector dropped it and its contents **on the ground** for anyone to take. Now they go to the breaker (inventory, then /box), bound.
- The **Wardrobe Tome** is sold for real now: Tome (+2,500 set XP, 30 crystals, common slot) and **Greater Tome** (+10,000, 100 crystals, rare slot).
- Not built (Q-74): **Tool Skins** need held-item models in the resource pack (comes with pack v2). Tell me which skins and which tools. **Hologram** has no description anywhere: what should it be?
- Q-75: the Tome gives set XP (as built). A 4 Nov note said "+1 wardrobe slot". Which one do you want?

## Ranks v2 leftovers (update138)
- Ranks v2 was already built (names, colours, LuckPerms groups and perks, auto Ashborn, weekly kit and monthly tag, `/trash` admin only). Left over and done now:
  - Rank names (Hatchling ... Firecrown, Ashborn, Builder) can't be used as nicknames.
  - `/rank preview` also shows a sample chat line.
  - A test checks that every rank colour in chat matches the LuckPerms group and that prefix order follows the weights.
- Still waiting for the credit shop: rank prices, paying only the difference on upgrade, and the season script that gives the won ranks.

## Money x100 (update139) — READ BEFORE DEPLOYING
- **Back up the database and the `plugins/PhoenixSkyblock/` folder first.** This update multiplies every money amount by 100, once: balances, banks, island banks, AH listings, notes, the ledger, pending rewards, backups, stats, and every money setting and catalog price (owner-edited values too). Shards, crystals and credits are not touched.
- How it stays safe: every changed file gets `money-scale: 100` and a backup `<file>.pre-money-x100.bak`; each change is logged in the console. The database part is 30 small migrations that each run exactly once. A fresh install starts at the new numbers and is never scaled again.
- New numbers, for example: start money $100,000; /pve ring 1 pays $2,000 a kill (ring 10 $35,000); bank tier 10 holds $1T; the pay limit ladder is $2M / $10M / $50M / $250M / no limit.
- Everything keeps its ratios: island level points, upgrade prices against income, shop against sell prices (the "no buy-and-sell profit" check still passes).
- What to check on the test server after the first start:
  1. The console shows "money x100 done" lines.
  2. `/bal` is 100x what it was.
  3. `/is level` shows about the same level as before.
  4. A shop price and a /sell price are both 100x.
  5. `/ah` listings are 100x.

## GUI pass, tool names, Tinker and Sage (update140, plan: `docs/plans/GUI_PASS.md`)
Your message before bed: "&a colour on hoe and pickaxe, change names, ALL GUIs". What I did (defaults, easy to change):
- **Tool names are green (&a)**: Island Hoe (was "Harvester Hoe"), Island Pickaxe, Island Rod, Island Sword; prestige stars stay orange after the name. The join message now names all four tools.
- **No glass panes as decoration**: removed from the /p punish menu (full gray fill and red divider), the battle pass empty tiles and the crate spin result. Status tiles in about 15 menus (daily, vote, milestones, pass, upgrade and mastery levels, guide tasks, loadouts, enchant slots, boosters, top list, AH durations, island permissions) use dyes now: lime = done, yellow = now, gray = later/locked.
- **Kept on purpose (Q-76)**: the sell chest, trash bin and trade window still use a pane row or pane slots, because those menus take real items and the bar shows where items can't go. Removing them safely needs a slot-protection check on the live server first. Do you want them gone too?
- **Everything reachable from /menu**: the Rewards menu has a row with Crates, Vote, Boosters and Black Market; Travel has **The Arena** (/pve) next to the Pond.
- **Old Depths/Forge texts gone from menus**: skills (Mining at /mine, Foraging = grown island logs, Combat at /pve), enchanter lore, settings, profile switching, mailbox sources, wardrobe gates.
- **Boss names** now live in the messages file (`grind.pve.boss.names`).
- **Tinker and Sage at spawn (FX-21, done)**: the two Forge Quarter NPCs both opened /tool. The quartermaster is now **Tinker** (tools) and the blacksmith is **Sage** (skills), so the quest line can send players to them.

## Dynamic prices v2 (update141, plan: `docs/plans/DYNAMIC_PRICES_V2.md`, your P-03)
The old model (one price per item for the whole server that fell with every sale) is switched off. Instead:
- **Per island, per item, per day:** the first **3,000** items pay 100%, then every 100 more pay 1% less, down to **60%**. Island members share the count; a player's own count follows them too, so hopping islands doesn't reset it.
- **04:00 reset (Oslo):** half of what was sold over 3,000 carries into the next day, so a heavy seller starts a bit lower and is back to full price after a normal day.
- **Weekly change:** every Monday 00:00 each item gets -10% to +10% (2% steps) for the week, the same for everyone. The shop shows it as "Price: +6% today".
- Hot Item (+25%) and sell bonuses are unchanged. The shop's "never cheaper than it sells for" rule is now 5.5x (was 7.5x), so it only got looser.
- The sell chest says "Price today: 85%" (green/yellow/red) when part of a sale is past the 3,000; `/eco sell price <item>` shows what all islands sold of it today.
- All numbers are in `economy.yml → sell.daily.*` and `sell.weekly.*`. Defaults I picked are in **Q-77**.

## Small fixes (update142-143)
- **/titles**: titles nobody can earn any more (Deep Diver, Tycoon, Reborn: their achievements are off) are hidden unless you already have them (`cosmetics.yml titles.hidden-unless-owned`).
- **Spawn portal**: the old "The Depths" sign over the portal that leads to the farm and mine world now says **Farm & Mine · /farm /mine** (the hologram updates on the next build apply or restart).
- **FX-15**: profile switching is blocked in the /farm and /mine world (it was blocked in the Depths world, which no longer exists).
- **/skills** no longer promises "Next unlock: Mine Tier II · Copper" and the like (those were Depths tiers, which are gone; /farm and /mine unlock by amount). Each skill now says its levels raise the island level. An owner-edited unlock list stays as it is.
- Contract text "Kill N Depths mobs" is now "Kill N mobs". The /help Skills topic lists all five skills and where to train them.
- Two lore lines that were too wide for Bedrock were split (island missions without an island, the sell chest price note).

## Hoe, shards, farm money and spawners (update145, plan: `docs/plans/HOE_SHARDS_SPAWNERS.md`)
Your message after testing ("prestige 6 in 5 minutes", "so few shards", "each level should be felt"):
- **Island Hoe levels 15x slower** (`islandtools.yml levels.hoe-xp-divisor: 15`). A level now takes 15x more crops, so it also brings 15x more shards.
- **More shards:** 60% a ripe crop (was 40%), and the **Shard Finder** gives 3% a level (was 1%; 100 levels = 4x shards). Its lore shows only the level ("More shards", "Even more shards"), no numbers.
- **Shards and money float up from the block**: "+2 ◆" on the crop, ore, catch or mob that gave it, gone after 0.5 s, only you see it. Same for the /farm, /mine, /fish and /pve money. The action bar lines for these are gone. At most 8 at once per player; they are never saved and never count for island entity limits.
- **Less money from farming:** crop sell prices -40% (wheat $700, carrot/potato $900, beetroot $1,200, nether wart $1,800 ...) and the /farm bonus halved ($2,000 to $15,000).
- **/shop seeds and crops** cost 10x their new sell price: wheat seeds $7k (was $150k), carrot and potato $9k (was $250k), melon and pumpkin seeds $6k (was $400k).
- **Spawners drop 1 of each of their drops** (cow: 1 beef + 1 leather; zombie: 1 rotten flesh; spider: 1 string + 1 spider eye), and every spawner pays back in **about 50 hours**:
  - Tier 1 $5M (pig, cow, chicken, sheep, rabbit; hoglin $7.5M, moved from tier 4)
  - Tier 2 $12.5M (zombie, skeleton, spider, cave spider, slime; zombified piglin $17.5M, moved from tier 4)
  - Tier 3 $30M (creeper, witch); drowned, husk and stray $12.5-13.5M (their loot is tier 2 loot)
  - Tier 4 $75M (blaze, enderman, guardian)
  - A tier's best spawners earn about 2.5x the tier below, so climbing is worth it.
- Spawner loot (`mob-loot`) never gets the 3,000-a-day price drop.
- Your page answers (update146): tier unlocks need **10,000 / 25,000 / 50,000** kills of the tier below, and premium tier 5 needs **100,000** tier 4 kills.
- Live files are updated once (sell.yml catalog-version 3, shop.yml catalog-version 8; a price you changed yourself stays). Every change is in the server log.

## Simpler spawners, no weekly price change, payment fix (update147)
Your "simple, nothing unnecessary" answer:
- **Spawners in /shop:** tier 1 pig, cow, chicken, sheep, rabbit ($5M) · tier 2 zombie, skeleton, spider, slime ($12.5M) · tier 3 creeper, witch ($30M) · tier 4 blaze, enderman, guardian ($75M). Every mob in a tier earns the same; each tier about 2.5x the one below; ~50 h payback. Hoglin, cave spider, zombified piglin, drowned, husk and stray are no longer sold (placed ones keep working).
- **No weekly price change** any more (`sell.weekly.enabled: false`). Only the daily island price stays; spawner loot always pays full.
- **Fix:** shard and money payments could fail when two hit the same account at once (MariaDB "Record has changed since last read"). They are retried now, safely (every try is a full rollback).

## Smaller economy, storage, enchants (update148)
- **All money prices / 4** (sell, shop, spawners, island upgrades and masteries, /farm /mine /fish /pve pay, money pouches) and **start money $10k** (also after joining an island). Knobs: `economy.yml sell.price-multiplier: 0.25`, `shop-settings.yml buy.price-multiplier: 0.25`, `upgrades-settings.yml cost-multiplier: 0.25`. Spawners still pay back in ~50 h.
- **/farm crops are clear steps**: wheat $100, carrot $175, potato $275, beetroot $400, nether wart $600 a crop (after the / 4).
- **The $17.5k a carrot was Golden Blocks** (the bonus on every crop at x5). Now Golden Blocks makes the bonus 3x as likely (12% instead of 4%) at x2: still rare.
- **Island Hoe / Pickaxe / Rod drops go to your inventory first**, then the tool storage; only when both are full does breaking pause. Shards keep coming while there is room.
- **Enchants**: hoe and pickaxe enchants cost 10x the shards a level (Explode level 1: 20 shards), and go to 500 (everyday ones), 300 (mid) or 100 (rare: Tornado, Meteor, Nuke, Jackpot, Keyfinder). The prestige cap still opens 20 levels per prestige.

## Look v2: feature colours (update169, plan: `docs/plans/LOOK_V2.md`)
- One colour per thing, flat (no gradients): PHOENIX red + SKYBLOCK white on the scoreboard and tab, money green,
  credits aqua, crystals light purple, Farm yellow, Mine aqua, Fish blue, PvE red, Island green, Events light purple.
- Scoreboard in blocks (You, the area, Event) with a coloured bar on each row and small-caps labels (capitals on Bedrock).
- Chat: `PHOENIX »` and every module prefix flat in its feature colour.
- Tab: name, season and players online; footer `/help · /store` and the IP.

## The credit shop (update167, plan: `docs/plans/STORE_FULL_PLAN.md` section 1)
- **`/store`** (also `/credit shop`, `/creditshop`, `/buy`, and the Store button in `/menu`): tabs **Ranks**, **Boosters**,
  **Cosmetics**, **Extras**; your bought and earned credits on top; a confirm screen for every buy; if a delivery fails,
  the credits come back by themselves.
- **Ranks:** the 7 ranks with 3-4 perk lines each, prices 500 to 10,000 credits, an upgrade costs only the difference,
  bought credits only. A purchase is announced ("Steve is now Firewing! Type gg") and everyone who types gg within 30 s
  gets 1 earned credit (once per purchase, no daily limit). Credit packs bought on Tebex get the same gg moment.
- **Boosters:** server-wide Sell / Tool XP / Skill XP / Crystals boosters, 30 min 300 or 60 min 500 credits (you get the
  item, right-click starts it for everyone).
- **Cosmetics:** links to the tool skins (per tool) and the wardrobe sets.
- **Extras:** premium battle pass (1,000), one pass level (100), streak save (100, while a broken streak can still be
  saved, 2 days), profile slots 2 / 3 (500 / 1,500), an extra loadout preset (300, up to 3), an Island Rename Token (50).
- **`/credit gift <player>`** (or the cake button): the shop for an online friend, their prices, paid with bought credits.
- **`/credit pay <player> <amount>`:** send bought credits, confirm screen, both get a line. **`/credit history`:** the
  credit lines of your transactions.
- **`/is name [name]`:** name your island (the first name is free, later changes use a Rename Token).
- **Chargebacks:** a chargeback whose credits were already spent removes the ranks bought with credits and bans (console
  `/ban`, config `credit-shop.chargeback-ban`).
- **Eclipse key for credits: off** (keys are earned only; the Black Market keeps it for crystals).
- **For you:** make the Tebex webstore with the 4 packs (500 = 5, 1,000 = 10, 2,700 = 25, 14,000 = 130), each running
  `credit grant {id} <package> {transaction}` (packages `c500`, `c1000`, `c2700`, `c14000`).
- **Test:** `/eco give <you> credits 20000 test` (owner node), then `/store`: buy Hatchling, then Firewing (pays 1,500),
  type gg from a second account, buy a booster, the pass level, `/is name Test Isle`, a rename token and rename again,
  `/credit pay <alt> 10`, `/credit history`, `/credit gift <alt>`.

## Look v2 and the rest of the store plan (next)
Answers saved in `docs/plans/LOOK_V2.md` (theme B, feature colours) and `STORE_FULL_PLAN.md`. Next: the new look on
every scoreboard, tab list, chat line and menu; then /pv, join when full, /reclaim and cosmetic points; island boosters
and KOTH; masteries; Black Market v8; Cosmetics v2; Fire tools; the resource pack; the compound pass.
Note: small caps letters (ᴍᴏɴᴇʏ) are not Bedrock-safe (the build checks every message), so they come as a style tag
that shows small caps on Java and plain capitals on Bedrock.

## Hoe plan, part 1: the enchant core (update165, plan: `docs/plans/HOE_IMPROVEMENTS.md`)
- **Milestones instead of breakthroughs:** every level is stronger (a fast start: Shard Burst 100 → 110 at level 2 → 385 at level 100), and every 4% of the track an **extra +10%** that stays (25 per enchant: every 100 levels of 2,500, 40 of 1,000, 20 of 500). Area and timed enchants get the extra chance instead (no lag). Every milestone: action bar + sound; every 5th: a title. The tile shows "Next boost: +10% at level 300 (14 to go)" with a bar.
- **Shard Finder** milestones too: +10% extra every 25 levels.
- **Buy Max** (x1 → x10 → x100 → Max) and an **On / Off mode** in the enchant menu (switch an enchant off, its levels stay).
- **One action bar line** for all procs at once, with the **combo counter** ("x84 · +8.4% shards · +6% XP").
- **Never a full-bag pause** at /farm and /mine: what does not fit drops on the ground **for you only** (nobody else sees or takes it).
- **Storage warning** at 90% (once), the storage tile turns orange. The tool tile shows the **next crop or ore** ("Carrots: 1,240 / 3,000").
- **Sweep and Vein** without a prestige (tool level 30).
- Shard bonus cap **+1,000%** (was +300%).
- **Prestige reward** on every tool: a Blaze key, a Phoenix key at every 10th prestige and every rebirth. **Hoe titles:** Farmhand (1), Harvester (10), Field Lord (25), Harvest King (50), Reborn Reaper (first rebirth).
- **Juicy hits:** big procs pop with particles and a chime that rises with your streak (only you).

## Tools v6: compound shards and 9 new enchants (update164, plan: `docs/plans/TOOLS_V6_COMPOUND.md`)
- **Shard Finder:** +3% a level on top of the last level (compound), 250 levels (about 1,600x shards at the max). The menu shows only the level. Its price grows +3.5% a level, so every level costs about the same number of blocks.
- **More shards:** 10 a block (was 6), the rod 300 a catch (was 200), the sword 80 a kill (was 50). Shards are **always rounded up** (10.3 is 11).
- **Half price at /farm and /mine:** every crop and ore sells for 50% (wheat $200, carrot $350, potato $550, beetroot $800, nether wart $1,200; coal $500 ... diamond $3,050, emerald $4,100).
- **Enchants:** everyday ones 2,500 levels, middle ones 1,000, rare ones 500. Each level is a bit stronger than the last (compound), up to 15x at the max. Shard Burst gives 100 at level 1, 1,500 at the max.
- **9 new enchants:** Shard Storm, Frenzy, Golden Touch, Duplicator, XP Surge, Crystal Find, Booster Find, Chain Reaction and Shard Rain. Pickaxe and hoe 26 enchants each, rod 19, sword 15.
- **No more prestige walls:** no enchant needs a prestige. They open at tool levels 1 to 48, closer together near the top. Once open, a prestige never locks one again. The level cap grows with every level the tool ever reached, faster near the top (level 50 at prestige 0: a third of each track, prestige 2: everything).
- **New enchant menu:** a coloured banner per row (Area, Shards, Loot and Finds, Boosts) with how many are open, then the enchants in unlock order.
- **Auto-Sell and Auto-Compactor:** buy once, on any tool, and every tool has it, through prestige and rebirth.
- **Small notes:** the Angler mastery now asks for island levels 5 / 15 / 30 / 50 (not Farming pillar levels). Mob Loot stays at +12% a level.
- **Note:** a Shard Finder above level 250 from before now counts as 250 (now worth far more than before).

## The whole list, batch 1 (update160, plan: `docs/plans/WHOLE_LIST_7NOV.md`)
- **/mine:** emerald is the last ore and sells ($8,200). Emeralds from an island generator sell too; villager emeralds still don't.
- **/shop:** a click opens the buy screen (1 / 8 / 16 / 32 / 64 and the price).
- **Black Market, new list:** island items, spawners, the Coinmaster set and the elytra cost **money**; books, keys, tomes, set tokens and the tag cost **crystals**. Premium spawners $200M (1 a day), Coinmaster $40M, Auto-Sell Chest $25M, Chunk Collector $20M, Elytra $5M, Sell Wand $2.5M. Blueprint removed. The wallet shows your money too.
- **Switched on:** Mob Loot (+12% spawner drops a level, up to +120%) and XP Boost island upgrades, custom island generators (the Generator upgrade can be bought), the Angler achievement, the Angler mastery (+% fishing loot with the rod), the Rewards menu with `/weekly` and `/monthly`.
- **KOTH** now pays **2 Conqueror keys** + 500 crystals (was 5 Ember keys); the Conqueror crate is shown in /crates (no pedestal at the yard until the builder adds a 7th).
- **Gatehouse:** the iron bars in front of the /fish and /pve portals are gone and their particles are on.
- The "Depths" upgrade tab is called **Grinding** (Mine Fortune, XP Boost). "/credit pay (coming soon)" is gone from the /pay message.
- **Still off on purpose:** Lumberjack, Timber, Deep Pockets, Crystal Rush, Lucky Strike (Depths leftovers); Swift Hands, Beast Tamer, Island Fuel, custom crops and the credit shop wait for your answers on `DECISIONS_RANKS_7NOV.html`.

## The whole list, batch 2 (update161)
- **Kindle's Island**, a new quest book chapter (8 steps): place a spawner, harvest 50 island crops, mine 50 generator blocks, make your island bigger, put $10,000 in the island bank, buy one more island upgrade, reach island level 2, check /is top. Reward: $2M, 50 crystals, a Blaze key and the **Kindled** title. Steps you already did count at once (island level too).
- Rowan's chain: the first chapters already are it (Rowan, /menu, /is, /farm, harvest, sell), so nothing doubled.
- Mastery island-level gates checked against the new island XP pace (level 20 is about 5-6 hours alone, level 50 about 2 days alone, much faster in a team): kept as they are.

## The whole list, batch 3 (update162)
- **Menus:** 239 lore rows in 21 menus got the tool-style bar (`| Label: value`): island menu, members, warps, visit, notes, bank, sell, transactions, /is level and upgrades, rewards, crates, boosters, trade, wardrobe, profiles, achievements, collection, milestones, shop, spawner. 64 menu titles are now Title Case ("Buy Upgrade", "Island History", "Item Box" ...).
- FX-09: the carrots step no longer says "3,000" (the number is in `essentials.yml grind.farm-unlocks`).
- ROADMAP and BACKLOG show what is built now.
- **Not done on purpose:** FX-05 (a hard crash in the half second of a Sell Wand sale): making a chest crash-safe needs a world save per sale, too heavy for a very small risk. 5-row menus stay 5 rows (your "smaller GUIs" rule).

## The whole list, batch 4: Island Vault (update163)
- **`/is storage`** (or `/is vault`, or the new ender chest tile in the island menu): one chest for the whole island. Every member sees the same chest at the same time, so nothing can be taken twice. Rows come from the **Island Vault** upgrade (2 rows at level 1, up to 6), which can be bought now. Without it the tile says where to buy it.
- Who: members whose role may open containers (the leader sets it in /is permissions). Bound items and island tools can't go in.
- Saved a second after every change, when the last person closes it, every minute and on shutdown.
- The challenge book's hand-in tasks now have a working **"From island storage"** button (the items and the task progress are saved together, so a crash never takes one without the other).

## Your picks, part 4: King of the Hill (update159)
- **KOTH in the Warzone**, every 3 hours for up to 15 minutes: one chat line ("King of the Hill! /koth"), `/koth` takes you in front of the hill. Stand on the gold 7x7 hill with no other island on it: 2 minutes in total wins (another island on it pauses the clock; after 15 minutes the island that held it longest wins). Every player of the winning island in the Warzone gets 5 Ember keys and 500 crystals. PvP is on there; you keep your items.
- The Warzone has no build yet (only a 7x7 builder platform), so KOTH lays a 31x31 stone brick floor around it (only into air, never over a build). When the builder makes the Warzone, set `essentials.yml server-events.koth.arena-radius: 0` and move `koth.x/y/z` to the real hill. The Gatehouse portal sign now says "Warzone · KOTH: /koth" (the portal itself stays closed until that build exists).

## Your picks, part 3: tool skins (update158)
- The hoe, pickaxe and sword menus have a **Skins** button (bottom right): Diamond (free), Iron 250, Golden 500, Copper 500 and Netherite 1,000 credits. Bought once (with a confirm screen), switch any time; only the look changes. They are the vanilla models until the pack v2 art exists. List and prices: `islandtools.yml skins.list`.

## Your picks, part 2 (update157)
- **Meteor at spawn**, every hour: one chat line ("A meteor landed at spawn: /spawn"), a title at spawn, five glowing fragments on the plaza north of the spawn point and one boss bar. Hit a fragment 25 times (everyone can help); whoever breaks it gets $25k-150k, 10-40 crystals and a 20% chance at an Ember key. Gone after 2 minutes. No blocks change.
- **Fishing Contest** at /fish, every 2 hours for 10 minutes: one chat line to start, a boss bar at the pond ("7:12 · You: 12 (#3)"), most catches win: #1 3 Ember keys + 300 crystals, #2 2 + 200, #3 1 + 100. One chat line with the top 3 at the end.
- Every number is in `essentials.yml server-events.*`.

## Your picks, part 1 (update156, plan: `docs/plans/OWNER_PICKS_7NOV.md`)
- **Shard Finder shows its number again** ("+111% shards") on the tool and in the perk menu.
- **Top Tools board** in the Hall of Heroes (the old Foraging board): players by tool prestiges, a rebirth counts 50.
- **Auto-Compactor** (hoe and pickaxe perk, tool level 15, 40,000 shards): 9 wheat become a hay bale, 9 ingots a block (coal, redstone, lapis, diamond, gold, melon, slime too), in your bag and in the tool storage. A block sells as exactly 9 items and counts as 9 for the daily price, so it never changes what you earn.

## /fish and /pve contracts (update154)
- **Fix (update155):** the test server's contracts still used the very first pools (Depths mine/chop/kill, which are never offered since the Depths are gone), so you only got sell, tool levels, visit and like. They now use the current pools: harvest, ore, fish, fight, sell, tool levels, enchant, visit, like.
- New contract types: "Catch 25 fish at /fish" and "Kill 100 mobs at /pve" (daily), 150 / 700 (weekly), 250 / 1,000 (island missions). The weekly island missions pick 3 of the 5 kinds.

## Quests and leftovers (update153)
- **Quest book:** "Reach hoe level 10" (and the other tool-level steps) now count the level your tool already has. The "invite a friend" step also completes by visiting or liking an island, so solo players are never stuck.
- **Contracts:** buying grind enchant levels counts for "enchant" contracts.
- **Masteries that did nothing are hidden** (Deep Pockets, Crystal Rush, Lucky Strike: their bonuses were only read by the old Depths). Bought ranks stay saved.
- Battle Pass tiles without a reward no longer say "Coming soon".
- **Suggestions for you:** `SUGGESTIONS_7NOV.html` in this folder: 12 ideas from the big skyblock servers with Yes / No / Other buttons. Copy your answers and paste them to me.

## Less spam, clearer events, spawn fixes (update152)
- **Grind events:** one event at a time for 60 s, then 3-5 minutes off (was 2-4 at once for 30 s every minute). It starts with a title that says what it does ("DOUBLE DROPS · Every drop x2 · 60 s"), one boss bar, no chat line, never the same event twice in a row.
- **No more chat spam:** buying perks/enchants, Battle Pass levels, spawner levels, island limit warnings, booster endings and island level rewards are action bar lines; skill level-ups are a title only; collection finds are a title only; the island level-up chat comes every 5 levels; the /farm /mine /fish /pve welcome comes once per session; joining the booster queue is no longer broadcast.
- **GUI pass:** every menu row uses the tool style ("| Label: value"), and confirm screens have noun titles ("Confirm Prestige" instead of "Prestige?").
- **Spawn:** the Crate Keeper stands in the Crate Yard and the Tailor in the Market Square; the Black Market NPC opens /blackmarket and the Contract Board opens /contracts. Gatehouse portals say "The Pond · /fish", "The Arena · /pve", "Warzone · Closed"; the Events board explains the grind events; the old Depths board is now "Farm & Mine". The "Coming soon" leaderboard shows Island Worth; empty boards say "Be the first!".

## Island level v2 (update151, plan: `docs/plans/TOOLS_V5.md` section 3)
- **Island level = island XP**, and it updates by itself within a few seconds (no Recalculate button, no waiting). Island XP comes from everything members do: crops on the island and at /farm, ores from generators and blocks at /mine, catches at /fish, kills at /pve, and every spawner mob that dies on the island (0.1 each, lava farms too). Bank, skills, achievements and block worth no longer count (block worth stays as the "Value" tab of /is top).
- **Each level needs a bit more:** level 10 = 47,000 XP (about an hour alone at 13 blocks a second), level 50 = 530,000.
- **Rewards:** every level puts $20,000 x the level in the island bank (level 10: $200k); every 5 levels each member gets crystals (10 x the level) and an Ember key; every 10 levels the whole server hears it. Islands that already have levels get those rewards once now.
- **New /is level menu** in the tool style: the level bar, where the XP comes from (Farming, Mining, Combat, Foraging with their share) and the next five rewards. Island Upgrades button on top.
- **/is upgrade** in the tool style too (| Stats, | Price rows), and upgrades that do nothing ("Coming soon") are hidden. No chat line per pillar level any more.

## Tools v5 and spawners (update150, plan: `docs/plans/TOOLS_V5.md`)
Your answers before you went to sleep:
- **Perks are your own now** (Shard Finder, Extra Drops, Efficiency, Fortune, Storage ...), bought with your own shards, and they work everywhere: your island, /farm, /mine, /fish, /pve. Everyone who already played got the island's old levels as their own, converted to the same strength. New players start at 0.
- **Long tracks:** Shard Finder 2,500 levels (up to 26x shards), Extra Drops 1,000 (+150%), Wisdom 500 (+500% tool XP), Sell Bonus 500 (+100%), Growth 500, Storage 500, Lucky 1,000.
- **Balanced at 13 blocks a second:** every block the tool breaks counts for shards now (also Sweep and Explode/Blast blocks). Base 6 shards a block. Simulated: a new upgrade every few seconds at the start, Shard Finder ~50 after 1 hour, ~500 after 50 hours, everything maxed after ~200 hours; popups go from "+6" to "+500". Prices are about 5x lower than this evening.
- **No spam:** buying a perk or enchant level is an action bar line; a Shard Pouch floats up from the block (no title).
- **Spawners:** 64 of a mob type at the first level, +32 a level (96, 128 ... 544). New island upgrade **Spawner Capacity** (/is upgrade, Production): +32 of every mob type per level, 10 levels. One block holds them all.
- **Kills to go:** the hologram above every spawner shows "Lv 3 · 1,240 kills to +32", the spawner menu shows the same (new layout in the tool style), and the "limit reached" message tells how many kills are left.
- **Cooked meat:** spawner mobs that die burning (lava, fire, Fire Aspect) drop cooked meat.

## Bigger shard numbers and breakthroughs (update149, plan: `docs/plans/SHARDS_X10_BREAKTHROUGHS.md`)
Your "only 1-3 shards even maxed ... low to high ... goodies that suddenly feel a lot stronger":
- **Every shard x10, same value:** a ripe crop now gives about 6 shards (was 0.6), so you see "+6", "+20", "+60" instead of "+1". Prices x10 too (Shard Finder level 1: 460, Explode level 1: 200). **Balances x10 once** (database migration for all four shard types), shard notes and shard block pay (10 a break) too. Shard Burst 30-60, Jackpot 400-1,000, Lucky pouch 250-2,500. Tutorial shard rewards 1,000 / 5,000.
- **Every level costs a bit more than the one before** (smooth curve, never a wall).
- **Breakthroughs:** every enchant jumps at 2%, 10%, 30% and 100% of its max level: x1.5, x2, x2.5 and x3 (a 500-level enchant at 10, 50, 150 and 500; a 100-level one at 2, 10, 30, 100). Between them it grows slowly. The menu always shows the next one ("Breakthrough: x2 at 50 (12 to go)"), and reaching one gives a title, a sound and a chat line. Knob: `islandtools.yml enchants.breakthroughs`.
- Why (research): rising numbers feel like progress; a visible next goal makes people speed up toward it (goal gradient); a plateau followed by a big jump is what players remember.

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

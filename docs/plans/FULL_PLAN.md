# Full plan (night of 6-7 Nov 2026)

The owner asked for the absolute full plan of everything, then the full build, without more questions. This file is that plan. Every number is a config value (staff can change it in-game); playtests tune them. Sources: `MASTER_PLAN.md` (bugs, cleanup), `OWNER_ANSWERS_6NOV.md`, `planning/TOOL_ENCHANTS_6NOV.md`, `planning/RESEARCH_6NOV.md`, `planning/INSPIRATION_6NOV.md`, `plans/TOOLS_V3.md`, `planning/FEEDBACK_5NOV.md`.

## 0. Rules that every system follows
1. **Fast, visible progress.** Many small steps, never "3 levels of 50 hours". The next goal is minutes away early, under 30 minutes mid game; only rebirths take hours. Every gain shows on screen (actionbar "+1,200 XP | 68% to level 12", a sound, a short title for big moments).
2. **Big numbers.** All money ×100 (see §1). Compact format ($1.2M, $3.4B, $5.6T).
3. **Synergy, not a single best activity.** /farm, /mine, /fish and /pve each pay about the same per hour at the same progress. The **island** is where money grows long term (spawners, generators, island crops, bank interest, sell multiplier). The grind areas are where **shards, tool levels and XP** come from. Each area's top levels are **gated by island upgrades** (Area Masteries, §4), and Area Masteries ask for work in the other areas (cross-requirements). Nobody can max one area while ignoring the island.
4. **Less is more.** One menu per thing, one clear next step, one action line. A feature that needs a paragraph to explain is cut or simplified.
5. **No glass panes in GUIs.** Empty slots stay empty (air) until the pack v2 background replaces them; menus are laid out tight so there is no dead space.
6. **Text:** short, professional, plain. Lore: max 8 lines; the requirement or lock reason first, then the effect (now → next), then one action line. One server prefix for system chat; framed blocks only for big announcements; every big title is also written in chat. Bedrock-safe symbols only (build test).
7. **Security:** every money, shard and item path is atomic and audit-logged; caps per tick; no item leaves a container before the matching money is reserved; placement limits hold against pistons, explosions, island resets and transfers.

## 1. Economy v2
| Topic | Plan |
|---|---|
| Scale | **Money ×100** once: every money config value (sell prices, shop prices, upgrade and mastery costs, bank tiers, spawner prices, AH min/max, money notes, thresholds, quest/crate reference income, grind bonuses, start money) and every stored balance (money, bank, island bank, AH listings, mailbox money). One migration, recorded in `decisions.yml`, idempotent. Shards, crystals and credits are not scaled. |
| Income targets (after scale) | hour 1: about $5M total; hour 10: $200M/h; hour 50: $5B/h; hour 200: $100B/h+. Late game reaches trillions. |
| Currencies | Money; Crystals (quests, challenges → Black Market, books, wardrobe); **Farming, Mining, Fishing and Combat Shards** (personal, tool currency); Credits (store). No other currency. |
| Shards | Personal. Payable only to members of your own island (`/pay <member> <amount> <shard>` and the island menu). Never via trade or AH. |
| Pay limits (anti-boost) | Replaces the "$10k earned" gate. Daily limit on money others can give you (/pay, trades, AH sales to players): none → $200k; 3 h played → $1M; 6 h → $5M; 12 h → $25M; 24 h → no limit. Active playtime only (AFK does not count). Island members are exempt. Over the limit, the rest waits in the mailbox until the next day. `/paylimit` menu: one row ladder, "Done", "You are here", "Unlocks in 24m of playtime". `/paytoggle` blocks incoming payments. |
| Dynamic prices v2 | Per island and per item per day: the first 3,000 items sell at 100%, then -1% per 100 more down to 60%; half of the excess recovers at 04:00. Weekly global drift every Monday: -10% to +10% in 2% steps per item ("Wheat +6% this week"). Hot Item stays (+25%). Item lore and /sell show "Price today: 85%" with green / yellow / red. The old global model is retired. |
| Spawner payback | Mob drops keep full vanilla loot; their sell prices are set so one mob is worth about $300 / $600 / $1,000 / $1,800 (tiers 1-4, after scale). Spawner prices $10M / $25M / $50M / $100M. Payback about 100 h (tier 1) to 150 h (tier 4) **at full upgrades** (delay and prestige included). Never shown to players. |

## 2. Grind areas: /farm, /mine, /fish, /pve
Shared areas in the `grind` world. Same building blocks everywhere so players learn them once:
- **Tier ladder per player** (what you farm, mine, catch or fight). Next tier unlocks by count **and** an island Area Mastery tier for the higher tiers.
  - /farm (8): wheat 0 · carrot 1,500 · potato 6,000 · beetroot 15,000 · melon 35,000 · pumpkin 70,000 · sugar cane 140,000 · nether wart 250,000.
  - /mine (10): coal 0 · iron 400 · gold 1,500 · redstone 4,000 · lapis 8,000 · diamond 15,000 · emerald 25,000 · amethyst 45,000 · quartz 80,000 · ancient debris 150,000.
  - /fish (6 waters): Shallows 0 · River 500 · Lake 2,000 · Reef 6,000 · Deep 15,000 · Abyss 35,000 (catches).
  - /pve (10 tiers): §6.
  - Tiers 5+ need the matching Area Mastery at tier 2/4/6/8 (§4).
- **Storage** (grind inventory, owner Q-30): drops go straight into the area storage (farm, mine, fish, combat), never the inventory. Storage menu: contents, Sell all, size upgrade (shards). Full storage: actionbar warning, drops pause (no loss).
- **Events** (owner): 6 events, about 30 s each, 2-4 at once picked at random, then a 30-60 s break. Double XP, Double Drops, Double Shards, Instant Regrow (fish: Bite Frenzy), Proc Frenzy (x2 proc chance), Golden Blocks (x5 value). Boss bar per active event; scoreboard "Event in 0:42".
- **Scoreboard per area** (max 10 lines): tier and progress, storage %, events, next step.
- **NPC per area** with quests (§7) and a shop for the area (rods, bait, sigils).
- Money per hour at equal progress is about the same in all four areas; the area's real reward is its shard type and tool XP.

## 3. Tools v4 (four tools)
Base plan `TOOLS_V3.md`, extended to 4 tools:
| Tool | Area (personal grind set, 17 enchants) | Island set (7, shared by the island) | Shards |
|---|---|---|---|
| Pickaxe | /mine | generator ores | Mining |
| Hoe | /farm | island crops | Farming |
| Rod | /fish | island water | Fishing |
| Sword | /pve | spawner mobs on the island | Combat |
- **One item per tool per player.** The grind set is personal; the **island set's levels belong to the island** and every member's tool uses them on the island; any member can upgrade the island set with their own shards (owner 7 Nov).
- **Core upgrades** (always on): pickaxe Efficiency, Fortune, Shard Finder, Sell Bonus; hoe Haste, Fortune, Shard Finder, Sell Bonus; rod Lure, Luck, Shard Finder, Sell Bonus; sword Sharpness, Looting, Shard Finder, Sell Bonus.
- **Grind sets:** pickaxe and hoe as in `TOOLS_V3.md`. Rod: Net (catch 2-5 extra), Whirlpool (5-10 catches, 20 s cooldown), Tidal Wave (rare big haul, 60 s), Kraken (very rare, 5 min), Big Catch (+1 rarity), Golden Fish (5x value), Shard Burst, Shard Echo, Shard Jackpot, Compound Interest, Cash Catch, Scholar, Quick Study, Combo, Treasure Chest (a Collector-style find), Lucky Streak, Keyfinder. Sword: Cleave (hits mobs around), Chain Lightning, Meteor, Execute (instant kill below 20% HP, not bosses), Double Loot, Bloodlust (heal on kill), Boss Slayer (+boss damage), Shard Burst, Shard Echo, Shard Jackpot, Compound Interest, Cash Kill, Scholar, Quick Study, Combo, Lucky Streak, Keyfinder.
- **Levels:** tool level 1-100 (fast curve: level 2 in about 30 s, level 100 in about 3 h at prestige 0). **50 prestiges** (+5% shards and money for that tool each, a new reward every 10), then **infinite rebirths** (+10% each, a badge, about 10% more needed each time).
- **Enchant levels 1-1000.** The cap is set by the island's Area Mastery: tier × 100 (Mastery 10 → 1000). This is the island ↔ area synergy: to push the pickaxe past 300 the island needs Mine Mastery 4.
- Cost per enchant level `c × L^1.2` shards (c = 2; big procs 3-8; island set 1), bulk +1 / +10 / +100 / MAX. First upgrade inside a minute.
- Shard income targets: 60-120 a minute at the start, 1,500-4,000 mid game, 15,000-30,000 late.
- Caps kept: one area proc per break; area-broken blocks give drops only; money enchants together max +25%; shard enchants together max +65%; rare finds every 2,500-10,000 blocks at max; cooldowns on big procs.
- **Menu:** right-click the tool: opens on the set for where you are; tabs Grind / Island / Core / Prestige. Each enchant: icon, name, one line, now → next, cost, `+1 +10 +100 MAX` buttons. Locked: "Needs Mine Mastery 4 (island)".
- Old `tools` module (Forge, /forge, /tool, /blueprint, axes) is removed; Tool Skin Blueprint becomes a cosmetic skin item (Black Market).
- Migration: the island's tool levels become the island set; members' grind sets start at those levels; old perk levels become core levels; nothing is lost.

## 4. Island v2
| Topic | Plan |
|---|---|
| Upgrade menu | One menu, 3 tabs: Island, Production, Masteries. Each upgrade tile: name, one line, "Tier 3 / 10", now → next, the **requirement done on the island** + money from the island bank. Requirements in the lore first. |
| Island tab | Size (10 tiers), Members (6), Warps (6), Vault (5), Bank (10, capacity + interest merged), Hoppers (6). |
| Production tab | Generator (10), Crop Growth (10), Harvest Yield (10), Mine Fortune (10), Spawner Cap (10), Spawner Speed (10), Mob Loot (10), Sell Multiplier (12). Lumberjack, XP Boost and the Depths tab are removed; Fuel Tank stays hidden while production fuel is off. |
| Masteries tab (new) | **Farm, Mine, Fish and Combat Mastery**, 10 tiers each. Each tier raises that area's enchant cap by 100 and unlocks area tiers. Requirements mix the island and the other areas (synergy), e.g. Mine Mastery 3: "Mine 5,000 ores with your island generator" + "Kill 500 mobs in /pve"; Farm Mastery 4: "Harvest 20,000 island crops" + "Catch 300 fish". Money from the island bank. The old masteries (timber, swift hands, warrior, ...) are folded in or retired. |
| Requirements per tier | about 1.5x per tier; money about 2.2x per tier (research §2). Example Generator: mine 500 / 1,500 / 4,000 / 10,000 ... ores on the island; $1M → $2.2M → $4.8M ... |
| Island level | Points from upgrades and masteries bought, block value, collection milestones, achievements, island quests. Shown as one bar "Level 12 · 340 / 500 points". The Depths source is removed. |
| /is top | Island level board + weekly gain board; top 10 each season get rewards (F11); no leader kicks in the last 14 days (F11, built). Menu: top 10 heads, your place, "how to climb" line. |
| Island statistics menu | Crops, ores, fish, kills, money earned, per member, from the island menu. |
| Shared island tool sets | §3. |

## 5. Spawners v2 (owner plan, 5 Nov)
- Every spawner spawns every **10 s**; **Delay** upgrade (island, Spawner Speed) down to 5 s.
- **Spawner level per island per mob type** from kills of mobs from your own spawners on your own island: level n needs `B × n²` kills with B = 100 / 250 / 600 / 1500 (tier 1-4); level 2 of tier 1 = 400 kills (about 20 minutes with 4 spawners).
- **Placement limit per mob type** = 4 × level, max 64 (level 16). Premium tier 5 (Black Market) limit 3.
- **Prestige** at level 16: back to level 1, +5% drops for that mob type, up to 10.
- Spawn radius 3. Mobs do not move (stacked, as today). The old efficiency curve (100/60/35/20/10%) is removed.
- **Spawner menu** (one screen): level, progress bar to next level, placed / limit, delay, next prestige. Nothing else.
- Drops: §1 payback. Saturation applies to mob drops like everything else.
- Labels do not count against the island display limit (FX-03).

## 6. PvE v1: arena + bosses
- `/pve` arena in the grind world: **10 tiers** in rings around the centre (tier 1 outside, tier 10 in the middle). Tier n unlocks after `kills` in tier n-1 (200, 600, 1,500, 3,000, 6,000, 10,000, 16,000, 25,000, 40,000) and Combat Mastery for tiers 5+.
- Mobs: one type per tier (zombie, husk, skeleton, spider, stray, drowned, piglin brute, blaze, wither skeleton, ravager), custom HP and damage per tier, a fixed number per tier spawned around the ring, respawn 3 s, no wandering out of the ring, capped entities (no lag at 300 players).
- Rewards per kill: Combat XP (the Combat skill), Combat Shards, the mob's loot into combat storage, money like the other areas.
- **Personal boss per tier:** a Boss Sigil (from tier kills or the PvE NPC shop) summons that tier's boss in the ring; only the summoner sees and fights it (shared VisibilityService). Boss bar with HP, 3 minutes to kill, loot table with a rare drop, Lucky Streak pity. Bosses: Rotting King, Desert Tyrant, Bone Archer, Brood Mother, Frost Wraith, Tide Caller, Bastion Warlord, Infernal Core, Wither Knight, Ravager Alpha.
- No PvP. Death in /pve: back to the arena start, nothing lost.
- Sword tool (§3). Armour: vanilla armour from the shop; PvE armour later.
- Combat achievements and titles come back with PvE (owner Q-21).

## 7. Quest line v2 (no tutorial)
- `/quest` shows the current quest, its progress and the next step; the sidebar "Next:" line and the boss bar hint show the same step (NextStepService).
- Chains of NPC quests; NPCs hand you to each other; gate lines say what is missing ("Reach Pickaxe level 10, you have 7").
- **Spawn, Rowan (6):** talk to Rowan; claim your island (/is); visit /farm; harvest 20 wheat; sell (/sell); come back and get your first reward.
- **Island, Kindle (8):** place your first spawner; harvest 50 island crops; mine 50 generator ores; buy island size 2; deposit in the island bank; buy your first island upgrade; reach island level 2; check /is top.
- **/farm, Bramble (8):** harvest 100 wheat; upgrade a hoe enchant; unlock carrots; fill and sell the farm storage; reach hoe level 10; trigger an Explode; unlock potatoes; prestige the hoe once.
- **/mine, Flint (8):** mine 100 coal; upgrade a pickaxe enchant; unlock iron; sell the mine storage; reach pickaxe level 10; trigger a Blast; unlock gold; prestige the pickaxe once.
- **/fish, Marla (7):** catch 10 fish; upgrade the rod; reach the River; catch an Uncommon; sell the fish storage; reach rod level 10; catch a Rare.
- **/pve, Warden Hale (7):** kill 20 zombies; upgrade the sword; reach tier 2; summon and beat the tier 1 boss; reach sword level 10; reach tier 3; beat the tier 2 boss.
- First reward inside 60 seconds of joining. Each quest: money (minutes of your income), shards for that area, sometimes a key; the last quest of each NPC gives a crate key and a title.
- Everyone can do the line, old players too (no skipping, progress they already have completes those quests at once with their reward). The invite step is gone.
- Contracts stay as daily and weekly tasks (pools: /farm, /mine, /fish, /pve, island).
- The old tutorial module is reused for this (book, NPC dialog, steps).

## 8. Menus, chat, HUD, pack (UI v2)
- **GUIs:** no glass panes; tight layouts; bottom bar from the GUI-1 framework; lore template from §0.6; locked items show one dim padlock with one line.
- **Chat:** one prefix for system messages; framed blocks (rule, title, one line, `» action «`, rule) only for events, rare drops, store purchases, level-ups; anti-spam cooldown on repeated errors (2 s per message key).
- **HUD:** boss bar per active booster or event ("Double Drops · 0:24 (2/3)"); actionbar XP line after gains; short first-time hints in yellow (not red).
- **Titles:** big word + one subtitle, 1.5 s, coloured by meaning (rare gold, mythic pink, event ember), always repeated in chat.
- **Scoreboard:** max 10 lines, two blocks, label grey, value white, event countdown on top, no date line.
- **Pack v2 (P-19):** flat dark menu background with one accent colour, a title plate font, custom icons in the /collection style, a logo glyph for the scoreboard. Built after the gameplay batches; the GUI layouts are made pack-ready now.
- **/menu and /help:** every feature reachable (Friends, quest book, Pass, Milestones, Mailbox, Boosters, Crates, Vote, Fish, PvE); help topics rewritten for the new systems; dead topics removed.

## 9. Other features
| Feature | Plan |
|---|---|
| Mailbox | `/box` becomes `/mailbox` (old command works): one inbox for items, money, store purchases, pay-limit overflow and messages; Claim all. |
| Crates | One crate menu: preview with chances, open animation about 2 s (skippable), pity bar "Guaranteed rare in 7 opens", rewards scaled to your progress; nothing weird (no near-useless items). |
| Collection | Pages per area: Farm crops, Ores, Fish, Mobs and Bosses, Crates. Each entry: found / not found, amount; page milestones give island points and small perks. |
| Auction house | Categories, search by name, sort (price, time), "List similar", price hint, pay limits apply to buyers' payments to sellers. |
| Shop | Every useful block and item (Q9), Mob Drops tab, the 49 removed items back (Q-42); groups grid; one click buy, shift for 64. |
| Black Market | Crystals; Sell Wand, Auto-Sell Chest, Chunk Collector (own island only, FX-01/02), Wardrobe Tome, Tool Skin Blueprint, premium spawners. |
| Ranks v2 | From `q/specs-v3` (RANKS-V2): names and perks, plan stays as written there, built after the gameplay batches. |
| Daily streak, vote | Vote streak ladder with the next reward visible (day 5 ... 30). |
| Milestones | On (Q-44). |

## 10. Build order (batches, each released to the test server)
1. **Batch 1, fixes and cleanup:** FX-01 to FX-21 (`MASTER_PLAN.md` §2), C-01 Depths/dungeon leftovers, C-02 remove the old tools module, Bedrock symbol test, milestones on.
2. **Batch 2, economy:** money ×100 migration, pay limits by playtime, shards payable to island members only, dynamic prices v2, spawner drop prices (payback).
3. **Batch 3, tools v4 core:** personal tools with shared island sets, core upgrades, enchant framework (procs, caps, cooldowns, display), pickaxe and hoe grind sets, prestige and rebirth, events system, storage.
4. **Batch 4, island v2:** upgrade menu with on-island requirements, Area Masteries (enchant caps, area tier gates), island level and /is top, island statistics.
5. **Batch 5, spawners v2.**
6. **Batch 6, quest line v2 + menus/help coverage + mailbox.**
7. **Batch 7, /fish** (area, rod, catches, storage, NPC).
8. **Batch 8, /pve** (arena, sword, bosses).
9. **Batch 9, polish:** crates, collection, AH, shop, Black Market items, chat/HUD pieces.
10. **Batch 10:** pack v2, ranks v2.
Progress is tracked in `docs/STATUS.md` and the morning report `docs/MORNING_REPORT_7NOV.md`.

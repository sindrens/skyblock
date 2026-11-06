# Research results (research prompt, run 6 Nov 2026)

Three web research passes (Sonnet, read only) on `RESEARCH_PROMPT.md`, plus the owner's screenshots (`INSPIRATION_6NOV.md`). Public numbers are scarce: most big servers do not publish enchant chances or quest pacing. **Sourced** facts have a link; everything marked *proposal* is our design and needs playtesting.

## 1. Tool enchants, prestige, rebirth
Sourced:
- Chance enchants stay small: X-Prison procs 0.275-10% at max ([wiki](https://github.com/Drawethree/X-Prison/wiki/Enchants)), Dank Prison doublers 5% max, key finders 3% max at level 3000 ([EdPrison](https://edwardbelt.gitbook.io/edprison-core-documentation/features/enchantments)), Lemoncloud one crate per 10,000 blocks ([Lemoncloud](https://wiki.lemoncloud.org/gamemodes/prison/pickaxes-and-tokens)).
- New enchants unlock on milestones so there is always a next goal: Lemoncloud gates epic enchants by prestige (Drill P10, Laser P20, Tornado P30, Meteor P45, Nuclear P70); Pika unlocks by blocks mined (500, 2,000, 50,000 ...) ([Pika](https://pika-network.net/posts/1482638)).
- Bulk buy buttons (+1, +5, +25, +50, MAX) are standard where levels are high ([X-Prison](https://github.com/Drawethree/x-Prison)).
- "+0.01%" steps feel pointless; servers make the step itself a jump (Dank: 10 / 20 / 30 tokens, x100 at level 10) ([Dank](https://wiki.dankprison.com/wiki/Enchant_Guide)).
- Prestige ladders: Lemoncloud 100 prestiges at +10% sell each; Pika prestige, then ascension, then rebirth with a permanent multiplier; Pet Simulator 99 rebirths unlock a feature each time.
- Visible procs: Bee Swarm shows every buff as a coloured token, buffs last 15-45 s ([fandom](https://bee-swarm-simulator.fandom.com/wiki/Ability_Tokens)). Dank "Frenzy" gives everyone 2x money for 15 s.
*Proposal:* see `docs/plans/TOOLS_V3.md`.

## 2. Island upgrades with requirements
Sourced:
- EnchantedMC ([wiki](https://enchantedmc.net/wiki-skyblock/)): one island-level ladder; each level = one checkpoint done on the island + money. Farming 6 checkpoints (wheat 100k, then +1 crop each), Mining 13 (coal 25k ...), Slaying 6. Each level unlocks something concrete (spawner stack size, border, crops, ore blocks). Numbers grow 2-5x per step.
- SuperiorSkyblock2: upgrades with price + `required-checks` such as island level >= 10 ([wiki](https://wiki.bg-software.com/superiorskyblock/overview/upgrades)). BentoBox Upgrades: formulas like `500 * [level]`, panel "Level 2 / 5" ([docs](https://docs.bentobox.world/en/latest/addons/Upgrades/)).
- MineSuperior: money only, each step 2-3x the last ([gitbook](https://minesuperior-skyblock.gitbook.io/home/skyblock/island-upgrades)).
*Proposal* (5 tiers each, one requirement per tier done on the island, money from the island bank, about 1.5x requirement and 2.2x money per tier):
| Upgrade | Tiers | Requirement per tier | Money |
|---|---|---|---|
| Members | 2-6 slots | island level 2 / 4 / 6 / 8 / 10 | 5k, 12k, 30k, 70k, 150k |
| Size | +1 step each | harvest 500 / 1.5k / 4k / 10k / 25k crops | 10k ... 300k |
| Crop growth | 1.25x-2x | 1k wheat, 2k carrots, 3k potatoes, 4k beetroot, 6k mixed | 8k ... 250k |
| Generator | new ore per tier | mine 500 coal, 400 iron, then gold, redstone, diamond | 10k ... 300k |
| Spawner cap | 4-64 | spawner kills 200 / 1k / 5k / 20k / 60k | 15k ... 600k |
| Hopper cap | steps | island level 3 / 5 / 7 | 5k, 15k, 40k |
| Harvest yield / Mine fortune | +5-25% | farm or mine 2k / 6k / 15k / 40k / 100k | 20k ... 700k |
UI: "Harvest 1,000 / 3,000 wheat on your island", now → next, a progress bar. No split tracks. → P-04.

## 3. Spawners
Sourced: WildStacker minimum delay 200 ticks (10 s), spawn range 4, nearby-entity cap 6 ([wiki](https://wiki.bg-software.com/wildstacker/overview/spawners-stacker/spawner-upgrades)); leveled-spawner plugins show current vs next stats and price. No public server uses kill-to-level spawners (ours is new); EnchantedMC gates the stack cap by island level. No sourced price-per-mob tables.
Check of the owner's plan (calc): price = payback hours x 360 mobs/h x value: tier 1 ≈ $100k, tier 4 ≈ $1M, which matches today's shop prices. Kill levels: level n needs B·n² kills (level 10 = 10k / 25k / 60k / 150k).
**Risks to settle in P-01:** the delay upgrade and prestige (+50% at 10) shorten payback (100 h → ~67 h at max prestige), so payback is computed at max upgrades; kills count only for mobs from your own spawners on your own island; spawner drops must use the same saturation as other goods.

## 4. Dynamic prices
Sourced: EzShops (price x (1 - 1%) per unit sold, clamped 0.5-5.0) and EcoShop (log curve, about 0.5x-1.5x after ~600 transactions, optional 0.9 daily decay) are both server-wide ([EzShops](https://ez-plugins.github.io/EzShops/shops/pricing/dynamic-pricing.html), [EcoShop](https://hub.auxilor.io/wiki/ecoshop/dynamic-pricing)). Per-island saturation is new.
*Proposal:* per island and item, 3,000 items a day sell at 100%; beyond that -1% per 100 items down to 60%; half the excess recovers at 04:00. Weekly global drift each Monday: -10% to +10% in 2% steps, shown as "Wheat +6% this week". Tooltip: "Price today: 85% (you sold a lot of wheat)", green/yellow/red. → P-03.

## 5. Quests and onboarding
Sourced: Roblox guidance "get to the fun quickly", goals always visible ([Roblox](https://create.roblox.com/docs/production/game-design/onboarding.md)); top Roblox games give a visible reward within 60 s ([analysis](https://rowatcher.com/news/what-every-top-100-roblox-game-gets-right-a-design-analysis)); Hypixel gates content by skill level.
*Proposal:* the first quest pays out at spawn within 60 s; every quest names one action; the next step is always on the sidebar; gates say what is missing ("Reach hoe level 10, you have 7"). NPC per location (names to be chosen): spawn guide, island, /farm, /mine, later /fish and PvE, 5-8 quests each. → P-09.

## 6. Anti-boosting with playtime
Sourced: ActivityEconomy rolling earnings cap with AFK protection ([wiki](https://github.com/CorruptedStudio/ActivityEconomy/wiki/ActivityEconomy-Wiki)); ColorGens pay-limit ladder (owner's screenshots). No public trade-tax numbers.
*Proposal* (daily limit on money others can pay you: /pay, trades, sales to players): none → $2,000; 3 h → $10,000; 6 h → $50,000; 12 h → $250,000; 24 h → unlimited. Active playtime only (no AFK). Over the limit goes to the mailbox. Island members exempt; `/paytoggle`. → P-08.

## 7. Fishing
Sourced: Fisch rods $300 to 15M, few stats (luck, lure speed), passives on higher rods, ~3-4x price steps ([gamezebo](https://www.gamezebo.com/walkthroughs/fisch-rods/)); Fisch events (luck x6/x10, double catch) ([guide](https://www.droidgamers.com/guides/fisch-events-guide/)); fishing plugins use 6 rarity tiers, a fish bag, timed competitions with a boss bar and leaderboard ([EvenMoreFish](https://modrinth.com/plugin/evenmorefish)).
*Proposal:* one shared lake; 8 rods (luck + speed, passive from rod 5); 6 rarities; broadcast only the top two; fish bag (same as farm/mine storage); the same 6-event system; a competition every ~15 min. → P-18.

## 8. PvE
Sourced: Hypixel Slayer: pay coins, kill N mobs, a boss spawns for you (tiers cost 2k / 7.5k / 20k / 50k) ([fandom](https://hypixel-skyblock.fandom.com/wiki/Bosses)); level-gated combat areas; dungeons are the heaviest; public world bosses are cheap in entities.
*Proposal, simplest to biggest:* (1) one /pve arena with 3 tiers of static spawns, level-gated, Combat XP + a combat currency; (2) add personal boss quests (Slayer-lite, $500 / $2,500 / $10,000 / $40,000); (3) a public world boss on a timer. Dungeons not recommended. → P-17 (owner picks).

# ECON-TABLES: the concrete lists behind ECON-SPAWNERS, ECON-SHARDS and ECON-SELL-SHOP-AH

Written 1 Nov 2026 by the planner from `modules/shop/src/main/resources/shop.yml` (branch `q/gui-phase2`, deployed), `stacking.yml` defaults and the owner answers of 1 Nov. **All prices here are placeholders** (owner: "exact prices later"). They are round on purpose and sit behind the two master knobs (`sell.price-multiplier`, `buy.price-multiplier`, both 1.0). Every number lives in config, never in code.

Contents: A. spawner roster, tiers, custom drops · B. spawner economy check · C. shard block ladder and budget · D. shop lists (REMOVE and ADD) · E. sell roots and the derived-price rule table · F. auction slot table.

---

## A. Spawner roster (4 earned tiers x 5 + premium tier of 3) and the custom drop of every mob

Tier numbers stay 1 to 5, so existing `spawner_1`..`spawner_5` items and stored rows need **no remap** (only the mob lists change). Mobs that leave the roster become **legacy**: still placeable and working (owner EP-Q8: "spawners already owned keep working"), counted in their old tier, never sold, never in crates or the Black Market, vanilla loot as today.

| Tier | Name | Mobs (5 each; tier 5 has 3) | Sold for | Unlock per island |
|---|---|---|---|---|
| 1 | Farm | pig, cow, chicken, sheep, rabbit | money, /shop | none (open) |
| 2 | Cave | zombie, skeleton, spider, cave_spider, slime | money, /shop | kill 1,000 tier 1 mobs |
| 3 | Wild | creeper, witch, drowned, husk, stray | money, /shop | kill 5,000 tier 2 mobs |
| 4 | Nether | blaze, enderman, guardian, hoglin, zombified_piglin | money, /shop | kill 20,000 tier 3 mobs |
| 5 | Premium | iron_golem, wither_skeleton, evoker | shards (Black Market) and credits (credit shop) | none; per-account buy limits (section B) |
| legacy | | squid (old tier 2), magma_cube (3), glow_squid (4), piglin (4), shulker (5), ravager (5), breeze (5) | never | n/a |

Every kill of a mob of the previous tier on the island counts (any source: spawner, bred, natural), summed per island, shown on the shop tile as `Kill 1,000 tier 1 mobs 312/1,000`. Config can narrow a gate to named mobs (`spawners.unlock.tier-3.mobs: [zombie]` prints `Kill 5,000 Zombies 312/5,000`, the owner's example wording).

### Custom drop per mob (one item per mob, 1 per kill at 1.0x loot)
Item id = `mobdrop_<mob>`. Name in `messages/stacking.yml` `drops.<item id>`. **Base material = one inert vanilla item per tier** (placeholder look; the resource pack gives every drop its own icon later): tier 1 `clay_ball`, tier 2 `flint`, tier 3 `iron_nugget`, tier 4 `gold_nugget`, tier 5 `echo_shard`. Rarity footer: COMMON, UNCOMMON, UNCOMMON, RARE, EPIC. Sell price = same for the 5 mobs of a tier on purpose: equal income means equal payback means one round price per tier. Listed in `sell.yml` under `custom-items.spawner-drops` (category `spawner-drops`).

| Tier | Mob | Item id | Name (placeholder) | Sell price at x1.0 |
|---|---|---|---|---|
| 1 | pig | `mobdrop_pig` | Pig Bristle | $5 |
| 1 | cow | `mobdrop_cow` | Cow Tuft | $5 |
| 1 | chicken | `mobdrop_chicken` | Chicken Plume | $5 |
| 1 | sheep | `mobdrop_sheep` | Wool Lint | $5 |
| 1 | rabbit | `mobdrop_rabbit` | Rabbit Fluff | $5 |
| 2 | zombie | `mobdrop_zombie` | Rotten Sinew | $10 |
| 2 | skeleton | `mobdrop_skeleton` | Bone Splinter | $10 |
| 2 | spider | `mobdrop_spider` | Silk Gland | $10 |
| 2 | cave_spider | `mobdrop_cave_spider` | Venom Sac | $10 |
| 2 | slime | `mobdrop_slime` | Gel Glob | $10 |
| 3 | creeper | `mobdrop_creeper` | Blast Powder | $16 |
| 3 | witch | `mobdrop_witch` | Brew Residue | $16 |
| 3 | drowned | `mobdrop_drowned` | Barnacle Shell | $16 |
| 3 | husk | `mobdrop_husk` | Dust Rag | $16 |
| 3 | stray | `mobdrop_stray` | Frost Splinter | $16 |
| 4 | blaze | `mobdrop_blaze` | Ember Core | $30 |
| 4 | enderman | `mobdrop_enderman` | Void Mote | $30 |
| 4 | guardian | `mobdrop_guardian` | Guardian Scale | $30 |
| 4 | hoglin | `mobdrop_hoglin` | Hoglin Tusk | $30 |
| 4 | zombified_piglin | `mobdrop_zombified_piglin` | Gilded Scrap | $30 |
| 5 | iron_golem | `mobdrop_iron_golem` | Golem Plate | $80 |
| 5 | wither_skeleton | `mobdrop_wither_skeleton` | Cursed Rib | $80 |
| 5 | evoker | `mobdrop_evoker` | Spell Fang | $80 |

Rules: spawner mobs drop **only** the custom item (vanilla loot of legacy mobs stays); the SK10 rare drop `evoker:totem_of_undying:0.02` stays (shulker line removed with shulker). Looting-style kill bonuses and the Mob Loot upgrade multiply the amount (fraction rolled once per batch, expected value exact). Drops are never used in vanilla recipes (guard in ECON-SPAWNERS step 6). They are never in the Forge or any recipe in this release (only sold).

---

## B. Spawner economy check (all assumptions in one place; C14 reruns the model)
Default per spawner: 1 mob every 10 s = **360 mobs per production hour**. Income per hour = 360 x drop price x 0.85 (assumed average dynamic price; the shop tile uses this constant, `spawners.dynamic-factor`). "Production hour" = a player within 16 blocks or island fuel (unchanged).

| Tier | Price (money, round) | Drop | Income/h (1st spawner) | Payback 1st | Payback 17th (60%) |
|---|---|---|---|---|---|
| 1 | $100,000 | $5 | $1,530 | 65 h | 109 h |
| 2 | $250,000 | $10 | $3,060 | 82 h | 136 h |
| 3 | $500,000 | $16 | $4,896 | 102 h | 170 h |
| 4 | $1,000,000 | $30 | $9,180 | 109 h | 182 h |
| 5 | money-equivalent $3,000,000 (label only) | $80 | $24,480 | 122 h | 204 h |

All five paybacks fall in the owner's 60 to 122 h band (EP-Q9) and rise with the tier. Premium prices: **400 shards** (Black Market, `fixed` row, limit 1 per day per account) and **2,500 credits** (credit shop, only once the credit shop lane allows spawner entries; suggested season limit 4). Per-tier island cap 32 (16 at 100% + 16 at 60%), credit perk adds up to +64 (35% band).
- Whole collection at the cap, base: 25.6 spawners' worth per tier = **$1.10M/h**; with every spawner upgrade maxed (speed x2.0 + mobs x2.0 + loot x1.5 = **x6.0**) = $6.6M/h = **18%** of a late regular's $36.5M/h (sheet band 8% to 25%; alert above 25%). If the C14 rerun disagrees, lower the three step lists in `stacking.yml`, nothing else.
- Unlock pace: tier 2 after 1,000 tier 1 kills = 2.8 h of one spawner (42 min with 4); tier 3 after 5,000 = 3.5 h with 4 tier 2 spawners; tier 4 after 20,000 = 13.9 h with 4 (6.9 h with 8) tier 3 spawners.
- Price scaling: when the owner raises `sell.price-multiplier` the shop price knob `buy.price-multiplier` must move with it, otherwise payback shrinks (a unit test asserts all five paybacks stay in 60 to 122 h at equal knobs).

---

## C. Shard block ladder and shard budget (all numbers config `shardblock.yml`)
| Level | Respawn (s) | Shards/h per block, attended | Double chance | Shards/h with double | Respawn upgrade cost (crystals) | Double upgrade cost (shards) |
|---|---|---|---|---|---|---|
| 0 | 1,800 (30 min) | 2.00 | 0% | 2.00 | start | start |
| 1 | 1,500 | 2.40 | 5% | 2.52 | 100 | 10 |
| 2 | 1,200 | 3.00 | 10% | 3.30 | 300 | 25 |
| 3 | 900 | 4.00 | 15% | 4.60 | 900 | 50 |
| 4 | 600 | 6.00 | 20% | 7.20 | 2,700 | 100 |
| 5 | 420 (7 min) | 8.57 | 25% | 10.71 | 8,100 | 200 |
Totals per block: 12,100 crystals + 385 shards (a sink for both). The respawn level and the double level are independent (0 to 5 each).

Bounds (why shard inflow cannot run away): (1) **shards only on a break by a player** (nothing accumulates while away; an auto-clicker is the only automation and is capped below); (2) max **4 blocks per island** (`max-per-island`); (3) **daily cap 100 shards per profile** per Oslo day (`daily-cap-per-profile`, enforced in the same database transaction as the payout); (4) Black Market limits per account stay (premium spawner 1 per day).
| Player | Setup | Attended | Shards/day |
|---|---|---|---|
| Regular | 1 block, respawn 2, double 1 | 3 h | 9.5 |
| Dedicated | 2 blocks, respawn 4, double 3 (6.9/h each) | 4 h | 55 |
| Whale | 4 blocks, level 5 / 5 | 4 h | 171, **capped to 100** |
A premium spawner (400 shards) takes a regular about 42 days, a whale 4 days. Credit shop: shard block suggested **1,000 credits** (set in `creditshop.yml`), suggested limit 4 per account per season. Crates: rare reward (section C of ECON-SHARDS).

---

## D. Shop lists (C5-Q2 b: "keep today's 253, add the uncraftable blocks, remove what is not useful, so players craft what they need")
Rules (the generator and `ShopListRulesTest` use exactly these):
- **SH1 keep roots:** a block with no crafting-grid or stonecutter recipe (natural, mined, grown, smelted, dropped) stays or is added.
- **SH2 remove crafted-from-shop:** a block with a crafting-grid or stonecutter recipe whose every input is a shop item (or itself a removed craftable) leaves the shop. Smelting does not count as crafting (glass, stone, terracotta stay).
- **SH3 keep functional:** an item whose recipe needs an input the shop does not sell stays (hopper, anvil, enchanting table, pistons ...).
- **SH4 owner prices stay:** hopper $2,500, bone meal $30, flint and steel $40 (owner S4, S6, S7). Their numbers scale with `buy.price-multiplier` like everything else.
- Removed items move to `worth-only:` with their last price (island block worth stays) and stay **sellable** through derived prices.
- Added items use `price: auto` = `shop.auto-markup` (10) x the item's sell base, rounded up (stays above the x7.5 rule).

### D1. REMOVE (53 items; generated from `shop.yml`, 253 today)
| Tab | Removed items and why |
|---|---|
| essentials (-9, left 17) | crafting_table (4 planks), furnace (8 cobblestone), smoker (furnace + 4 logs), chest (8 planks), barrel (6 planks + 2 slabs), composter (7 slabs), grindstone (2 sticks + slab + 2 planks), ladder (7 sticks), baked_potato (smelts from the shop potato) |
| wood (-12, left 12) | all 12 planks (oak, spruce, birch, jungle, acacia, dark_oak, mangrove, cherry, pale_oak, crimson, warped, bamboo): 1 log = 4 planks |
| stone-earth (-11, left 17) | coarse_dirt, sandstone, stone_bricks, mossy_stone_bricks, mossy_cobblestone, andesite, polished_andesite, polished_diorite, polished_granite, polished_deepslate, deepslate_bricks |
| special (-7, left 21) | polished_blackstone, polished_blackstone_bricks, end_stone_bricks, quartz_bricks, quartz_pillar, packed_ice, blue_ice |
| colors (-1, left 52) | glass_pane (6 glass = 16 panes) |
| decoration (-5, left 23) | painting, armor_stand, white_banner, chiseled_bookshelf, moss_carpet |
| redstone (-8, left 20) | redstone_torch, repeater, dropper, lever, stone_button, oak_button, stone_pressure_plate, note_block |
| farming | nothing removed (27 kept) |

### D2. KEEP (189 items, unchanged price)
essentials: stonecutter, anvil, enchanting_table, bookshelf, brewing_stand, cauldron, bucket, water_bucket, lava_bucket, flint_and_steel, shears, fishing_rod, torch, scaffolding, white_bed, bread, blast_furnace. farming: all 27. wood: the 12 logs/stems/bamboo block. stone-earth: dirt, grass_block, podzol, mud, sand, red_sand, gravel, clay, cobblestone, stone, bricks, calcite, tuff, diorite, granite, deepslate, cobbled_deepslate. special: netherrack, soul_sand, soul_soil, basalt, blackstone, nether_bricks, red_nether_bricks, end_stone, purpur_block, quartz_block, smooth_quartz, prismarine, prismarine_bricks, dark_prismarine, amethyst_block, obsidian, crying_obsidian, ice, snow_block, copper_block, mycelium. colors: 16 concrete, 16 stained glass, 16 terracotta, glass, tinted_glass, terracotta, white_wool. decoration: soul_torch, lantern, soul_lantern, end_rod, candle, campfire, shroomlight, 3 froglights, flower_pot, decorated_pot, item_frame, glow_item_frame, iron_bars, moss_block, azalea, flowering_azalea, spore_blossom, dandelion, poppy, cornflower, sunflower. redstone: redstone, comparator, piston, sticky_piston, observer, hopper, dispenser, crafter, light_weighted_pressure_plate, heavy_weighted_pressure_plate, tripwire_hook, daylight_detector, target, redstone_lamp, rail, powered_rail, detector_rail, activator_rail, minecart, trapped_chest.

### D3. ADD (46 uncraftable blocks and plants; `price: auto`)
| Tab | Items (sell base in $, shop price = 10 x) |
|---|---|
| wood (+11) | oak_leaves, spruce_leaves, birch_leaves, jungle_leaves, acacia_leaves, dark_oak_leaves, mangrove_leaves, cherry_leaves, pale_oak_leaves, azalea_leaves, flowering_azalea_leaves (0.5 each, shop 5) |
| stone-earth (+3) | dripstone_block (1, shop 10), pointed_dripstone (0.5, shop 5), rooted_dirt (0.5, shop 5) |
| special (+4) | magma_block (1.5, shop 15), warped_wart_block (2, shop 20), crimson_nylium (2, shop 20), warped_nylium (2, shop 20) |
| farming (+13) | weeping_vines, twisting_vines, big_dripleaf, small_dripleaf, glow_lichen, mangrove_roots, muddy_mangrove_roots, sea_pickle (1 each, shop 10); crimson_fungus, warped_fungus (2, shop 20); crimson_roots, warped_roots, hanging_roots (0.5, shop 5) |
| decoration (+15) | short_grass, fern, dead_bush (0.5, shop 5); blue_orchid, allium, azure_bluet, red_tulip, orange_tulip, white_tulip, pink_tulip, oxeye_daisy, lily_of_the_valley (1, shop 10); lilac, rose_bush, peony (1.5, shop 15) |
Waiting for the price overhaul C15 (not added now, they break the x7.5 rule or sell chain): glowstone, sea_lantern, cobweb, pumpkin, melon, hay_block, slime_block, honey_block, sponge, coral blocks (ocean stays play-only).

### D4. After the change
wood 23, stone-earth 20, special 25, farming 40, colors 52, decoration 38, essentials 17, redstone 20, spawners 20 (4 tiers x 5) = **255** (253 today). Premium spawners are not in /shop. Group lists for the GUI lane (`groups:` in `shop.yml`): the lane owns them; this table only says which ids join which tab.

---

## E. Sell prices: roots and the derived-price rule table
**Structure of `sell.yml` after the change** (keys already in the file stay; `config-version: 2`):
```
price-multiplier: 1.0                 # master knob (typed setting economy.yml sell.price-multiplier; the file key is not used)
categories:  ores, crops, mob-loot    # as today
custom-items: island-ores, depths-*, spawner-drops (new)   # as today + new
roots:                                # NEW: uncraftable blocks and plants, id: base price (decimals allowed, min 0.01)
derive:                               # NEW (rules below); exclude / overrides lists
```
**Roots** (units = dollars at multiplier 1.0; cobblestone = 1.0 as today):
| Group | Base | Items |
|---|---|---|
| Bulk | 0.5 | dirt, grass_block, rooted_dirt, mud, sand, red_sand, gravel, netherrack, all leaves, short_grass, fern, dead_bush, roots, vines, glow_lichen, pointed_dripstone |
| Stone | 1.0 | cobblestone, stone (smelt, same as cobble), cobbled_deepslate, deepslate, tuff, calcite, diorite, granite, basalt, blackstone, dripstone_block, snow_block, ice, soul_sand, soul_soil, podzol, clay, mycelium, moss_block |
| Wood | 1.0 | every log, stem, bamboo_block |
| Nether/End | 1.5 to 2 | end_stone 1.5, magma_block 1.5, warped_wart_block 2, nylium 2, obsidian 6, crying_obsidian 8 |
| Flowers | 1.0 (tall 1.5) | every flower |
| Existing | as today | everything already in `sell.yml` keeps its price (crops, ores, mob loot, Depths) |
| Rare blocks | per item | amethyst_block, quartz_block, prismarine*, purpur, copper_block, nether_bricks: priced from their parts by the rules below |

**Derived rules** (`derive.factor.<class>` percent; price = cheapest recipe: sum of input prices / output count x factor; recipes with any unpriced input stay unsellable; depth cap 6):
| Class | Examples | Factor |
|---|---|---|
| storage (9 to 1 and back) | iron_block, gold_block, quartz_block from 4 | 100% |
| shaping (stairs, slabs, walls, fences, plates, buttons, doors, trapdoors, planks from log) | oak_planks, stone_brick_stairs | 90% |
| decorative (bricks, polished, chiseled, cut, smooth, pillars, mossy) | stone_bricks, polished_andesite | 90% |
| dyeing (wool, carpet, concrete powder, stained glass, terracotta + dye) | red_wool | 90% of parts, dye counted at its own price |
| processing (smelting, blasting, smoking, campfire) | glass, stone | 100% (never above the raw input, as today) |
| compacting and uncompacting | packed_ice, blue_ice, hay | 100% |
Never derived and never sellable: filled shulker boxes, bundles, tools and armor, enchanted or renamed vanilla items, Forge materials of the current band, blueprint parts, fragments, quest items, bone meal (owner price S7 would break the x7.5 rule: `derive.exclude`).
Test oracle: for **every** recipe of the committed export, `price(output) x count <= sum(price(inputs))` (no craft-sell loop at any multiplier combination of 0.4 to 1.5 on the roots).

---

## F. Auction house slot table by group (listing slots at once; `skyblock.auction.slots.<n>`)
**One source of truth: the ranks lane.** `RANKS-V2.md` decision D9 and its `server/permissions/ranks.txt` (section D2) already define the grants; this table is a copy for the economy specs and a test compares them. Base is `auction.yml listing.slots: 5`; the highest node a player holds wins; the free group id is `ember` (display name Ashborn, RANKS-V2 D11).
| Group (display name) | Slots | Node |
|---|---|---|
| `default` (New, before the tutorial ends) | 5 | none (base) |
| `ember` (Ashborn, free) | 6 | `skyblock.auction.slots.6` |
| `hatchling` | 7 | `.7` |
| `fledgling` and `torchbearer` (won) | 8 | `.8` |
| `firewing` | 9 | `.9` (new node) |
| `flamecrest` and `flameheart` (won) | 10 | `.10` |
| `sunfeather` | 12 | `.12` (new node) |
| `pyrelord` and `firecrown` (won) | 14 | `.14` (new node) |
| `immortal` | 16 | `.16` (new node) |
| staff groups | inherit their donor-free base (5 to 6) unless the ranks lane grants more | |
Difference to the lead's note "Ashborn 5": the free Ashborn group gets **6** in RANKS-V2 and New gets 5. Safe default: RANKS-V2's numbers (they are already specified there). To follow the note instead, change one line in `ranks.txt` (`ember` -> `.5` i.e. no grant). The personal Economy achievements add up to +2 slots on top (AC2); the cap `listing.max-slots` stays 54.

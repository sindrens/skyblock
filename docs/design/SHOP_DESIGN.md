# /shop v2: the right items, one layout, easy buying

**Status:** draft v1 for the owner (31 Oct 2026). Nothing is built yet.
**Sources:**
- `modules/shop` (ShopModule, ShopMenu, CategoryMenu, BuyAmountMenu, QuickBuy, ShopCatalog);
- `shop.yml` (live = repo);
- `planning/SHOP.md`, `planning/SELL_PRICES.md`, ECONOMY_BALANCE;
- owner answers: 8 (no confirm on quick-buys), 642 → F2 (fixed spawner price), F14 (undo plus a second click over 50% of your money, can be turned off).

**Sketches:** the "Phoenix GUI Sketches" page, screens 04 and 05.

---

## 1. Today
| Tab | Items | Pages |
|---|---|---|
| building-blocks | 40 | 2 |
| wood | 33 | 2 |
| stone-bricks | 33 | 2 |
| glass-colors | 84 | 3 |
| decoration | 60 | 3 |
| redstone | 28 | 1 |
| farming | 28 | 1 |
| utility | 26 | 1 |
| **Total** | **332** | **15** |

**Problems:**
- **Too many pages, and back is lost.** 15 pages in all. From page 2+ there is no way back to the categories, because PagedMenu puts Previous on 45 (`PagedMenu.java:81`). Every page turn also reopens the window, which makes the cursor jump.
- **Buying works differently on Java and Bedrock.**
  - Java: left-click buys 1 at once; a stack or an amount needs right/shift-click (`CategoryMenu.java:130-136`, `messages/shop.yml:81-83, 105-111`).
  - Bedrock: always gets the amount screen (`QuickBuy.java:25-37`).
- **The help text is wrong:** "Big buys ask you to confirm first" (help.yml ~1023) contradicts answer 8.
- **The amount screen is missing things:** it has no balance and no Sell button. Its Cancel button duplicates Back and Close.
- **Items that hurt the game or make no sense:**
  - `ender_chest` ($1,500) kills the donor `/ec` perk.
  - `hopper` ($120) clashes with DESIGN ("priced so many isn't worth it"). Hoppers are now 64 items every 8 ticks.
  - `bone_meal` ($6) is a near-free crop boost.
  - `redstone_block` costs more than 9 dust.
  - Villager job blocks (villagers aren't used, 693).
  - 16 dyes, coloured wool and coloured carpets (players craft them).
  - Stripped logs (an axe makes them).
  - Carpets appear in two tabs.
- **Missing:** flint and steel (answer 721), fishing rod, shears, simple food, and the spawners tab (F2).
- **Dead settings and texts:** `shop-settings.yml buy.amounts / confirm-above`, `messages/shop.yml confirm.*`.

---

## 2. The catalog: 9 tabs, about 253 items
Everything is **vanilla** (Minecraft's own textures) except the Spawners tab. Every tab fits one page (28 slots) except Colors (2 pages).

| # | Tab | Items |
|---|---|---|
| 1 | **Essentials (26)** | crafting table, furnace, blast furnace, smoker, chest, barrel, composter, stonecutter, grindstone, anvil, enchanting table, bookshelf, brewing stand, cauldron, bucket, water bucket, lava bucket, **flint and steel**, **shears**, **fishing rod**, torch, ladder, scaffolding, white bed, **bread**, **baked potato** |
| 2 | **Farming (27)** | wheat/beetroot/melon/pumpkin seeds, carrot, potato, sugar cane, cocoa beans, cactus, sweet berries; oak/spruce/birch/jungle/acacia/dark oak/cherry/pale oak saplings + mangrove propagule; bamboo, kelp, lily pad, brown/red mushroom, vine, glow berries, bone meal |
| 3 | **Wood (24)** | log + planks of oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry, pale oak, crimson (stem), warped (stem), bamboo (block) |
| 4 | **Stone & Earth (28)** | dirt, grass block, coarse dirt, podzol, mud, sand, red sand, gravel, clay, sandstone, cobblestone, stone, stone bricks, mossy stone bricks, mossy cobblestone, bricks, calcite, tuff, andesite, diorite, granite (+ polished ×3), deepslate, cobbled deepslate, polished deepslate, deepslate bricks |
| 5 | **Special Blocks (28)** | netherrack, soul sand, soul soil, basalt, blackstone, polished blackstone, polished blackstone bricks, nether bricks, red nether bricks, end stone, end stone bricks, purpur, quartz block, smooth quartz, quartz bricks, quartz pillar, prismarine, prismarine bricks, dark prismarine, amethyst block, obsidian, crying obsidian, ice, packed ice, blue ice, snow block, copper block, mycelium. The Nether and End are off, so this is the only source |
| 6 | **Colors (53, 2 pages)** | 16 colours × concrete, stained glass and terracotta, plus glass, tinted glass, glass pane, terracotta and white wool |
| 7 | **Decoration (28)** | soul torch, lantern, soul lantern, end rod, candle, campfire, shroomlight, 3 froglights, flower pot, decorated pot, painting, item frame, glow item frame, armor stand, white banner, iron bars, chiseled bookshelf, moss block, moss carpet, azalea, flowering azalea, spore blossom, dandelion, poppy, cornflower, sunflower |
| 8 | **Redstone (28)** | redstone, redstone torch, repeater, comparator, piston, sticky piston, observer, hopper, dropper, dispenser, crafter, lever, stone button, oak button, stone/light/heavy pressure plates, tripwire hook, daylight detector, target, redstone lamp, note block, rail, powered rail, detector rail, activator rail, minecart, trapped chest |
| 9 | **Spawners (11, custom)** | tier 1 at $117k: pig, cow, chicken, sheep, rabbit; tier 2 at $213k: zombie, skeleton, spider, cave spider, slime, squid. Fixed price (F2). The lore shows "Next works at X%" and the payback time |

**Never in the shop**, with the reason:
- Anything /sell buys or that crafts into it (buy > sell rule 712): ores, ingots, crops, mob loot, glowstone, sea lanterns, hay, slime blocks, storage blocks.
- Spawn eggs, TNT, beacons, elytra, totems (722).
- Spawner tiers 3–5.
- Ender pearls (a teleport exploit, and tier-4 spawner loot).
- Name tags (named mobs never stack).
- XP bottles, and diamond and netherite gear (they skip progression).
- Forge materials and all custom items.

---

## 3. One layout for every shop page
```
 0 Essentials | 1 Farming | 2 Wood | 3 Stone & Earth | 4 Special | 5 Colors | 6 Decoration | 7 Redstone | 8 Spawners   ← tabs
 9 | 10 ....................................... 16 | 17
18 | 19 ....................................... 25 | 26        28 item slots
27 | 28 ....................................... 34 | 35
36 | 37 ....................................... 43 | 44
45 Back | 46 Balance | 47 Undo | 48 ? | 49 Close | 50 Main menu | 51 Sell | 52 ‹ Prev | 53 Next ›
```
- **Tabs on every page.** One click switches, and there is no category screen. `/shop` opens your last tab (Essentials the first time). The current tab has a gold well, a glint and "✔ You are here".
- **In-place refresh:** tab and page changes don't reopen the window (no cursor jump). The title is `Shop » <Tab>`.
- **The buy page uses the same tabs and the same bottom bar,** so you never get lost. Back returns to the tab.
- **Sell (51)** opens the sell chest; its Back returns to the same tab.

---

## 4. Buying (one plain click, Java and Bedrock alike)
**Click an item** → the buy page:

| Slot | Button | What it does |
|---|---|---|
| 13 | The item | Price each, stack price, "You can afford: N" |
| 20 | **Buy 1** | Buys 1 |
| 22 | **Buy 16** | Buy 8 for items that stack to 16 |
| 24 | **Buy 64** | One stack |
| 30 | **Fill inventory** | As many as fit and you can afford |
| 32 | **Type an amount** | Chat prompt; `1.5k` works |

- **Items that don't stack** (buckets, beds, tools) show only Buy 1 and Type an amount.
- After a buy, the page stays open and the numbers refresh. The action bar says `✔ +64 Torch −$256 · $1,199,744 left`.
- **Java shortcut:** shift-click on a grid item buys a stack straight away. It's mentioned only in the ? guide, never in lore.

**Grid item lore:**
```
Oak Log
| Buy Price: $8 each
| Sell Price: $2 each          (only if /sell buys it)
| Stack of 64: $512
You can afford: 1,250

» CLICK to choose an amount
```

**Big buys (F14):**
- A buy over 50% of your money turns the button **orange** with a pling: "✘ That is 74% of your money · Balance after: $3,284 · » CLICK AGAIN to buy". It disarms after 5 s or on any other click.
- **Undo last buy (47):**
  - A gold plate whose count = seconds left (15 s).
  - Lore: item, price, time left, "» CLICK to get $512 back".
  - The refund is all-or-nothing, and only while the items are still in your inventory. A spawner can be undone until it is placed.
  - Idle: grey, "Nothing to undo".
- Both features can be turned off in `/settings` (default on).

---

## 5. Search and favourites
**Not at launch.** Every item is one tab click and one item click away, and searching on Bedrock means typing. This reverses answer 718, which was given when the shop had 332 items on 15 pages. Slot 52 is free on single-page tabs if it's wanted later.

---

## 6. Code changes (for the developer)
- **Catalog migration:** `ShopModule.addMissing` (`:222-267`) only adds items, so it needs a versioned migration that also removes and moves them. `ShopCatalog.java:27,143` forces categories into inner slots. Update `ShopCatalogTest`; it asserts the 8 old ids, more than 200 items and `redstone_block`.
- **Menus:** `ShopMenu` and `CategoryMenu` merge into one tabbed `ShopMenu`. `BuyAmountMenu` becomes the buy page, and `QuickBuy` is simplified (no platform split).
- **Paging:** fix `PagedMenu` in common so 45 stays Back and pages use 52/53. This fixes every paged menu, not only the shop.
- **Text:**
  - Rewrite `messages/shop.yml` with CLICK wording, and remove `confirm.*`.
  - Fix help.yml "Big buys ask you to confirm".
  - Update UI_STYLE §4.1, whose example still says `RIGHT-CLICK buy 64`.
- **Icons:** change the Sell icon to a gold nugget (`ShopModule.java:696` uses a gold ingot) until pack art exists.
- **Prices to change (owner):**
  - hopper → about $2,500;
  - bone meal → $30;
  - remove ender chest;
  - add flint and steel $40, fishing rod $150, shears $60, bread $15, baked potato $15.
- **Docs:** SHOP.md, ECONOMY_BALANCE §4.3/§14.2 and OPEN_QUESTIONS:303 still say "+10% per owned spawner". Change them to fixed price + diminishing output (F2).

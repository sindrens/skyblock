# v5 plan: owner feedback 3 Nov 2026

Source: the owner's message of 3 Nov 2026 00:14 and the new icon sheet. Everything below is planned before any code. The
work is done in **7 batches**; after each batch the owner gets one build command, so a usage limit never leaves a broken
branch. Spawn builds are out of scope (owner).

---

## 0. How I read the message

Below is how I read each point, with the defaults I take. **[Q]** marks the open questions in section 13; I build each
one with the default shown and you can change it later.

| # | You said | Plan |
|---|---|---|
| 1 | Extra drops only on relevant blocks, never on placed blocks | Each tool has a drop list (pickaxe: ores and stone; axe: logs; hoe: crops; sword: mobs). Blocks a player placed never give extras, XP, crystals or shards (`PlacedBlocks` already tracks them). The same rule applies to Depths tools. |
| 2 | No tools when spawned in; island tools on the island, Depths tools in the Depths | Island tools only exist in the island world and Depths tools only in the Depths; in every other world (spawn, hub, events) you carry neither. On a world change the tools of that world are put in the same hotbar slots, and the others are stored. No tools in the starter chest. |
| 3 | Every crop on the island follows the island growth rules | Wheat, carrots, potatoes, beetroot, nether wart, cocoa, sweet berries, melon and pumpkin stems, sugar cane, cactus and bamboo grow one stage every N seconds (island upgrade `crop-growth`). This applies to every crop on the island, not only those harvested with the hoe. |
| 4 | Tilled dirt needs no water | Tilling on an island makes moist farmland at once; it never dries out or turns back to dirt. |
| 5 | Show the growth instead of random full growth | Each stage step plays a small particle puff and a soft sound for players nearby. Looking at a crop shows a hologram "Wheat 3/7 · next in 6s". |
| 6 | Island tool upgrades need tasks + shards | Each upgrade level costs shards **and** a task for that tool (e.g. "mine 500 ores with the island pickaxe"). Tool tiers cost money and shards. |
| 7 | Shards come from grinding with island tools, plus an enchant for more | Island tools drop shards (chance per relevant block or kill). Each tool gets the upgrade **Shard Finder** (+% shard chance). |
| 8 | Island tools are SHARED | Tiers and upgrades belong to the **island**: when anyone upgrades, every member's copy updates at once, and new members get the island's levels. Hoe storage stays personal. |
| 9 | Plan exactly what each currency does | Section 1. |
| 10 | Pip gets stuck in blocks and moves weirdly (Depths and tutorial) | Rewrite Pip's movement: he flies with no collision, eases toward a point above the path, steps around solid blocks, never goes inside one, and turns smoothly. Fix the tutorial trail too (it draws on the ground, skipping hidden or underground points). |
| 11 | New players always start on their own island; the tutorial starts there | First join creates the island right away and teleports you there (no spawn screen). Kindle starts the first steps on the island. |
| 12 | Join an island any time; clickable invite that warns the island is removed | Invites get **[Accept] [Deny]** buttons in chat. Accept opens a confirm screen: "Your island <name> (level N) will be deleted. Join <island>?". |
| 13 | Depths mine tier 2 after 50 blocks; everything much faster | The Depths tier gates move from skill levels to **blocks broken in the tier below** (tier 2: 50 tier-1 blocks). Every later gate is about 4x lower than today. Stone tools come sooner (section 5). |
| 14 | Starter island needs more | A premade farm (a 7x7 plot with a water line, tilled and planted with wheat), a sapling and dirt patch, a composter, a crafting table, a chest (seeds, bread, bone meal; no tools) and Kindle's spot. All of it is in config (`islands.yml starter.extras`), placed when the island is created. |
| 15 | Shift + right-click in the air opens that tool's menu | Works with any island or Depths tool (never when looking at a block, so placing and farming still work). It opens the new clean tool menu (section 8.6). |
| 16 | More holograms, sounds, animations | Section 10. |
| 17 | No items in the satchel from Depths mining and log cutting | Bug: drops from the Depths mine and log zones must go to the satchel. I'll find and fix the cause and add a test. |
| 18 | Progression requirements much lower | Lower everywhere: Depths gates, skill curve for levels 1-10, task book tier 1-2, milestone tier 1-2, Kindle story steps 1-5. |
| 19 | Message revamp: simpler, no "Phoenix" title | Every message file is rewritten in plain, short sentences. The shared `<prefix>` changes from the Phoenix title to a short label per module ("Island", "Shop", "Depths"...). |
| 20 | New icons (the sheet) | I use the sheet; I pick one variant per button (section 9.1) unless you choose others. **[Q1]** |
| 21 | Remove custom textures for rods and tools; keep vanilla | Remove the tier textures of Depths tools, island tools and fishing rods (they look vanilla again). **Skins** get detailed custom textures. Crate keys, fuel cells, boosters and shard blocks keep their own pictures (they have no vanilla item). **[Q2]** |
| 22 | Skins detailed and "insane looking" | 32x32 HD pixel art per skin with shading and highlights, researched from popular RPG and skyblock skin styles. 3 themes per tool kind to start (section 9.3). |
| 23 | Check /menu and Kindle for what's missing | Section 8.1 and 8.8. |
| 24 | /shop has the wrong colours; vanilla, simple | `/shop` uses plain vanilla chest colours (no tinted panels); dark grey title text; no custom frame behind the items. |
| 25 | More shop items (cooked steak from cow grinders, etc.) | Every mob drop, cooked food and farm output a grinder can make can be sold (section 4). |
| 26 | Kindle skin: you choose | I'll pick a friendly skin and put it in `guide.yml skin`. |
| 27 | No island level caps | Already off (caps 0). They stay off, and the cap code is removed from the level menu text. |
| 28 | Contribution per player | Section 7. |
| 29 | Tutorial and early game faster; let me forge during the tutorial | The forge step unlocks Forge at once (no level gate during the tutorial); every tutorial step is shorter (section 5). |
| 30 | Remake shard blocks: better, upgraded with something else, faster | Section 6. |
| 31 | Better masteries and upgrades icons in Kindle | New icons `feature/masteries` and `feature/upgrades` (section 9). |
| 32 | Better island/Depths tool look; the hoe is boring | With vanilla textures (point 21) the look comes from the item **name, lore and glow per tier**, plus skins. The hoe gets a proper tool menu with its storage, regrow timer and Auto-Sell. |
| 33 | Auto-sell possible | Island tool upgrade **Auto-Sell** (pickaxe, axe, hoe): drops are sold at once at 90% of the price (a toggle in the tool menu). Shard cost, last level of each tool. |
| 34 | Items in the shop can't be sold (bones, wheat, ...) | Every shop item that can be farmed, mined or dropped gets a sell price (section 4). Buying always costs more than selling (no buy-sell loop). |
| 35 | Bookshelf, anvil, enchanting table show no picture in /shop | Those items are bases of custom icons, so the pack draws the icon or nothing. Fix: icon bases never use real shop items (move those icons to unused bases) and a test fails if a shop item is an icon base. |
| 36 | No zigzag in /shop; more space | One clean grid: 7 per row, full rows, centred, one empty row between categories; no staggered layout. |
| 37 | /travel: one more row | 5 → 6 rows with a free row around the places. |
| 38 | /sell looks weird | Rebuild (section 8.4). |
| 39 | Skills: sword, pickaxe, axe (not a log) | The skill icons use tools: combat sword, mining pickaxe, foraging axe, farming hoe, fishing rod. New lores. |
| 40 | /milestones easier; show locked ones | Every chain shows from the start (locked ones grey with "Start: mine any coal ore"). Island chains give island perks, personal chains give player perks; more perks (section 7.3). |
| 41 | GUIs for /is, /eco, /warp, /kit, /afkpool | Each opens a menu without arguments (section 8). |
| 42 | /help shows /appeal; too much text | Players never see staff or appeal commands in /help (appeal stays reachable from the ban screen). One short line per item, symmetrical layouts, missing gear icon fixed. |
| 43 | Chat shows "»»" | One "»". |
| 44 | Tab: extra name row, double ping, "/menu ... /help" lines | Remove the second name row and the extra ping; the footer only says "/help". |
| 45 | Scoreboard: more space, clean title (no gradient) | Empty lines between groups (wallet, island, Depths); the title is plain bold gold "PHOENIX SKYBLOCK" with no gradient or stars. **[Q3]** |
| 46 | "Steps" in the tutorial has no texture | Fixed with the new icon set (icon key `tutorial/steps`). |
| 47 | /shop <search> | `/shop string`, `/shop stone`, `/shop spaw`: fuzzy search over names and aliases, best match first. One exact match opens that item's buy screen; several open a results page. |
| 48 | Shard block texture looks weird | New shard block pictures, one per tier (section 9). |
| 49 | /bank: clean vanilla+ GUI | A 3-row menu: a **chest** "Personal Bank" on the left and an **ender chest** "Island Bank" on the right. Each opens a clean 3-row screen: balance, deposit, withdraw, interest timer. |
| 50 | Remove /tutorial; Kindle sends you places | `/tutorial` is removed (an alias to `/is guide` stays so old habits still work). The tutorial steps run by themselves and Kindle gives them. |

---

## 1. What each currency is for (the core)

The rule: **each currency comes from one kind of play and is spent on that same kind of play.** Money is the exception
and goes everywhere. That way an island needs a miner, a farmer, a fighter and a diver, and members grind different
things.

| Currency | Earned by | Spent on | Tradeable | Owner |
|---|---|---|---|---|
| **Money** `$` | Selling anything (shop sell, /sell, auto-sell, AH), quests, bank interest | Shop items, island upgrades (size, generators, members), tool **tiers** (with shards), shard block upgrades (with items), AH, Kindle trades | Yes (`/pay`, notes, trade, AH) | Profile |
| **Shards** ◇ | **Island tools**: chance per relevant block or kill, more with Shard Finder; shard blocks (a steady small amount) | **Island tool** upgrades and tiers, Black Market | Yes (`/pay`, trade) | Profile |
| **Crystals** ◆ | **Depths**: Depths tool drops, Depths tasks, dungeon mobs | **Depths tool** upgrades and enchants (`/enchanter`), Forge extras, satchel upgrades | No | Profile |
| **Credits** | Store (Tebex), earned credits from votes and events | `/store` only (cosmetics, ranks, keys) | Bought: yes; earned: no | Account |

Changes this implies:
- **Shard blocks** are no longer the main shard source but a small bonus (section 6). Shards mainly come from grinding
  with island tools.
- **Shard blocks are upgraded with money and items** (not shards), so the shard economy stays "grind with island tools".
- Island tool upgrades cost **shards + a tool task**; tiers cost **money + shards**. This makes sure the miner upgrades the
  pickaxe and the farmer upgrades the hoe: tasks only count your own use of that tool kind.
- Crystals stay in the Depths. Nothing on the island costs crystals any more (island tool upgrades did; that moves to
  shards; existing crystal spends are refunded as shards at 1:1 with a one-time migration).
- The island bank holds money only (as now).

Earning rates (draft, all in config, tuned so one hour of grinding with one tool ≈ one upgrade level early on):
| Source | Base | With Shard Finder V |
|---|---|---|
| Island pickaxe, ore or stone (natural) | 2% for 1 shard | 4.5% |
| Island axe, log (natural) | 2% | 4.5% |
| Island hoe, ripe crop | 1% (crops are fast) | 2.5% |
| Island sword, mob kill | 4% | 9% |
| Shard block break | 1 per break (regrows) | — |

---

## 2. Island tools v2

### 2.1 Shared by the island
- New tables `island_tool_levels (island_id, kind, tier, upgrades JSON)`. The old per-profile rows migrate: each island
  takes the **highest** level any member had per upgrade (nobody loses anything).
- Every member's item is rendered from the island's levels; an upgrade by one member updates every online member's item
  at once (same slot, no new copy) and plays a sound with a line: "Alex upgraded the island pickaxe: Fortune III".
- Leaving or being kicked: you get your own island's tools (a new island starts at wood). Joining: you get the new
  island's levels.
- Who can upgrade: the role permission `Upgrade island tools` (leader and admin by default, members on).
- Hoe storage stays **per player**.

### 2.2 Costs
- **Tier** (wood → stone → iron → gold → diamond → netherite): money + shards, and the island must reach an island level
  (tier gates 0 / 3 / 10 / 20 / 35 / 50). Draft: stone $500 + 20 shards (the first one comes fast, section 5), iron
  $5k + 150, gold $25k + 500, diamond $100k + 1,500, netherite $400k + 4,000.
- **Upgrade level**: shards + a task on that tool. The task counts **the island's** use of that tool kind since the last
  level (any member), so a team can share it, but it only counts island tools of that kind.
  Draft per level 1→5: shards 15 / 40 / 100 / 250 / 600; task 100 / 400 / 1,200 / 3,000 / 8,000 relevant actions.
- Everything is in `islandtools.yml` (`tiers.<tier>.money/shards/island-level`, `upgrades.<id>.levels.<n>.shards/task`).

### 2.3 Upgrades per tool (all levels capped as today; new ones in bold)
- Pickaxe: Efficiency, Fortune, Haste, Magnet, Smelter, Extra Drops, **Shard Finder**, **Auto-Sell**.
- Axe: Efficiency, Timber, Extra Drops, Magnet, Sharpness, **Shard Finder**, **Auto-Sell**.
- Hoe: Row, Storage, Replant (always on), Extra Drops, **Growth Touch** (each harvest gives nearby crops one stage),
  **Shard Finder**, **Auto-Sell**.
- Sword: Sharpness, Looting, Sweeping, Fire Aspect, Lifesteal, **Shard Finder**.

### 2.4 Extra drops only where they make sense
- One list per tool kind in `islandtools.yml relevant.<kind>` (pickaxe: ores, stone, cobblestone from generators,
  deepslate, netherrack...; axe: logs and wood; hoe: ripe crops; sword: mobs).
- Extra drops, Fortune, Smelter, shard drops, crystals and XP bonus only on those blocks, and only when natural: blocks a
  player placed never count (`PlacedBlocks`; generator output counts as natural). Same rule for Depths tools.
- Tests: placed ore gives no extra; dirt with the pickaxe gives no extra; ripe vs unripe crop.

### 2.5 Tools follow the world
- In the island world: the 4 island tools, given automatically in your remembered slots (hotbar 1-4 the first time).
- In the Depths: your Depths tools in the same slots, and island tools are stored.
- Anywhere else (spawn, hub, events, other servers' worlds): neither; both are stored and come back when you return.
- Stored tools live in the database (as island tools do now), so a crash never loses them; the swap is atomic per world
  change and guarded against inventory-full and death mid-swap.
- The starter chest no longer contains tools; the starter kit drops its tool lines.

### 2.6 Shift + right-click in the air
- Any island or Depths tool, sneaking, right-click **air** → that tool's menu (section 8.6). Clicking a block keeps the
  normal action. Bedrock: sneak + tap air works the same; the menu is also in `/is tools` and `/depths tools`.

---

## 3. Island farming v2

- **Auto farmland:** tilling dirt/grass on an island gives moist farmland at once; island farmland never dries or turns to
  dirt (cancel the fade/moisture events in the island world only).
- **Our own growth clock for every crop on islands:** vanilla random-tick growth of crops is cancelled in the island world
  (only crops; grass, leaves and the rest stay vanilla). A per-chunk crop index (loaded chunks only) steps every crop one
  stage every `crop-growth` seconds (island upgrade: 60 s → 15 s, milestone perks down to 10 s). The work is spread over
  ticks in small batches (budget 0.5 ms per tick, profiled) and never loads chunks.
- Crops: wheat, carrots, potatoes, beetroot, nether wart, cocoa, sweet berries, melon and pumpkin (stem stages, then the
  fruit), sugar cane, cactus, bamboo, kelp (stage = one block taller, max height as vanilla).
- **Animation:** every stage step shows a small `HAPPY_VILLAGER` puff (players within 16 blocks, max 40 per tick per
  player) and plays a soft, rare sound (crop break pitch 1.6, max once per 2 s per player). A full-grown crop gets a
  gentle sparkle every few seconds so ripe rows stand out.
- **Crop hologram:** looking at a crop within 5 blocks shows a small text display above it: "Wheat 3/7 · next stage 6s"
  (one per player, follows the look, 2 updates per second).
- Hoe rows, storage and Growth Burst stay as built and now use the same growth clock.
- **Starter farm** (section 5).

---

## 4. Shop and selling v2

### 4.1 Every item has a fair price
- A new price table `shop-prices.yml` covers every farmable, mineable, droppable and cookable item: crops and seeds,
  every ore and ingot, blocks from generators, every mob drop (bones, rotten flesh, string, spider eye, gunpowder, ender
  pearl, blaze rod, ghast tear, slime ball, magma cream, feathers, leather, wool, ink sac, prismarine, raw and **cooked**
  meats: steak, porkchop, chicken, mutton, rabbit, cod, salmon), honey, logs and planks, flowers.
- Rule: `sell = base`, `buy = base × 2.5` (never below sell × 2), so nothing can be bought to sell. Cooked meat is worth
  raw × 1.5 (it costs fuel or a lava grinder). Spawner-farm items are priced for volume (a cow spawner on lava makes
  steak + leather).
- Prices come from one balance sheet (`docs/ECONOMY_BALANCE.md` section 4 is updated) based on how fast each item is
  made per hour at tier 1 and tier 5.
- Unsellable stays: tools, enchant books, notes, set pieces, keys, cosmetic items.

### 4.2 /shop search
- `/shop <text>` (with tab completion of item names): fuzzy search over item names, materials and aliases
  (`spaw` → spawners, `stone` → stone first, then stone bricks, cobblestone...). Ranking: exact name, starts-with,
  word starts-with, contains, typo distance 1. One clear match opens its buy screen; several open a results page sorted
  best first. Nothing found: "No item called 'xyz'. Try /shop and browse."

### 4.3 /shop look
- Vanilla: plain chest colours, no custom frame or tinted panels, title in the normal dark grey.
- Categories along the top row (centred). Items in a clean 7-wide grid, full rows first, centred last row; no zigzag.
  An empty row between the grid and the bottom row for breathing room.
- Item lore: 2 lines only: "Buy: $X · Sell: $Y" and "Left-click buy · Right-click sell". Buy screen: 1, 16, 32, 64 and
  "fill inventory".

### 4.4 Auto-sell
- Island tool upgrade Auto-Sell (2.3). Also the existing auto-sell chest (when it goes live, separate item).

---

## 5. New player flow, tutorial and early pace

### 5.1 First join
1. First join → create the island (solo) **before** they finish loading; teleport straight there. Spawn is skipped.
2. Kindle stands next to the spawn point and greets them with a title + sound, then gives step 1.
3. `/tutorial` is removed. The tutorial engine runs the same steps automatically; Kindle is the voice (chat lines from
   Kindle, his status line shows the step, the HUD objective stays).
4. Steps (shorter): break the tree with the island axe → till and plant (the starter farm) → harvest with the hoe → sell
   in `/sell` → buy the **stone tier** (cheap, first-time price $100 + 5 shards that the tutorial gives) → visit the
   Depths with Pip (Pip leads the way off the island) → mine 20 blocks (Depths tier 2 unlocks at 50) → forge one item
   (Forge unlocked for this step) → back home: Kindle's first story quest. About 10 minutes in total.
5. Skippable: "Skip tutorial" in Kindle's menu (gives the same rewards as finishing).

### 5.2 Joining another island
- Invites show **[Accept] [Deny]** buttons (hover text explains). Accept → confirm screen when you own an island:
  "Your island <name> (level N, M blocks) will be deleted." Joining when you have no island or only the untouched
  starter island skips the warning.
- Joining keeps your personal things (money, shards, crystals, skills, personal bank, personal milestones); the island
  things (bank, tools levels, island milestones) are the new island's.

### 5.3 Faster early game (drafts, all in config)
- Depths tier gates: **blocks broken in the tier below**: T2 50, T3 250, T4 800, T5 2,000, then ×2 each (was skill levels).
- Skill curve: levels 1-10 need about a quarter of today's XP.
- Task book tier 1 and 2: targets ÷ 4. Milestone tier 1-2: targets ÷ 3. Kindle story 1-5: smaller amounts (16, 32, ...).
- Stone tools: the stone tier of the island tools is cheap the first time (5.1), and the Depths Quartermaster sells stone
  Depths tools for crystals from the first trip.

### 5.4 Pip
- Movement rewrite: Pip is a display entity moved by us (no physics). Each tick he eases toward a point 1.5 blocks above
  the next route point ahead of the player; when that point is inside a block he rises until free (max 3 blocks) and
  never enters solid blocks; turning eases too. He waits when the player stops and comes back when they are 8+ blocks
  away. In the Depths he follows the same rules.
- Trail: drawn only on the visible floor (skips points inside blocks and under the floor), max 24 points ahead, fades
  behind the player.
- Tests: the pure path step (never inside a solid block, never more than 1.6 blocks per tick).

---

## 6. Shard blocks v2

- A shard block gives **1 shard per break** and regrows (as now), a small steady bonus.
- **Upgrades use money + items** that the island makes (draft: tier 2 $2k + 64 cobblestone, tier 3 $10k + 32 iron
  ingots, tier 4 $40k + 16 gold blocks, tier 5 $150k + 8 diamond blocks). Each tier regrows faster (60 → 45 → 30 → 20 →
  12 s) and has a chance of 2 shards.
- New pictures per tier (section 9) and a regrow animation: the block shows cracks (vanilla crack overlay) that close up
  over the regrow time, then a chime and a sparkle when it is back.
- Limit per island stays 4 (configurable).

---

## 7. Island level, contribution, milestones and perks

### 7.1 No caps
- Level caps stay 0; the "cap" lines are removed from the level menu.

### 7.2 Contribution per member
- Every island level point is credited to the member who earned it (block placed, task done, milestone tier, upgrade
  paid; money paid into upgrades counts per $1k). New table `island_contribution (island, profile, points, money, season)`.
- `/is level` → "Members" tab: each member's share in % with what they did ("Alex 42% · 1,200 blocks, 3 milestones").
- Leaving: your share stays on the island (history), and the island level doesn't drop.

### 7.3 Milestones v2
- Every chain shows from the start. Locked ones are grey with a "Start:" line; started ones show progress.
- Simple layout: tabs Island / Personal at the top, a 7-wide grid, one tile per chain: icon, tier "3/10", a bar, the next
  perk. A click on a ready tile claims it (as now).
- Perks:
  - **Island chains** → island perks: crop growth −1 s, generator speed +2%, island level points +1, shard block regrow −1 s,
    hoe storage +64, island tool task −2%.
  - **Personal chains** → player perks: skill XP +2%, satchel slots +1, crystal chance +1%, sell price +1% for that item,
    Depths tool durability, Kindle errand rewards +5%.
- More chains: every crop, every ore, every log, every common mob (island); Depths ores, Depths mobs, dungeon floors,
  fishing (personal). The first tiers come fast (5.3).

---

## 8. GUIs: clean, symmetrical, vanilla+

Rules for every menu (added to `UI_STYLE.md`):
- **Symmetrical:** buttons centred on the middle column; rows full or centred (1, 3, 5 or 7 per row).
- **Small:** the fewest rows that fit, plus one free row when it is crowded (travel, shop).
- **Short text:** name + at most 3 lore lines + one action line. No paragraphs.
- **Vanilla+ look:** vanilla chest look by default; custom frame only on the hub menus (`/menu`, `/is`, Kindle).
- Every command that does things gets a menu when typed without arguments.

### 8.1 /menu
Clean 5-row hub: Island, Depths, Shop, Sell, Bank, Skills, Milestones, Achievements, Travel, Kits, AFK pool, Settings,
Help. Missing today: kits, AFK pool, settings, milestones.

### 8.2 /is (no arguments)
The island menu: home, members, upgrades, tools, level, bank, warps, settings, Kindle. (Check every button works.)

### 8.3 /eco, /warp, /kit, /afkpool
- `/eco` (staff): a menu to look up a player and see/adjust each currency (with reason prompt), audit logged.
- `/warp`: a menu of warps (Travel places + crates) with icons; `/warp <name>` stays.
- `/kit`: a menu of kits with cooldown timers; click claims.
- `/afkpool`: a menu with the pool's rewards, your time today and a Join button.

### 8.4 /sell
3-row sell box redesign: the top two rows take items, the bottom row has "Sell all" (green, with the total), "Sell
hand", and "Back". Live total in the title. Clear "Can't sell" marks on items without a price.

### 8.5 /bank
- Main screen (3 rows): **Personal Bank** = chest (left, slot 11), **Island Bank** = ender chest (right, slot 15), your
  balances in the middle (slot 13).
- Each bank (3 rows): balance + interest timer (middle), Deposit (left: 10%, 50%, all, custom) and Withdraw (right: the
  same), history button. No more than 7 buttons.

### 8.6 Tool menu (shift-right-click, /is tools, /depths tools)
- 4 rows: the tool in the middle top; its upgrades as one centred row (each: level dots ●●●○○, next cost, task progress
  bar); the tier button; hoe only: Storage and Auto-Sell toggle; skins button. Same design for island and Depths tools.

### 8.7 /travel
6 rows (was 5) with the places centred and a free row; locked places show "Unlocks at island level N".

### 8.8 Kindle
Check every tile (Next step, Story, Errands, Finds, Challenges, Quests, Milestones, Upgrades, Masteries, Island level,
Depths, Help, Guide book) and remove dead ones. New Upgrades and Masteries icons. Layout 5 rows, symmetrical.

### 8.9 /help
- Players never see staff, admin or appeal commands.
- Main help: one tile per topic (Island, Tools, Farming, Depths, Gear, Economy, Shop, Milestones, Social, Settings), one
  line each; the gear tile gets its icon.
- A topic: max 7 tiles in one centred row or two, each "/command · what it does" in 2 lines. Click runs the command
  (or opens its menu).

### 8.10 Skills
Icons: combat iron sword, mining iron pickaxe, foraging iron axe, farming iron hoe, fishing fishing rod. Lore: level,
bar, "+X% <perk> at level N".

---

## 9. Resource pack

### 9.1 New icons from your sheet
Default picks (one style per row so buttons look like a set):
- Back **C**, Previous **C**, Next **C** (the blue tiles).
- Close **B** (red circle), Danger, Help, Search, Info, Type.
- Confirm **C** and Cancel **C** (the square tiles); On/Off toggles; Locked; Undo.
- Buy **A** (price bag), Buy more **B** (+), Sell **A** ($ coin up), Sell all **A** (bag up), Fill.
- Money, Shards, Crystals, Credits, Bank (piggy), Top (trophy), Depths (lit pickaxe), Auction (gavel).
The sheet is cut into 64x64 PNGs (clean edges, transparent background). If you have the original files, sending them
gives sharper icons. **[Q1]**

### 9.2 Removed
Tier textures of Depths tools, island tools and fishing rods (they show vanilla). The vanilla-fallback bases for tools
are restored. Pack size drops.

### 9.3 Skins (custom and detailed)
- Research first (Hypixel SkyBlock dungeon item looks, Wynncraft weapons, popular "detailed tools" packs) for shape and
  shading ideas; all art is our own, drawn in code at **32x32** with 3-tone shading, rim light and an emissive glow
  layer (glowing parts animated in the inventory with an `.mcmeta`).
- Themes per tool kind (pickaxe, axe, hoe, sword, rod): **Ember** (molten edges, flickering glow), **Frostbite** (ice
  crystals, pale blue light), **Void** (dark purple with stars). More themes later from the Eclipse and Black Market.
- The art comes from code, so I will send you previews for approval before anything ships.

### 9.4 Other new art
Shard block per tier (5), tutorial "steps", Upgrades, Masteries, Kits, AFK pool, Settings, Warps. Shop items are never
used as icon bases (test).

---

## 10. Holograms, sounds and animations
- Crop growth puffs, ripe sparkle, crop hologram (section 3).
- Shard block cracks closing + chime (section 6).
- Tool upgrade: totem-like particle burst around the player + anvil-and-chime sound; island-wide line.
- Island level up: firework above the island centre, title for online members.
- Kindle: idle head turns toward the nearest member, a wave animation when you open his menu, "!" above his head when
  something is ready.
- Depths tier unlock: the existing wave + title, now at 50 blocks.
- Sell: coin sound scaled by the amount; big sales (> $10k) a short chime.
- All in `sounds.yml` / module configs; every effect can be turned off per player in Settings ("Effects: all / few / off").

---

## 11. Text revamp
- Every `messages/*.yml` file rewritten: short plain sentences, active voice, one idea per line, the fix in every error
  ("You need 20 shards. Get shards by mining with your island pickaxe.").
- `<prefix>`: one short coloured label per module ("<gold>Island</gold> ›") instead of the Phoenix title. Retired texts
  are migrated with `Messages.retire`.
- Chat: one `»` (`<rank><name><tag> <gray>»</gray> <white><message>`), with a migration of the old default.
- Tab: header = server name; one row per player (rank, name, tag; no extra name row, one ping); footer = "Need help?
  /help".
- Scoreboard: plain title (no gradient), groups separated by empty lines:
  ```
  PHOENIX SKYBLOCK
  
  Money  $12,340
  Shards 85   Crystals 1,200
  
  Island  Sunny Shores
  Level 12  ·  Members 3
  
  Depths  Tier 2
  
  play.skybornmc.net
  ```
- All lores and titles of menus shortened to the template in UI_STYLE.

---

## 12. Batches (each ends with a green build for the owner)

| Batch | Contents | Why first |
|---|---|---|
| **1. Feel** | Text revamp core (prefix, chat », tab, scoreboard), new icon sheet in, tool/rod textures out, shop vanilla look + grid + spacing, travel 6 rows, skills icons, help compact + gear icon + no appeal, shop icon-base fix | Quick, visible, low risk |
| **2. Start** | Own island on first join, starter island extras + farm, no tools in chest, tutorial → Kindle (remove /tutorial), faster steps, forge in tutorial, Pip + trail fix, clickable invites + confirm | The first 10 minutes decide if players stay |
| **3. Tools** | Tools follow the world (island/Depths/none), shared island tools + migration, shards from tools + Shard Finder, shard costs + tasks, relevant-only extras + no placed blocks (also Depths), shift-right-click menu + new tool menu, Auto-Sell | Core loop |
| **4. Farming** | Auto farmland, growth clock for every crop, animations, crop hologram | Depends on batch 3's hoe |
| **5. Economy** | Currency rules (section 1), price table for every item, cooked meats, /shop search, shard blocks v2, /sell and /bank redesign | Needs the currencies final |
| **6. Progress** | Depths gates by blocks (50), lower curves, satchel bug, milestones v2 + perks, contribution per member, no caps text | Balance pass on top of 3-5 |
| **7. Polish** | /menu, /is, /eco, /warp, /kit, /afkpool menus, Kindle tiles + icons, skins art (after your preview OK), holograms/sounds/animations, full message pass on the rest | Last because it touches everything |

---

## 13. Owner answers (3 Nov 2026 00:22)
- **Q1 Icons:** use the two sheets (`tools/cartoon/owner/`, 128x128 masters cut from them; picks in `render.py OWNER_PICKS`).
  Anything missing is drawn in the same style.
- **Q2 Textures kept:** yes, but crate keys, fuel cells, boosters and shard blocks get a **massive, detailed** art update
  (batch 7, previews first).
- **Q3 Scoreboard title:** "something else relevant, good looking, not too much" → `SKYBORN SKYBLOCK` in two plain
  colours (sky blue + ember), no gradient, no stars.
- **Q4 Tools outside island/Depths:** none (yes).
- **Q5 Crystal refunds:** **no refunds**; the island keeps the highest levels anyone had.
- **Q6 Pip:** yes, Pip is the guide off the island. **Kindle sends Pip with you and introduces him before you meet him**
  (batch 2: a Kindle line "Take Pip with you, he knows the way down" right before the Depths step, then Pip appears next
  to Kindle).

# Plan: owner answers of 4 Nov 2026 (00:02)

Source: owner's yes/no answers to the 26-point list. "No" items are not built: 11 (confirm screen), 15 (leaderboard
holograms), 19 ("next upgrade" hint), bone meal in the starter chest. This file is the plan; each phase ends with
`./gradlew build`, commit, push and a test checklist.

Improvements over the first list are marked **(improved)**.

---

## Phase 0: running now (Opus agent)
Delete the Depths module and the tutorial. Add `GrindBreakEvent` so /farm and /mine count for skills, tasks, quests
and collection. New start flow: the island is ready at once; a bossbar points to the guide NPC **Rowan** at spawn
(name set in config/messages); `/start` (alias `/guide`); Rowan's menu has Island, Farm and Mine. The starter chest
holds wheat seeds 8, oak sapling 2, bread 16, water bucket 1, lava bucket 1 and ice 2 (no bone meal).

## Phase 1: start flow and safety (items 3, 4, 26)
- **Guide gate (3):** `/pay`, `/trade` and `/ah` stay locked until the player has talked to Rowan once
  (`guide_talked` flag on the profile). The error says *"Talk to Rowan at spawn first (/start)."* Staff bypass:
  `skyblock.guide.bypass`.
- **Anti-boosting (3) (improved):** a new player (less than `anti-boost.playtime-minutes: 180` of playtime) can
  receive at most `anti-boost.max-received: 1,000,000` in total from other players. That total counts `/pay`, money
  in trades and auction sales, so the "list dirt for 1M" trick is covered too. Over the cap, the transfer is
  refused for both sides, with a clear message ("<name> is new and can receive $X more until they have played 3 h").
  Also: a new player can send at most `anti-boost.max-sent: 250,000` (stops alt farms feeding a main). Every refusal
  goes to the audit log (`economy/anti_boost`), and staff get a notice after 3 refusals in 10 minutes. The playtime
  comes from our own profile playtime, not the vanilla statistic, so it is per profile.
- **Welcome (4):** on the first join: the title shows the island name with a subtitle "Welcome to Phoenix Skyblock",
  and Rowan greets the player once in chat (a `greeted` flag).
- **Console (26):** `core.yml slow-operation-ms` goes 5 → 10 (ValueChange). Below 10 ms nothing is logged.

## Phase 2: HUD, tab list and scoreboard (items 5–8, 20, 24)
- **Tab header/footer (5):** header *Phoenix Skyblock* (gradient) + "Island level N"; footer `play.skybornmc.net`
  · "N online". Refreshed every 5 s, only when it changed.
- **Tab order (6) (improved):** viewer-specific teams on each player's own scoreboard (the HUD already gives each player
  one): `0` your island members (green names), then by rank weight, then by name. The chat nametag suffix (island
  level) moves into the same teams, so the two systems don't fight over the teams. Bedrock/Geyser shows the same
  order.
- **Island rank (7):** scoreboard line "Rank #12 of 140". The ranking is recomputed async every 5 min from the
  island level cache, never per tick.
- **Farm/Mine section (8):** in the grind world the sidebar swaps its middle block for: current crop or ore tier, a
  progress bar to the next tier (farmed X / Y), and "+$N bonus this visit". It returns to normal when the player leaves.
- **Auto-Sell summary (20):** every 10 s, if anything sold: action bar *"Auto-Sell +$1,240 (312 items)"*. Nothing
  shows when nothing sold.
- **Colors (24) (improved):** one fixed color per kind of thing, added to `UI_STYLE.md` and `style.yml`: money green,
  shards aqua, crystals light purple, levels/XP gold, farm yellow, mine gray-blue, island green, warnings red. Fewer
  lines, but every line has its own color.

## Phase 3: selling (items 9, 10, 14a)
- **`/sell` (10):** sells every sellable item in the inventory at once, with no menu and no confirm (the hotbar
  follows `sell-skip-hotbar`). Tools, named items, enchanted items and our own custom items are never sold. One chat
  line shows the total and the item count; hovering lists what was sold. The `/sell` menu code stays but is not
  used (`sell.menu-enabled: false`).
- **Shop "Sell all" button (9):** in the shop's bottom bar; does the same as `/sell`.
- **Sell NPCs (14a):** one "Merchant" NPC at /farm and one at /mine (the same NPC system as Rowan). Click = sell all;
  shift-click = open the storage for that area.

## Phase 4: storage (item 14b) (improved: one clear order)
Loot always goes: **inventory → storage → (only if storage is full) action-bar warning "Storage full", drops lost
like vanilla, never on the ground at /farm and /mine** (stops item lag).
- **Farm storage** (loot at /farm) and **Mine storage** (loot at /mine) are per-player virtual storages
  (material → count, DB-backed, saved with PlayerSaves rules). Separate upgrade tracks bought with money: capacity
  starts at 2,000 items; `storage.farm.levels` / `storage.mine.levels` (e.g. 25 levels, +2,000 each, nice-rounded
  costs). Menu: item list with counts, "Sell all", "Take out" (click = 1 stack, shift = as much as fits), upgrade button.
- **Hoe storage (inside the hoe):** on islands the hoe keeps its own storage in its PDC (the existing `storage` upgrade
  becomes its capacity). Same menu layout.
- **Fix shift-right-click on tools:** shift+right-click in the air opens the tool menu with the "Storage" button.
  Today the click is eaten by block interaction and the off hand. The fix is to listen on `PlayerInteractEvent`
  (RIGHT_CLICK_AIR and RIGHT_CLICK_BLOCK on non-interactable blocks, main hand only) at HIGH and cancel the vanilla use
  (tilling).
- Auto-Sell (when owned) sells from the storage every 10 s and feeds the summary in Phase 2.

## Phase 5: tool progression (items 12, 13, 16, 17, 18)
**Levels and prestige (12, 17) (improved, "ups and downs"):** a tool earns XP per relevant block. Level L needs
`xp(L) = base × L^1.6 × (1 + 0.35 × prestige)`, so each level is a bit slower than the last up to the max level
(`levels.max: 100`). At 100 the tool can **prestige**: the level goes back to 1 (fast again), the tool gets a ★ in its
name (★ to ★★★★★, then a number), and a permanent bonus (+2% drops, +1% shards per prestige, configurable). Every
level-up shows a title and plays a sound. Prestige also sends a broadcast and fireworks. That is the sawtooth: slower,
slower, prestige, fast again. The milestones every 100 crops are replaced by this.

**Combo (16) (improved):** only fully grown crops and natural ores count, and each block counts once per regrow. Each
one adds 1 combo if it comes within 1.5 s of the last; every 25 combo is +5% drops (max +50% at 250). If you stop, the
combo is halved every 1.5 s instead of dropping to 0, so a short pause doesn't wipe it. It shows in the action bar.
Leaving the area resets it. The window, step and cap go in config.

**Rare finds (13):** every counted block has a tiny chance (`rare.chance: 0.02%`, raised by the Lucky upgrade) to roll
the rare table: custom enchant book (common to epic, from the enchants module), crate key, shard pouch (50–500), money
pouch. The player gets a title and a sound. Epic+ finds are broadcast. The table and weights go in config. The roll is
server-side only and counted in the audit log.

**Upgrade rework (18) (improved): many small levels, real costs in shards.**
- Shard pace target: an active player earns about **300–500 shards an hour** at the start (hoe 3% per ripe crop,
  ~150–250 crops/min). The first levels cost about **100–200 shards** (an upgrade every 15–20 min), mid levels cost
  thousands, and the last ones cost tens of thousands. The cost formula for long tracks is
  `cost(L) = base + step × L^1.35`, nice-rounded (NiceNumbers).
- Long tracks (tiny perk per level):

  | Upgrade | Levels | Per level | At max |
  |---|---|---|---|
  | Shard Finder | 1,000 | +0.1% shards | +100% |
  | Extra Drops | 500 | +0.1% | +50% |
  | Lucky (rare finds) | 500 | +0.2% rare chance | +100% |
  | Sell Bonus (tool) | 500 | +0.02% sell price | +10% |
  | Combo Time | 100 | +10 ms window | +1 s |
  | Storage (hoe) | 250 | +200 items | +50,000 |
  | Growth (island crops) | 250 | +0.2% | +50% |

- Enchant-like perks keep their vanilla levels with big jumps: Fortune I–III (500 / 2,500 / 10,000), Efficiency I–V,
  Haste I–II. Row, Magnet and Auto-Sell are one-time unlocks priced in the thousands.
- The upgrade menu shows a level bar (*"Lv 132/1000"*) and lets you buy x1, x10 or x100 (left, shift-left, right
  click) at the summed cost. Existing levels and shards carry over (v6 migration: old level n → new level
  n × (newMax / oldMax), rounded down).
- No "next upgrade" hint (owner: no).

## Phase 6: menus and lore (items 21, 22)
- **Bottom bar (21):** one shared helper in `common` (`MenuFooter`): back at 45, info/balance at 48, close at 49,
  page arrows at 50/52 when there is paging, help at 53. Every menu uses it. A test checks that every registered menu
  puts it in the same slots.
- **Lore (22):** every item lore uses the mockup format (bold name; muted subtitle; `| Header` sections; empty line;
  `» CLICK to ...` last). A test scans all `messages/*.yml` lore lists for the rules (last line is a click line or
  empty, no glass panes, no italics).

## Phase 7: replacing "Coming soon" (item 23)
Everything that shows "Coming soon" today, built for real:
- **Sell Wand** (black market): right-click a chest on your island to sell what's in it; 100 uses; the uses show in
  the lore.
- **Auto-Sell Chest:** a placeable chest on your island that sells what goes into it every 30 s (hoppers feed it) at
  the normal sell price; one per island, more with island upgrades. **Balance:** it uses the same, already lowered
  prices of auto-farm crops (cactus, cane, pumpkin, melon, bamboo), so big auto-farms stay modest.
- **Chunk Collector:** a hopper-like block that picks up item drops in its chunk into its own storage (stops item
  lag from farms). It collects only; selling is separate. One per island, more with island upgrades.
- **Wardrobe Tome:** +1 wardrobe slot (max from config).
- **Blueprint:** Depths blueprints are gone. It becomes a **Tool Skin Blueprint**: it unlocks a skin for an island tool
  (cosmetic, tradeable).
- **"Tips" setting:** a chat tip every 10 min from `tips.yml`, which can be turned off.
- **Help "Combat & Events":** renamed **Events**, and the first real event is built: an **Envoy** (supply drops at
  /farm and /mine every 2 h, announced 5 min before, loot table in config). Bosses/KOTH stay in the BACKLOG and are not
  shown.

## Phase 8: daily reward (item 25) (improved)
- A 7-day streak calendar (a menu row of 7 days). Each day is better than the last, and day 7 gives a crate key plus
  shards. Then the cycle repeats, with a small bonus each finished week (max 4 weeks).
- Missing one day doesn't break the streak (48 h window). Missing two days does, and the menu says so.
- Anti-alt: you can claim after 10 minutes online that day and after talking to Rowan. The reward scales a little
  with island level (capped), so a fresh alt gets the least.
- Claiming happens in the `/daily` menu. On join, a chat line with a click link appears only when something can be
  claimed.

---

### Order and safety
Phases run one at a time, because they touch shared files (hud, islandtools, economy). Each phase gets unit tests for
its pure logic: anti-boost caps, XP curve, combo decay, cost formula and migration, storage add/overflow, daily streak
window. Then `./gradlew build`, commit, push and a checklist for the owner.

### Defaults chosen without asking (can be changed in config)
NPC name Rowan; 180 min / $1M received / $250k sent caps; max tool level 100; storage starts at 2,000; rare chance
0.02%; Envoy every 2 h; Tool Skin Blueprint instead of the old Depths blueprint.

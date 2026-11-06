# Plan: slower hoe, more shards, less farm money, shard popups, spawners 1x loot (7 Nov 2026)

Owner, 7 Nov 2026 (after testing update144): "prestige 6 in 5 minutes", "so few shards", "upgrade so expensive",
"each level should be felt", "less money from farming at default", shards shown as short holograms on the crop (not
the hotbar), "each spawner mob drops 1x of each of its drops", shop crop prices updated, "make sure it's all balanced".
Every number below is a config value (owner can change it in-game).

## 1. Island Hoe: 15x less tool XP
- New `islandtools.yml levels.hoe-xp-divisor: 15`: hoe tool XP per crop is divided by 15 (pickaxe, rod, sword stay).
- Effect: level 1 to 50 takes about 28,500 ripe crops instead of 1,900; prestige 6 takes about 15x longer.
- Because a level now takes 15x more crops, it also brings 15x more shards: there is time to spend them.

## 2. Shards: more, and Shard Finder matters more
- `shards.chance-hoe` 40% to **60%** a ripe crop (+50%).
- `shards.finder-per-level` 1% to **3%** a level (100 levels = 4x shards instead of 2x). Price unchanged.
- The Shard Finder lore no longer says how much better it gets ("Now +X%", "Next +Y%" are gone for this perk; the
  item shows the level and "More shards while you grind").
- Together: about 22x more shards per tool level than today, and Shard Finder is the obvious first buy.

## 3. Less money from farming at default
- Crop sell prices **-40%** (sell.yml v3; an owner-edited price stays): wheat 1,200 to 700, carrot/potato 1,500 to 900,
  beetroot 2,000 to 1,200, nether wart 3,000 to 1,800, sugar cane 400 to 250, melon slice 200 to 120, pumpkin 1,000 to
  600, cocoa 800 to 500, cactus 500 to 300, sweet berries 600 to 350, torchflower 39,600 to 24,000, pitcher plant
  87,000 to 52,000.
- /farm money bonus **halved**: 4,000 / 7,000 / 12,000 / 20,000 / 30,000 to 2,000 / 3,500 / 6,000 / 10,000 / 15,000.
- Sell bonuses, Cash Crop and Golden Crop stay percentages, so they shrink with the prices.

## 4. /shop seeds and crops: 10x their crop's new sell price (shop.yml v8)
wheat seeds 150,000 to 7,000 · beetroot seeds 250,000 to 12,000 · melon and pumpkin seeds 400,000 to 6,000 · carrot and
potato 250,000 to 9,000 · sugar cane 300,000 to 2,500 · cocoa beans 350,000 to 5,000 · cactus 350,000 to 3,000 · sweet
berries 400,000 to 3,500. Every one stays above the no-profit rule (5.5x the highest sell price). The sell price shown
in /shop is read live from /sell, so it follows the new prices by itself.

## 5. Shard and bonus popups instead of the action bar
- Each shard gain and each /farm bonus shows as a small text ("+2 ◆", "+$3,500") on the block or mob that gave it,
  floats up and is gone after 0.5 s (10 ticks). The 3-second shard action bar line and the bonus action bar line are gone;
  the payment stays batched (one ledger row per 3 s) and the sound stays.
- Safety: one `TextDisplay` per popup, only the player who earned it sees it (`setVisibleByDefault(false)` +
  `showEntity`), not persistent (never saved, never counts for island entity limits), removed by a task after 10 ticks
  and on plugin stop. At most 8 popups alive per player (more in the same moment are merged into the newest), and none
  when the block is more than 24 blocks away. Off with `shards.popups: false`.

## 6. Spawners: 1x of each drop, priced by what they earn
- Spawner mobs drop **exactly 1 of each of their drops** per kill (times Mob Loot and prestige): cow 1 beef + 1
  leather, zombie 1 rotten flesh, spider 1 string + 1 spider eye. (`spawners.vanilla-loot: false`, `spawners.drops` lists
  every item; several lines per mob are now allowed.) Witch: glowstone, sugar, glass bottle, spider eye and stick (no
  redstone: an island ore; no gunpowder: the creeper's item, and with it the witch would earn almost like tier 4).
- Loot prices so each tier earns about 2.5x the tier below: tier 1 items $140 (porkchop $280, a pig drops one item),
  tier 2 $700 (string and spider eye $350 each), gunpowder $1,750, blaze rod and ender pearl $4,200, prismarine shard and
  crystals $2,000. Wool gets its own price ($140) instead of the derived $643.
- **Every spawner pays back in about 50 hours** (before upgrades); its shop price follows what it earns:

| Tier | Mob | $ per kill | $ per hour | Price |
|---|---|---|---|---|
| 1 | pig, cow, chicken, sheep, rabbit | 280 | 100k | 5M |
| 1 | hoglin (moved from tier 4) | 420 | 150k | 7.5M |
| 2 | zombie, skeleton, spider, cave spider, slime | 700 | 250k | 12.5M |
| 2 | zombified piglin (moved from tier 4) | 980 | 350k | 17.5M |
| 3 | creeper | 1,750 | 630k | 30M |
| 3 | witch | 1,600 | 580k | 30M |
| 3 | drowned, husk, stray | 700-760 | 250-270k | 12.5-13.5M |
| 4 | blaze, enderman, guardian | 4,200-4,300 | 1.5M | 75M |

- Spawner loot does **not** count for the 3,000-a-day sell limit (`sell.daily.exempt-categories: [mob-loot]`).
- Climbing is worth it: each tier's best mobs earn 2.5x per spawner, and every new mob type adds its own places.

## Code
islandtools (divisor, chance, finder, lore, popups), common (`hud/Popups`, `config/CatalogUpdate`), economy (sell.yml v3
price changes, mob-loot exempt), essentials (farm bonus v-change, bonus popup), shop (v8 prices), stacking (multi-drop
lines, roster, vanilla-loot off). Unit tests for the pure parts. Live files: typed configs by value changes; sell.yml
and shop.yml by `CatalogUpdate` (only untouched values change; every change is logged).

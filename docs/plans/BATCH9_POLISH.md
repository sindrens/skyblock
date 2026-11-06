# Batch 9: crates, collection, shop, auction house (plan, 7 Nov 2026)

Owner, 7 Nov 2026: "keep working on everything, make it balanced, full implementation after it's planned".
This plan follows FULL_PLAN.md section 9. It starts from a read of what is already built (a lot is).
Principle 0: fun, simple, less is more. Nothing new for its own sake.

## 1. Crates (update136)
Already there: one /crates menu, a preview with the real chances, a skippable spin, pity per crate, and pouches that
grow with your progress.
- **Spin 3 s → 2 s** (`spinner.duration-ticks` 60 → 40; an owner-edited value is kept).
- **Pity bar**: the crate tile and the preview show a bar and "Top reward guaranteed in N opens" in place of
  "Luck meter x/y". The pity numbers stay (Ember 30, Cinder 28, Blaze 23, Inferno 18, Phoenix 18): the plan's "7"
  was an example, and 7 would make top rewards 3-4x as common. That would flood the server with them.
- **No more "coming soon" rows**: Sell Wand, Auto-Sell Chest, Chunk Collector, Tool Skin, Tome, Blueprint, Hologram,
  kill effect and pillar XP do not exist yet (Q-71), so they leave the tables. Their chance goes to the crate's money
  pouch, so the totals stay 100% and nobody gets a placeholder. They come back when the items are made.
  The catalog version goes 2 → 3; a migration does this only on rows still equal to the default.
- **Key Fragments off**: they were made from a Depths item that no longer exists. The tile hides and the texts lose
  "Depths".
- **Progress scaling** follows the four areas: the pouch tier uses the best of Farming, Mining, Fishing and Combat
  (was Mining, Foraging and Combat). The levels per tier stay.

## 2. Collection (update137)
Today the pages are built from removed Depths items: they warn on every start and can never fill (FX-20).
- **Pages**: Farm (5 crops), Ores (the 7 ores at /mine), Fish (every catch at /fish), Mobs (the 10 ring mobs and the
  10 bosses), Crates (as now). Page ids: `farm`, `mine` (kept, so old milestones and titles stay), `fish`, `mobs`,
  `crates`.
- **Recording** from the grind events: a crop, ore, catch or kill counts the first time and adds to the amount.
- **Amount**: each entry shows "Found 3,412". A new database column `amount` holds it (default 1 for old rows).
  The "new find" chat line still shows only once.
- **Milestones** stay: 25/50/75/100% give island points, a full page gives a title and a Blaze key, everything gives
  the Keeper of Ashes title and a Phoenix key. New small perk: every 25% milestone also gives 5 crystals (20 a page).
  It is small but feels like something, and crystals can't be traded.

## 3. Shop (update138)
- **The 34 items that are still gone come back** (Q-42): 12 planks, 16 stone/brick variants, baked potato, blue/packed ice,
  chiseled bookshelf, moss carpet, white banner. They go into Building (new groups Planks and Stone Variants) and the
  matching tabs. Prices: the old v3 prices, at least 7.5x the sell value (the shop's one rule).
- A live `shop.yml` gets them through migration v7. It moves these ids from `worth-only` into their tabs, together
  with the 19 utility items from earlier, which an old live file may still hold there.
- **Mob Drops** gets what the /pve mobs drop and the shop lacks: arrow, ink sac, glow ink sac, magma cream, ghast tear,
  phantom membrane, nautilus shell, wither skeleton skull, coal, iron ingot. Items without a sell price (arrow, magma
  cream, ghast tear, phantom membrane) get one in `sell.yml` first.
- **Click**: unchanged (click opens the buy page, shift-click buys a stack). Your GUI-1 answer said a click opens the buy
  page; the plan's "one click buy" goes against it. That is question Q-73.

## 4. Auction house (update138)
Already there: categories, name search, sort by newest/ending/price, a typical-price hint, and pay limits.
- **List similar**: a button in the browse menu: "Similar to the item in your hand". It shows only listings of that
  exact item.
- **Price hint on a fresh server**: when there are not yet enough sales for a typical price, the sell page shows
  "Cheapest now: $X" from the current listings.
- **Pay limit**: today a buy over the seller's daily receive limit is refused, with a message. The plan says the extra
  should wait in the mailbox. Kept as it is: a refused buy can never lose anyone money. It is in the report.

## Tests
- Crates: the catalog tables still add up to 100, the migration on a v2 file, pity bar maths (opens left = pity - counter).
- Collection: the catalog has no warnings with the bundled file, amounts add up, and the first-find notice shows only once.
- Shop: new counts, the one-rule price check on every item, migration v7 on a v6 file.
- Auction: the "similar" filter matches only the same item key; the cheapest-now hint ignores your own listings.

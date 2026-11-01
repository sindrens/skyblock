# Builder shop (`modules/shop`)

Buy building blocks, decoration, redstone, seeds, basics and tier 1-2 spawners for money, and sell back what the sell table buys (PHASE-3 M3.5, **Shop v2** GUI-3, **GUI-P3**: category menu, buy GUI and sell GUI, I9 section 12; design `docs/design/SHOP_DESIGN.md`). Every sale uses the `/sell` rules (`SellService`: dynamic price, Hot Item, sell bonus and cap, one ledger transaction, items back or to `/box` on failure); the shop never prices a sale itself. Items and prices: `shop.yml` (**draft: the owner approves the prices before launch**); settings: `shop-settings.yml`; texts: `messages/shop.yml`. Reload: `/skyblock reload shop`.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/shop [category]` | Without a category: the **category menu**. With one (`wood`, `stone-earth`, `special`, `colors`, `decoration`, `essentials`, `farming`, `redstone`, `spawners`, tab-completed) that category's item grid, with Back to the category menu; the old names (`utility`, `glass-colors`, `building-blocks`, `stone-bricks`) still find their category. Also in `/menu` (Shop) and `/help shop` | `skyblock.shop.use` (everyone) | ✅ |

## Pages (vanilla chest menus, work on Bedrock)
```
 Category menu (4 rows):  4 wallet · 9 11 13 15 17 / 19 21 23 25 the nine categories (centred, shop.yml order)
                          27 Back · 28 Sell chest · 29 Undo · 31 Close                  (8 or fewer categories: 3 rows)
 Item grid (6 rows):      4 wallet · rows 1-4: one group per row, centred, split evenly (16 = 6 + 5 + 5), whole groups per page
                          45 Back · 46 Sell chest · 47 Undo · 49 Close · 52/53 pages (paper, count = target page)
 Buy GUI (3 rows):        4 item · row 1 centred: Buy 1 · Buy 16 · Buy 64 · Fill inventory · Type an amount
                          18 Back · 19 Sell · 20 Undo · 22 Close              (non-stackables: Buy 1 · Type, 12 14)
 Sell GUI (3 rows):       4 item · row 1 centred: Sell 1 · Sell 16 · Sell 64 · Sell all · Type an amount
                          18 Back · 19 Buy · 22 Close                         (non-stackables: Sell 1 · Sell all · Type)
```
- **Plaques** (Java with the icon pack, GUI-FONT): `SHOP` on the category menu, the tab's `categories.<id>.short` on a grid (`FARMING`), `BUY` / `SELL` on the buy and sell GUIs.
- **Wallet tile (MENUS-V3):** slot 4 of the category menu and every grid is `Your Money` with the full amount (framework `messages/menus.yml wallet.money`). The buy and sell GUIs keep the item on 4 and show no balance (`| You can afford` is the money hint).
- **Grouped grid (MENUS-V3 6.2):** `shop.yml categories.<tab>.groups.<group>: [ids]` lists the rows of a tab (Farming: seeds, crops, saplings // plants, lush plants, Nether plants). Each group starts a new row, is centred (7-wide grid) and split evenly over rows of up to 7; a page holds whole groups (4 rows), a group that does not fit starts the next page; inside a group the file order is cheap to dear. Ids in no group form a last group; unknown ids are skipped with a console line. Group names (`messages/shop.yml groups.<tab>.<group>`) are for staff only; the grid draws no captions.
- No tab row any more and no remembered tab: `/shop` always starts at the categories; Back on a grid returns there.
- **Click map on a grid tile:**

| Input | Result |
|---|---|
| Left click (Bedrock tap) | the buy GUI (locked or unavailable items explain why) |
| Right click | the sell GUI (only when sellable; otherwise just the error sound) |
| Middle click (creative clients) / **Shift + Right-click** (survival) | sells every one of that item you carry (storage and hotbar, never armor or offhand) |
| Shift + Left-click | buys one stack (a big buy arms first: second shift-click) |
| Number keys, drop, double click, offhand | ignored (the client is resynced) |

  Bedrock reaches selling through the **Sell** button of the buy GUI and **Sell all** on the sell GUI; nothing needs right or middle click.
- **Tooltips (I9, MENUS-V3 6.4):** `| Buy Price: $8 each` (bold green), `| Sell Price: $2 each` (bold red) or a red `Not sellable` (no sell price, spawners), a live note when the sell price moved (`Falling: -4% from base price`, `Rising: +6% ...`, `Hot item today: +25%`; only from `sell.note-min-percent`), spawners keep their `| Information` (next works at, payback), ONE muted Java hint (`Right-click: sell` when the item sells, else `Shift-click: buy a stack`), then `» CLICK to buy`. Middle click and Shift + Right-click still sell all (taught in `/help shop` and by the sell GUI's Sell all button). Bedrock sees no click words and no hints. Sell prices under $1 show two decimals (`$0.40`).
- **Buy GUI:** the item tile (prices, stack price, you can afford, room for); lime buttons (our confirm icon) that can buy, grey ones that can't, orange while armed (big buy). Type an amount: chat on Java (`1.5k` works), the tap number picker on Bedrock. **Sell** (19) switches to the sell GUI of the same item without stacking Back.
- **Sell GUI:** the item tile (`| Sell Price`, the live note, `| You carry`, `| You get` for all you carry, `incl. your +B% sell bonus`). Each button says what you get; carrying fewer than the button sells what you carry. Grey buttons (none carried, not sellable, under $1) only play the error sound. Type an amount: chat on Java, the tap picker (1 to what you carry) on Bedrock. **Buy** (19) switches back. The economy prints the sale line (`✔ Sold 16x Coal for $32` in the action bar; a chat line with hover breakdown from `sell.big-sale-chat-from`).
- **Sell chest** (46 on the grid, 28 on the category menu): opens the `/sell` chest for many item kinds at once.
- **Feedback:** every buy shows `✔ +64 Torch -$256 · $1,199,744 left` in the action bar with a soft click (`feedback.sound`); a chat line only from `feedback.chat-from` ($100,000).

## F14: big buys and Undo (both on by default, switchable per player in `/settings` → Gameplay)
- **Second click for big buys** (`shop-big-buy`): a buy costing more than `big-buy.percent` (50%) of your money turns its button **orange** with a sound: "That is 74% of your money · Balance after: $3,284 · » CLICK AGAIN to buy". It disarms after `big-buy.arm-seconds` (5 s) or on any other click. A typed big amount shows armed on the Type button; a big Java shift-click says it in the action bar and needs a second shift-click.
- **Undo last buy** (`shop-undo`, 47 on the grid, 20 on the buy GUI, 29 on the category menu): a clock (our undo icon) whose count is the seconds left (`undo.seconds`, 15). Its lore names the item, the price and the time left. One click gives the **full price back, once**, only while **every** bought item is still in your inventory (a spawner until it is placed). A gray dye "Nothing to undo" otherwise. Sales have no undo (C4). Only the last buy can be undone; quitting or switching profile ends it.

## Catalog (SHOP_DESIGN §2, Shop v3 tab order)
Wood 24 · Stone & Earth 28 · Special Blocks 28 · Colors 53 (3 pages) · Decoration 28 · Essentials 26 · Farming 27 (2 pages) · Redstone 28 · Spawners 20 (four groups of five, one tier per row). The item lists of the economy design (ECON-TABLES D, 255 items) arrive with the economy lane; the groups already name its items (ids that are not sold yet are skipped).
- Owner price decisions: hopper **$2,500** (S6), bone meal **$30** (S7), new flint and steel $40, fishing rod $150, shears $60, bread $15, baked potato $15 (S4). **Removed:** ender chest (S6), stripped logs, dyes, coloured wool and carpets, villager job blocks, redstone block, most flowers (S3).
- **Spawners tab** (ECON-SPAWNERS): tier 1 (pig, cow, chicken, sheep, rabbit) $100,000, tier 2 (zombie, skeleton, spider, cave spider, slime) $250,000, tier 3 (creeper, witch, drowned, husk, stray) $500,000, tier 4 (blaze, enderman, guardian, hoglin, zombified piglin) $1,000,000. Handed out as the stacking module's spawner items. Only tiers up to `spawners.max-tier` are sold; `spawners.enabled: false` empties the tab.
- `worth-only:` keeps the last price of items the shop no longer sells, only as block worth for the island level and trade value hints.
- **One-time migration:** a live `shop.yml` of config-version 1 is moved to the 9 tabs on startup. Items that stay keep the live entry (owner prices, `enabled: false`, `requires`); a price the design changes (hopper, bone meal) only moves when the live value still equals the old default; removed default items go to `worth-only` with their live price; items the owner added himself are kept in the matching new tab. Backup `shop.yml.before-v2`; every change is in the console, in `shop-migration-v2.log` and in the audit log (`shop_catalog_migrate`, category `config`).
- **Migration to config-version 3 (MENUS-V3 6.3, data-driven):** the live file becomes whatever the bundled v3 `shop.yml` says: new tab order, `groups:`, the Spawners tab of the economy design. Items you have keep your entry (price, `enabled`, `requires`, own `contains`); a price that still equals its Shop v2 default (frozen `migrations/shop-v2.yml`) moves to the new default (`price: auto` is copied as is); default items the bundled file drops go to `worth-only` at your price; new items are added and leave `worth-only`; your own items stay in their tab; your tab icons stay. Backup `shop.yml.before-v3`, `shop-migration-v3.log`, console lines, audit `shop_catalog_migrate` with `version: 3`. A version-1 file runs v1 -> v2 -> v3 in one start.

## Rules
- Money comes from the **active profile's** account. One ledger transaction per purchase (reason `shop_buy`, idempotency key `shop_buy:<purchase id>`, details: item, quantity, unit price, category, profile), visible in `/transactions` and `/logs`. The purchase row is written in the same database transaction.
- **Full inventory:** free space is counted first; if not all of it fits, only what fits is bought and charged (`Bought 40 of 64: no more room`); no room at all = nothing bought, nothing paid. Items go into the hotbar/main inventory only. If the inventory fills up while the payment runs, the rest goes to `/box` (never dropped).
- Not while frozen, in staff mode or during a profile switch. One purchase (or undo) at a time per player.
- **Selling from the shop (GUI-P3):** the items are taken out on the main thread first (escrow; lowest slots first, the last stack partly), then one ledger transaction (reason `sell`, key `sell:<sale id>`, sale source `shop`) pays the active profile and writes the `sell_sales` row; anything not sold (or everything on a failure) goes back to the inventory, then `/box`. Guards: the menu click debounce, one shop sale in flight per player with `sell.min-gap-ms` (400) between sales, the economy's own one-sale-at-a-time lock, and the escrow itself (a second click finds nothing carried). Frozen balance blocks selling like buying.
- **Buy > sell:** an item is only for sale if its price is above the most it (or what it turns back into, `contains:`) could ever bring with `/sell` at the current settings (×7.5 by default). Items that break the rule show "Not for sale right now" and are named in the console on start/reload. A build test (`BuySellInvariantTest`) checks the bundled `shop.yml` against `sell.yml`.
- Optional per item: `requires: { farming: 10 }` (skill level needed), `enabled: false`.

**Settings (GUI-P3):** `sell.min-gap-ms` (400, 100..5000) between two shop sales of one player; `sell.note-min-percent` (2, 0..100) for the live price note.

**Audit:** a shop sale is the economy's normal `sell` row (ledger reason `sell`, key `sell:<sale id>`, details with `source: shop` and every item); the economy writes one `shop_buy` row per purchase and one `shop_undo` row per refund (category `economy`). Category `item`: `shop_pending` / `shop_deliver` (handed over later), `shop_review` (crash mid hand-over), `shop_undo` (items taken back, refund paid) and `shop_undo_failed` (refund refused, items given back). Category `config`: `shop_catalog_migrate`.

## Safety (for developers)
- `shop_purchases` row states: `delivering` (written by the payment's guard) → `delivered`; `delivering` → `pending` if the player is gone/switched profile/in staff mode; `pending` → `delivering` is a conditional update (claimed exactly once on join, profile switch), then handed over. On startup rows still `delivering` (crash) become `review` (SECFIX1 F-12) for staff.
- **Undo:** offered only for a purchase handed over right away, after its row is `delivered`. The click takes all items back first (main thread), then one guarded ledger deposit (`shop_undo`, idempotency key `shop_undo:<purchase id>`) moves the row `delivered → refunded` in the same database transaction, so it pays exactly once. If it fails, the items go back (inventory, then `/box`; offline: the paying profile's box). Can lose a refund, never dupe.
- After handing items over the player's data is saved (`buy.save-player-data`), so a crash can't roll the inventory back to before a paid purchase.
- The live buy > sell check uses `SellService#maxUnitPrice` (economy); `safety.check-sell-prices: false` turns it off (not recommended).

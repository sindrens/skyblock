# Builder shop (`modules/shop`)

Buy building blocks, decoration, redstone, seeds, basics and tier 1-2 spawners for money (PHASE-3 M3.5, **Shop v2** GUI-3, design `docs/design/SHOP_DESIGN.md`). A real money sink: the shop never buys anything back (the **Sell** button, slot 51 of every shop page, opens the `/sell` chest). Items and prices: `shop.yml` (**draft: the owner approves the prices before launch**); settings: `shop-settings.yml`; texts: `messages/shop.yml`. Reload: `/skyblock reload shop`.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/shop [tab]` | Opens the shop on your last tab (Essentials the first time). With a tab (`essentials`, `farming`, `wood`, `stone-earth`, `special`, `colors`, `decoration`, `redstone`, `spawners`, tab-completed) it opens that tab; the old category names (`utility`, `glass-colors`, `building-blocks`, `stone-bricks`) still find their new tab. Also in `/menu` and `/help shop` | `skyblock.shop.use` (everyone) | ✅ |

## Pages (6 rows, works on Bedrock)
```
 0 Essentials | 1 Farming | 2 Wood | 3 Stone & Earth | 4 Special | 5 Colors | 6 Decoration | 7 Redstone | 8 Spawners
 rows 1-4: 28 items per page (Colors has 2 pages)
 45 Back | 46 Balance | 47 Undo last buy | 48 ? | 49 Close | 50 Main menu | 51 Sell | 52 ‹ Prev | 53 Next ›
```
- **Tabs on every page**, no category screen. A tab click switches in place (same window, title `Shop » <Tab>`). The current tab glints and says "✔ You are here".
- **Click an item** (Java and Bedrock alike) → the **buy page** (same tabs and bottom bar, title `Shop » <Item>`): the item at 13 (price each, stack price, "You can afford", room for), **Buy 1** (20), **Buy 16** (22; 8 for items that stack to 16), **Buy 64** (24; one stack), **Fill inventory** (30: as many as fit and you can afford) and **Type an amount** (32: chat on Java, `1.5k` works; the tap number picker on Bedrock). Items that don't stack (buckets, beds, tools) show only Buy 1 and Type an amount. The page stays open and refreshes after a buy. A tab click on the buy page goes back to that tab.
- **Java shortcut:** shift-click on a grid item buys one stack at once (only in the ? guide, never in lore).
- **Grid lore:** `| Buy Price` each, `| Sell Price` each (when `/sell` buys it), `| Information` with *Stack of 64* and *You can afford*, then `» CLICK to choose an amount`. Spawners also show **Next works at X%** (owner F2 hook; 100% until the diminishing-output curve is built) and **Pays back in about N h** (`spawners.income-per-hour.tier-N`).
- **Feedback:** every buy shows `✔ +64 Torch -$256 · $1,199,744 left` in the action bar with a soft click (`feedback.sound`); a chat line only from `feedback.chat-from` ($100,000).

## F14: big buys and Undo (both on by default, switchable per player in `/settings` → Gameplay)
- **Second click for big buys** (`shop-big-buy`): a buy costing more than `big-buy.percent` (50%) of your money turns its button **orange** with a sound: "That is 74% of your money · Balance after: $3,284 · » CLICK AGAIN to buy". It disarms after `big-buy.arm-seconds` (5 s) or on any other click. A typed big amount shows armed on the Type button; a big Java shift-click says it in the action bar and needs a second shift-click.
- **Undo last buy** (`shop-undo`, slot 47): a gold plate whose count is the seconds left (`undo.seconds`, 15). Its lore names the item, the price and the time left. One click gives the **full price back, once**, only while **every** bought item is still in your inventory (a spawner until it is placed). Grey "Nothing to undo" otherwise. Only the last buy can be undone; quitting or switching profile ends it.

## Catalog (SHOP_DESIGN §2, ~253 items)
Essentials 26 · Farming 27 · Wood 24 · Stone & Earth 28 · Special Blocks 28 · Colors 53 (2 pages) · Decoration 28 · Redstone 28 · Spawners 11.
- Owner price decisions: hopper **$2,500** (S6), bone meal **$30** (S7), new flint and steel $40, fishing rod $150, shears $60, bread $15, baked potato $15 (S4). **Removed:** ender chest (S6), stripped logs, dyes, coloured wool and carpets, villager job blocks, redstone block, most flowers (S3).
- **Spawners tab** (S5): tier 1 (pig, cow, chicken, sheep, rabbit) $117,000 and tier 2 (zombie, skeleton, spider, cave spider, slime, squid) $213,000, fixed price (F2). Handed out as the stacking module's spawner items. Only tiers up to `spawners.max-tier` (2) are sold; `spawners.enabled: false` empties the tab.
- `worth-only:` keeps the last price of items the shop no longer sells, only as block worth for the island level and trade value hints.
- **One-time migration:** a live `shop.yml` of config-version 1 is moved to the 9 tabs on startup. Items that stay keep the live entry (owner prices, `enabled: false`, `requires`); a price the design changes (hopper, bone meal) only moves when the live value still equals the old default; removed default items go to `worth-only` with their live price; items the owner added himself are kept in the matching new tab. Backup `shop.yml.before-v2`; every change is in the console, in `shop-migration-v2.log` and in the audit log (`shop_catalog_migrate`, category `config`).

## Rules
- Money comes from the **active profile's** account. One ledger transaction per purchase (reason `shop_buy`, idempotency key `shop_buy:<purchase id>`, details: item, quantity, unit price, category, profile), visible in `/transactions` and `/logs`. The purchase row is written in the same database transaction.
- **Full inventory:** free space is counted first; if not all of it fits, only what fits is bought and charged (`Bought 40 of 64: no more room`); no room at all = nothing bought, nothing paid. Items go into the hotbar/main inventory only. If the inventory fills up while the payment runs, the rest goes to `/box` (never dropped).
- Not while frozen, in staff mode or during a profile switch. One purchase (or undo) at a time per player.
- **Buy > sell:** an item is only for sale if its price is above the most it (or what it turns back into, `contains:`) could ever bring with `/sell` at the current settings (×7.5 by default). Items that break the rule show "Not for sale right now" and are named in the console on start/reload. A build test (`BuySellInvariantTest`) checks the bundled `shop.yml` against `sell.yml`.
- Optional per item: `requires: { farming: 10 }` (skill level needed), `enabled: false`.

**Audit:** the economy writes one `shop_buy` row per purchase and one `shop_undo` row per refund (category `economy`). Category `item`: `shop_pending` / `shop_deliver` (handed over later), `shop_review` (crash mid hand-over), `shop_undo` (items taken back, refund paid) and `shop_undo_failed` (refund refused, items given back). Category `config`: `shop_catalog_migrate`.

## Safety (for developers)
- `shop_purchases` row states: `delivering` (written by the payment's guard) → `delivered`; `delivering` → `pending` if the player is gone/switched profile/in staff mode; `pending` → `delivering` is a conditional update (claimed exactly once on join, profile switch), then handed over. On startup rows still `delivering` (crash) become `review` (SECFIX1 F-12) for staff.
- **Undo:** offered only for a purchase handed over right away, after its row is `delivered`. The click takes all items back first (main thread), then one guarded ledger deposit (`shop_undo`, idempotency key `shop_undo:<purchase id>`) moves the row `delivered → refunded` in the same database transaction, so it pays exactly once. If it fails, the items go back (inventory, then `/box`; offline: the paying profile's box). Can lose a refund, never dupe.
- After handing items over the player's data is saved (`buy.save-player-data`), so a crash can't roll the inventory back to before a paid purchase.
- The live buy > sell check uses `SellService#maxUnitPrice` (economy); `safety.check-sell-prices: false` turns it off (not recommended).

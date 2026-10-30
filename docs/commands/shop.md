# Builder shop (`modules/shop`)

Buy building blocks, decoration, redstone, seeds and utility items for money (PHASE-3 M3.5). A real money sink: the shop never buys anything back (selling is `/sell`). Items and prices: `shop.yml` (**draft: the owner approves the prices before launch**); settings: `shop-settings.yml`; texts: `messages/shop.yml`. Reload: `/skyblock reload shop`.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/shop [category]` | Opens the shop: categories → items → amount. With a category (`building-blocks`, `wood`, `stone-bricks`, `glass-colors`, `decoration`, `redstone`, `farming`, `utility`, tab-completed) it opens that category straight away. Also in `/menu` (emerald) and `/help shop` | `skyblock.shop.use` (everyone) | ✅ |

**Menus** (6 rows, taps only, works on Bedrock): categories (slots from `shop.yml`) with your balance in the top middle → the category's items with price per item and per stack (pages of 28) → the item page: **Buy 1x / 16x / 64x** (`buy.amounts`) and **Custom amount** (number picker, up to what fits in your inventory and `buy.max-amount` = 2,304). Purchases from `buy.confirm-above` ($100,000) ask to confirm. Each button says when it won't work (not enough room, can't afford).

**Rules**
- Money comes from the **active profile's** account. One ledger transaction per purchase (reason `shop_buy`, idempotency key `shop_buy:<purchase id>`, details: item, quantity, unit price, category, profile), visible in `/transactions` and `/logs`. The purchase row is written in the same database transaction.
- **Full inventory:** free space is counted first; if the amount doesn't fit, nothing is bought and nothing is paid. Items go into the hotbar/main inventory only (never armor/offhand). If the inventory fills up while the payment runs, the rest drops at the player's feet.
- Not while frozen, in staff mode or during a profile switch. One purchase at a time per player.
- **Buy > sell:** an item is only for sale if its price is above the most it (or what it turns back into, `contains:`) could ever bring with `/sell` at the current settings (base × dynamic ceiling × Hot Item × (1 + sell cap) = ×7.5 by default). Items that break the rule show "Not for sale right now" and are named in the console on start/reload. A build test (`BuySellInvariantTest`) checks the bundled `shop.yml` against `sell.yml`.
- Optional per item: `requires: { farming: 10 }` (skill level needed; Farming tier unlocks come later), `enabled: false`.

**Audit:** the economy writes one `shop_buy` audit row per purchase (category `economy`, with the item details). Purchases handed over later are logged as `shop_pending` (player left/switched profile before hand-over) and `shop_deliver` (category `item`).

## Safety (for developers)
- `shop_purchases` row states: `delivering` (written by the payment's guard) → `delivered`; `delivering` → `pending` if the player is gone/switched profile/in staff mode; `pending` → `delivering` is a conditional update (claimed exactly once on join, profile switch), then handed over. On startup rows still `delivering` (crash) become `pending`.
- After handing items over the player's data is saved (`buy.save-player-data`), so a crash can't roll the inventory back to before a paid purchase.
- The live buy > sell check uses `SellService#maxUnitPrice` (economy); `safety.check-sell-prices: false` turns it off (not recommended).

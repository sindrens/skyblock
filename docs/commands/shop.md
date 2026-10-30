# Builder shop (`modules/shop`)

Buy building blocks, decoration, redstone, seeds and utility items for money (PHASE-3 M3.5, quick-buy grid SELL2). A real money sink: the shop never buys anything back (the **Sell** button, slot 51 of every shop menu, opens the `/sell` chest). Items and prices: `shop.yml` (**draft: the owner approves the prices before launch**); settings: `shop-settings.yml`; texts: `messages/shop.yml`. Reload: `/skyblock reload shop`.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/shop [category]` | Opens the shop: categories → quick-buy grid. With a category (`building-blocks`, `wood`, `stone-bricks`, `glass-colors`, `decoration`, `redstone`, `farming`, `utility`, tab-completed) it opens that category straight away. Also in `/menu` (emerald) and `/help shop` | `skyblock.shop.use` (everyone) | ✅ |

**Menus** (6 rows, works on Bedrock): categories (slots from `shop.yml`) with your balance in the top middle → the category's **quick-buy grid** (pages of 28). Java: **left-click buys 1** (`quick-buy.left-amount`), **right-click buys 64** (`quick-buy.right-amount`), **shift-click opens the amount screen**. Bedrock (Floodgate): a **tap opens the amount screen**. Item lore (STYLE2 LoreBuilder): `| Buy Price` each, `| Sell Price` each (when `/sell` buys it, before bonuses), `| Information` with *Stack of 64* and *You can afford: N*, then `» LEFT-CLICK buy 1` / `» RIGHT-CLICK buy 64` / `» SHIFT-CLICK pick amount` (Bedrock: `» TAP to choose`). The **amount screen**: −64 −16 −1 / item / +1 +16 +64 (`buy.picker-steps`), **Type an amount** (chat, accepts `1.5k`), **Buy N for $X** (says when only part fits or you can't afford it) and **Cancel**; starts at `quick-buy.start-amount` (1), up to `buy.max-amount` (2,304). Purchases never ask to confirm (owner answer 8, FIXES1; the old `buy.confirm-above` setting is gone and ignored if still in shop.yml).

**Feedback.** Every purchase shows an action bar line (`✔ +64 Oak Log -$512`) with a soft click (`feedback.sound`); a chat line is added only from `feedback.chat-from` ($100,000). Not enough money: says the price, your balance and "buy fewer or sell items first".

**Rules**
- Money comes from the **active profile's** account. One ledger transaction per purchase (reason `shop_buy`, idempotency key `shop_buy:<purchase id>`, details: item, quantity, unit price, category, profile), visible in `/transactions` and `/logs`. The purchase row is written in the same database transaction.
- **Full inventory:** free space is counted first; if not all of it fits, only what fits is bought and charged (`Bought 40 of 64: no more room`), still one atomic transaction; no room at all = nothing bought, nothing paid. Items go into the hotbar/main inventory only (never armor/offhand). If the inventory fills up while the payment runs, the rest drops at the player's feet.
- Not while frozen, in staff mode or during a profile switch. One purchase at a time per player.
- **Buy > sell:** an item is only for sale if its price is above the most it (or what it turns back into, `contains:`) could ever bring with `/sell` at the current settings (base × dynamic ceiling × Hot Item × (1 + sell cap) = ×7.5 by default). Items that break the rule show "Not for sale right now" and are named in the console on start/reload. A build test (`BuySellInvariantTest`) checks the bundled `shop.yml` against `sell.yml`.
- Optional per item: `requires: { farming: 10 }` (skill level needed; Farming tier unlocks come later), `enabled: false`.

**Audit:** the economy writes one `shop_buy` audit row per purchase (category `economy`, with the item details). Purchases handed over later are logged as `shop_pending` (player left/switched profile before hand-over) and `shop_deliver` (category `item`).

## Safety (for developers)
- `shop_purchases` row states: `delivering` (written by the payment's guard) → `delivered`; `delivering` → `pending` if the player is gone/switched profile/in staff mode; `pending` → `delivering` is a conditional update (claimed exactly once on join, profile switch), then handed over. On startup rows still `delivering` (crash) become `pending`.
- After handing items over the player's data is saved (`buy.save-player-data`), so a crash can't roll the inventory back to before a paid purchase.
- The live buy > sell check uses `SellService#maxUnitPrice` (economy); `safety.check-sell-prices: false` turns it off (not recommended).

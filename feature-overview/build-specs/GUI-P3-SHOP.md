# Build spec GUI-P3-SHOP: /shop in categories, buy GUI, sell GUI, left / right / middle click

Cards: I9 v2 section 12 (Locked), I7 v3, G9 v3 (hub button only); C5 and C4 are **Review 1** cards, so only their ledger-decided parts are specified here: I9-R1-05 (click map), C4-R1-01 (sell rules = `SellService`), C5-R1-01 (click map answered), C5-R1-02 (no search), I9-R1-07. Nothing from the undecided C4/C5 proposals (auto price, derived prices, push limit, market tips, staff shop toggles, price changes, item list changes) is in this spec. Written 31 Oct 2026. No code in this file. Build order: P1 (GUI-P1-PACK.md) -> P2 (GUI-P2-MENUS.md) -> **P3 (this)**, one worktree `q/gui-phase1`.

## 0. Goal and scope
**Goal:** `/shop` opens a **category menu** (icons, centred, 4 rows), then the **item grid** of the chosen category (7 wide, up to 4 rows, paging). A shop tile reads like the MC Hub shop: Buy price, Sell price (or red "Not sellable"), a live price note, and the clicks: **left = buy GUI, right = sell GUI, middle (and Shift + Right-click on Java) = sell every one of that item you carry**, Shift + Left = buy one stack (as built). Bedrock reaches everything with buttons: a `Sell` button on the buy GUI and `Sell all` on the sell GUI. Every sale goes through the existing `SellService` (C4 rules), atomic, items to `/box` on failure; buying keeps the built engine, undo and the second-click rule for big buys. **No prices, no item list, no tab list change.**

**In scope:** shop menus, tooltips, click map, `SellService` seam for "sell this item from the inventory", economy report line for single-item sales, shop messages and settings, docs, tests.
**Out of scope:** search (none in the shop, I9-R1-07), C15 price changes, the sell chest (`/sell`) itself, confirm pages for single buys or sells (I9: none), auction house, sell wands.

## 1. Assumes from earlier steps
From **P1:** `core.menus().icon(viewer, key, name, lore)`, icon keys `shop/buy`, `shop/sell`, `shop/sell_all`, `shop/fill`, `shop/<category>` (one per tab id with `-` -> `_`: `essentials farming wood stone_earth special colors decoration redstone spawners`), `state/confirm`, `state/undo`, `state/off`, `nav/*`, `currency/money`.
From **P2:** `Centered.columns/layout/gridSlot`, `MenuLayout` (3/4/6 rows: Back, `actionA`/`actionB`, Close, previous/next), `Menu.navBar()`, `Menu.SLOT_ACTION_A (46)`, `SLOT_ACTION_B (47)`, `MenuButton#withMiddle`, `ClickRoute.MIDDLE` (Shift + Right-click also routes there), `MenuClick#isMiddle`, `MenuService#icon/pageItem/backItem/javaLines`, `LoreBuilder#hint`, `PagedMenu` with paper page arrows, `style.yml` `area` colours, the 3-row confirm style.

## 2. Decisions made in this spec (developer decisions; owner only vetoes)
| # | Decision | Why |
|---|---|---|
| D1 | `/shop` always opens the category menu; the "remember last tab" feature (`lastTab`, `rememberTab`) is removed. `/shop <category>` opens that category's grid directly, with Back to the category menu. | Owner request; no tabs on the grid any more. |
| D2 | The grid has **no tab row**. Row 0 holds only the summary tile on slot 4 (your balance). To change category: Back. | I9 section 2/4: empty slots stay empty; balance moves to the summary slot (GUI-P10). |
| D3 | Category menu = `Centered.layout(9)` = 4 rows, 5 + 4 tiles: row 1 slots 9, 11, 13, 15, 17; row 2 slots 19, 21, 23, 25. Order = `shop.yml` order. With fewer categories the layout re-centres (3 rows up to 8). | I9 section 2 patterns. |
| D4 | Buy GUI and sell GUI are **3 rows with the same layout**: summary/item tile on slot 4, one centred row of choices on row 1, bottom row Back 18, action 19 and 20, Close 22. | I9 section 12: "same layout and pieces for both directions". |
| D5 | Sell GUI choices: `Sell 1`, `Sell 16`, `Sell 64` (from `ShopMath.quickAmounts(maxStack)`: 1/8/16 for 16-stackers, only 1 for non-stackers), `Sell all`, `Type an amount`. Buy GUI choices: `Buy 1`, `Buy 16`, `Buy 64` (stack), `Fill inventory` (stackable items only), `Type an amount`. "Type an amount" stays an optional extra: Java types in chat, **Bedrock uses the existing tap picker** (`NumberPickerMenu`), so no typing is ever required. | I9 section 12 lists it for both GUIs; owner "no typing" read as "never required". |
| D6 | "Confirm" in the owner's request = the built C5 rule: a buy over `big-buy.percent` (50%) of your money arms its button (orange) and needs a **second click** within `big-buy.arm-seconds` (5). No confirm page for single buys or sells (I9). | I9 section 12, C5. |
| D7 | The sale itself uses a new `SellService` seam (`sellItem`, `carried`) that runs the **same escrow -> pay -> give back path as `/sell`** in `SellFeature`; the shop never prices anything. Source tag in the sale record: `shop` (data field, not a ledger reason). | C4-R1-01, GUI-P11. |
| D8 | Not sellable (`SellService#price` empty, spawners): the tile says `Not sellable` in red; right-click, middle click and the sell GUI buttons only play the **error sound** (I9). | I9. |
| D9 | Live price note text: `Falling: -4%` / `Rising: +6%` (percent from the base price, `SellPrice#percentFromBase`) with a muted `from base price`, or `Hot item today: +25%` when `SellPrice#hot`. The word "today" from I9 is dropped for Falling/Rising because the sell service exposes change from base, not from 24 h ago. Shown only when `abs(percent) >= sell.note-min-percent` (2). | Honest data. |

## 3. Classes: exact changes (module `modules/shop`, package `net.skybornmc.skyblock.shop` unless noted)
| Class | Change |
|---|---|
| `ShopModule` | Section 5. |
| `ShopCategoryMenu` (**new**, extends `Menu`) | The category menu (5.1). |
| `ShopMenu` | Becomes the **item grid** only (5.2): no tabs, summary slot 4, Sell 46, Undo 47, tooltips from `ShopTooltips`, four click handlers. Constructor `(ShopModule, String categoryId)`; field/methods `tabId`, `selectTab`, `tab()` renamed `categoryId`, `category()`. Constants `SLOT_BALANCE/SLOT_UNDO/SLOT_SELL` removed (use `Menu.SLOT_SUMMARY = 4`, `SLOT_ACTION_A`, `SLOT_ACTION_B`; add `public static final int SLOT_SUMMARY = 4` to `Menu` if P2 did not). `gridLore` moves to `ShopTooltips`. |
| `BuyMenu` | Rewritten to 3 rows (5.3). Keeps `BigBuyGuard`, `typed`, `press`, `typedAmount`, `afterBuy`, `askAmount`. |
| `SellItemMenu` (**new**, extends `Menu`) | The sell GUI (5.4). |
| `ShopTooltips` (**new**, pure core + one builder method) | Tooltip lines and price/note text (5.5). |
| `ShopSellGuard` (**new**, pure) | Per-player in-flight and minimum-gap guard (6). |
| `SellStates` (**new**, pure, in `SellItemMenu`'s file or its own) | `static State of(boolean sellable, long carried, long quoteTotal)` -> `OK`, `PARTIAL`, `NONE`, `NOT_SELLABLE`, `TOO_CHEAP` (5.4). |
| `ShopCatalog` | `Category` gets `iconKey()` = `"shop/" + id.replace('-', '_')`; `slot()` stays parsed but is no longer used for layout (comment). Nothing else; **`ShopCatalogMigration`, prices, items untouched**. |
| `ShopSettings` | Two new keys (5.7). |
| `ShopEngine`, `ShopStore`, `UndoLedger`, `BigBuyGuard`, `ShopMath`, `InventorySpace`, `ShopCatalogMigration` | **Unchanged** (add the tiny pure `ShopMath.sellAmounts(int maxStack)` = `quickAmounts` alias only if the tests prefer it; otherwise reuse `quickAmounts`). |
| `common/.../sell/SellService` | Two default methods (4.1). |
| `common/.../sell/SellTake` (**new**, pure) | Slot-take plan (4.2). |
| `modules/economy/.../sell/SellFeature`, `SellServiceImpl` | Implement the seam; single-item report line (4.3). |
| `modules/economy/src/main/resources/messages/economy.yml` + tracked copy | Two new action-bar texts (4.3). |
| `modules/shop/src/main/resources/messages/shop.yml` + tracked copy | Section 5.6. |
| `modules/menus/src/main/resources/help.yml` | The `shop` help topic text updated to the new click map and categories. |
| `docs/commands/shop.md`, `CHANGELOG.md`, `docs/design/SHOP_DESIGN.md` header | Section 8. |
Removed from `ShopModule`: `lastTab`, `rememberTab`, `tabButton`, `tabName` (-> `categoryName`), `tabDescription` (-> `categoryLore`), `decorateBar`.

## 4. Seam in common/economy (do first inside this build)

### 4.1 `common/src/main/java/net/skybornmc/skyblock/common/sell/SellService.java` - add
```
/** Sellable items of this kind (same item, unique/flagged stacks excluded) in the storage slots 0-35 (hotbar included, never armor/offhand). Main thread. */
default long carried(Player player, ItemStack template) { return 0; }

/**
 * Sells up to {@code amount} (0 = all) of {@code template} from the player's storage slots 0-35 through the same
 * escrow, one ledger transaction (reason sell, key sell:<sale id>), give-back and /box rules as /sell. {@code done} runs on
 * the main thread: the {@link SellResult}, or {@code null} when nothing was attempted (loading, frozen, switching, busy,
 * not sellable, none carried). Main thread.
 */
default void sellItem(Player player, ItemStack template, int amount, String source, java.util.function.Consumer<SellResult> done) { done.accept(null); }
```
`SellServiceImpl`: add two hooks like `chestOpener` (`itemSeller(...)`, `carriedCounter(...)`) set by `SellFeature` and delegate (`carried` returns 0 when unset, `sellItem` calls `done.accept(null)`).

### 4.2 `common/.../sell/SellTake` (pure; used by `SellFeature`, unit-tested)
`static int[] plan(int[] slotAmounts, long want)`: given the amounts of the matching stacks in slot order, returns how many to take from each: `want <= 0` = take everything; otherwise take from the lowest slot first until `want` is reached (partial last stack); `want` above the total takes everything; never negative, never above a slot's amount.

### 4.3 `modules/economy/.../sell/SellFeature.java`
- `long carried(Player, ItemStack template)`: for slots `0..STORAGE_SLOTS-1` (36), count stacks with `stack.isSimilar(template) && service.sellable(stack)`.
- `void sellItem(Player player, ItemStack template, int amount, String source, Consumer<SellResult> done)` (exactly this order):
  1. `!ready || module.frozen(player) || profiles().isSwitching(uuid)`: same messages/sound as `sell(...)` (`sell.loading|frozen|switching`), `done(null)`.
  2. `!service.sellable(template)`: error sound, `done(null)` (no chat line; the tile already says Not sellable).
  3. `!selling.add(uuid)`: `sell.busy`, `done(null)`.
  4. On the main thread build the list of matching slots (same test as `carried`), run `SellTake.plan(...)`, remove exactly those amounts from the inventory (**escrow**: a fully taken slot becomes empty, a partly taken slot keeps the rest), collect the taken copies.
  5. Nothing taken: `selling.remove(uuid)`, error sound, `done(null)`.
  6. Otherwise call the existing private `pay(player, taken, source, done)` (it saves player data, builds the `SellRequest` with `bonusPercent(player)` and `Actor.player`, calls `service.sell`, gives back what was not sold or everything on failure through `giveBack` -> inventory then `/box`, reports, then `done`).
- `report(...)`: for `SOLD` with **exactly one quote line** use the new action-bar texts `sell.sold-bar-item` / `sell.sold-bar-item-bonus` (below) with `<amount>` (items), `<item>` (`keyName(line.price().itemKey())`), `<amount-money>` is `amount` as today; everything else (big-sale chat line + hover breakdown, sound `money`, `some-unsold`) unchanged. This also improves `/sell hand`.
- `SellServiceImpl` wiring in `SellFeature#enable`: `service.itemSeller(this::sellItem); service.carriedCounter(this::carried);`.
- Messages (economy.yml next to `sold-bar`): `sold-bar-item: "<c:success><sym:check></c:success> <c:text>Sold <c:value><items>x <item></c:value> for <amount>"`, `sold-bar-item-bonus: "... for <amount> <c:muted>(<bonus>% bonus)"`. Tutorial/quests/tasks listeners read `ItemsSoldEvent#source`: the new source `shop` is not in any exclude list by default (the coder re-checks `excludedSell` in progression/quests defaults and the tutorial `steps.sell.sources`, which is empty = any), so shop sales count like `/sell`.

## 5. Shop module

### 5.1 `ShopCategoryMenu` (new)
- `rows()` = `Centered.layout(n).rows()` (n = categories in the catalogue; 9 -> 4). `title()`: `service.title(viewer, text("menu.title"))` = `Shop`.
- `build()`: `navBar()` (Back 27 when `parent() != null`, Close 31); slot 4 summary: balance tile (icon `currency/money`, name `<area:money><bold>Your balance`, lore the amount in money colour + muted "Items go straight to your inventory."); category tiles in `Centered.layout(n).slots()` order: icon `module.categoryIcon(viewer, c)` (catalogue key `c.iconKey()` when present, else the vanilla `c.icon()` item for owner-added categories), name `categories.<id>.name` (bold `<area:money>`), lore the existing `categories.<id>.lore` sentence (two short grey lines) then `» CLICK to open`; click = `openChild(new ShopMenu(module, c.id()))`. Spawners category always shown (its grid says "No spawners right now" when `spawners.enabled` is false, as built).
- Bottom row actions: slot **28** (`MenuLayout.actionA(4)`) Sell chest button (`module.sellButton(viewer)`, icon `shop/sell`, opens `/sell` chest with Back to this menu), slot **29** Undo (`module.undoButton(viewer)`). On 3 rows these are 19 and 20.
- `tickInterval()` 20: refreshes the Undo tile and the balance; no other work.
- Empty catalogue: `setEmpty(ID, "menu.empty")`.

### 5.2 `ShopMenu` (item grid)
- `extends PagedMenu<ShopCatalog.Item>` (6 rows, 28 per page, left-aligned fill, `navBar()`, paper page arrows with the target page as count, title suffix `(2/3)`). Title crumbs: `Shop`, category name.
- `decorate()`: slot 4 balance summary (same tile as 5.1); slot 46 Sell chest button; slot 47 Undo. No other buttons.
- `entry(item)`: `MenuButton.of(tile, click -> openBuy(player, item))` `.withRight(click -> openSell(player, item))` `.withShift(click -> shiftBuy(click, item))` `.withMiddle(click -> sellAll(player, item))`. Routing is the P2 `ClickRoute`: LEFT = plain, RIGHT = right, SHIFT_LEFT = shift, **SHIFT_RIGHT and MIDDLE = middle**, Bedrock = plain only.
  - `openBuy`: `module.refuse(player, item)` check, then `openChild(new BuyMenu(module, item, categoryId))` (as built).
  - `openSell`: if the item cannot be sold (`SellInfo.sellable()` false) -> error sound only; else `openChild(new SellItemMenu(module, item, categoryId))`.
  - `shiftBuy`: unchanged (Java Shift + Left buys one stack, big buys arm and need a second shift-click with the action-bar notice).
  - `sellAll`: `module.sellAll(player, item, this::afterSell)` (section 5.8).
- `tick()` (20): `BigBuyGuard` expiry as built and the Undo tile refresh (no full rebuild); `afterSell`/`afterBuy` refresh the grid (carried counts, balance).
- `emptyKey()`/`entries()` unchanged (Spawners off message, disabled items hidden).

### 5.3 `BuyMenu` (3 rows)
- Slot 4: the item tile (icon of the item, name bold, lore = `ShopTooltips.buyTile` = the grid tooltip's price lines **without** click hints, plus `Balance`, `You can afford`, `Room for`, non-stackable note). Choices on row 1 through `Centered.columns(n)` + 9 where the choices in order are: `Buy q` for each of `ShopMath.quickAmounts(maxStack)`, `Fill inventory` only when `maxStack > 1`, `Type an amount`. Typical stackable item: 5 choices -> slots 9, 11, 13, 15, 17; non-stackable: `Buy 1` + `Type` -> slots 12, 14.
- Buy buttons (as built logic): lime concrete with icon `state/confirm` (count = amount, max 64) when `ok`/`partial`, `GRAY_CONCRETE` when no room/too expensive, `ORANGE_CONCRETE` + glint when armed (second click). Fill = icon `shop/fill` (chest base) or the grey "can't" dye as built. Type = `NAME_TAG` as built (armed typed amount shows on it as built).
- Bottom row: Back 18 (to the grid), **19 `Sell`** (icon `shop/sell`; only when `SellService` exists; opens the sell GUI of this item **replacing this menu**: `SellItemMenu` gets `parent(parent())` so Back goes to the grid, never ping-pongs), **20 Undo** (`module.undoButton`, hidden when the player turned undo off), Close 22.
- `tickInterval()` 10 as built (armed expiry and typed reset; Undo refresh every second).
- `title()`: `Shop » <item>` (unchanged text, 2 parts).
- Everything about payment (`module.buy`, `ShopEngine`, one ledger transaction `shop_buy` key `shop_buy:<purchase id>`, partial buys, `/box` overflow, delivery rows) is unchanged.

### 5.4 `SellItemMenu` (new, 3 rows)
- `SellInfo` (record built by `module.sellInfo(player, item)` on the main thread): `sellable` (SellService present, item not a spawner, `sell.sellable(template)`), `price` (`Optional<SellPrice>` from `SellService#price(item.id())`), `carried` (`SellService#carried`).
- Slot 4 tile: the item, name bold, lore: `| Sell Price: $x each` (red bar format) or `| Sell Price: Not sellable`, the live note (5.5), `| You carry: N`, and `| You get: $total` for **all you carry** from `SellService#quote(stacks, SellService#bonusPercent(player))` (stacks built with `ShopMath.stacks(carried, maxStack)`), with a muted `incl. your +B% bonus` when the capped bonus is above 0.
- Choices on row 1 via `Centered`: `Sell q` for each of `ShopMath.quickAmounts(maxStack)` (icon `shop/sell`, base gold nugget, count = amount up to 64), `Sell all` (icon `shop/sell_all`, hopper; name shows the carried number), `Type an amount` (name tag; Java chat prompt `sell.ask`, **Bedrock `NumberPickerMenu`** with max = carried, min 1). Each button's lore: `You get $X` (quote of `min(q, carried)`), the amount actually sold when you carry fewer (`Only N: you sell N`), and `» CLICK to sell`.
- Button states (pure `SellStates.of(sellable, carried, quoteTotal)` -> `OK`, `PARTIAL`, `NONE` (carry 0), `NOT_SELLABLE`, `TOO_CHEAP` (total under $1)): NONE/NOT_SELLABLE/TOO_CHEAP are grey (`GRAY_DYE`, muted name) and clicking them only plays the error sound.
- Click on an OK/PARTIAL button: `module.sellAmount(player, item, q, after)`; `Sell all` = `sellAmount(..., 0, ...)`.
- Bottom row: Back 18 (grid), **19 `Buy`** (icon `shop/buy`, opens the buy GUI of the same item replacing this menu, same parent rule as above), Close 22. No Undo (no undo for sales, C4).
- `tickInterval()` 20: recompute `SellInfo` and quotes (price moves live); skipped while a sale is in flight.
- `title()`: `Sell » <item>` (messages `sell.title`).

### 5.5 `ShopTooltips`
Pure functions plus one builder `List<Component> grid(ShopModule, Player viewer, ShopCatalog.Item item, SellInfo info, boolean java, boolean openState)`:
- Java viewer lines in this order (empty sections dropped, one blank line before the action):
  1. `| Buy Price: $8 each` (bold green) + muted ` · Left-click`
  2. `| Sell Price: $2 each` (bold red) + muted ` · Right-click`, **or** `| Sell Price: Not sellable` with `Not sellable` in red
  3. live note (one line, orange `Falling`, green `Rising`/`Hot`), only when it moves
  4. spawner items only: the built `| Information` section (next works at X%, payback), nothing else
  5. muted hint (Java only, only when sellable): `Middle-click or Shift + Right-click: sell all`; muted hint: `Shift + Left-click: buy a stack`
  6. `» CLICK to buy` (bold action yellow, always last; omitted for locked/unavailable items, whose reason lines follow as built)
- **Bedrock viewer** (`module.bedrock(viewer)`, no suffixes, no hint lines): `| Buy Price: $8 each`, `| Sell Price: $2 each` / `Not sellable`, live note, `» CLICK to buy`.
- Built with `LoreBuilder` (`priceLine`, `section`, `hint`, `action`) and the labels in `messages/shop.yml` `tooltip.*`; dynamic values escaped. At most 8 lines for Java, 6 for Bedrock (test).
- `static String sellPriceText(double unit, LongFunction<String> money)`: `unit >= 1` -> `money.apply(Math.round(unit))`; below 1 -> `"$" + two decimals` (`Locale.ROOT`), e.g. `$0.40` (no more hidden sell line for cheap items).
- `static LiveNote liveNote(double percentFromBase, boolean hot, double minPercent)`: `HOT` when hot (carries the `hotFactor` percent), else `RISING` / `FALLING` when `abs(percent) >= minPercent`, else `NONE`; the percent is rounded to a whole number.
- `buyTile(...)`: the buy GUI item tile lore (price lines without click words, balance, afford, room).

### 5.6 `messages/shop.yml` (bundled + tracked copy) - changes (all English; keep every existing key not listed)
```
menu:
  title: "Shop"
  balance:
    name: "<area:money><bold>Your balance"
    lore: ["<cur:money><balance></cur:money>", "", "<c:muted>Items go straight to your inventory."]
  sell:                                   # the sell chest button (46/47 area, 28 on the category menu)
    name: "<area:money><bold>Sell chest"
    lore: ["<c:text>Put many items in and sell", "<c:text>them at live prices.", "", "<bold><c:action>» CLICK</c:action></bold> <c:text>to open"]
  # info, info-bedrock are DELETED (no ? button)
category:
  action: ["", "<bold><c:action>» CLICK</c:action></bold> <c:text>to open"]
categories:      # names become bold green; lore stays one short sentence in two lines (existing text)
  essentials: {name: "<area:money><bold>Essentials", lore: [...existing...]}   # same for all nine ids
tooltip:         # LoreBuilder labels ({price}, {pct}, {amount}, {total} are filled in)
  buy: "<bold><c:buy>| Buy Price: </c:buy><c:value>{price}</c:value></bold><c:muted> each</c:muted>"
  buy-java: "<bold><c:buy>| Buy Price: </c:buy><c:value>{price}</c:value></bold><c:muted> each · Left-click</c:muted>"
  sell: "<bold><c:sell>| Sell Price: </c:sell><c:value>{price}</c:value></bold><c:muted> each</c:muted>"
  sell-java: "<bold><c:sell>| Sell Price: </c:sell><c:value>{price}</c:value></bold><c:muted> each · Right-click</c:muted>"
  not-sellable: "<bold><c:sell>| Sell Price: </c:sell><c:error>Not sellable</c:error></bold>"
  falling: "<c:warning>Falling: -{pct}%</c:warning> <c:muted>from base price</c:muted>"
  rising: "<c:success>Rising: +{pct}%</c:success> <c:muted>from base price</c:muted>"
  hot: "<c:success>Hot item today: +{pct}%</c:success>"
  hint-sell-all: "Middle-click or Shift + Right-click: sell all"
  hint-stack: "Shift + Left-click: buy a stack"
  action-input: "CLICK"
  action: "to buy"
sell:
  title: "Sell"
  item:
    name: "<area:money><bold><item>"
    carry: "<c:bar>| </c:bar><c:text>You carry: <c:value><carried></c:value>"
    total: "<c:bar>| </c:bar><c:text>You get: <cur:money><total></cur:money>"
    bonus: "<c:muted>incl. your +<bonus>% sell bonus"
  button:
    sell:
      name: "<area:money><bold>Sell <amount>"
      lore: ["<c:text>You get <cur:money><total></cur:money>", "", "<bold><c:action>» CLICK</c:action></bold> <c:text>to sell"]
    partial: ["<c:warning>You only carry <carried>: you sell <carried>.", "<c:text>You get <cur:money><total></cur:money>", "", "<bold><c:action>» CLICK</c:action></bold> <c:text>to sell"]
    all:
      name: "<area:money><bold>Sell all <carried>"
      lore: ["<c:text>Every <c:value><item></c:value> <c:text>you carry.", "<c:text>You get <cur:money><total></cur:money>", "", "<bold><c:action>» CLICK</c:action></bold> <c:text>to sell all"]
    none: {name: "<c:muted>Sell <amount>", lore: ["<c:error><sym:cross> You don't carry any."]}
    not-sellable: {name: "<c:muted>Sell <amount>", lore: ["<c:error><sym:cross> Not sellable."]}
    too-cheap: {name: "<c:muted>Sell <amount>", lore: ["<c:error><sym:cross> Too little to sell (under $1)."]}
    type:
      name: "<c:value>Type an amount"
      lore: ["<c:text>Sell any amount you carry", "<c:text>(1 to <c:value><max></c:value><c:text>).", "", "<bold><c:action>» CLICK</c:action></bold> <c:text>to type"]
  ask: "<prefix><c:text>Type how many to sell (1 to <c:value><max></c:value><c:text>) or <c:value><cancel></c:value><c:text>. <c:muted>(<seconds>s)"
  invalid: "<prefix><c:error><sym:cross></c:error> <c:text>That's not an amount from 1 to <c:value><max></c:value><c:text>."
  picker-title: "Sell <item>"
  none-carried: "<c:error><sym:cross></c:error> <c:text>You don't carry any <c:value><item></c:value><c:text>."
buy:
  switch-sell:
    name: "<area:money><bold>Sell"
    lore: ["<c:text>Sell this item instead.", "", "<bold><c:action>» CLICK</c:action></bold> <c:text>to open"]
sell-switch-buy:   # button on the sell GUI that goes back to buying
  name: "<area:money><bold>Buy"
  lore: ["<c:text>Buy this item instead.", "", "<bold><c:action>» CLICK</c:action></bold> <c:text>to open"]
```
Also: `buy.info*` keys deleted; `undo.idle` name stays (now shown on `state/off`/gray dye) and `undo.ready` lore as built; `lore.stack`/`lore.afford` keys stay (used by the buy tile); `entry.*` keys stay. `ShopMessagesTest` is extended for the new keys.

### 5.7 `shop-settings.yml` (typed `ShopSettings`) - new keys
| Key | Default | Range | Meaning |
|---|---|---|---|
| `sell.min-gap-ms` | `400` | 100..5000 | minimum time between two shop sales of one player (spam guard, section 6) |
| `sell.note-min-percent` | `2` | 0..100 | the live price note shows only when the sell price is at least this far from base |
No other key, no price key. The key `big-buy.*`, `undo.*`, `buy.*`, `spawners.*`, `safety.*` stay.

### 5.8 `ShopModule` changes
- `registerCommand`: argument renamed `category` (same `Arg.word` with `Suggest.of(this::categoryIds)` = **tab completion of the nine ids**, aliases from `tabOf` still resolve), `/shop` -> category menu, `/shop <category>` -> grid. Description "Buy and sell blocks, decoration, spawners and more". Permission `skyblock.shop.use` unchanged; **no new node**.
- `open(Player, Category)`: `problem(player)` check as built; no category -> `ShopCategoryMenu` (its parent = the menu the player has open, e.g. the hub, else none); with category -> a `ShopCategoryMenu` instance as the grid's explicit parent (`grid.parent(categoryMenu)`; Navigation rule 2: a code-set parent wins), then `core.menus().open(player, grid)`.
- New helpers: `categoryIcon(viewer, c)`, `categoryName`, `categoryLore`, `SellInfo sellInfo(Player, Item)`, `boolean bedrock(Player)` (exists), `void sellAll(Player, Item, Runnable after)`, `void sellAmount(Player, Item, int amount /*0 = all*/, Runnable after)`, `void switchTo(Menu from, Menu next)` (sets `next.parent(from.parent())`, then `open`).
- `sellAll` / `sellAmount` (the only code that sells; main thread):
  1. `problem(player)` present -> `fail(...)`, done. `SanctionService.denied(core.services(), player, Use.SHOP)`: check the same sanction gate `buy` uses (frozen balance) -> return.
  2. `SellInfo info`: not sellable -> error sound only, done. `info.carried() == 0` -> action bar `sell.none-carried` + error sound, done.
  3. `ShopSellGuard.tryStart(uuid, now, settings.sellMinGapMs)` false -> return silently (click spam).
  4. `sell.sellItem(player, template, amount, "shop", result -> { guard.finish(uuid, now); after.run(); })`. The economy prints the sale line (action bar, or chat from the big-sale threshold) and plays `money`; the shop prints nothing more.
- `undoButton`: ready = `CLOCK` via `icon(viewer, "state/undo", ...)` with the seconds as count; idle = `GRAY_DYE` via `state/off` with the existing `undo.idle` texts (I7: the undo icon is the clock; the old pressure plates go).
- `sellButton`: icon `shop/sell` (gold nugget), opens the sell chest (`openSell` as built).
- `isShopMenu(Menu)`: also true for `ShopCategoryMenu` and `SellItemMenu` (profile switch and disable close them); `refreshUndo` refreshes `ShopCategoryMenu`, `ShopMenu`, `BuyMenu`.
- `onQuit`: `sellGuard.forget(uuid)`.

## 6. Click map, atomicity and duplicate-click guards (spelled out)
| Input (Java) | Result | Notes |
|---|---|---|
| Left | buy GUI | refused items (`refuse`) explain why |
| Right | sell GUI (only if sellable; else error sound) | |
| Middle (creative only sends it) | sell all of that item | |
| Shift + Right | sell all of that item | because survival clients do not send middle click; the build checks both on the test server |
| Shift + Left | buy one stack | big buy arms first (second shift-click) |
| anything else (number keys, drop, double click, offhand) | ignored + client resync | framework |
| Bedrock tap | always the plain action (buy GUI); Sell and Sell all through the GUI buttons | |
- **Atomicity:** buying: unchanged (`ShopEngine`, one guarded ledger transaction, purchase row in the same DB transaction, items handed after payment, overflow to `/box`). Selling: items are removed on the main thread (escrow) **before** the ledger call; one transaction `sell` key `sell:<sale id>` writes payment, `sell_sales` row and audit; failure/too-cheap/overflow returns every stack inventory first then `/box`, never the ground.
- **Guards, in layers:** (1) framework debounce `click-debounce-ms` (120); (2) `ShopSellGuard`: one sale in flight per player and `sell.min-gap-ms` (400) between shop sales, released in `done`; (3) economy `selling` set (`sell.busy`); (4) the escrow itself: a second middle click after the first finds nothing carried and only gets the `none-carried` line; (5) buying keeps `buying` (`buy.busy`) and `BigBuyGuard`. Double middle click, middle then Shift+Right, and spamming the sell GUI can never sell items twice or pay twice (ledger key unique per sale, items already gone).
- **No shared-value tricks:** a sale pays the profile that holds the items (`ProfileService#profileKey`); switching profile while a GUI is open closes it (existing `onProfileSwitch`). Frozen balance blocks both buy and sell (`SanctionService`).
- Buy-then-sell cannot gain: unchanged `BuySellInvariantTest` (shop price above x7.5 of the best sell price); a shop sale uses exactly `SellService`.

## 7. Tests to write (names; the coder runs them only if the owner allows)
| Test | Module | What |
|---|---|---|
| `ShopTooltipsTest` | shop | `sellPriceText` whole money from $1, two decimals below; `liveNote` threshold, hot beats rising/falling, rounding; Java lines contain the click suffixes and both hints and end with the action; Bedrock lines contain none of them; at most 8 / 6 lines; `Not sellable` line when no price; spawner keeps the Information section |
| `ShopClickMapTest` | shop | with all four handlers present: LEFT -> PLAIN, RIGHT -> RIGHT, SHIFT_LEFT -> SHIFT, SHIFT_RIGHT -> MIDDLE, MIDDLE -> MIDDLE; Bedrock every type -> PLAIN; NUMBER_KEY/DROP -> IGNORED (uses `ClickRoute`) |
| `ShopLayoutTest` | shop | category menu: 9 -> 4 rows, slots 9,11,13,15,17,19,21,23,25; 8 -> 3 rows; buy/sell GUI choice slots: 5 choices -> 9,11,13,15,17; 3 -> 11,13,15; 2 -> 12,14; bottom row Back 18, actions 19/20, Close 22, summary 4; grid slots equal `PagedMenu.CONTENT_SLOTS`; no search slot used |
| `SellStatesTest` | shop | `SellStates.of(...)` table: sellable+carry>=q = OK, carry<q = PARTIAL, carry 0 = NONE, not sellable = NOT_SELLABLE, total<1 = TOO_CHEAP |
| `ShopSellGuardTest` | shop | in-flight blocks, gap blocks, `finish` releases, `forget`, gap 0 allowed |
| `ShopMessagesTest` (existing, extended) | shop | every literal key used exists in `messages/shop.yml` incl. the new `tooltip.*`, `sell.*` keys |
| `NoShopSearchTest` | shop | source scan: no `SPYGLASS`, no `nav/search`, no `askSearch`, no search settings in the shop module |
| `ShopCatalogTest`, `BuySellInvariantTest` (existing) | shop | must stay green unchanged (proof that prices/items/tabs did not change: 9 tabs, 253 items, counts per tab) |
| `SellTakeTest` | common | exact partial take, all (`want 0`), want above total, empty, order by slot, never exceeds a slot |
| `SellServiceSeamTest` | common/economy | default `sellItem` calls `done(null)` and `carried` returns 0 (no service = harmless) |
| `ShopCommandCompletionTest` | shop | `categoryIds()` contains the nine ids; the old v1 names still resolve through `tabOf` |
Existing tests that must be updated: `ShopV2Test` (no change expected), `ShopMessagesTest` (dynamic keys list: `categories.*.name/lore` still exist), any test referencing `ShopMenu.SLOT_*`.

## 8. Docs, config sync
- `docs/commands/shop.md`: rewrite "Pages" (category menu, grid, buy GUI, sell GUI), the click table of section 6, "Sell button = sell chest", Undo icon, `/shop [category]` completion; keep the F14, catalog and safety sections; add the new settings keys.
- `modules/menus/.../help.yml` `shop` topic: new click map in plain words (left buy, right sell, middle or Shift + Right sell all, Bedrock buttons).
- `CHANGELOG.md`: "Shop: category menu, buy GUI and sell GUI (left buy, right sell, middle sell all), tooltips with sell price and live note; sells use the /sell rules."
- `python3 tools/sync_server_configs.py`; commit tracked copies of `messages/shop.yml`, `messages/economy.yml`, `help.yml`.

## 9. Manual test checklist (owner; Java and Bedrock)
Java (test server, owner permission):
1. `/shop`: 4-row category menu, nine icons 5 + 4 centred, balance on slot 4, Back/Close, Sell chest 28, Undo 29. `/shop wood` opens the Wood grid; Back goes to the category menu.
2. Grid: 7 wide, left-aligned, no tab row, balance on 4, Sell 46, Undo 47, Back 45, Close 49, page arrows only when needed (Colors has 2 pages). Tooltips: Buy price with `Left-click`, Sell price with `Right-click` or red `Not sellable`, live note when a price moved, muted middle/shift hints, yellow `» CLICK to buy` last. Prices and items identical to before (spot-check five).
3. Left click -> buy GUI (3 rows): Buy 1 / 16 / 64, Fill inventory, Type an amount; buying works; items arrive; Undo (slot 20) refunds within 15 s; a buy over half your money turns orange and needs a second click.
4. Right click on a sellable item (e.g. coal or wheat if in the sell table) -> sell GUI: price, You carry, You get; Sell 1/16/64, Sell all, Type an amount; selling pays the same as `/sell`, line `Sold 16x ... for $...` in the action bar; Back goes to the grid; the `Buy` button switches without stacking Back.
5. Middle click (creative) and **Shift + Right-click (survival)** on a sellable tile: sells everything carried (hotbar included, armor/offhand untouched); one line; again at once: only "You don't carry any ...". Spam-click 20 times: one sale only.
6. Right/middle on a `Not sellable` tile and on a spawner: only the error sound, no menu, no message.
7. Shift + Left on a tile buys a stack; big one arms first.
8. Full inventory, frozen account (staff), profile switch with the shop open: same refusals/closing as before; nothing lost (items to `/box` on a failed sale).
9. `/shop <tab>` tab completion lists the nine ids; old names (`utility`) still open a category; `/menu` Shop button opens the category menu with Back to the hub.
10. Tutorial sell step completes with a shop sale.
Bedrock (phone): category menu taps; grid tap = buy GUI; buy GUI `Sell` button opens the sell GUI; `Sell all` works; `Type an amount` opens the tap picker; tooltips show no Java hints; nothing needs right or middle click.

## 10. Risks and rollback
| Risk | Mitigation |
|---|---|
| Accidental Shift + Right-click sells a big stack | I9/C4: no confirm, no undo; the tooltip says it; question 5 |
| Survival client may send Shift + Right differently on some versions | both Middle and Shift + Right route to sell all; test on Java 1.21.5 and 26.3 |
| `SellService` default methods hide a missing economy module | shop checks `core.services().optional(SellService.class)`; tiles then say Not sellable |
| Removing `lastTab` changes habits | decided D1 |
| Tooltip 8 lines on Java | accepted for the price tile (I9 limit of 6 is for menu buttons); Bedrock 6 |
Rollback: `git revert` the P3 commits (P1/P2 stay valid; the old 9-tab shop returns). No data change anywhere: no migration, no tables, ledger reasons and audit rows unchanged (`shop_buy`, `shop_undo`, `sell`; sale `source` value `shop` is detail data).

## 11. Size estimate
**L** (4 new shop classes, 3 changed, 2 common, 2 economy, 3 message files, about 11 test classes). 2-3 working days.

## 12. Questions the coder cannot settle (safe default applies)
1. Tooltip form: this spec composes I9's `| Buy Price` / `| Sell Price` bars with a muted `Left-click` / `Right-click` suffix for Java instead of MC Hub's separate `BUY (Left-Click)` headings (keeps the locked I9 format, fewer lines). Default as specified; the owner may ask for the headings.
2. Keep `Type an amount` although the owner wrote "no typing": default keep (optional extra, Bedrock uses taps).
3. "Confirm" = the C5 second click for big buys, no confirm page: default as I9.
4. Live note says `from base price` instead of `today` (data limit). Default as D9.
5. Should a middle-click sale above some value ask for a second click like big buys? Default no (I9, C4: no confirm, no undo); a future `sell.all-confirm-above` key is not built.
6. All category names are green (`<area:money>`, they are all "Shop"); distinct colours would exceed the 5-colour screen limit.

# Build spec CREDIT-SHOP: finish the credit shop, buy credits on Tebex, one purchase path

Written 1 Nov 2026 by the `planner` for the `coder`. Real code checked read-only in `../skyblock-wt-tutfix` (branch `q/gui-phase2`, deployed: the credits wave 1a/1b is in it: `Currency.CREDITS` and `CREDITS_EARNED`, `CreditSpend`, `CreditView`, `EconomyService.spendCredits` and `creditLegs`, `/credit bal`, `TxReason.STORE_GRANT`). Cards: **C1 (Locked, v5)** is the base; the shop card I10 and the order card C17 do not exist as files (NEW-CARDS.md), so this spec is their build text. Owner words (1 Nov): "work on finishing the credit shop; add buycraft/Tebex to buy credits; set up the credit shop; everything called gems is credits". Ranks come from spec `RANKS-V2.md` (group ids, prices, perks). The shard block is `shard_block` from the ECON spec (another planner).

## 0. STATUS
- C1 is **Locked**; this spec follows it and changes **two** wording points of C1 (both written to the ledger, section 14): the store grant command is `/credit grant` (C1 named `/storegrant`; a hidden top-level alias `storegrant` keeps the C1 text true) and its amount comes from a package table, not from the typed command.
- The rank part depends on `RANKS-V2.md` (F2 is not Locked yet). Build order: **RANKS-V2 first**, then this spec. The Tebex and `/credit grant` part (sections 5 and 6) does not depend on ranks and may ship first.
- Rule of the wave: **no tests are run and no server is started by the coder**; `./gradlew compileJava compileTestJava` only. Test files are written (names in section 11).
- Size: **L** (about 3 to 4 days). Branch `q/credit-shop`. Commits: (1) `feat(economy): store grant, chargeback and purchases table` (2) `feat(creditshop): module, orders and entitlements` (3) `feat(creditshop): shop menus and rank purchase` (4) `feat(creditshop): shard block and cosmetic entries` (5) `docs(creditshop): commands, Tebex setup, changelog`.

## 1. Goal and assumptions
Goal: a player opens `/credit shop`, sees their credits, buys a donor rank (or an item) with **one** confirm, and gets it at once; the owner sells credits on Tebex and every payment turns into credits exactly once; staff can see, revoke and charge back.

Assumptions (checked in code):
1. Credits belong to the player (all profiles). A spend is `CreditSpend.execute(economy, tx, guard)`: earned first, then bought, **one** DB transaction with the caller's guard (the guard may run twice, deadlock retry), keys `key` and `key:2` (at most 94 characters). `CreditSpend.refund(economy, depositTemplate, spendKey)` gives back the same halves. `economy.creditLegs(key)` returns what a spend took per half.
2. Existing callers spend credits directly: profile slots (`ProfileServiceImpl.buySlot`), Black Market charters (`Purchases.buy`), wardrobe credit sets. **They stay as they are in this build** (they work and are idempotent); migrating them behind `CreditShop.buy()` is the C1-R2-08 follow-up (listed in section 12). The new source-scan test only covers the new module.
3. `/credit` is registered in the economy module (`EconomyModule.registerCredit`, only `bal` today). `/store` (and alias `/buy`) is a link command in `SocialModule.link("store", ..., "buy")` that prints the Tebex URL (`social.yml storeUrl = https://store.skybornmc.net`) with a Bedrock popup. C1-R1-36 keeps that.
4. `/menu` -> You has a `store` button (`menu.yml you.store`, `command: "store"`) and a `credits` button (`credit bal`). G9-R1-03: the shop is reached through You, no main-page button.
5. Menus: `Menu`, `ConfirmMenu` (middle tile = details, one-shot, Bedrock form through `BedrockForms`), `NumberPickerMenu(title, min, max, initial, steps, onConfirm)`, `Centered.columns(count)`, `MenuLayout`; panels P3/P4/P6 from `docs/FORMAT.md` (`I9 v3`); pack layers are optional and invisible to this build (plain layer only; titles are plain crumbs, `title.short` keys are written for the later plaque).
6. Console commands run from Tebex arrive through the Tebex plugin as **console** commands. Tebex variables (docs.tebex.io, "An Introduction To Commands"): `{id}` game ID (UUID for Java, XUID for Bedrock), `{username}`, `{transaction}`, `{packageId}`, `{price}`, `{currency}`; command types: Initial, Expiry, Renewal, **Chargeback**, **Refund**. Commands run 1 to 3 minutes after payment, may be resent by the Tebex panel (so the grant must be idempotent).

## 2. Decisions (owner / dev)
| # | Decision | Source |
|---|---|---|
| S1 | One in-game shop: `/credit shop`, `/creditshop`, `/store shop`. `/store` and `/buy` print the Tebex link **and** a clickable `[Open the credit shop]`. Reachable from `/menu` -> You -> Store (the `store` button now opens the shop; the shop has a "Buy Credits" button that runs `/store`) | C1-R1-30, C1-R1-36, G9-R1-03; the lead's "/store alias" is read as `/store shop` + the You button because C1-R1-36 (owner) keeps `/store` as the link |
| S2 | Categories at launch: **Ranks** (7 donor ranks) and **Items** (Shard Block). **Cosmetics** appears as soon as one cosmetic entry has a price. Island perks, Boosters, Convenience and Pass are **hidden** (not built: `/is upgrade` M5.4, `island_perks` table, boosters pricing C15, pass F9). A category with no available entry is never shown, "coming soon" is never shown (owner G2 / principle 0) | C1-R2-16 + owner "hide it" |
| S3 | One purchase path `CreditShop.buy(...)`, one `credit_orders` table, one `entitlements` table, one delivery retry loop | C1-R2-08, C1-R2-09, C17 |
| S4 | Every credit spend in the shop goes through `ConfirmMenu` (Bedrock form), no exception | C1-R1-14 |
| S5 | A rank costs `max(0, target total - owned total)`; only ranks **bought in this shop** (entitlement rows) count as owned; the menu spells out the sum; credits only; a rank is permanent and works on every profile; a lower or equal rank is refused ("Included in <rank>") | C1-R2-15, F2 |
| S6 | Items arrive **bound to the profile** when any earned credit paid for them; items paid only with bought credits arrive **unclaimed (tradeable)**. The right-click claim is a **stub**: an unclaimed item simply stays tradeable | C1-R2-29, C1-R2-09 (claim stubbed, see 12) |
| S7 | Delivery rule for items: **never lost**; in the rare crash window at most doubled once (audited). Rank grants are idempotent by order id and retried until they work | C1-R2-08 (stackable items cannot be voided by uuid) |
| S8 | Store grants: console only, idempotent by Tebex transaction id, amount from a **package table in config** (never typed), ceiling per grant, ledger reason `store_grant`, one `store_purchases` row, no per-purchase alert, **failure** alerts staff | C1-R1-17, C1-R1-18 |
| S9 | Chargeback/refund from Tebex: take back the **bought** credits that are left (never below 0), mark the purchase, tell staff, list the player's orders with a clickable `[Revoke]`; no automatic ban, no automatic rank removal (staff review) | C1-R1-35 (order picker replaced by a click list; staff-friendly), C1-R2-18 |
| S10 | `/credit hold` is **not built** (needs a freeze check in every credit path; the existing sanction check on spends already blocks frozen players) | lead: "only if trivial": it is not |
| S11 | Shard Block price default **0 = not for sale (hidden)**: the price is owner/ECON/C15 (owner: "exact prices later"); rank prices are decided (RANKS-V2 D4) | owner C4-Q2, EP-Q1 note |

## 3. What the player sees
- `/credit shop` (aliases `/creditshop`, `/store shop`), `/menu` -> You -> Store:
  - **Hub (P4, plaque `SHOP`)**: slot 4 wallet tile `Your Credits` (diamond, `<cur:credits>`, total exact like `12,500`; for the owner of the balance two muted rows `| Bought: 2,000` and `| Earned: 10,500`), categories centred on row 1 (Ranks, Items, and Cosmetics when it has an entry), nav row: Back 27 (You), `Buy Credits` 28 (runs `/store`: link line plus the Bedrock popup), Close 31.
  - **Ranks (P3, plaque `RANKS`)**: slot 4 wallet, the 7 ranks on row 1 columns 1 to 7 in price order. Tile (name in the rank's own look colour, at most 6 lines of 36 characters, last line the action):
    ```
    Firewing
    Firewing Steve »» Hello!           (the real badge + white name + gray arrows: the preview)
    Permanent. Every profile.
    /ec, /nick, +1 profile, 9 AH slots  (RankPerks highlights, from perks.yml)
    | Cost: 2,000 (you own Hatchling)   or  | Cost: 700
    » CLICK to buy                       or  ✔ You own this  /  Included in Firewing
    ```
    Base items (one per tile, none repeated): egg, feather, blaze_rod, fire_charge, sunflower, magma_cream, totem_of_undying (Hatchling to Immortal). The owned rank has a glint.
  - **Items (P3, plaque `ITEMS`)**: slot 4 wallet, entries centred by `Centered.columns(count)`. Shard Block tile: `Shard Block` (name in the shard colour), "Break it to get a shard.", "Right-click it to upgrade it.", `| Cost: 150 each` (the configured price), `» CLICK to buy`. A click opens `NumberPickerMenu` (1 to `max-per-order`, steps 1/4/16), then the confirm.
  - **Cosmetics**: same screen; tags and titles from `creditshop.yml`.
  - **Confirm (ConfirmMenu, P3)**: crumb `Buy Firewing?`; the middle tile lists: the item, `Permanent. Every profile.`, `Price: 2,700`, `You own Hatchling: -700`, `| You pay: 2,000`, `Earned credits are spent first.`; Confirm / Cancel. A rank for a player with no island is fine (account perk). After confirm: chat line `PHOENIX » ✔ You bought Firewing for 2,000 credits. 3,100 left.`, sound `success`, and for ranks a broadcast `[Store] Steve is now Firewing!` (config `broadcast.ranks`, true).
  - Refusals say the fix: not enough credits shows the missing amount and the `Buy Credits` pointer; "You already own a higher rank".
- `/credit history`: opens the `/transactions` menu with the credit ledgers only (both halves). Rows show reason `credit_shop` (reference `shop:<item>`), `store_grant`, `vote`, ...; hover shows the order number.
- `/credit bal` unchanged (exists).
- Staff: `/creditshop revoke <order number> [refund]`, and a chargeback listing (section 6).

## 4. Data (new tables, H2 and MariaDB 11.8 valid, `IF NOT EXISTS`, never edit applied migrations)
**4.1 creditshop module, `modules/creditshop/src/main/resources/db/creditshop/V001__credit_shop.sql`**
```sql
-- Credit shop (card C1/C17, build spec CREDIT-SHOP). Times in epoch milliseconds.
-- One row per purchase. State: debited (paid, not delivered), delivered, void (revoked by staff).
-- once_key makes "one rank per player" a database fact: rank orders use 'rank:<group>', everything else the order id;
-- a voided order gets ':void:<order_no>' appended so the player can buy again.
CREATE TABLE IF NOT EXISTS credit_orders (
  order_no BIGINT NOT NULL AUTO_INCREMENT,
  order_id BINARY(16) NOT NULL,
  player_uuid BINARY(16) NOT NULL,
  profile_key BINARY(16) NOT NULL,
  item_id VARCHAR(48) NOT NULL,
  kind VARCHAR(8) NOT NULL,
  quantity INT NOT NULL,
  price BIGINT NOT NULL,
  earned_paid BIGINT NOT NULL DEFAULT 0,
  bought_paid BIGINT NOT NULL DEFAULT 0,
  once_key VARCHAR(80) NOT NULL,
  state VARCHAR(10) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (order_no),
  CONSTRAINT uq_credit_orders_id UNIQUE (order_id),
  CONSTRAINT uq_credit_orders_once UNIQUE (player_uuid, once_key)
);
CREATE INDEX IF NOT EXISTS idx_credit_orders_state ON credit_orders (state, created_at);
CREATE INDEX IF NOT EXISTS idx_credit_orders_player ON credit_orders (player_uuid, order_no);

-- What the player now holds because of an order (C17). type: rank | item | tag | title. state: unclaimed (a tradeable item
-- paid only with bought credits) | claimed (account entitlement, or an item bound at delivery) | void.
CREATE TABLE IF NOT EXISTS entitlements (
  entitlement_id BINARY(16) NOT NULL,
  player_uuid BINARY(16) NOT NULL,
  type VARCHAR(8) NOT NULL,
  ref VARCHAR(48) NOT NULL,
  quantity INT NOT NULL,
  state VARCHAR(10) NOT NULL,
  order_id BINARY(16) NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (entitlement_id)
);
CREATE INDEX IF NOT EXISTS idx_entitlements_player ON entitlements (player_uuid, type, state);
CREATE INDEX IF NOT EXISTS idx_entitlements_order ON entitlements (order_id);
```
Both inserts happen in the **guard** of the spend (same transaction as the debit). The guard checks `once_key` first (`SELECT 1 ... WHERE player_uuid=? AND once_key=?`): found = return false (DUPLICATE, nothing charged). **Idempotency key of the spend**: `shop:<orderId>` (a new random orderId per confirmed click; replays of the same id are refused as DUPLICATE; 41 characters, the `:2` suffix fits).
**4.2 economy module, `modules/economy/src/main/resources/db/economy/V010__store_purchases.sql`** (next free number after V009)
```sql
-- Credits sold on Tebex (card C1, store grant). One row per Tebex transaction and package; the primary key is the receipt.
CREATE TABLE IF NOT EXISTS store_purchases (
  tx_id VARCHAR(64) NOT NULL,
  package_id VARCHAR(32) NOT NULL,
  player_uuid BINARY(16) NOT NULL,
  credits BIGINT NOT NULL,
  usd_cents INT NOT NULL,
  state VARCHAR(12) NOT NULL,
  taken BIGINT NOT NULL DEFAULT 0,
  granted_at BIGINT NOT NULL,
  closed_at BIGINT NULL,
  PRIMARY KEY (tx_id, package_id)
);
CREATE INDEX IF NOT EXISTS idx_store_purchases_player ON store_purchases (player_uuid, granted_at);
```
State: `granted`, `chargeback`, `refunded`.

## 5. Tebex: granting credits (module economy)
**5.1 Config** (`economy.yml`, `EconomyConfig`; version bump with the new keys only):
| Key | Default | Meaning |
|---|---|---|
| `credit-store.packages` | `["c500=500|5", "c1000=1000|10", "c2700=2700|25", "c14000=14000|130"]` | package id `=` credits `|` USD (C1-R2-28: 500=$5, 1,000=$10, 2,700=$25, 14,000=$130). Parsed like `RankLooks`; a bad line is skipped with a warning; ids `[a-z0-9_]{1,32}` |
| `credit-store.max-grant` | `20000` | refuse any package above it (typo guard) |
| `credit-store.alert-failures` | `true` | staff alert + console on a failed grant |
Add `store_grant` and `store_chargeback` to `economy-watch.yml earnings.ignore-reasons` (check the key in `EconomyWatchConfig`/`WatchJob`: reasons that are not earnings/spending of a profile) so a purchase never raises an earn-rate or big-transfer alert (C1-R1-18: no per-purchase alert).
**5.2 Command** `/credit grant <player> <package> <txid>` (`EconomyModule.registerCredit`, sub `grant`; plus a hidden top-level `storegrant` with the same handler, same arguments).
- Allowed only for the **console** (`sender instanceof ConsoleCommandSender`); anything else is refused and audited `economy / store_grant_refused`. Node `skyblock.credit.grant` (`default: false`, declared in `economy.yml`) is **never granted**: `server/permissions/economy.txt` adds `lp group admin permission set skyblock.credit.grant false` and `lp group owner permission set skyblock.credit.grant false` (exact false beats the owner's `*`; the owner inherits admin so the owner needs its own line, same trick as `skyblock.economy.credits.grant`).
- `<player>`: UUID with or without dashes (Tebex `{id}` for Java), a 12 to 16 digit decimal XUID (Bedrock: `new UUID(0, xuid)`, the Floodgate UUID), or a name known to the server (existing `resolve(name)`). Unknown name = refused (Tebex shows the command as failed, the panel can resend). A UUID that never joined is accepted (the account is created by the ledger).
- Steps: parse; package lookup (`credit-store.packages`; unknown = refused with the list of ids); `credits > max-grant` = refused; then **one** call `economy.executeGuarded(Transaction.deposit(AccountRef.player(uuid), Currency.CREDITS, credits, TxReason.STORE_GRANT).actor(Actor.system("tebex")).reference("tebex:" + txid).idempotencyKey("tebex:" + txid + ":" + package).detail("package", package).detail("usd", usd), guard)`, guard = `SELECT 1 FROM store_purchases WHERE tx_id=? AND package_id=?` (exists = `return false`) then `INSERT` the row (state `granted`). `Currency.byCanonicalKey("credits")` (never an alias). Result: SUCCESS = log `[Store] <player> +<credits> (<package>, tx <txid>)`, audit `economy / store_grant` (details txid, package, usd, balance after), if online: chat `Thank you! +2,700 credits.` + sound `success`; **DUPLICATE = "already granted" log line, no change** (a resend by Tebex is safe); ERROR/others = console WARNING + staff alert (permission `skyblock.staff.economy.anomalies`, text names player, package, txid) + Discord channel `alerts` when a webhook exists.
- Tab completion: `<player>` offline players plus the hint `<uuid>`, `<package>` = the configured ids, `<txid>` = the hint `<transaction>`.
- No 'gg' bonus, no thanks credits in this build (C1/F11 later).
**5.3 Chargeback and refund** `/credit chargeback <player> <txid>` (also used for Tebex Refund commands; permission `skyblock.credit.chargeback`, declared in `economy.yml`, granted to `admin` in `economy.txt`; the console always works; tab: `<player>` offline players, `<txid>` = the recent transaction ids from an in-memory list of the last 200 `store_purchases` loaded at enable and updated on grant).
- Finds the rows of that player and txid in state `granted`; none = "nothing to charge back" (replay safe). For each row: take `min(credits, bought balance)` with `economy.executeGuarded(Transaction.withdraw(player, CREDITS, taken, "store_chargeback")..., key "tebex-cb:<txid>:<package>", guard updating the row to `chargeback`, `taken`, `closed_at`)`. Credits spent already are **not** clawed back from anyone else (C1-R1-35); the unpaid part is `owed = credits - taken`.
- Then fires `StoreChargebackEvent(playerUuid, txid, taken, owed)` (new, `common/economy/`, main thread). The creditshop module listens: it sends staff (`skyblock.creditshop.revoke`) a chat block: `[Store] Chargeback: Steve, tx 123, took 500 of 2,700 (2,200 owed)`, `Steve's orders:` one line each order `#41 Firewing 2,000 (rank) [Revoke]` (click runs `/creditshop revoke 41`), `second chargeback for this player` when `store_purchases` already holds one, and writes audit `economy / chargeback`. **No automatic ban and no automatic rank removal** (staff decide; the owner asked for easy to use).

## 6. The shop module (new `modules/creditshop`)
**6.1 Gradle.** `modules/creditshop/build.gradle.kts` (copy `modules/blackmarket/build.gradle.kts`: `implementation(project(":common"))`, H2 and slf4j for tests), `settings.gradle.kts` `include("modules:creditshop")`, `plugin/build.gradle.kts` `implementation(project(":modules:creditshop"))`, `SkyblockPlugin#registerModules` `modules.register(new net.skybornmc.skyblock.creditshop.CreditShopModule(core));`. Module id `creditshop`, `dependencies()` = `Set.of("economy")` (soft in code: the module refuses to open when `EconomyService` is absent).
**6.2 Classes** (package `net.skybornmc.skyblock.creditshop`, `modules/creditshop/src/main/java/net/skybornmc/skyblock/creditshop/`):
| Class | Job |
|---|---|
| `CreditShopModule` | `SkyblockModule`; loads `creditshop.yml`, registers `/creditshop`, publishes `CreditShop` (below), starts the delivery retry task, listens to `PlayerJoinEvent` (retry that player's `debited` orders) and `StoreChargebackEvent`; `disable()` stops the task |
| `CreditShopConfig` | `ModuleConfig` for `creditshop.yml` (section 7) |
| `Catalogue` | builds the entries: ranks from `ranks.*` (hidden when `core.ranks().available()` is false or the group is missing from `core.ranks().groupIds()`; warning once), items and cosmetics from `catalogue.items` lines; validates item ids against `ItemRegistry`, tag/title ids against `Cosmetics.ids(kind)`; price 0 or an invalid line = hidden |
| `ShopRules` (pure) | `rankPrice(target, ownedGroups, prices)` = `max(0, total(target) - max(total(owned)))`; `canBuyRank` (not owned, not lower or equal); `categoriesVisible(entries)`; the line parser |
| `CreditShop` (interface in `common/economy/CreditShop.java`) | published service: `void open(Player)`, `void open(Player, String category)`; `CreditShop.of(services)`. Used by `/credit shop`, `/store shop` and the You button |
| `CreditShopImpl` | `open(...)` and `buy(Player, Plan, Runnable after)` — **the only caller of `CreditSpend.execute` in the module** |
| `Plan` (record) | item id, kind, quantity, price, once key, rank group; recomputed from fresh state at confirm time (price or ownership changed = refused with `buy.changed`) |
| `OrderStore` | all SQL (async through `core.database()`): `insertOrder` + `insertEntitlement` (used inside the guard), `ownedRanks(uuid)`, `markDelivered`, `markVoid`, `debitedOrders(limit)`, `bumpAttempts`, `byNo`, `ordersOf(uuid)`, `legs update` |
| `Delivery` | rank: `core.ranks().addGroup(uuid, group)`; item: build with `core.items().create(ref, qty, "credit_shop", null)`, mark bound when `earned_paid > 0` (`BoundItems.mark(stack, profileKey)`), hand out with `ItemBox.give(...)` (online on that profile) or `ItemBox.deliver(profileKey, ...)` (offline or another profile), then `PlayerSaves.save(player, "credit_shop")`, then `markDelivered`; tag/title: `Cosmetics.grant(uuid, kind, ref, "credit_shop")`; the retry loop runs every `delivery.retry-seconds`, at start and at join |
| `HubMenu`, `RanksMenu`, `ItemsMenu` (+ cosmetics through `ItemsMenu`) | `Menu` subclasses P4/P3/P3 (section 3), slots from `Centered`, wallet tile slot 4, Back/Close fixed slots |
| `ShopCommands` | `/creditshop`, `/creditshop revoke <order> [refund]` |
**6.3 Buy flow (`CreditShopImpl.buy`).** (1) fresh `ShopRules` check (ownership from `OrderStore.ownedRanks`, price, rank still configured, frozen/sanction check is inside `spendCredits`); (2) per-player `inFlight` set (double click: `buy.busy`), minimum `rate.min-seconds-between-orders` (2); (3) `orderId = UUID.randomUUID()`, `tx = Transaction.withdraw(AccountRef.player(player.getUniqueId()), Currency.CREDITS, price, "credit_shop").actor(Actor.player(player)).reference("shop:" + itemId).idempotencyKey("shop:" + orderId).detail("item", itemId).detail("order", orderId.toString())` and `guard` = `once_key` check + `insertOrder(state 'debited')` + `insertEntitlement` (the `once_key` unique constraint is the second line of defence if two servers ever run); (4) `CreditSpend.execute(economy, tx, guard)`; (5) SUCCESS: `creditLegs("shop:" + orderId)` -> update `earned_paid`/`bought_paid`, then `Delivery` as above, audit `creditshop / buy` (item, price, split, order no, profile), chat, sound, broadcast for ranks; INSUFFICIENT_FUNDS: `buy.no-credits` with the missing amount; DUPLICATE: `buy.changed`/"you already own it"; anything else: `buy.failed` (nothing was charged: the debit and the order row are one transaction). (6) A delivery that fails leaves the order `debited`; the player is told `buy.pending` ("Your purchase is safe, it will arrive in a minute"); after `delivery.max-retries` (20) a critical staff alert names the order number (the money is already taken; staff fix by hand or `/creditshop revoke <no> refund`).
**6.4 Revoke** `/creditshop revoke <order number> [refund]`: permission `skyblock.creditshop.revoke` (declared in `creditshop.yml`, granted to `admin` in `server/permissions/creditshop.txt`; owner inherits). Tab: order numbers from an in-memory list of the last 100 orders, then `refund`. Effects in one DB transaction: order `void`, entitlement `void`, `once_key` gets `:void:<order_no>`; then rank: `core.ranks().removeGroup(uuid, group)` (spec RANKS-V2 A4); tag/title: `Cosmetics.revoke`; item: the staff chat says "item already handed out: take it from the player by hand (inspect)". `refund`: `CreditSpend.refund(economy, Transaction.deposit(AccountRef.player(uuid), Currency.CREDITS, price, "credit_shop_refund").reference("shop-refund:" + no).idempotencyKey("shop-refund:" + no), "shop:" + orderId)` (same halves as paid). Audit `creditshop / revoke`. Add `credit_shop_refund` to the ledger reasons next to `TxReason.STORE_GRANT` (`common/economy/TxReason.java`): `CREDIT_SHOP = "credit_shop"`, `CREDIT_SHOP_REFUND = "credit_shop_refund"`, `STORE_CHARGEBACK = "store_chargeback"`.

## 7. Config and messages
**`creditshop.yml`** (module config, `/skyblock reload creditshop`):
| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | shop on/off (off: `/credit shop` says it is closed) |
| `ranks.order` | `[hatchling, fledgling, firewing, flamecrest, sunfeather, pyrelord, immortal]` | group ids in price order |
| `ranks.prices` | `["hatchling=700", "fledgling=1400", "firewing=2700", "flamecrest=4000", "sunfeather=6000", "pyrelord=9000", "immortal=13500"]` | rank **total** price in credits (RANKS-V2 D4, owner EP-Q12, round numbers); upgrade price = difference |
| `ranks.icons` | `["hatchling=egg", "fledgling=feather", "firewing=blaze_rod", "flamecrest=fire_charge", "sunfeather=sunflower", "pyrelord=magma_cream", "immortal=totem_of_undying"]` | base items (vanilla ids existing in 1.21.5) |
| `catalogue.items` | `["shard_block=item\|shard_block\|0\|16\|items"]` | `id=type\|ref\|price\|max-per-order\|category`; type `item` (custom item id from `ItemRegistry`), `tag`, `title`; **price 0 = hidden** (shard price pending, S11); category `items` or `cosmetics` |
| `catalogue.icons` | `["shard_block=prismarine_shard"]` | display base item for items without a custom icon |
| `delivery.retry-seconds` | `60` | retry loop |
| `delivery.max-retries` | `20` | then a critical staff alert |
| `rate.min-seconds-between-orders` | `2` | per player |
| `broadcast.ranks` | `true` | `[Store] Steve is now Firewing!` |
**`messages/creditshop.yml`** (`modules/creditshop/src/main/resources/messages/creditshop.yml`; style tokens only, no hex, English, `<prefix>` on chat lines). Required keys with the English text:
```
hub.title: "Credit Shop"                     # plain crumb;  title.short: "SHOP"
ranks.title: "Ranks"                          # title.short: "RANKS"
items.title: "Items"                          # title.short: "ITEMS"
wallet.name: "<cur:credits>Your Credits</cur:credits>"
wallet.total: "<c:value><total>"
wallet.split: ["<c:bar>|</c:bar> <c:text>Bought: <c:value><bought>", "<c:bar>|</c:bar> <c:text>Earned: <c:value><earned>"]
category.ranks: {name: "<area:reward>Ranks</area:reward>", lore: ["<c:text>Permanent. Every profile."]}  # + "» CLICK to open"
category.items: {name: "<area:money>Items</area:money>", lore: ["<c:text>Things you can hold."]}
category.cosmetics: {name: "<area:skills>Cosmetics</area:skills>", lore: ["<c:text>Tags and titles."]}
buy-credits: {name: "<area:money>Buy Credits</area:money>", lore: ["<c:text>Opens our store page."]}
rank.owned: "<c:success>✔</c:success> <c:text>You own this"
rank.included: "<c:muted>Included in <c:value><rank>"
rank.cost-full: "<c:bar>|</c:bar> <c:text>Cost: <c:value><price>"
rank.cost-upgrade: "<c:bar>|</c:bar> <c:text>Cost: <c:value><price> <c:muted>(you own <rank>)"
rank.permanent: "<c:text>Permanent. Every profile."
confirm.crumb: "Buy <item>?"
confirm.details: ["<c:text>Permanent. Every profile.", "<c:bar>|</c:bar> <c:text>Price: <c:value><total>", "<c:bar>|</c:bar> <c:text>You own <owned>: <c:value>-<discount>", "<c:bar>|</c:bar> <c:text>You pay: <c:value><price>", "<c:muted>Earned credits are spent first."]
buy.done: "<prefix><c:success><sym:check></c:success> <c:text>You bought <c:value><item></c:value><c:text> for <c:value><price></c:value><c:text> credits. <c:value><left></c:value><c:text> left."
buy.broadcast: "<tag:store><bold>[Store]</bold></tag:store> <c:value><player></c:value> <c:text>is now <rank><c:text>!"
buy.no-credits: "<prefix><c:error><sym:cross></c:error> <c:text>You need <c:value><missing></c:value><c:text> more credits. Use <c:action>Buy Credits</c:action> in the shop."
buy.owned: "<prefix><c:error><sym:cross></c:error> <c:text>You already own <c:value><rank></c:value><c:text> or a higher rank."
buy.changed: "<prefix><c:error><sym:cross></c:error> <c:text>The price or your rank changed. Open the shop again."
buy.busy: "<prefix><c:muted>One moment..."
buy.failed: "<prefix><c:error><sym:cross></c:error> <c:text>Something went wrong. Nothing was charged, try again."
buy.pending: "<prefix><c:warning>●</c:warning> <c:text>Your purchase is safe. It arrives in a minute."
closed: "<prefix><c:error><sym:cross></c:error> <c:text>The credit shop is closed right now."
```
(`<tag:store>` and `<cur:credits>` exist in `style.yml`.) Economy messages (`modules/economy/src/main/resources/messages/economy.yml`): `credit.grant.ok` ("`[Store] <player> +<credits> credits (<package>, tx <tx>)`"), `credit.grant.duplicate` ("Already granted: tx <tx> (<package>). Nothing changed."), `credit.grant.unknown-package` ("Unknown package <package>. Known: <list>."), `credit.grant.console-only` ("This command is for the console (Tebex)."), `credit.grant.failed`, `credit.grant.thanks` (player: "`Thank you! +<credits> credits.`"), `credit.chargeback.done`, `credit.chargeback.none`, `credit.alert.failed-grant`. Staff texts for the chargeback block live in `creditshop.yml` `chargeback.*`.

## 8. Permissions and files (CLAUDE.md "Adding a module")
- Declared in `modules/economy/src/main/resources/permissions/economy.yml`: `skyblock.credit.shop` (`default: true`), `skyblock.credit.history` (`true`), `skyblock.credit.grant` (`false`, "console only, never granted"), `skyblock.credit.chargeback` (`false`).
- Declared in `modules/creditshop/src/main/resources/permissions/creditshop.yml`: `skyblock.creditshop.revoke` (`false`, admin, revoke an order and see chargeback lists).
- Grants: `server/permissions/economy.txt` (admin: `skyblock.credit.chargeback true`; admin and owner: `skyblock.credit.grant false`); new `server/permissions/creditshop.txt` (`lp group admin permission set skyblock.creditshop.revoke true`; header comment as the other files). `PermissionFilesTest` stays green (every granted `skyblock.*` node is declared, one owner each).
- Docs: `docs/commands/creditshop.md` (table Command, Description, Permission, Status) and `docs/commands/economy.md` (`/credit shop`, `/credit history`, `/credit grant`, `/credit chargeback`), then `./gradlew commandsIndex`.

## 9. Commands and tab completion
| Command | Behaviour | Tab completion |
|---|---|---|
| `/credit shop` | open the shop hub | none |
| `/creditshop` | same (module command) | none; second word `revoke` for staff |
| `/store shop` | `SocialModule.link(...)` gets one optional extra sub for `store`: `shop` calls `CreditShop.of(services).open(player)`; plain `/store` and `/buy` print the link plus `<click:run_command:'/credit shop'><c:action>[Open the credit shop]</c:action></click>` (message `links.store` in `social.yml`/`messages/social.yml`) | `shop` |
| `/credit history` | `/transactions` menu preset to both credit ledgers (`LedgerFilter` gets a `credits()` preset that matches `currency IN (credits, credits_earned)`; the currency picker gets one "Credits" entry; `EconomyStore` ledger query takes a currency set) | none |
| `/credit grant <player> <package> <txid>`, `/storegrant ...` | console only (section 5.2) | player (offline), package ids, hint `<transaction>` |
| `/credit chargeback <player> <txid>` | section 5.3 | player, recent tx ids |
| `/creditshop revoke <order> [refund]` | section 6.4 | order numbers, `refund` |
Help: add a `credits` topic to `modules/menus/src/main/resources/help.yml` (credits vs shards vs money in one line each, the same words as C1-R2-01; where to buy; "Ranks are permanent and work on every profile") and make `You -> Store` open the shop: `menu.yml you.store: {icon: feature/store, command: "credit shop", service: CreditShop}` (the existing default; also fix `MenuV3Migration` fallback comment). A `service: CreditShop` entry is **hidden** while the service is absent (module off), matching owner G2.

## 10. Mojang, safety and abuse
**Mojang warnings (logged; the owner accepts the risk, C1-R1-31):** (a) bought credits are tradeable in `/trade` (`tradeable.credits: true`) and a credit-bought item can be resold on the AH: a real-money to in-game-money path (C1 Rules). (b) The **Shard Block** sells a path to shards, which are tradeable: progress for money. (c) Ranks sell convenience only (RANKS-V2), but the weekly kit holds a little island fuel (minor progress). Before the store opens, the lead reads Mojang's current commercial usage guidelines (C1 hard blocker, I6 RTA-1); the shop shows no player-facing legal text. A buyer always has a receipt: the Tebex email plus the `store_purchases` row plus `/credit history`. Never sold: keys, money, crystals, shards (directly), levels, XP (C1-R2-06): see the closed set below.
- **Closed set in code** (not config): `Catalogue.ALLOWED_TYPES = {rank, item, tag, title}`; only `ItemType` MATERIAL, COLLECTOR, CONSUMABLE or COSMETIC items may be sold (`core.items().get(id)` and its definition's `ItemType`, checked on load); TOOL, ARMOR, SET_PIECE, CRATE_KEY, NOTE, PET, PET_EGG and SPAWNER entries are dropped with a warning (power, keys and money are never sold). The Shard Block is a placeable block: the ECON spec must register it as MATERIAL (or the coder adds its type to this list on the ECON owner's word).
- **Dupe and double-spend:** one transaction per purchase (debit + order + entitlement); unique `once_key` for ranks; unique `order_id`; unique ledger idempotency key; the Tebex grant is idempotent by `tebex:<tx>:<package>` AND the `store_purchases` primary key; the Tebex panel's "resend" and our retry never double-pay.
- **Frozen/sanctioned players:** `spendCredits` already checks sanctions for both halves; the shop shows the standard refusal.
- **Spam:** `inFlight` and `rate.min-seconds-between-orders`; confirm is one-shot.
- **Rank delivery stuck** (LuckPerms down, group missing): order stays `debited`, retried every 60 s and at join, critical alert after 20 tries; nothing is lost; staff can revoke with refund.
- **Chargeback after the credits were spent:** the debt is shown (`owed`), staff review; credits already spent on ranks stay until staff revoke (decision S9).
- **Season/profile deletes:** credits and entitlements are account data, never touched by profile delete or season reset (C1-R1-12b).

## 11. Tests to write (names only; written, not run; `compileTestJava` must pass)
- `modules/creditshop/.../ShopRulesTest`: `rankPriceIsTargetMinusOwned`, `ownedHighestWins`, `lowerOrEqualRankRefused`, `fullPriceWhenNothingOwned`, `priceZeroHidesEntry`, `catalogueLineParsing`, `badLinesAreSkipped`, `categoriesWithoutEntriesAreHidden`, `onlyAllowedTypes`.
- `.../OrderStoreTest` (H2): `orderAndEntitlementWriteTogether`, `onceKeyBlocksSecondRank`, `voidFreesOnceKey`, `orderNoIncreases`, `debitedOrdersListedForRetry`.
- `.../CreditShopBuyTest` (H2 with the real `EconomyStore` like `EconomyStoreTest`): `buyDebitsAndWritesOrderOnce`, `fiftyParallelBuysOfOneRankPayOnce`, `replayOfTheSameOrderIdIsDuplicate`, `insufficientFundsWritesNothing`, `earnedSpentFirstAndRecorded`, `guardFailureRollsBackTheDebit`, `redeliveryAfterCrashDeliversOnce`.
- `.../CreditShopSingleSpendPathTest` (source scan): only `CreditShopImpl` in `net.skybornmc.skyblock.creditshop` calls `CreditSpend.execute`, and only with reason `TxReason.CREDIT_SHOP`.
- `.../MenuLayoutTest`: wallet tile on slot 4, seven ranks on columns 1 to 7, Back and Close on the fixed slots of FORMAT section 3, no repeated base item on a screen, every tile line at most 36 characters and at most 6 lines with the action last.
- `modules/economy/.../StoreGrantTest` (H2): `grantWritesLedgerAndReceiptOnce`, `replayHundredTimesWritesOnce`, `unknownPackageRefused`, `aboveMaxGrantRefused`, `nonConsoleRefused`, `uuidXuidAndNameResolve`, `grantNeverUsesGemsAlias`, `chargebackTakesAtMostTheBoughtBalance`, `chargebackTwiceIsANoop`, `chargebackIgnoresEarnedCredits`.
- `plugin/.../PermissionFilesTest` (existing, stays green) plus new `CreditGrantNodeNeverGrantedTest` (no `server/permissions/*.txt` line sets `skyblock.credit.grant` to true).
- `modules/economy/.../CreditStoreConfigTest`: `defaultPackagesMatchC1` (500/5, 1000/10, 2700/25, 14000/130), `badPackageLineSkipped`.

## 12. What is stubbed or left out (nothing is shown as "coming soon")
| Item | Why | Where it lands |
|---|---|---|
| Island perks (+1 level, size, spawner cap), Boosters, Convenience slots, Pass | need `/is upgrade` M5.4, the `island_perks` table, booster prices (C15), pass F9 | cards B14, B15, F11, F9; the catalogue entry types are closed in code, new types are added with those cards |
| Prices of everything except ranks | owner: "exact prices later" (C4-Q2), C15 overhaul | `creditshop.yml` / `catalogue.items`, price 0 = hidden |
| Right-click **claim** of unclaimed items (C1-R2-09) | not needed for function; an unclaimed item stays tradeable | with the physical-items card (sell chest, chunk collector) |
| `/credit hold`, `/credit trail`, `/eco chargeback` menu, `/eco revert` | C1 staff tools; not trivial | C1 follow-up wave; `/credit chargeback` and `/creditshop revoke` cover the money case now |
| Migrating profile slots, Black Market charters and wardrobe sets behind `CreditShop.buy()` | they already spend safely; moving them needs their own tests | C1-R2-08 follow-up (the source-scan test then widens) |
| "gg after a store purchase" credit, thanks credits | F11 | F11 |
| Credit packs shown in game with USD | the store page owns prices; in game only a `Buy Credits` link | none |
| Discord store announcements | Discord is postponed (LATER.md) | I6 |

## 13. Tebex setup (what the OWNER does; the coder writes this as `docs/STORE_SETUP.md` and links it from `docs/commands/creditshop.md`)
1. **Webstore.** In the Tebex control panel create the store (domain `store.skybornmc.net`, currency **USD**). Game server type Minecraft: Java Edition. Read Mojang's commercial guidelines before publishing (C1 blocker); store page text says the store is not affiliated with Mojang.
2. **Four packages** (category "Credits"), names exactly as the in-game packs: `500 Credits` $5, `1,000 Credits` $10, `2,700 Credits` $25 (2,500 + 200 bonus), `14,000 Credits` $130. Package ids in the Tebex panel can be anything; the command text carries our package key.
3. **Commands per package** (Tebex panel -> Packages -> edit -> Commands). Initial command (run even if the player is offline, delay 0): `credit grant {id} c500 {transaction}`, `credit grant {id} c1000 {transaction}`, `credit grant {id} c2700 {transaction}`, `credit grant {id} c14000 {transaction}`. **Chargeback** and **Refund** command on each package: `credit chargeback {id} {transaction}`. No leading slash. `{id}` is the UUID for Java players and the XUID for Bedrock players (Tebex: "Game-specific primary ID").
4. **Tebex plugin on the server.** Put the Tebex plugin jar (Paper) in `plugins/`, restart, then in the console run once `tebex secret <SECRET>` with the key from Tebex panel -> Game Servers -> your server -> Secret key; check with `tebex info`. The key stays in `plugins/Tebex/` on the VPS (never in git). For the owner's own records the key may be listed in `/opt/skyblock/.env` as `TEBEX_SECRET=...` (the file `apply-secrets.sh` reads); **nothing in this build reads it** (the plugin keeps its own copy; the exact file format of the Tebex plugin was not verified, so no script writes it). **Compatibility of the Tebex plugin with Paper 26.3 is unverified**: if it refuses to load, the fallback is Tebex's Headless/command API (a separate small job, not in this spec); tell the lead before launch.
5. **Bedrock:** in Tebex enable the Bedrock username prefix so `{id}` arrives as the XUID; the grant command also accepts a name.
6. **Test with Tebex "Manual payment"** (never real money): create a manual payment for a test account for package `c500`, wait 1 to 3 minutes, check the console line `[Store] <player> +500 credits (c500, tx ...)`, `/credit bal` (500 bought), then `credit grant <player> c500 <same tx>` again from the console = "Already granted". Optional: `credit chargeback <player> <tx>` takes the 500 back. The ledger rows stay (they are the audit trail); use a test account.
7. **Alerts.** A failed grant (unknown package, database error) alerts staff and the console; a successful grant is silent. The economy watch thresholds for credits (C1-R1-10: 1,000 single, 10,000 per day, 1,000 repeated) keep watching transfers between players, not store grants.

## 14. Ledger lines (appended to `LEDGER.md` by the planner)
`CS-R1-01` to `CS-R1-06`: shop scope and hidden categories, `/credit grant` + package table (changes the name in C1), chargeback hook, order and entitlement tables, items bound when earned credits paid, shard block price 0 until priced.

## 15. Manual test checklist (Java + Bedrock; after the deploy, test accounts only)
Java: 1) `/menu` -> You -> Store opens the hub; wallet shows total, split only for yourself; `Buy Credits` prints the link; 2) Ranks: seven tiles with preview line and cost; owned rank glints; lower ranks say "Included in ..."; 3) buy Hatchling with credits you have (use `/eco give <test> credits_earned 3000 test` as an admin, or `credit grant` from the console for bought credits): confirm shows the sum, one confirm, chat line, `[Store]` broadcast, `lp user <test> parent info` lists `hatchling`, tab shows the new word; 4) buy Firewing: price 2,000, order #2, both ranks in `entitlements`; 5) buy a lower rank: refused; 6) not enough credits: message names the missing amount; 7) double click on Confirm: one order only (`/credit history` shows one row); 8) `/creditshop revoke <n> refund` removes the group and refunds the same halves; buying again works; 9) Items: Shard Block hidden while its price is 0; set `catalogue.items` price to a test number, `/skyblock reload creditshop`, the tile appears, quantity picker, item arrives (inventory or `/box`), bound when earned credits paid; 10) console `credit grant <test> c500 TEST-001` twice: +500 once, second "Already granted"; `credit chargeback <test> TEST-001` takes 500, staff chat lists the player's orders with `[Revoke]`; 11) `/credit grant` from a player (even the owner) is refused; 12) tab completion of every argument; 13) `/credit history` shows the grant, the buy and the refund. Bedrock phone: the whole shop is taps only, the confirm is a form, no typing (the quantity picker uses taps), tiles readable at 36 characters, `Buy Credits` opens the popup with the address.

## 16. Risks and rollback
| Risk | Level | Mitigation / rollback |
|---|---|---|
| Tebex plugin does not load on Paper 26.3 | **high for launch**, none for the server | test first (step 13.4); fallback headless API; no code path depends on the plugin |
| A wrong package line gives too many credits | medium | `max-grant`, package table in config, console only, audit; revoke with `/eco take` |
| Rank delivered twice / not at all | low | unique `once_key`, idempotent `addGroup`, retry loop, critical alert |
| Item doubled in the crash window | low | audited (`credit_order_redeliver`), decided S7 |
| Chargeback after credits were spent | medium | `owed` shown, staff revoke; ban stays manual |
| `/store` semantic confusion (link vs shop) | low | `/store` prints the link plus a click to the shop; both documented |
| Mojang commercial rules | policy | warnings in section 10; owner accepted; read terms before opening the store |
Rollback: `creditshop.yml enabled: false` (shop closed, `/credit grant` and chargeback still work), then remove the Tebex package commands; the tables stay (history, receipts). A bad release: `git revert` and redeploy; data in `credit_orders`, `entitlements`, `store_purchases` is only ever added, never changed by old code.

## 17. Questions the planner could not settle with a safe default (none blocks the build)
1. **Shard Block price** (and the ECON item id/shape): default hidden at price 0 until the owner/C15 sets `catalogue.items`.
2. **Tebex plugin compatibility with Paper 26.3** and its key file format: unverified, needs the owner's install test.
3. `/store` stays the link (C1-R1-36); the lead's "`/store` alias" is satisfied by `/store shop`, the You button and the click line. Say so if the owner wants `/store` itself to open the shop.
4. Whether a staff-gifted rank (LuckPerms by hand) should count as owned for the upgrade price: default **no** (only shop-bought ranks count; C1-R2-15 "owned permanent ranks").

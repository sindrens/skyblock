# Phase 3: Economy

**Goal:** a safe, transparent, dupe-proof economy with big numbers, a full transaction ledger staff can audit, banks with interest, dynamic selling, a builder shop, an auction house and secure trading.

Depends on Phases 0–2. Design: `DESIGN.md` §4, `CONTEXT.md` §3 (income meta: Depths ~50%, island ~35%, events ~15%; big numbers).

---

## M3.0 Balance sheet (before any prices are coded)
A spreadsheet/doc `docs/ECONOMY_BALANCE.md` with: target money per hour at each progression stage (first hour, day 1, week 1, month 1, month 3), sell values per item and tier, interest yields, upgrade cost curve (Phase 5 preview), and inflation checks (money created vs. removed per day at 300 players). **The owner approves it** before M3.4 prices go live. Targets (to confirm): first hour ≈ $50k earned; day 3 ≈ $5M; week 2 ≈ $500M; month 2 ≈ $50B for top players.

## M3.1 Currency core and ledger
- Currencies: **Money**, **Tokens**, **Gems**, **Shards** (`long`, whole units). Accounts for players and (M3.3) island banks.
- **Atomic transactions** in the database (`UPDATE … SET balance = balance - ? WHERE balance >= ?`), never read-modify-write in Java. Every change writes a **ledger row**: id, time, season, currency, from-account, to-account, amount, balance-after (both sides), **reason** (`pay`, `sell`, `shop_buy`, `ah_buy`, `ah_sale`, `trade`, `interest`, `bank_deposit`, `admin_give` …), reference id (auction id, trade id …).
- `EconomyService` for all modules (no Vault).
- `/bal [player]` (all currencies), `/baltop [currency]` (cached every 5 min, solo/team filter later), `/pay <player> <amount> [currency]` (suffixes 1.5k/2M/3B; confirm above a threshold; can't pay while frozen/muted? no: muted can pay; min amount; offline targets allowed).
- **Players' own history:** `/transactions` GUI (own ledger, filter by currency/reason/time).
- **Staff:** ledger is part of `/logs` (`/logs pay`, `/logs player <name> economy`); `/eco give|take|set|reset <player> <currency> <amount> <reason>` (owner/admin, reason required, logged, staff-notified).

## M3.2 Money notes
- `/withdraw <amount>` → **Money Note** item (lore: amount, signed by, date, unique id). Right-click redeems. Each note id is stored; redeeming marks it used, so a duplicated note can never be cashed twice (the copy is confiscated + staff alerted).

## M3.3 Banks and interest
- `/bank` GUI showing **Personal** and **Island** bank side by side.
- **Personal bank:** 10 tiers (capacity + perks per tier; perks list to approve: lower AH tax, extra AH listing slots, small sell bonus …). Deposit/withdraw with the number picker.
- **Island bank:** members deposit; withdraw requires the role permission; capacity grows with the Bank Capacity upgrade (Phase 5); every action logged and visible to the leader in `Island → Bank → History`.
- **Interest:** every 3 h, **also offline**, computed lazily from timestamps (no mass updates), with a **per-payout cap**; rates island 0.25% / personal 0.15% (tunable). Early-withdrawal rule: withdrawing before the next payout forfeits that cycle's interest + 2% fee (shown clearly before confirming). Next payout countdown in the menu.

## M3.4 Selling and dynamic prices
- Price table config: base price per item (vanilla + custom items), category, and whether it's sellable.
- **Global dynamic prices:** each sale lowers the price slightly (by volume), recovering towards base over time (2%/10 min), **floor 40% / ceiling 150%** of base; unsold items drift above base. Daily **Hot Item** (+X%).
- `/sell` GUI: drop items in → live total → confirm. `/sell hand`, `/sell all`. Shows price, trend arrow (▲▼) and % from base.
- Sell multipliers (rank, upgrades, boosters, sets) stack additively with a **max cap**.
- Every sale in the ledger (item, quantity, unit price, multiplier).

## M3.5 Builder shop (`/shop`)
- Categories: Building blocks, Wood, Stone & bricks, Glass & colors, Decoration, Redstone, Farming (seeds unlocked by Farming tier later), Utility. Buy ×1/×16/×64/custom; prices in config; every purchase in the ledger. Acts as a real money sink.

## M3.6 Auction house (`/ah`)
- List the held item: price, duration (12/24/48 h), listing fee (small %) + sales tax (%), max listings (by rank/bank tier).
- Browse: categories, search by name, sort (price, ending soon, newest), filters (currency later).
- Buy confirm → item to buyer (or to their **collection bin** if inventory is full), money to seller (minus tax) even if offline; expired/cancelled items to the seller's collection bin.
- **Dupe-safe:** item removed from the inventory and stored serialized in the DB in one transaction; purchase is a DB transaction that can't double-sell.
- Logged: `ah_list`, `ah_buy`, `ah_cancel`, `ah_expire`, with full item data.

## M3.7 Secure trade (`/trade`)
- `/trade <player>` request → two-sided GUI: each side places items + money; any change resets both "ready" states; both confirm → 3 s countdown → swap in one transaction.
- Distance limit (or same world), not while in combat/frozen, cancels on logout/teleport. Full trade (both sides' items and money) logged → `/logs trade`.

## M3.8 Economy dashboard (staff)
- `/eco admin` GUI: money supply per currency, money created/removed today by reason, top earners per hour, biggest transactions, interest paid, AH volume.
- **Anomaly alerts** to staff (and Discord later): a player earning >X× the normal rate per hour, huge single transfers, repeated note redemptions.

---

## UI mockup: `/bank` (6 rows)
```
Row 1  red frame · [Gold block: Personal bank $12.5M / $50M · Tier 4] · [Ender chest: Island bank $340M / $1B] · red frame
Row 2  [black] Deposit  Withdraw  Tier perks   |  Deposit  Withdraw  History [black]
Row 3  [black] Interest: 0.15% · next in 1h 12m  |  Interest: 0.25% · next in 1h 12m [black]
Row 6  standard navigation
```

## Data (tables)
`accounts` (owner type player/island, currency, balance), `ledger`, `money_notes`, `bank_tiers` (config), `prices` (config) + `price_state` (current multiplier per item), `sales`, `ah_listings`, `ah_collection`, `trades`.

## Release / test checklists (owner)
| Milestone | You check |
|---|---|
| M3.0 | Read and approve the balance sheet |
| M3.1 | `/pay` with 1.5k / 2M, `/bal`, `/baltop`, `/transactions`, `/logs pay` shows it |
| M3.2 | Withdraw a note, redeem it, try redeeming a copy (staff can spawn one) |
| M3.3 | Bank deposit/withdraw, interest countdown, early-withdrawal warning |
| M3.4–M3.5 | `/sell` totals and price trend, shop purchases |
| M3.6 | List, buy, expire, collection bin |
| M3.7 | Trade with an alt; change items after ready → resets; log shows both sides |
| M3.8 | Dashboard numbers move after your test trades |

## Open questions (defaults apply unless changed)
Decided (2026-10-29): AH listing fee 1% + sales tax 5%, 5 listings (more by rank); `/pay` asks to confirm above $1M; shards tradeable, gems not; start $1,000; `/transactions` for players. Tag items, tools, blueprints and skins can be sold on the AH.

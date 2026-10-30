# Economy (`modules/economy`)

Currencies: Money, Crystals (island crystals, replaced tokens; not tradeable), Gems, Shards (whole-unit `long`). Every change is one atomic database transaction with a ledger row (`eco_ledger`) and an audit row (category `economy`, action = reason), so `/logs pay <player> [other]` and `/logs player <name> economy` show it. Settings: `economy.yml`; texts: `messages/economy.yml`.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/bal [player]` (`/balance`, `/money`) | All four balances (yours, or another player's, online or offline) | `skyblock.economy.balance` (+ `skyblock.economy.balance.others`) | ✅ |
| `/baltop [currency]` (`/balancetop`, `/moneytop`) | Richest players per currency; shown from a cache at once, read again in the background when older than `baltop.refresh-seconds` (30) | `skyblock.economy.baltop` | ✅ |
| `/pay <player> <amount> [currency]` | Pay a player (offline too). Amounts like `1500`, `1.5k`, `2M`, `3B`. Asks to confirm at or above `pay.confirm-above` ($1M). Minimum `pay.min`; only tradeable currencies (money, shards); frozen players can't pay, muted players can; 3 s cooldown (`commands.yml`) | `skyblock.economy.pay` | ✅ |
| `/transactions` (`/txs`) | Your own ledger in a paged menu; filter by currency, type and time | `skyblock.economy.transactions` | ✅ |
| `/withdraw <amount> [currency]` | Turn money into a **Money Note** item (tradeable; right-click to cash it). Money only by default (`notes.currencies`). Min/max `notes.min`/`notes.max` ($100 / $1T), cooldown `notes.withdraw-cooldown-seconds` (10 s), needs a free inventory slot (checked before any money is taken), frozen players can't, new accounts (< `notes.new-account-minutes`, 120) can't | `skyblock.economy.withdraw` | ✅ |
| `/eco give <player> <currency> <amount> <reason>` | Staff: create currency for a player | `skyblock.owner.economy` | ✅ |
| `/eco take <player> <currency> <amount> <reason>` | Staff: remove currency (fails if they have less) | `skyblock.owner.economy` | ✅ |
| `/eco set <player> <currency> <amount> <reason>` | Staff: set an exact balance (`0` allowed); the ledger gets the difference | `skyblock.owner.economy` | ✅ |
| `/eco reset <player> <currency\|all> <reason>` | Staff: back to the starting balance (`start.*`) | `skyblock.owner.economy` | ✅ |
| `/eco note <player> <amount> <reason>` | Staff: give an **online** player a money note without taking money (offline → refused). Staff alert + audit `admin_note`; the money is created when the note is cashed (`note_redeem`) | `skyblock.owner.economy` | ✅ |

Other: `skyblock.staff.economy.alerts` (chat alert when staff use `/eco`; helpers and up).

**Money notes (M3.2).** Right-click a note (main hand) to cash it; no permission needed. Each note is a row in `money_notes` (id = the item's unique id); the amount comes from that row, never from the item. Cashing locks the row, marks it `redeemed` and deposits in one database transaction (idempotency key per note), so a note can never be cashed twice: a copy is taken from the hand, staff with `skyblock.staff.notify` get an alert, and an audit row `note_dupe` is written (`/logs`). Notes can't be crafted with, sold to villagers or used in a cartography table, and can't be listed on the auction house (`auction.yml → items.allow-money-notes: false`). Audit actions: `note_withdraw`, `note_redeem`, `admin_note`, `note_dupe`, `note_return_failed`.

Placeholders (cached, for the HUD and other plugins via PlaceholderAPI): `balance`, `balance_formatted`, `crystals`, `crystals_formatted`, `gems`, `gems_formatted`, `shards`, `shards_formatted` (`_formatted` = `12,500` / `1.2M`, no symbol).

For developers: `core.services().optional(EconomyService.class)` (`common/economy`): `execute(Transaction)` with reasons from `TxReason`, a `reference` and an `idempotencyKey` for anything that can be replayed. Never decide with the cached balance; run the transaction and handle `INSUFFICIENT_FUNDS`.

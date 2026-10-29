# Economy (`modules/economy`)

Currencies: Money, Tokens, Gems, Shards (whole-unit `long`). Every change is one atomic database transaction with a ledger row (`eco_ledger`) and an audit row (category `economy`, action = reason), so `/logs pay <player> [other]` and `/logs player <name> economy` show it. Settings: `economy.yml`; texts: `messages/economy.yml`.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/bal [player]` (`/balance`, `/money`) | All four balances (yours, or another player's, online or offline) | `skyblock.economy.balance` (+ `skyblock.economy.balance.others`) | ✅ |
| `/baltop [currency]` (`/balancetop`, `/moneytop`) | Richest players per currency; recalculated every `baltop.refresh-minutes` (5) | `skyblock.economy.baltop` | ✅ |
| `/pay <player> <amount> [currency]` | Pay a player (offline too). Amounts like `1500`, `1.5k`, `2M`, `3B`. Asks to confirm at or above `pay.confirm-above` ($1M). Minimum `pay.min`; only tradeable currencies (money, shards); frozen players can't pay, muted players can; 3 s cooldown (`commands.yml`) | `skyblock.economy.pay` | ✅ |
| `/transactions` (`/txs`) | Your own ledger in a paged menu; filter by currency, type and time | `skyblock.economy.transactions` | ✅ |
| `/eco give <player> <currency> <amount> <reason>` | Staff: create currency for a player | `skyblock.owner.economy` | ✅ |
| `/eco take <player> <currency> <amount> <reason>` | Staff: remove currency (fails if they have less) | `skyblock.owner.economy` | ✅ |
| `/eco set <player> <currency> <amount> <reason>` | Staff: set an exact balance (`0` allowed); the ledger gets the difference | `skyblock.owner.economy` | ✅ |
| `/eco reset <player> <currency\|all> <reason>` | Staff: back to the starting balance (`start.*`) | `skyblock.owner.economy` | ✅ |

Other: `skyblock.staff.economy.alerts` (chat alert when staff use `/eco`; helpers and up).

Placeholders (cached, for the HUD and other plugins via PlaceholderAPI): `balance`, `balance_formatted`, `tokens`, `tokens_formatted`, `gems`, `gems_formatted`, `shards`, `shards_formatted` (`_formatted` = `12,500` / `1.2M`, no symbol).

For developers: `core.services().optional(EconomyService.class)` (`common/economy`): `execute(Transaction)` with reasons from `TxReason`, a `reference` and an `idempotencyKey` for anything that can be replayed. Never decide with the cached balance; run the transaction and handle `INSUFFICIENT_FUNDS`.

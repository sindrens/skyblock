# Secure trade (`modules/trade`)

Two players trade items and money face to face. Settings: `trade.yml`; texts: `messages/trade.yml`. Every trade is logged (audit category `trade`: `trade_start`, `trade_complete` with **both item lists** (incl. the serialized stacks, so staff can restore them) and both money offers, `trade_cancel` with the returned items, `trade_deliver`, `trade_recover`); money moves are in the ledger as `trade` (`/transactions`, `/logs pay <player>`). Staff: `/logs trade <player>`.

**The window (GUI-BUGS, owner U7 trade safety):** ?, Close, Esc and Main menu never cancel a trade; it stays open while the window is closed and `/trade` goes back. It ends after `session.away-seconds` (60) away. Only **Cancel trade** (slot 53) ends it at once.

**Sanctions (Phase P7, spec 13.1):** `/trade <player>` and `/trade accept` are refused when either side's trading is blocked or balance is frozen (the other side is told without details). Money in a trade is also checked by the economy safety net.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/trade [player]` | Ask a player nearby to trade (they get a clickable request). If they already asked you, the trade starts. Without a name: reopens your open trade, or a menu of players nearby to pick from. A player who `/ignore`s you gets no request (you see "requests off"; owner answer 316) | `skyblock.trade.use` | ✅ |
| `/trade accept [player]` | Accept a trade request (the name is only needed when several players asked) | `skyblock.trade.use` | ✅ |
| `/trade deny [player]` | Deny a trade request | `skyblock.trade.use` | ✅ |

**The trade window** (6 rows, taps only, works on Bedrock): your side on the left, theirs on the right. Tap a stack in your own inventory to offer it (up to 12 stacks), tap your offered item to take it back, tap the gold ingot to offer money (number picker; a currency menu first when more than one currency can be traded), tap **Confirm**. When both confirmed, a short countdown runs (3 s); **any change by either side clears both confirmations** and stops the countdown. Then the swap happens in one go. Closing the window or **Cancel** ends the trade and every item goes back.

**Value hints (owner R3-38):** every offered stack shows a `| Value (this stack)` section: what the server pays for it (`/sell` price), the `/shop` price, and the **auction median** only when at least 5 different players sold and 5 different players bought it in the last 14 days (`value-hints.*`); sales between the same person or two accounts that share an address never count. Items with neither server price (spawners, books, tools, sets ...) show **Value unknown**. The median is read in the background (cached 10 min). **Money (owner F1):** money can only be offered after the profile earned $10k itself (`economy.yml → send-gate`), unless the partner is a member of its island; lowering an offer always works. Crystals can never be offered (R3-5b).

Rules (all in `trade.yml`): request and accept within 16 blocks in the same world (`distance.max-blocks`), the trade ends when the players get more than 24 blocks apart (`distance.cancel-blocks`) or after 10 minutes; requests expire after 60 s, 5 s between requests. The trade ends on logout, damage, teleport, a profile switch, a freeze or staff mode; no requests while frozen, in staff mode or within 10 s of combat (`combat.seconds`). Players can turn trade requests off in `/settings` (Social → Trade requests).

Money: currencies in `money.currencies` that `economy.yml` also marks tradeable (default money and shards). **Crystals are never tradeable** (CR3), earned credits never; bought credits only if `economy.yml → tradeable.credits` (default off; credits move with `/credit pay`, card C1). No new-account limit (owner answer 131). Money goes between **profile** accounts.

What can be offered: anything except locked wardrobe armor (sets are bound to the profile; unredeemed Set Tokens are fine), menu items, dupe-flagged items, staff items (`/give`, `/i`, creative; owner answer 332, also inside containers), admin blocks and filled shulker boxes/bundles (`items.*`). Starter kit pieces are tradeable here (PHASE-4: trade yes, auction house no). Money notes, enchant books, Disenchant Scrolls, Set Tokens, tools, blueprints and tag items are fine (they are unique and tracked; their ids stay the same).

Other permissions: none.

## Dupe safety (for developers)
- An offered item **leaves the inventory** and is written to `trade_escrow` (state `held`) in a transaction that first locks the trade row and checks it is still `open`. Taking it back is a conditional `held → pending` update.
- **The swap is one database transaction** (`EconomyService.executeBatch`): every money leg (one ledger row each, idempotency key `trade:<id>:<a|b>:<currency>`) plus the item switch (`TradeStore.completeGuard`: the trade must be open and hold exactly the rows the window shows; every held row gets the other side as owner and becomes `pending`; the session becomes `completed`). If anything fails (money short, a row differs), nothing changes.
- Items are delivered from `pending` rows, each exactly once (`pending → delivered`), only into the inventory of the profile they belong to, never while switching profile or in staff mode; otherwise they wait (delivered on join, after a profile switch and every `tasks.delivery-seconds`). Items that don't fit stay waiting.
- **Crash recovery:** on startup every trade still `open` is cancelled (all held items back to whoever put them in). While running, trades open in the database but not in memory for 2 minutes are cancelled the same way. On a normal shutdown open trades are cancelled before the database closes.
- P4: a trade between two profiles of the same player is refused (`ProfileService.blockOwnTransfer`, path `trade`, audited `profile_transfer_blocked`).

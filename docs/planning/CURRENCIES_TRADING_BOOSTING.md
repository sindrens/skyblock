# Currencies, trading and boosting

**Status:** needs owner input

You want players to trade "everything", but nobody should be able to **boost**. This file lays out the options and our pick.

## What you already decided
- **660:** "yes but needs planned since i kinda want everything, so people cant trade currencies but we need it so people wont be boosting, we need a solution".
- **666:** notes for **all** currency types; tradeable by dropping them to players, by `/trade`, and wagered in `/duels` "and more".
- **Principle 2:** never a way to move value between your own profiles.
- **121 / 121b:** every profile gets start money; deleting a profile loses its money.
- **CR3 (round 3):** crystals not tradeable (Suggested earlier; 666 now asks for all types).
- **574 / 575:** gems hidden from others; no gem list in `/baltop`.
- **656 / TUT-4:** `/pay` and the auction house unlock at the tutorial finish.
- **573:** `/pay` has no daily limit, alerts only for huge or repeated transfers.
- **P10-6:** keys from votes, AFK, calendar, playtime and rank claims are bound; keys from quests, pass, events and fragments are tradeable.
- **1031:** up to 3 accounts per IP earn in the AFK pool.

**The review warned (REVIEW3):**
- **R3-5:** gem notes turn real-money gems into in-game money (pay-to-win, Mojang rule risk). Wagering paid gems is gambling. Crystal notes break CR3 and the enchant balance.
- **R3-1:** start money + delete/recreate + `/pay` = an endless alt money printer.
- **R3-2:** bound keys still give tradeable loot.
- **R3-3:** 3 AFK earners per IP triples free keys.

## What exists in the game now
| Currency | Earned by | `/pay` | Notes | `/trade` | AH |
|---|---|---|---|---|---|
| Money | selling, quests, interest, events | yes | yes | yes | is the AH currency |
| Shards | bosses, envoys, KOTH, Depths tier 9+ | yes | no | yes | no |
| Crystals | custom tools, island missions | no | no | no | no |
| Gems | store credits, voting (3 per vote), "gg" | no | no | no | no |
| Credits | store (Tebex) | no | no | no | no |

Notes today: money only, $100 to $1T, unique database row each, bearer item.

## The problem / what to decide
First: what is "boosting"? We think it means three things:
1. **Alt feeding:** extra accounts vote, AFK, collect start money and keys, then send it all to a main.
2. **Paid value becoming game value:** gems (real money) turned into money, items or a wager.
3. **Win trading:** losing duels or events on purpose to move a wager or a rank.

Friends giving a new player a gift is normal trading. We don't want to block that.

## Options
**A. Strict (the review):** money and shards tradeable; crystals and gems never. Safest. Not "everything".

**B. Everything earned is tradeable, paid value never (our pick).** Money, shards and crystals can be paid, noted, traded, dropped and wagered. Gems and credits never. Anti-boost rules below stop alt feeding.

**C. Everything, gems too.** Matches 666 exactly. Gems become money, which breaks Mojang's commercial rules and makes the store pay-to-win. We advise against it.

## Our proposal (option B)
**Per currency:**
- **Money:** `/pay`, notes, `/trade`, drop a note, duel wager. As today plus wagers.
- **Shards:** same as money (new: shard notes).
- **Crystals:** tradeable (crystal notes, `/trade`, wager), but **10% of every transfer burns** (a sink) and a profile can **receive at most 2× a regular player's weekly crystal income** per week. Enchant progress can't be bought outright.
- **Gems and credits:** account-bound. No notes, no trade, no wager. Things bought with gems stay bound (R3-14, R3-20).

**Anti-boost rules (one shared "transfer guard" for pay, notes, trade, drops, AH, wagers):**
1. **Earned first (R3-1):** a profile can send value only after it earned $10k itself (start money and rewards don't count).
2. **Same network:** transfers between accounts that shared an IP in the last 30 days are blocked. Staff can whitelist families.
3. **Bound stays bound (R3-2):** loot from bound keys is bound to the profile.
4. **AFK keys for 1 account per IP (R3-3);** money and fuel still go to 3.
5. **Drops:** a dropped note can't be picked up by an account on the same IP or by your own other profile (profile drop tags already exist).
6. **Wagers:** no wager between same-IP accounts; at most 3 wagered duels per pair per day (see DUELS.md).
7. **Flow alerts:** the economy dashboard alerts when one account sends another most of its earnings again and again.
8. **Confirm big sends:** confirm from $1M, 1,000 shards or 5,000 crystals (660).

## Questions for you
1. Does "boosting" mean alt feeding, paid value becoming game value, and win trading? **Suggested: Yes.** Add anything else below.
2. Go with option B (everything earned is tradeable, gems and credits never)? **Suggested: Yes.**
3. Gems and credits can never be noted, traded, dropped or wagered (R3-5)? **Suggested: Yes.**
4. Shard notes, like money notes? **Suggested: Yes.**
5. Crystals tradeable with a 10% burn and a weekly receive cap? **Suggested: Yes.** (Option A would keep them untradeable.)
6. A profile can send value only after earning $10k itself (R3-1)? **Suggested: Yes.**
7. Block transfers between accounts that shared an IP in the last 30 days, with a staff whitelist for families? **Suggested: Yes.**
8. Loot from bound keys is bound too (R3-2)? **Suggested: Yes.**
9. AFK pool keys for one account per IP, money and fuel for up to 3 (R3-3)? **Suggested: Yes.**
10. Dropped notes can't be picked up by same-IP accounts or your own other profiles? **Suggested: Yes.**
11. Confirm screen from $1M, 1,000 shards or 5,000 crystals? **Suggested: Yes.**
12. Notes rate limit of 20 per minute (R3-40)? **Suggested: Yes.**
13. "And more" in 666: where else should currencies go? (Auction house as a payment currency, player shops, buy orders?) Write below. **Suggested:** money only on the AH and in player shops.

## Your notes (write here)


# C1 Currencies: improvements, must-haves and quick fixes (31 Oct 2026)

Requested by the owner after round 2 ("go over and find improvements for each, the important must-haves and quick fixes"). Proposals, not decisions, unless the line says "decided". Anything you accept goes to the ledger as C1-R3-nn.

## 0. Price sanity check with 100 credits = about 1 USD (decided rate, C1-R2-15)
| Thing (built or planned) | Credits | About |
|---|---|---|
| Vote (per site per day) | 3 | $0.03 |
| Day-7 streak | 10 | $0.10 |
| Thanks (per thank) | 1 | $0.01 |
| Premium battle pass (per season) | 1,000 | $10 |
| Profile slot 2 / 3 | 500 / 1,500 | $5 / $15 |
| Wardrobe sets | 400 / 900 | $4 / $9 |
| Island size 11 / 13 / 15 | 250 / 500 / 750 | $2.50 / $5 / $7.50 |
| Ranks (5 to 100 EUR) | about 500 to 10,000 | $5.50 to $110 if 1 EUR = 1.1 USD |

Findings:
1. **Slot 3 ($15) costs more than the cheapest ranks ($5 to $10), and a free-rank player cannot reach it by voting** (1,500 credits = 100 days of 15 a day). Probably fine, but check against F2 (the rank also gives slots). Propose: slots keep prices, ranks include them.
2. **A voter earns about $0.15 a day (5 sites, 15 credits) = $4.50 a month of store value for free.** That is a lot of value if the shop sells real things; it is the reason for the budget anchors (item 10) and for earned credits never being tradeable.
3. **Store currency is USD** (decided, C1-R2-30). Packages: 500 = $5, 1,000 = $10, 2,700 = $25, 14,000 = $130; top rank 13,500 credits = about $125.

## 1. The loophole I found (CLOSED by C1-R2-29: earned-paid items arrive bound; perks are entitlements, not items)
Earned credits cannot be sent. But the shop accepts them, and the item you buy arrives **unclaimed and tradeable** (C1-R1-39, C1-R2-09). So a vote farmer can turn untradeable earned credits into a tradeable item and sell it on the AH for money. That makes "earned credits are not tradeable" untrue in practice. 10 alt accounts voting on 5 sites earn 150 credits a day, which is a 400-credit tag every 3 days, resold for money.
Options (proposed): (a) items paid with any earned credits arrive already claimed and bound; only items paid fully with bought credits arrive unclaimed (recommended: one rule, no extra balance); (b) every item is bound at purchase and the unclaimed step goes (reverses your answer); (c) accept it.

## 2. More island perks bought with credits (your item 14: "even more island upgrades, relevant suggestions")
Rule: credits buy PERKS with a time or season end and a hard cap; money from the island bank keeps buying upgrade LEVELS (C1-R1-38). Ranked by how little they hurt competition:
| # | Perk | Kind | Cap and note |
|---|---|---|---|
| 1 | Island name colour, gradient and icon in /is top, browser and visit list | cosmetic | season or permanent per island |
| 2 | Island border colour and particles | cosmetic | per island |
| 3 | Visitor welcome title, sound and particle | cosmetic | per island |
| 4 | Featured slot in the /is visit browser (1 of 8 rotating, labelled "Featured") for 1, 3 or 7 days | visibility | one featured island per slot; never shown in /is top ranking |
| 5 | Extra personal homes and loadout slots | convenience | account level, not island |
| 6 | Hologram and NPC style pack for island signs and notes (B9) | cosmetic | per island |
| 7 | Timed island fly pass for all members (1, 3, 7 days) | convenience | clashes with the Wings mastery (level 20, $60k); owner to decide |
| 8 | Entity limit +N (production space) for a season | production | hard cap, for example +50 over the money line; C14 models it |
| 9 | Hopper limit per chunk +N for a season | production | performance risk at 300 players; needs H9 sign-off |
| 10 | Collector / auto-sell chest capacity +N for 1, 3, 7 days | production | needs D7 review |
| 11 | Offline run time +N hours for the fuel tank (1, 3, 7 days) | production | needs D5 review |
Recommended first release: 1 to 6 (cosmetic and convenience, no balance risk) plus 8. Items 7, 9, 10, 11 wait for their cards.

## 3. Improvement per accepted round 2 item
1. **One line per currency:** also put the four lines on the first page of the tutorial and on the currency NPC hologram at spawn; test with someone who has never played.
2. **Two credit ledgers:** add `/credit bal` text for new players: "Bought credits can be sent. Earned credits can't." in one sentence. Write the migration with a dry-run mode that prints row counts per module before it changes anything. Run it on a copy of the live database first.
3. **Trail:** add a "who holds it now" line at the top (receiver's bought balance) and a CSV export for a chargeback case. Keep the per-row text in `messages/economy.yml`.
4. **/credit pay:** tab completion also suggests the last 5 recipients; the confirm screen shows the receiver's head and name with a "first time paying this player" badge.
5. **Alerts:** put the alert list in one staff menu with a "mark reviewed" button and a note, so alerts are not lost in chat; keep a weekly count per rule so noisy rules get tuned.
6. **Never-directly-grant list:** also add a startup check that logs an error if a shop entry in config uses a forbidden type. Put the list in `docs/commands/credit.md`.
7. **Source lists:** the retry queue is a table with a staff button "re-run" and a count in the admin panel; unknown reasons also show the calling class name in the log.
8. **Purchase path:** write the order state machine as a table in the build spec (created, debited, delivered, void) with the crash points; unit-test every crash point.
9. **Entitlements:** claims show a short animation and sound (a visible reward moment); the claim message names where the item now lives ("Your Flame Tag is on your account").
10. **Budget anchors:** build the weekly report as a staff view "credits earned per source" and "credits spent per category" so the shop overhaul uses real numbers.
11. **Timed +1 perks:** the island menu shows a bar per perk with time left; an expiring perk warns the buyer 1 hour and 5 minutes before the end; a "renew" button.
12. **Price shape:** the shop shows the per-day cost next to each duration so players see the season perk is cheaper per day.
13. **Island perks:** the Perks tab names who bought what; the contribution menu (B4) counts credit perks as "supporter" contributions, separate from gameplay contribution.
14. **Season perks:** pre-season warning "your season perks end at the wipe" in the final week.
15. **Rank price:** show the price in credits and in dollars; the dollar figure is only text and never a currency.
16. **Credit shop:** a "New" and a "Recently bought" row, a favourites list, and a search through typed text (framework `input`), because the catalogue will be large.
17. **Popups:** group by currency so "+1,250 $ +3 ◆" shows on one line.
18. **Hold and chargeback:** every hold and chargeback action writes an audit row and a Discord message; the held player gets a plain message "A staff member is reviewing your credits".
19. **Balance proof:** show the last proof time and result in `/admin`.
20. **/eco revert:** keep a "revert history" so a revert can itself be explained to the player.

## 4. Must-haves before anything is built (in this order)
1. Fix the loophole in section 1 (owner decision).
2. Migration of gems to credits_earned with a dry run and unit tests on a copy of the live data shape (blocks everything else).
3. Split currency enum, `CreditView`, source-list enum and the purchase path (items 2, 7, 8).
4. Entitlement table and claim (item 9), with the crash tests.
5. The existing gaps from the code check: gate AH buying (C1-R1-07), freeze covers shards and credits (R1-09), alerts cover shards and credits (R1-10), profile-delete backup (R1-11).
6. Tebex package commands change to `/storegrant` before the rename goes live (R1-17).

## 5. Quick fixes that need no design (safe to do early, once the build phase starts)
- Replace the four "store credits" code comments and the RewardsSettings save-streak text.
- Hide `/baltop` for crystals (decided: not public) and make credits show the total.
- Change `send-gate.earned-reasons` is NOT here: round 1 item 5 is still undecided.
- Add shards and credits to `alerts.currencies`, remove crystals from transfer alerts (R1-10).
- Move the freeze default to money plus shards (R1-09).
- `Amounts`: credits always exact (R2-22).
- Fix the `/baltop gems` tab completion to the new names when the rename lands.

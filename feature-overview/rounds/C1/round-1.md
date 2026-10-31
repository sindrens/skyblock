# C1 Currencies: round 1 (31 Oct 2026)

Read first: `disagreements/C1.md`. **Answer item 1 first**: items 2 and 16 only exist if 1 = no, and items 8 to 10, 13 and 18 change slightly. Items 3 to 7, 9, 11, 12, 14, 15, 17, 19, 20 apply either way.
Items marked REVERSES change an earlier owner answer. They are only written down if you say yes, and the old answer is then marked superseded in the ledger.

## Mojang rules (read 31 Oct from minecraft.net/en-us/usage-guidelines, section on making money from a server)
- "Selling entitlements that affect gameplay provided they don't ruin other players' experience or give a competitive advantage in the game".
- "Selling and using in-game virtual currencies as long as they: have no real-world value and can't be cashed out, used, transferred across other servers, or converted into any other real-world currency; don't give the impression that the in-game currency is from or affiliated with Mojang; don't look like or have a name like Minecoins or any other virtual currency."
- A user who pays real money must get "an online payment and purchase history".
- This is a plain reading, not legal advice. Credits that players can trade for in-game money are the grey zone; a black-market of players selling credits for PayPal is exactly "real-world value".

## Terms used in this round
- **/store** = the in-game menu where credits are spent (PACK1 C2: "credits are spent in /store only; /shop stays money"). The Tebex web page is only where credits are bought. Today `/store` just prints a link; the in-game credits menu is planned (I6). Where slots, wardrobe sets and island size are sold is open (item 1).
- **Earned money** = money whose ledger reason is on `send-gate.earned-reasons`. Rewards count only if their reason is on that list (item 5).

## Suggestions

1. **[Economy] [Logic/UX] REVERSES R3-5a, F3, F4, F5 (for credits): one credits balance, account-bound, never sendable.**
   - Change: credits = one number per account. No /pay, no /trade, no notes, no AH, no drops. Bought and earned credits are the same coins, so vote and streak credits can buy everything credits can buy. Drop "two balances", "unclaimed items" and the transfer freeze on chargebacks. Everything bought with credits is bound to the account on purchase. Crystals already behave like this; credits become the second never-tradeable currency. Slots, wardrobe sets and island size keep their credit prices; they are sold from the in-game menus where they are sold today, and the new `/store` menu lists them as shortcuts.
   - Why: the owner rejected this once (F3: "opposite"), before the Mojang text was read. The Mojang rule says a sold virtual currency must have "no real-world value" and "can't be ... transferred" (quoted above). Tradeable bought credits are a path real money to money to items, and a market where players sell credits for PayPal is real-world value. Nothing of the two-balance design is built yet, so changing now is cheap. It also fits PACK1 ("spent in /store only") and principle 0 (one number, no claim step).
   - Risk: you lose the option of players buying credits and selling them for money to rich players, which the owner chose in F3. Store revenue could be lower. A friend cannot gift credits.
   - Size: S (rules and cards; the code for credit trading does not exist).
   - If you say no: answer item 2 and keep F3.

2. **[Security/Dupe] [Economy] (only if 1 = no) REVERSES F4: bought credits are tradeable by /trade only, with a hold and a daily cap.**
   - Change: bought credits can go through `/trade` only (not /pay, notes, AH, drops). Credits bought in the last 30 days are not tradeable (the hold shortens the chargeback risk; real chargeback windows are longer, several months, so it does not close it). Max 5,000 credits sent per account per day. Config: `credits.trade-hold-days: 30`, `credits.daily-send-max: 5000`. Needs lots (each purchase is a lot, oldest lot spent first, earned credits spent last and never tradeable). This overrides open question 1 of the card.
   - Why: it is the least bad version of F3 if you want credit trading. A stolen card cannot launder money through a long hold and a cap.
   - Risk: breaks principle 1 (waiting) and F4 on purpose, for real-money abuse only. The lot tracking is real work and the `/bal` line "Credits: 600 (200 tradeable)" is the confusing UI that item 1 avoids.
   - Size: L.

3. **[Bug] Rename gems to credits end to end (one migration per module, alias with an expiry).**
   - Change: `Currency.GEMS` becomes `CREDITS`, key `credits`. Idempotent migrations in the same style as `V003__tokens_to_crystals.sql`, one per module, each `UPDATE ... SET currency='credits' WHERE currency='gems'`:
     - economy: `eco_accounts`, `eco_ledger`, `money_notes`, `eco_watch_*` (next number V009);
     - auction, moderation (V008 trade-reason currency), shop (`currency_key`), blackmarket (`pay_currency`), wardrobe (the `gems` column and the origin values `gems`, `gems_claim`), profiles (slot `source = 'gems'`).
     - The reader lists the exact columns again before the build; any module that is disabled must not fail the others.
   - Also: live config keys (`tradeable.gems`, hud boards, Black Market prices, rewards, boosters, votes) get `credits` names with a one-time config migration (the decisions ledger pattern in `docs/decisions.yml`). `Currency.byKey("gems")` stays for 90 days (a config date `legacy-gems-alias-until`, removed by a unit-tested check), `/gems` is a hidden alias for 4 weeks. Colour `#FF8C1A`, glyph U+E204 (pack), text symbol: keep `✦` in plain text unless you pick another. Four "store credits" comments fixed. The jar and the database change in the same deploy; the old jar must never run against the migrated DB.
   - Why: PACK1 asked for it; 98 files mention gems, and two words for one thing cause bugs. A unit test per migration (old rows, new rows, rerun).
   - Risk: a missed `'gems'` string in a SQL query silently reads 0 credits. Tebex package commands must change in the same release.
   - Size: M.

4. **[Logic/UX] REVERSES the wording in PACK1 section 5: one clear sentence per currency, matching what is built and decided.**
   - Change:
     - Money = everyday buying and selling.
     - Crystals = enchants, crystal sets, set merges, and the island shop and Forge extras decided in CR2/CR4. Not island upgrades (those are paid from the island bank in money, principle 10).
     - Shards = the Black Market (including island charters).
     - Credits = the /store and the other places where credits are spent today (profile slots, wardrobe sets, charters).
     - Put these four lines in `/help currencies`, the `/bal` hover and the tutorial text. Correct PACK1 section 5 in the docs.
   - Why: PACK1 says crystals are for island upgrades and shards for tools and armour; the card, decisions and code say otherwise. Four clear roles are easier to learn than blurry ones.
   - Risk: if you really want crystals for island upgrades or shards for tools, that is a new feature and needs its own card; say so and I will log it.
   - Size: S.

5. **[Security/Dupe] Define "earned" as an exact list of reasons for real play, and narrow it.**
   - Change: `send-gate.earned-reasons` = `sell`, `depths_drop`, `island_task`, `pillar_milestone`, `quest_reward`. Remove `tutorial_reward`, `tutorial_optional`, `daily_reward`, `playtime_reward`, `pass_reward`, `crate_reward`, `afk_pool`, `interest`. Quests, tasks and pillars are rewards too; they stay because they need real activity. Rule in one line for the card: "money from selling what you gathered, plus task, pillar and quest rewards". (R3-1 says rewards do not count; the code default counts them.)
   - Why: an alt that logs in daily, votes and sits in the AFK pool should not unlock sending without playing. Before the build I will check ECONOMY_BALANCE for tier 1 income to confirm that $10k from selling is minutes of play (not yet verified).
   - Risk: quest rewards can still be farmed if quests are easy (check in F8). Items handed to an alt who then sells them still count as `sell` (see item 6). Config only, reversible.
   - Size: S.

6. **[Security/Dupe] Do not block the island-member exception or item trades; watch them.**
   - Change: keep F1 as decided. Add an economy-watch rule (C10), list for staff only, no automatic punishment: flag a profile that has played less than 2 hours and sends (/pay, notes, trade, island bank deposit) at least 80% of the money it ever received, when that sum is at least $2,000. A second flag: a profile whose sell income in its first 2 hours came mostly from items received through `/trade` from one other player (C7 logs both sides). Config under `watch.alt-funnel.*`: `max-playtime-minutes: 120`, `share: 0.8`, `min-amount: 2000`.
   - Why: the money funnel is worth about $1,000 start money per alt once item 5 stops rewards counting (the $2,500 tutorial reward then no longer unlocks sending but can still be sent to an island member), small against mid-game millions. A hard block hurts real new team members; a flag catches farms. Item laundering through trades cannot be fully closed without blocking trading; I recommend accepting it and watching it.
   - Risk: false flags for friends who pool money on day one; staff read the list.
   - Size: S/M.

7. **[Security/Dupe] Gate AH buying (buyer side) and re-check the gate at the /trade swap.**
   - Change: `modules/auction` calls `SendGate.check` for the buyer before a purchase (REVIEW3 F1 says AH buying is gated; the code does not). The gate is skipped when the seller is a member of the buyer's island, like everywhere else. `/trade` checks again at the moment items and money swap, not only when the offer is raised. The message says how much more to earn ("$4,200 more to earn before you can buy from players").
   - Why: today an alt can buy a main's overpriced AH listing without the $10k. This closes alt to main. It does not stop a main overpaying an alt's listing (the main has earned its $10k); that direction is accepted.
   - Risk: a new player trying to buy their first AH item meets the gate; the message must be friendly.
   - Size: S.

8. **[Logic/UX] `/baltop` stays public for money and crystals; credits and shards are staff-only.**
   - Change: remove credits and shards from the `/baltop [currency]` argument and suggestions for players; staff keep them behind `skyblock.economy.baltop.all`. Crystals stay public (they cannot be sent, so a list leaks no tradeable value).
   - Why: 574/575 say gems are hidden; today `/baltop gems` works for anyone. Shards are tradeable and a public list shows who holds value.
   - Risk: none for gameplay.
   - Size: S.

9. **[Security/Dupe] A freeze stops every transfer out, in every sendable currency.**
   - Change: `SanctionRules` default freeze currencies = `money, shards` (plus `credits` if tradeable). Freeze blocks /pay, /trade, notes, AH buying and island bank withdrawals to others. Spending in /shop, the Black Market and the forge stays allowed (spending is not sending).
   - Why: today the ledger blocks only money, so a frozen player can still move shards.
   - Risk: the code comment on `freezeCurrencies` says "stops from leaving the player's accounts"; I read that as sending. If you want a freeze to block spending too, say so.
   - Size: S.

10. **[Security/Dupe] Economy-watch alerts cover shards and (if tradeable) credits, with shard thresholds that can fire.**
    - Change: transfer alerts: `money`, `shards` with their own thresholds 100 single / 300 per day / 100 repeated, and `credits` with 1,000 / 10,000 / 1,000 only if item 1 = no. Remove `crystals` from the transfer alerts (they cannot be sent) but keep or add a crystal creation-rate alert (crystals created per player per hour) because crystals can still be duped or farmed.
    - Why: the card says gem thresholds are lowered, but gems are not in the list. The whole shard supply is a few hundred per season, so gem-sized thresholds would never fire.
    - Risk: noise from shard prizes; all values are config.
    - Size: S.

11. **[Bug] Deleting a profile backs up and clears every balance row.**
    - Change: on profile delete, copy money, crystals and shards into `eco_deleted_accounts` (profile id, amounts, deleted at), then zero the rows. Remove them from baltop and the supply snapshot. Staff restore through the B17 profile restore (the profile and its balances come back together; `/eco restore-profile`, admin, tab-completed). Backups are removed only by the owner (no timer). A late payout aimed at a deleted profile is logged and discarded; it is never redirected to another profile (principle 2, P4).
    - Why: principle 3 (staff-restorable backups) is not met today; orphaned rows inflate the supply numbers C14 relies on.
    - Risk: the restore command is one more tool.
    - Size: M.

12. **[Economy] [Bug] Rule for season rollover (build with I5).**
    - Change: ledger season id = the real season number, not the hard-coded `0`. At rollover: archive money, crystal and shard balances of every holder into `eco_season_archive` (leaderboards, veteran title), set live balances to 0, grant the start $1,000 under the new season key. Holders to handle: player purses, island banks (`owner_type = island`), personal banks, `money_notes`, AH listings and `/box`. Credits follow the card's open question 4 (default: kept). One batched transaction per 1,000 accounts, with a dry-run command that prints totals first.
    - Why: the card says "wiped" but nothing does it; a hand-made wipe on rollover day is risky.
    - Risk: depends on I5; written down here so the seasons card does not forget currencies.
    - Size: L (build with I5).

13. **[Logic/UX] Clean `/bal` and scoreboard. Changes the card.**
    - Change: `/bal` always shows Money, Crystals, Shards, Credits, one line each, zeros dimmed; hover = exact number plus the one-line purpose (item 4). The scoreboard shows Money and Crystals only; credits and shards never appear on any board (the card's "gems on the island board" line goes).
    - Why: the code shows crystals and gems on every board when above 0, which is clutter and a constant store reminder; a board should show what the player acts on every minute.
    - Risk: shard hunters like seeing their count; it stays in `/bal` and the Black Market menu.
    - Size: S.

14. **[Logic/UX] Every credits spend goes through the existing confirm menu.**
    - Change: use `common/menu/ConfirmMenu` (item, price, balance before and after, confirm and cancel; `BedrockForms` gives the Bedrock form). Applies to profile slots, wardrobe sets, charters and the /store. Always confirm, no exception.
    - Why: credits are real money; one misclick on a 1,500 price is a refund ticket. The framework piece already exists, so this is a few lines per sink.
    - Risk: one extra click.
    - Size: S.

15. **[Economy] Keep the shard plan; add a measurable check instead of raising supply.**
    - Change: keep 5-10 shards a day late in a season and the charter prices 150 / 300 / 450. At that rate an active player reaches 150 by about day 35 and 450 cumulative (first two charters) by about day 75; the third charter (900 total) is for the top players only, which is what "expensive" means. Add a balance-sheet check for the test server: a median active player holds at least 150 shards by day 35, otherwise lower charter 1 to 100. Set explicit per-source numbers in C13 (boss, KOTH, envoy, tier 9+) when those cards are reviewed.
    - Why: I first thought the supply was too low; the numbers show the plan fits the "1-2 month goal" in C13 only if shards start flowing in the first month, which depends on the unbuilt sources. A check is better than guessing.
    - Risk: shards are tradeable, so players with money can buy charters with shards from active players; that is intended (R3-5b).
    - Size: S (a doc and a config check).

16. **[Security/Dupe] (only if 1 = yes, or for the store grants either way) Chargeback rule without a debt counter.**
    - Change: a chargeback runs one admin command: `/eco chargeback <player> <credits>`. It removes up to that many unspent credits (the balance cannot go below 0, `CHECK (balance >= 0)` stays), removes the purchased rank, flags the account (RK15) and bans the account on the second chargeback. No debt counter.
    - Why: a debt counter needs repayment rules and a spend lock; a ban is simpler (principle 0) and the account is the thing that is lost. Credits already spent on a slot or a set are lost to us; the ban is the deterrent.
    - Risk: a determined card thief gets one purchase for free.
    - Size: S.

17. **[Security/Dupe] Store purchases arrive through one console-only grant command with an idempotency key.**
    - Change: a console-only command `/storegrant <uuid> <package> <transaction-id>` (not `/eco give`) writes the ledger row with key `tebex:<transaction-id>:<package>`; a replay writes nothing and logs "already granted". Each grant also writes a `store_purchases` row (account, package, price in EUR, transaction id, time). The delivery design itself belongs to I6; this card only fixes the ledger rule.
    - Why: the ledger already has a unique `idem_key` (VARCHAR 96); using it makes double grants impossible. Mojang requires an online payment and purchase history for the buyer; Tebex receipts probably cover it, and our table is the in-game backup.
    - Risk: Tebex runs commands on the server; the exact command syntax must be set up in the Tebex package (I6).
    - Size: S.

18. **[Security/Dupe] Only the owner can create credits by hand.**
    - Change: `/eco give|set credits` needs `skyblock.economy.credits.grant`, granted to the owner group only, with an explicit `false` on admin in `server/permissions/economy.txt` (wildcards and "missing = op" would otherwise grant it). Mandatory reason and a Discord alert. The automated `/storegrant` path has its own console-only permission and no alert per purchase. Admins keep money, crystals and shards.
    - Why: credits come from real money; a compromised admin account could print store value. 47 says /eco is admin and owner; this narrows one currency.
    - Risk: the owner runs credit corrections for support cases.
    - Size: S.

19. **[Bug] Move hard-coded numbers and text to config and messages.**
    - Change: `economy.yml`: `display.exact-below: 100000`, `baltop.check-ticks: 100`, `notes.purge-initial-ticks: 2400`, `notes.purge-interval-hours: 6`, `pay.cooldown-seconds: 3` (check what `commands.yml` already overrides before adding), the retry count 4 and delays; the English tab hints `NOTE_REASONS` and `STAFF_REASONS` move to `messages/economy.yml`. `Currency.transferable()` for crystals stays hard-coded on purpose.
    - Why: CLAUDE.md non-negotiable "no hardcoded numbers or text".
    - Risk: none. The 3 s /pay cooldown is a delay (principle 1); it exists only against spam, so I would keep it at 3 s unless you want it gone.
    - Size: S.

20. **[Logic/UX] [Economy] REVERSES the credit half of F6: island size above 9x9 is bought with shards only.**
    - Change: remove the 250 / 500 / 750 credit prices from the Island Charter; the three sizes cost 150 / 300 / 450 shards, last one season (P5-14), and are bought in the Black Market. Credits keep buying slots and looks.
    - Why: the card calls paid island size a "hard blocker" under Mojang's "no competitive advantage" rule. A bigger island is more production space, which is a gameplay advantage you can buy with credits. Shards come from play, so the advantage is earned. Shards are tradeable, so players with money can still get it from other players, which is not selling gameplay for real money.
    - Risk: you lose a store money-maker (the owner answered "both" in F6). With item 1 = yes, shard-only is the only consistent choice; with item 1 = no, credits being tradeable make the advantage purchasable anyway.
    - Size: S (one config line in `BlackMarketSettings`, `Purchases` credit path removed).

## Not part of this card (moved to I6, store plan)
Real-money price per credit, refunds, spending limits for minors, the Tebex package list. They decide whether the credit prices (500 / 1,500 / 400 / 900) are fair, but they are store decisions.

---
# Owner answers (31 Oct 2026, one message)
Reading used: the opening a/b/c/d answers the sections of `disagreements/C1.md` (A clashes, B built, C differences, D risks); the numbered answers are the 20 suggestions above. Items skipped by the owner stay undecided.

## Raw answer, by section
- **A (clashes):** There will no longer be anything called gems; it is renamed credits. Credits can be bought in the store, earned by voting on all sites every day, thanks, streak, and are used on different things such as in-game loot, ranks, perks. They are tradeable between players. No /withdraw for them; `/credit pay`, `/credit bal` etc. `/bal` shows all currencies. Crystals: "what do you think is logical, read the server; as it is today it does not show how I want it; we only have concepts". Shards = Black Market. Each currency needs a different purpose so players trade currencies and have to work on different things to get each of them. Progress must be visible, a feeling, satisfaction.
- **B (built, planned in card):** Yes it is built, but look for improvements.
- **C (differences):** Rewards need a full overhaul and so does the resource pack for the GUIs (no good GUI design yet; simple and easy to understand; find logical improvements). Public gem baltop: "yes that's fine"; maybe put credits in the alert currencies because it is the most valuable. Boards: yes, relevancy; symbols in the resource pack simple, make sense, easy to understand, "right now it is messy"; the scoreboard shows relevant things depending on where you are (island, forager mine, ...). Rename: yes, credits.
- **D (risks):** Yes; improve the logic and be safe with rollbacks. A rollback feature is needed: per island, per world (spawn etc.) and per chunk, `/rollback` with permissions.

## Per item
| # | Answer | Status |
|---|---|---|
| 1 | Not yes/no. Two types of credits: bought in the store (tradeable) and earned (battle pass, voting, thanks, ...: NOT tradeable and **per profile**). Improve the logic, ask more. | HELD (conflicts, see questions) |
| 2 | "yes, I agree, alert mod+ with alerts maybe? what do you think, be reasonable" | HELD (unclear what is agreed, F4 conflict) |
| 3 | yes, gems are credits now | accepted |
| 4 | yes "if this makes sense and suits the server, easy to understand, 1000 players" | accepted for money and shards; credits and crystals HELD |
| 5 | not answered | undecided |
| 6 | yes | accepted |
| 7 | yes, and lots of dupe prevention: "a server that allows EVERYTHING; what is NOT allowed is NOT even possible" | accepted (+ global principle) |
| 8 | yes; crystals are per island and not relevant for everyone; shards are, so money, shards and credits are public | HELD (credits public clashes with 574/575) |
| 9 | yes, "everything maybe? go through it and find what is safe and reasonable" | accepted, details decided by the developer below |
| 10 | yes 100%, research and plan a good way, critique | accepted |
| 11 | yes, in a good way, common sense | accepted |
| 12 | yes: everything in the season resets, not credits, but islands, values, skills etc; there is a list; everything deleted is stored in the old season files | accepted (credit handling HELD) |
| 13 | yes 100%, find a good way that fits my answers | accepted (direction) |
| 14 | yes | accepted |
| 15 | research, maybe fix; redo /shop, prices and rewards everywhere when the server is fully planned; make more things worth doing so there is not one meta | DEFERRED to the economy overhaul |
| 16 | yes, plan it, easy to use, test it in gradle | HELD (chargeback vs tradeable credits) |
| 17 | yes, easy to use | accepted |
| 18 | yes, clean and organised | accepted |
| 19 | not answered | undecided |
| 20 | "we need to redo; I did not understand this; find a solution that works for every part" | undecided, re-explained in round 2 |

---
# Follow-up answers (31 Oct 2026, second message)
| Question | Owner answer | Result |
|---|---|---|
| Credit scope | Both types are shared across ALL profiles and belong to THAT player. `/credit bal` shows the types; the spend menu shows them too. Aliases for the credit shop: `/credit shop`, `/creditshop`, `/store shop`. `/store` and `/buy` give the website. | Item 1 resolved: account-level, never lost. Earlier "earned = per profile" is superseded by this. |
| What credits buy | Ranks (price of the next rank minus the credit value of the current rank, automatic), cosmetics (trails, chat colours), island upgrades, boosters and a lot more. Categories, Buycraft as a reference, own custom command, resource-pack look, preferably many reasonable items. | Ranks and cosmetics accepted; island upgrades and boosters need confirmation (R3-7, see round 2). |
| Trading and chargebacks | Tradeable whenever, with alerts, and a full trail of where credits went and what they bought: click the buyer, open credit history, click a payment, open the receiver's credits, see what he bought. | Item 2 resolved: no hold, no cap (F4 stands), alerts to mod+, credit trail. |
| Public lists | All credits are public; `/credit bal <player>` shows the total only, never the tradeable/untradeable split. | 574/575 superseded. |
| Crystals / island currency | Does the island aspect also need a currency? Plan everything and come back with results: what is used where, what makes which currency, one line per currency. | `CURRENCY-MAP.md` written; direction asked in round 2. |
| Earned credits at reset | Credits belong to the player, kept always (leaving, kick, new island, reset). Thinks about sharing the OTHER currencies per island; asks for thoughts. Menu showing which players made what. | Credits never wiped. Island sharing: thoughts in CURRENCY-MAP, decision asked. |
| Commands | /credit (bal, pay, history) + /buy alias for /store (picked). | Conflicts with the line above (/buy gives the website). Asked. |
| Island size | "Yes this looks good", credits are useful for progress, "don't think much more about the EULA, but remind me and give warnings"; more island perks than size can be bought; balance it. | Read as shards AND credits (as built, F6). Asked to confirm. |

---
# Follow-up answers 2 (31 Oct 2026, third message)
| Question | Owner answer | Result |
|---|---|---|
| /buy | Website | `/store` and `/buy` give the Tebex link. The in-game shop is `/credit shop`, `/creditshop`, `/store shop`. |
| Streak and several profiles | "Find better logic around this; the rewards match the rewards given to each profile they are a part of, or something; plan it." | Not decided; planned in F7 and B17. Direction recorded. |
| Progress items for credits | "This is not rejected, remove this. It is going to be this and more; research other big skyblock servers' stores for inspiration. We give a competitive advantage to some degree, more quality of life." | Credits may buy island size, island upgrades, boosters and more QoL/progress items. Reverses R3-7 and REVIEW3 F7 for the credit shop. Mojang warnings stay logged. |
| Island currency | A: no new currency | Personal money, crystals and shards; shared island bank; Contribution menu. |
| Spend order | Earned first | Bought credits stay tradeable longer. |
| Chargeback | Remove from buyer, flag the trail | No automatic removal from receivers. |

---
# Follow-up answers 3 (31 Oct 2026, fourth message)
| Question | Owner answer | Result |
|---|---|---|
| Island upgrades vs principle 10 | Bank only. Credits cannot pay upgrade levels. Credits CAN buy other island perks: bigger island, more spawner limit, etc. ("/shard bal" read as /credit bal). Also in the credit store: a timed "+1" on a generator level (cobble gen level 7 + 1 = effective 8), applied in /island somewhere. Island upgrades are earned by progressing in different places on the server; the credit-shop ones last a whole season, or 1, 3 or 7 days. | Principle 10 stands. New concept: timed credit perks. |
| Unclaimed items | Keep unclaimed items | F5/R3-14 stay for credit items. |
| Send gate for credits | No gate, alerts and trail only | F4 stands. |

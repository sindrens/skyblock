# C1 Currencies: round 2 (31 Oct 2026)

20 new suggestions, none repeating round 1 (round 1 items 5 and 19 are still undecided and not re-offered; say "re-offer 5 19").
Built on your answers (credits: one balance per player, bought = tradeable, earned = not; island upgrade LEVELS money-only; timed credit perks; unclaimed items stay; no send gate; full trail; Mojang warnings only).
A critic pass on the first draft of this round found two blockers (purchase and claim safety) and several clashes with your accepted answers; this is the corrected version.
**[Mojang]** = sells progress. Each such item gets a warning line in its card (rule C1-R1-31), not a veto.
Every accepted item ships with its unit tests (concurrency, replay, migration rerun, crash redelivery, double claim) listed in its build spec; tests run with `./gradlew build` on H2, never a server.

## Suggestions

1. **[Logic/UX] One line per currency, same words everywhere (re-offers the part of round 1 item 4 that you left open).**
   - Change (for `/help currencies`, `/bal` hover, tutorial): 
     - Money: everyday buying and selling. Earned from selling, production, quests, interest. 
     - Crystals: your Depths power. Earned from Depths mining, forest and combat, island tasks and pillars. Spent on enchants, rolls, crystal sets, merges, Forge extras. Never tradeable, not public.
     - Shards: the Black Market. Earned from bosses, KOTH, envoys, rare Depths drops. Spent on the Black Market and island size.
     - Credits: the credit shop. Earned from the store, votes, thanks, streak, pass. Spent on ranks, cosmetics, island perks, boosters, convenience.
   - Why: each currency has its own work, so players trade money for shards and credits. Wording matches C1-R1-04a and your answers; crystals stay a personal Depths currency (your island answer A). Corrects the PACK1 sentence "crystals = island upgrades".
   - Risk: none to build.
   - Size: S.

2. **[Logic/UX] [Security/Dupe] Two ledger currencies: `credits` (bought, transferable) and `credits_earned` (earned, never transferable). Supersedes the "gems to credits" target of C1-R1-03 for old rows.**
   - Change: 
     - `Currency.CREDITS` is created only by the reasons `store_grant` and owner-only `admin_buy`. `Currency.CREDITS_EARNED` is created by `vote`, `thanks`, `streak`, `pass`, `event`, `admin_give`. `credits_earned` has `transferable=false` hard in code (like crystals).
     - Migration: every old `gems` row becomes `credits_earned`, because nothing was ever sold (only votes, streaks, thanks, staff). The 90-day `gems` alias resolves to `credits_earned` for deposits and is refused for store grants. Tebex package commands must use `/storegrant` (item 8 of round 1 = C1-R1-17), never the alias.
     - One spend call takes earned first, then bought, in one transaction with two ledger rows (C1-R1-34). A chargeback takes bought first.
     - Display: everyone sees the total ("Credits: 600"); the owner sees "600 (200 bought, 400 earned)". Baltop counts the total with a cached sum (30 s, async) because the `(currency, owner_type, balance)` index cannot sort a sum of two rows.
     - Staff `/eco give` can create only `credits_earned`; `credits` only by the owner (C1-R1-18).
     - Watch rules: transfer and repeat rules cover `credits` only; earn-rate rules cover `credits_earned`. Credits are keyed to the player, not the profile.
   - Why: the "earned can't move" rule sits in code and no setting can break it. It reuses the existing atomic ledger.
   - Risk: every reader of "credits" must use one helper `CreditView.total()`; menus, baltop, hud and watch all change.
   - Size: M.

3. **[Security/Dupe] [QoL] The trail is the ledger, shown as a click path (no lot maths).**
   - Change: `/credit trail <player>` (mod+ read-only, admin export) and a button in the staff player view. Rows from `eco_ledger`, newest first, filter All / Bought / Earned / Paid / Received / Spent. Plain text per row ("Bought 12,000 (store order 8841)", "Paid TCCreaper 570", "Spent 500 on Flame Tag") and the row shows its kind (`credits` or `credits_earned`). The existing 64-character `reference` column carries a fixed format (`order:<id>`, `pay:<id>`, `shop:<item>`). Clicking a "Paid" row opens the receiver's trail at that receipt, with his **still-held bought balance** shown at the top. A depth-limited tree view (3 steps) and an export serve chargebacks. Only `credits` rows are attributed; after receiving 570 the later rows are labelled "later activity, not proven to be these coins" because earned credits are spent first.
   - Why: your example (Pouched buys 12,000, pays TCCreaper 570, TCCreaper buys a 500 tag) is one path in the existing ledger. It is honest about what it can prove.
   - Risk: chains A to B to C to D can be long since there is no hold; the tree view stops at 3 steps and offers "continue". Credits rows must never be pruned or moved out of the live table at season rollover (C1-R1-12b); the card says so.
   - Size: M.

4. **[Logic/UX] [Security/Dupe] `/credit pay` spends bought credits only, confirms every pay, says why it refuses.**
   - Change: `/credit pay <player> <amount>`: offline player tab completion, amount hints. Bought credits only (the refusal says "You have 40 bought credits; your other 360 are earned and can't be sent"). Confirm screen on every pay (one click, consistent with C1-R1-14). Self-pay refused; a sender who is frozen or trade-blocked is refused (C1-R1-09); a frozen receiver follows the existing `freezeReceive` set. No cooldown (principle 1; round 1 item 19 still unanswered). The receiver's message has a [Receipt] click that shows only that one receipt.
   - Why: one clear command; the confirm protects real money from typos.
   - Risk: none.
   - Size: S.

5. **[Security/Dupe] Credit alerts to mod+: keep your thresholds and add three pattern rules.**
   - Change: keep C1-R1-10 as decided (alert to mod+ at 1,000 single / 10,000 per day / 1,000 repeated). On top, three new rules in the existing watch config layout (`rules.<rule>.*`): receiver gets credits from 3+ different senders in 24 h; a player with under 2 hours played receives 1,000+ (needs the playtime source of the account); pass-through (receiver forwards 90%+ within 10 minutes). Critical hits also go to Discord. Rules key on the player, not the profile.
   - Why: the shapes of laundering and chargeback-washing. Friend gifts may trigger "pass-through"; it is only a flag.
   - Risk: thresholds depend on the credit price (I6); start high and lower. This is new rule code, not just config.
   - Size: M.

6. **[Economy] [Security/Dupe] **[Mojang]** A hard "credits never directly grant" list, and a question on warzone.**
   - Change (proposed answer to the I10 question, not settled): credits never directly grant money, crystals, shards, crate keys, skill or tool or enchant levels or XP, Depths tier unlocks, quest or event progress. Indirect paths stay allowed, as you decided: ranks, cosmetics, convenience, island perks (items 11 to 14), personal boosters inside the booster caps (the built booster types already include sell, skill XP and crystals; they keep their caps). The list is one code check on the shop entry type (security, so not config).
   - **Question inside this item:** credit-bought sets and rank perks that work in the warzone and duels: allowed or looks-only there? R3-20 already says gem sets are looks only.
   - Why: "some advantage, mostly quality of life" without a money path and without paid combat power.
   - Risk: a later item may need an exception: a one-line, tested code change.
   - Size: S.

7. **[Security/Dupe] Code-registered source lists for `credits`, `credits_earned` and `shards` only.**
   - Change: the allowed ledger reasons per currency are declared in code (an enum), not in config (it is a security rule). Item 2 lists the credit sources; shards: `boss_reward`, `koth`, `envoy`, `depths_rare`, `crate_reward`, `admin_*`. A deposit with an unlisted reason is not dropped: it goes to a staff-visible retry queue and raises a critical alert (a player never loses a vote or pass reward by our bug). Other currencies stay as built. Reasons stay within the 32-character `reason` column.
   - Why: your rule "what is not allowed must not be possible", scoped to the three currencies where it matters, so we do not edit ~40 modules for a registry.
   - Risk: every new credit or shard source must register its reason (the test fails otherwise, which is intended).
   - Size: M.

8. **[Security/Dupe] One purchase path that reuses the existing safe pieces.**
   - Change: `CreditShop.buy()` is the only caller of the credit spend reasons; a unit test that scans the source for those constants enforces it (no new test library). It writes in one database commit, using the existing `executeGuarded`/`executeAll`: the debit rows, a `credit_orders` row (id, player, item, price, kinds spent, state) and the entitlement row (item 9). The item is created from the entitlement after `PlayerSaves.commitAfterSave`: **save the player file first, then mark `delivered`**. If the server dies in between, redelivery mints a NEW item UUID and marks the old UUID `void`; a void item found later is silently removed with no punishment. Rank grants go through LuckPerms with a retry keyed by the order id (LuckPerms is outside the database).
   - Why: F-01 in the security audit is "item removed, DB written, a crash restores the player file". This ordering closes it for purchases without punishing an innocent buyer.
   - Risk: more code than a plain give; every store item reuses it.
   - Size: M.

9. **[Security/Dupe] One entitlement table and a claim that removes the item first. Includes the lore line.**
   - Change: one `entitlements` table (id, player, type, item_uuid, state `unclaimed|claimed|void`, order id). Claim: remove the item from the inventory synchronously, save the player, then a conditional update `unclaimed to claimed` in the same commit that writes the account entitlement; if the database fails, the item is returned. If the update touches 0 rows (a copy): the husk is deleted and staff are alerted; there is no automatic trade block (R3-28 stays for system-found dupes only). The item shows "Bought for 400 credits" in the lore (the price paid, so it never goes stale). Unclaimed items can go in `/box`, ender chests and island storage, and can be traded or listed; they cannot be dropped on the ground (principle 3: items never drop). **[Mojang]** an unclaimed item sold on the AH is a credits-to-money path you accept.
   - Why: the claim is the moment value becomes permanent; removing first closes the window to sell the item while the claim is half done. The in-memory `DupeDetector` cannot see offline ender chests, the conditional update can.
   - Risk: the card F5 said "drop" was allowed for unclaimed items; this reads principle 3 as stronger. A claim is refused while the database is down (clear message).
   - Size: M.

10. **[Economy] Free players get progress too: an earned-credit budget rule.**
    - Change: design anchors, tuned in the shop overhaul (C15): a player who votes on all sites every day affords one cosmetic roughly every 2 weeks and a 7-day island perk roughly every week, never a season perk or a rank on votes alone; the pass and streak add on top. The watch job reports credits earned per source per week so the anchors can be checked with real numbers.
    - Why: your wish for visible progress for everyone, and a credit economy where votes matter but money still buys the best offers.
    - Risk: numbers come later; this fixes the rule so prices are made to fit it.
    - Size: S.

11. **[Logic/UX] [Economy] **[Mojang]** Timed island perks: "+1 level" on production lines, first release only.**
    - Change: owned by the islands module (`IslandUpgradeEffects`). A perk adds the effect of the NEXT level of a line for 1, 3 or 7 days or until the season ends: bought level 7 acts as 8. It ignores the task gate and the bank price of that level (your example). It never goes above the line's max level, and a maxed line shows "maxed" in the shop. First release: Production lines only (spawner rate, mob loot, crop growth, harvest yield, generator, fuel tank). Not included: sell multiplier, XP lines, Depths lines (money and XP effects, see item 6), size and member slots (kept after expiry), masteries. Buying a perk for a line that already has one: the longer end date wins, they do not add. On expiry the effect is removed; limit-type effects keep existing things and block new ones above the limit.
    - Why: your idea in a form that cannot lift a line past its cap and expires cleanly; the production lines are where the effect is a plain multiplier.
    - Risk: players feel the drop at expiry (timer and renew button). Needs `/is upgrade` (M5.4) first.
    - Size: M.

12. **[Economy] One price shape for perk durations.**
    - Change: price = base x factor, factors 1 / 2.5 / 5 for 1 / 3 / 7 days. The season perk costs 25 x base x (days left / season days), never below the 7-day price; so late buys are fair. Base is the price of the next level of that line. Final numbers belong to C15 (`credits.perks.duration-factors`).
    - Why: one formula, so the four durations never become four guesses; with ~90 days a season perk is about 3.6x cheaper per day than daily renewals, and 13 weekly renewals (65x) cost 2.6x a season perk.
    - Risk: if base is too low the season perk beats money upgrades; C15 checks.
    - Size: S.

13. **[Logic/UX] Perks belong to the island, the buyer is recorded, and a chargeback can revoke them.**
    - Change: one `island_perks` table (island, line or size or cap, `ends_at` or `season_id`, bought by, order id). Every member gets the effect; leave and kick do not remove it (REVIEW3 F9). The island menu has a "Perks" tab with timer and buyer name. No automatic refund on disband or reset (a refund is a manual owner action, the buyer record is the audit trail). A chargeback revokes every perk linked to the disputed orders (item 18).
    - Why: simple, fits "team = island", and closes the "buy a perk, charge back, keep the perk" hole.
    - Risk: a member who buys a perk and is kicked: the island keeps it (like bank deposits).
    - Size: M.

14. **[Economy] **[Mojang]** Season island perks that are not upgrade lines: size and spawner cap.**
    - Change: they live in the same `island_perks` table, ending at the season wipe. Island size 11 / 13 / 15: one purchase per step per island, with shards (Black Market) or credits, whoever pays first; the second currency is then not offered. Spawner cap: "+N maximum spawner stack size" (the stack limit of one spawner), bought in steps up to a hard season maximum of +64 (`credits.perks.spawner-cap-max`, a new proposal: say if you want it higher or lower). Visible in `/is` and `/is upgrade`.
    - Why: you named bigger island and spawner limit; one cap per season keeps one rich member from dominating and keeps money upgrades the main path.
    - Risk: spawner stack size changes income; C14 must model the maximum.
    - Size: M.

15. **[Economy] Rank upgrade price in credits, one ladder with the EUR store.**
    - Change: you pay `max(0, target rank credits - owned rank credits)`. Only permanent ranks count, whether bought with EUR or credits; season-won ranks do not. The menu spells out the sum ("Firewing price, minus your Hatchling, you pay"). The grant is idempotent by order id (item 8); the rank row keeps the order id so a chargeback finds it. The credits-per-EUR rate must be fixed before this is built (decided in I6; a build blocker, no number here).
    - Why: your rule, and the same ladder as the Tebex "upgrade pays the difference" (decision 388).
    - Risk: none beyond the rate.
    - Size: S/M.

16. **[Logic/UX] The credit shop has 6 categories, built after the GUI redo.**
    - Change: `/credit shop` (aliases `/creditshop`, `/store shop`): Ranks, Cosmetics (tags, titles, trails, chat colours, looks, pet skins), Island perks (items 11 to 14), Boosters (personal, timed, inside caps), Convenience (profile, loadout, wardrobe and vault slots, extra homes), Pass. The top shows the balance split. The battle pass is bought here, and its premium track may pay back at most 50% of its credit price in earned credits (no credit loop). The item list and prices are researched from other big skyblock stores in I10; the menu is built after the resource-pack GUI redo (I7) to avoid rework. **[Mojang]** island perks and boosters sell progress.
    - Why: Buycraft-style categories are familiar; six fit a 1,000-player learning curve.
    - Risk: an empty-looking category until I10 fills it.
    - Size: M.

17. **[QoL] Visible progress: small gain popups, session totals.**
    - Change: on a gain, a short action-bar "+1,250" in the currency colour, aggregated once per second, default on, a switch in `/settings`, no sound by default. `/bal` hover shows "This session: +48,200" from an in-memory counter (since login), no ledger queries. I check first that the action bar is not already used by another HUD piece.
    - Why: you want progress to feel visible; this costs almost nothing per player.
    - Risk: spam in the AFK pool or auto-sell; the 1 per second rule and the setting handle it. Overlaps G7 (scoreboard); settled there.
    - Size: S/M.

18. **[Security/Dupe] Freeze and hold keyed to the player, a quick "hold bought credits" action, a chargeback chain.**
    - Change: because credits are per player, a freeze on credits applies to the player, not one profile (switching profiles must not bypass it). Staff get `/credit hold <player>` (mod+): the player's bought credits cannot be sent or spent until released, for the gap between a dispute and the staff decision. `/eco chargeback` (C1-R1-35) walks the chain from the credit order: it takes bought credits first, revokes the rank, perks and unclaimed items linked to that order, and lists receivers on the trail. Nothing is taken from receivers.
    - Why: closes the race "buyer sends credits out the moment he disputes" and the hole "the rank or perk survives the chargeback".
    - Risk: one more staff action; the hold must have an expiry reminder (24 h) so it is not forgotten.
    - Size: M.

19. **[Security/Dupe] Extend the existing balance check with a watermark.**
    - Change: the watch job already has a `balance-mismatch` rule. Add a per-currency proof: sum of balances equals sum of ledger deltas up to a stored watermark id, so it is cheap and consistent. Operations that change balances outside the ledger (profile delete backup, season rollover, the gems migration) write a ledger row with a reserved reason, or the proof would alert every time. A mismatch is a critical alert naming the first differing account.
    - Why: catches a dupe or a broken migration within the hour.
    - Risk: every non-ledger balance change must write its row (a unit test covers the known ones).
    - Size: M.

20. **[Security/Dupe] `/eco revert`: undo wrong currency changes with compensating rows, never by deleting history.**
    - Change: `/eco revert <player> <time> [currency]` (admin, tab-completed; the name avoids a clash with the coming `/rollback`, H10): preview of the reversal rows, a confirm, then new ledger rows with a reason. It never takes a balance below 0, never reverses a transfer without showing the receiver's current balance for staff review, and any row that would create `credits` is owner-only (C1-R1-18). The preview lists items bought with those credits so staff can revoke them separately.
    - Why: you asked for safe rollbacks everywhere; this is the currency part (blocks, containers and worlds are H10).
    - Risk: credits already spent on items cannot be recovered by this alone.
    - Size: M.

## Not offered here (belongs elsewhere)
- Credit price in EUR, refunds, age rules: I6. Streak with several profiles: F7. Shard numbers: C13, I3, I4. Glyph shapes and symbols: I7 (the only part kept from the symbol idea: credits are always shown exact, for example "12,500", never "12.5k", because they are real money; say yes or no).
- Open from the critic, for later cards: rank perks in the warzone (item 6 question), what happens to perks and unclaimed items at season rollover (I5), staff permission layout for `/credit` (nodes `skyblock.credit.*`, `skyblock.credit.trail`, `skyblock.credit.hold`, owner-only grant) written in the build spec.

---
# Owner answers (31 Oct 2026)
**yes to all 20.** Extra instructions:
- 1: also redo **pillars** and the whole **/is top / island level calculation**: "too easy, not how it is supposed to be, has to be even better; plan it, ask me questions, give good suggestions". Handled when B13 and B16 are reviewed (questions asked there); ledger C1-R2-21.
- 14: "and even more island upgrades, write relevant suggestions". Read as: more island PERKS bought with credits (upgrade levels stay money-only, C1-R1-38). Suggestions are in `improvements.md` section 2.
- 15: 100 credits is about 1 USD.
- 16 to 20: "go over and find improvements for each, the important must-haves and quick fixes". Done in `improvements.md`.
- Also: fix MESSAGES for every message in the game, holograms, /warp colours (good colour use, bold, appealing), menus, shop blocks from 1.21.5 (ViaVersion range): "if they can see newer blocks then sure, find out and keep me updated". Logged in `CROSS-CUTTING.md`.
- Item 6's inside question (credit sets and rank perks in warzone and duels) and the "credits always shown exact" proposal were not answered separately; the blanket "yes" is read as yes to the proposal; the warzone question stays open.

---
# Follow-up answers (31 Oct 2026)
- **Loophole question:** "We change it up: if you buy a +1 generator boost it says it is for the island you are on, on that profile; it has to be on an island (if you don't have one, the shop says so). Only some things are given physically; for now nothing other than sell chests, chunk collectors etc. Ranks, vouchers and perks can be won from crates."
- **Store currency question:** "14,000 gems will be 240 USD. Packages: 500 gems 5 USD; 1,000 (100% more) 10 USD; 2,500 + 200 extra for 25 USD; the more you buy the more you get. The top rank costs 13,500 gems total."
- **Package prices:** 14,000 credits = $130 (rate improves with size). **Physical items:** anything paid with earned credits arrives bound; only items paid fully with bought credits arrive unclaimed and tradeable.

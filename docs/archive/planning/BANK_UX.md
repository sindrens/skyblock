# Bank: easier to use (`/bank`)

**Status:** needs owner input

You said: make the bank easier, "implementing the stuff I talked about before". Below is everything we found in your answers. If you said more somewhere else (chat, Discord), please add it under "Your notes".

## What you already decided
- **169:** upgrades are paid from the **island bank only**. Higher interest, paid **every 8 hours**. Withdrawal fee **2%**, lowered by `/is` masteries as the island grows.
- **E4:** keep the 2% early withdrawal fee.
- **263 / 591:** island bank money is **deleted** on disband or reset; it is **backed up** so staff can restore it. All profiles on that island are reset.
- **588:** 2% fee, but free for 10 minutes after each payout (Suggested). **REVIEW3 R3-41** asks to drop this free window.
- **589 / 590:** island bank withdraw limits (Mod 25%, Member 10% a day) **and** "withdraw Admin only". These two clash; **R3-11** asks: Leader and Admin only.
- **593:** `/baltop` counts purse + personal bank.
- **597:** interest pauses for a profile with under 2 hours played in 7 days, AFK counted. **R3-25** asks to stop counting AFK.
- **600:** a deposit bigger than the free room deposits what fits and says so.
- **604:** frozen players and staff in staff mode can't use the bank.
- **659:** unlocking a personal bank tier costs 2% of its capacity.
- **567:** `/bank` blocked while combat-tagged and in the warzone.
- **577:** `/bal` shows a `Bank: $X` line.
- **#38:** interest needs some playtime. **#45:** perks per personal tier ("owner list", still empty). **#50:** banks wipe with money at season reset.
- **P5-11:** paying an upgrade from the island bank has no fee and doesn't cost interest.
- **P5-19:** Treasury mastery: island level 15 / 35 / 60 → fee 1.5% / 1.0% / 0.5%.
- **TUT-1:** the last tutorial step is the bank.

## What exists in the game now
- `/bank` (also the island menu's Bank button and the Banker NPC) shows **both banks side by side**: personal (left) and island (right).
- Each side: balance and capacity, Deposit, Withdraw (number picker), interest clock, history. Personal side also has Tiers; island side shows your daily withdraw limit.
- Personal bank: per profile, 10 tiers. Interest 0.6% every 8 h. Island bank: 1.0% every 8 h, capacity from the Bank Capacity upgrade.
- Interest is paid on the **lowest balance during the period**, with a cap per payout.
- Withdrawing costs 2% **and** the current period's interest, except in the 10 free minutes after a payout.
- No chat message on payouts. No typed shortcuts (`/bank deposit 5k` doesn't exist).

## The problem
The bank has too many rules for one screen:
1. Two banks at once, 12 buttons.
2. A fee **plus** lost interest **plus** a free window **plus** the lowest-balance rule. Hard to explain, easy to feel cheated.
3. Depositing or withdrawing needs the number picker every time.
4. Tiers cost money and are hidden behind a button.
5. Players never see what interest they earned unless they open history.

## Our proposal: a simpler flow
**Rules (fewer, clearer):**
- **One fee, no tricks:** withdrawing costs the fee (2%, lower with Treasury). Drop "lose this period's interest" and drop the 10 free minutes (R3-41). The lowest-balance rule already stops payout timing.
- **Island bank:** everyone on the team can deposit. Only Leader and Admin withdraw (R3-11). Upgrades pay straight from it, with no fee.
- **Interest:** every 8 h, on the lowest balance in the period, with a cap. Needs 2 h of active play in 7 days (R3-25).

**Screens:**
```
/bank  (personal, 6 rows)
 [You: $1.2M purse]
 [Bank $840k / $1M]   [Interest: +$4,980 in 3h 12m]
 [Deposit all] [Deposit half] [Deposit amount...]
 [Withdraw all] [Withdraw half] [Withdraw amount...]   "You get $98,000 (fee $2,000)"
 [Upgrade bank: Tier 4 → 5, $40k]   [History]   [Island bank →]
```
- Big **all / half / amount** buttons; no picker needed for the common cases.
- The withdraw buttons show exactly what you get after the fee.
- **Upgrade bank** button right on the page (confirm screen), with the next tier's capacity and cost.
- **Island bank** opens its own page with the same buttons (withdraw only for Leader/Admin) and a "Pay upgrades from here" line.
- **Payout line:** after each payout you get one short line the next time you are online: `Bank paid $4,980 interest.` (can be turned off in `/settings`).
- **Typed shortcuts** for Java players: `/bank deposit <amount|all>`, `/bank withdraw <amount|all>` (new commands, need your OK).

## What we still need from you
- The perk for each of the 10 personal tiers (#45), or "no perks, capacity only".
- Anything you "talked about before" that is not in the list above.

## Questions for you
1. Withdraw fee only, no lost interest on top? **Suggested: Yes.**
2. Drop the 10 free minutes after a payout (R3-41)? **Suggested: Yes.**
3. Island bank: only Leader and Admin can withdraw, no daily withdraw for Mods or Members (R3-11)? **Suggested: Yes.**
4. Interest needs 2 h of active play in 7 days, AFK pool time not counted (R3-25)? **Suggested: Yes.**
5. Show personal and island bank as two pages (with a switch button) instead of side by side? **Suggested: Yes.**
6. Add Deposit all / half / amount and Withdraw all / half / amount buttons? **Suggested: Yes.**
7. Put the "Upgrade bank" (next tier) button on the main page? **Suggested: Yes.**
8. One chat line after each payout, with a `/settings` toggle? **Suggested: Yes.**
9. Add `/bank deposit <amount|all>` and `/bank withdraw <amount|all>`? **Suggested: Yes.**
10. Personal tier perks: capacity and interest cap only, no other perks? **Suggested: Yes**, unless you send a list.
11. Add a "Send sale money to bank" toggle in `/sell` (sales go straight to your bank)? **Suggested: No.** Keeps the purse simple; one deposit click is enough.
12. Keep "Bank money deleted on disband or reset, with a staff backup"? **Suggested: Yes** (your 263/591).
13. Staff command `/eco bank <player|island>` to view and fix banks (logged)? **Suggested: Yes**, admins only.

## Your notes (write here)


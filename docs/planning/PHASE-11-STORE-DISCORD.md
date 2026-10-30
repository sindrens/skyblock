# Phase 11: Store, ranks and Discord (outline)

**Status:** needs owner input (outline only; ranks are already planned in `docs/plans/RANKS_TITLES_ACHIEVEMENTS.md`)

## What you already decided
- **ROADMAP / DESIGN §11:** Tebex store; players buy **credits**; credits buy things in game. Discord linking, announcements, tickets.
- **Ranks:** 7 donor ranks Hatchling → Immortal (RTA-2), prices 5-100 EUR (RTA-7), account-wide, never expire (RTA-6), pay the difference on upgrade (388), won season ranks (RTA-12, 387), free rank Ashborn (RTA-3), island fly from Flamecrest (RTA-18).
- **389:** ranks give convenience and some progress, "to a degree, it's donations".
- **RTA-1:** check Mojang's usage guidelines before the store opens; set progress perks to 0 if forbidden. **R3-7 / R3-8** ask to decide now (sell/XP bonus and monthly keys on paid ranks).
- **Keys are earned only** (DESIGN §9), never sold.
- **Gems:** account-wide, hidden from others (574/575), from store credits, voting (3 per vote, P10-13) and "gg" after a purchase (RW1, max 5 a day).
- **PR5:** profile slot 2 = 500 gems or a rank (old names: Knight+), slot 3 = 1,500 gems or a higher rank (old: Warlord+). The new rank names still need mapping (ranks plan).
- **P10-27 / P10-28:** premium battle pass 1,000 credits per season, once per account.
- **R5:** a broken daily streak can be saved for 100 credits within 2 days.
- **P10-38 / N2-2:** island sizes 11x11-15x15 for gems or shards (R3-19 asks shards only).
- **835 / 958 / 959:** seasonal gem skins, gem sets as Sigils (R3-14, R3-20 ask to keep paid things bound and cosmetic).
- **320:** store.skybornmc.net, discord.skybornmc.net.
- **LATER:** Discord linking, punishment webhook, appeals step 2, store/event announcements, local time display; "gg" after purchases; website stats.

## What exists in the game now
`/freerank`, LuckPerms ranks, gems as a currency (hidden), a Discord sink for punishments (P3, not connected), `/store` and `/discord` menu buttons (coming soon).

## The problem / what to decide
1. Credits **and** gems: two paid currencies may confuse players.
2. Which paid things are allowed under Mojang's rules (R3-7, R3-8, R3-14, R3-19, R3-20, R3-44).
3. Gems at season reset: kept or wiped?
4. Chargebacks and refunds.
5. Discord: which features first.

## Our proposal (milestones)
- **M11.1 Tebex link:** purchases arrive in game safely (queued, idempotent, logged), even when the player is offline.
- **M11.2 One paid currency:** the store sells **gems** directly; "credits" is just the store's word for the same thing. Gems kept across seasons.
- **M11.3 Gem shop** (`/gems`): ranks upgrades, cosmetics, skins, tags, profile slots, battle pass premium, streak save, extra loadout and wardrobe slots. Nothing that gives keys or tradeable value.
- **M11.4 Rank perks live** from the ranks plan, after the Mojang check.
- **M11.5 Purchase moments:** server line "Steve bought Firewing!" + "gg" gives 1 gem (max 5 a day).
- **M11.6 Discord:** account linking (with a small cosmetic reward), punishment and appeal webhooks, event and store announcements, ticket bot, local time display.
- **M11.7 Chargebacks:** chargeback = ranks and gem items removed + ban until settled.

## Questions for you
1. One paid currency (gems), with "credits" removed as a separate thing? **Suggested: Yes.**
2. Unspent gems are kept across seasons? **Suggested: Yes.**
3. Paid ranks give convenience and cosmetics, with sell and XP bonus at 0% (R3-7)? **Suggested: Yes.** Won ranks keep their bonuses.
4. Paid ranks give monthly cosmetics, not crate keys (R3-8)? **Suggested: Yes.**
5. Nothing bought with gems is tradeable (skins, sets, tags stay bound)? **Suggested: Yes.**
6. Island sizes above 9x9 only for shards, not gems (R3-19)? **Suggested: Yes.**
7. Chargeback = remove what was bought + ban until settled? **Suggested: Yes.**
8. Discord first: linking, punishment/appeal webhooks and event announcements; ticket bot after? **Suggested: Yes.**
9. A cosmetic reward for linking Discord (a tag)? **Suggested: Yes.**
10. Public "X bought Y" line in chat? **Suggested: Yes**, once per purchase.

## Your notes (write here)


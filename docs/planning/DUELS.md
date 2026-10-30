# Duels

**Status:** needs owner input

## What you already decided
- **666:** currencies can be **wagered in `/duels`**.
- **876:** in the warzone and duels, damage enchants at 40%, healing 50%, area and movement enchants off.
- **963:** in the warzone and duels every set counts as 1×, 50% damage reduction, only conquest bonuses.
- **822:** PvP only in the warzone (duels happen in the warzone's arenas).
- **#76 / WR1:** keep inventory on death.
- **285 / 345:** teleports in events take 3 s.
- **BUILDER_BRIEF Q8:** 4 identical 30 × 30 arenas in the warzone's south-east, spectator balcony (Suggested).

## What exists in the game now
Nothing yet. The arenas are in the builder brief. `/duel` is listed as planned. PvP normalization for enchants and sets is built.

## The problem / what to decide
Requests or queue, own gear or kits, wagers, fair rules, and no win trading (CURRENCIES_TRADING_BOOSTING.md).

## Our proposal
**Start a duel:**
- `/duel <player>` (or the player picker) opens a setup menu: wager, best of 1 or 3. The other player gets a request with Accept / Deny (Bedrock popup).
- `/duel queue` for a random fair opponent (matched by duel rating, Phase 9).
- Both teleported to a free arena. No free arena → a copy is pasted (no waiting).

**Rules:**
- Own gear, normalized (876, 963). No elytra, no ender pearls, no flying, no `/` commands except `/duel leave` (= forfeit).
- Keep inventory always. Nothing is lost except the wager.
- 3-minute rounds. At 3 minutes both get Wither I until someone dies (no draws).
- Quit or disconnect = you lose (COMBAT_LOGGING.md).

**Wagers:**
- Money, shards, crystals (if tradeable), and items. Never gems or bound items.
- Both stakes go into escrow before the fight (like `/trade`), so nothing can be lost or duped.
- Winner gets both stakes minus **5%** (a sink).
- No wagers between accounts that shared an IP in 30 days. Max 3 wagered duels per pair per day.

**Extras:**
- Spectate from the balcony or `/duel spectate <player>`.
- Duel stats (wins, losses, streak) and a season leaderboard; ranked rating with Phase 9.

## Questions for you
1. `/duel <player>` with a setup menu (wager, best of 1 or 3) and a request? **Suggested: Yes.**
2. Add a `/duel queue` for random fair opponents? **Suggested: Yes**, with Phase 9.
3. Own gear with the PvP normalization, no kits? **Suggested: Yes.**
4. Paste extra arena copies when all 4 are busy, so nobody waits? **Suggested: Yes.**
5. No elytra, pearls, flying or commands in duels? **Suggested: Yes.**
6. 3-minute rounds, then Wither I until someone dies? **Suggested: Yes.**
7. Wagers: money, shards, crystals and items, never gems or bound items? **Suggested: Yes.**
8. Stakes held in escrow before the fight? **Suggested: Yes.**
9. 5% of the pot burns as a sink? **Suggested: Yes.**
10. No wagers between same-IP accounts, max 3 wagered duels per pair per day? **Suggested: Yes.**
11. Spectating and a season duel leaderboard? **Suggested: Yes.**
12. Should duel wins give Conquest pillar XP? **Suggested: No** (easy to farm with a friend).

## Your notes (write here)


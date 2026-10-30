# Combat tag and combat logging

**Status:** needs owner input

## What you already decided
- **567:** block `/ah`, `/sell`, `/shop`, `/bank`, `/trade` and `/withdraw` while combat-tagged and in the warzone, "and also combat logging when in combat (by players)".
- **377:** `/ec` and `/craft` blocked in the warzone, boss arena and 10 seconds after combat.
- **285 / 345:** teleports are instant, but take 3 seconds while in combat, in a combat zone, the warzone, the boss arena and events. Not blocked, only delayed.
- **132:** profile switching blocked in the warzone and boss arena.
- **733:** any damage ends an open trade; no trade requests within 10 seconds of combat.
- **822:** PvP only in the warzone (and duels).
- **WR1:** warzone death = keep inventory, lose 2% of purse money (max $1M). **682:** purse only. **R3-42** asks: killer gets half.
- **#76:** PvE death = keep inventory, lose nothing.

## What exists in the game now
- `CombatTagService` in common: **10 s** tag after dealing or taking damage from a player **or a hostile mob** (`core.yml → combat.tag-seconds`, `combat.mobs`).
- Used by loadouts, wardrobe, portals, `/ec` and `/craft`, teleport warmups.
- No combat-log penalty. No tag display. Economy commands are not blocked yet.

## The problem / what to decide
1. The tag counts mobs too. Blocking `/sell` while fighting Depths mobs would annoy miners and fighters there.
2. What happens when a player quits during a PvP fight?
3. How does the player see they are tagged?

## Our proposal
**Two tags:**
- **Mob tag (10 s):** only slows teleports (3 s) and blocks loadout swaps and profile switching, as today.
- **PvP tag (15 s):** from hitting or being hit by a player. Blocks `/ah /sell /shop /bank /trade /withdraw /ec /craft /pay`, profile switching, loadout swaps, flying and elytra. Teleports take 3 s.
- The warzone itself blocks the same economy commands, tagged or not (567).

**Show it:** action bar `⚔ In combat 12s` (Bedrock-safe), and a short line when it ends: `You're out of combat.`

**Combat logging (quit while PvP-tagged):**
- Counts as a **death by the last attacker**: 2% purse loss (max $1M), a death and kill on the stats, killer reward if R3-42 is yes.
- Short public line in the warzone only: `Steve logged out in combat.`
- In a duel: the one who quits loses the duel and the wager (DUELS.md).
- **No penalty** when the server stops, restarts or crashes, or when staff kick the player.
- 3 combat logs in 24 hours → staff alert.
- No NPC body left behind (keep inventory makes it pointless and it costs performance).

## Questions for you
1. Two tags: a 10 s mob tag that only slows teleports, and a 15 s PvP tag that blocks economy commands? **Suggested: Yes.**
2. PvP tag blocks `/ah /sell /shop /bank /trade /withdraw /ec /craft /pay`, profile switch, loadouts, fly and elytra? **Suggested: Yes.**
3. Keep economy commands blocked everywhere in the warzone, tagged or not (567)? **Suggested: Yes.**
4. Show `⚔ In combat 12s` in the action bar? **Suggested: Yes.**
5. Quitting while PvP-tagged counts as a death by the last attacker (2% purse, max $1M)? **Suggested: Yes.**
6. Public "logged out in combat" line, only to players in the warzone? **Suggested: Yes.**
7. No penalty on server stop, crash or staff kick? **Suggested: Yes.**
8. Staff alert after 3 combat logs in 24 hours? **Suggested: Yes.**
9. No NPC body for logged-out players? **Suggested: Yes.**
10. PvP tag length 15 seconds? **Suggested: Yes.**

## Your notes (write here)


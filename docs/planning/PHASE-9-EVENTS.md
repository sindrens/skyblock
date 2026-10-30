# Phase 9: Conquest and events (outline)

**Status:** needs owner input (outline only; the full plan `docs/phases/PHASE-9-*.md` comes after your answers)

## What you already decided
- **ROADMAP:** KOTH, envoys, PvP zones, duels, automatic event engine.
- **DESIGN §8 / §10:** KOTH gives Conquest XP to the capturer's island; envoys are scheduled supply drops; islands always safe; fairness for new players (low-level events). Automatic events: treasure hunt, money event, farming event, mining rush, double XP. **No** drop parties, **no** build contests.
- **Ideas 75-81:** KOTH, envoys, island competitions, treasure hunts, boss events, fishing competitions, chat games: yes. **82 lottery: no.**
- **EV1:** island competitions rotate weekly (most crops, mined, mobs, sold), top 3 get keys. **EV2:** fishing contest every 4 h, 15 min. **EV3:** chat games every 20 min, $5k.
- **#43:** shards come from bosses, envoys, KOTH and high Depths tiers.
- **CA7:** the KOTH crate is the **Conqueror Crate**.
- **P5-8:** the Conquest pillar is hidden until KOTH or events exist.
- **345 / 285:** teleports in events take 3 s.
- **WR1:** warzone death = keep inventory, lose 2% purse (max $1M). **R3-42:** killer gets half?
- **413:** warzone only through its portal at the castle.
- **DESIGN §1:** Final Week has double Conquest XP + extra KOTHs.
- **WORLD:** the Gatehouse has an event board (next KOTH / envoy / boss timers).

## What exists in the game now
The warzone world is built (KOTH keep, envoy field, 4 duel arenas, safe zone). Combat tag service, PvP normalization for enchants and sets, `/broadcast`, Conquest pillar hook (hidden). No event code.

## The problem / what to decide
How often, what rewards, how new players can join in, and how events fit a 300-player server without lag.

## Our proposal (milestones)
- **M9.1 Event engine:** one scheduler for every event (config times, Europe/Oslo), boss bar countdowns, event board at the Gatehouse, `/events` menu with what's next and rewards, chat via CHAT_BROADCASTS. Never two big events at once.
- **M9.2 Warzone rules:** PvP rules, combat logging (COMBAT_LOGGING.md), killer reward, safe zone, kill streak messages.
- **M9.3 KOTH:** 2-3 a day, 10 min capture in the keep's courtyard; Conqueror key + shards + Conquest XP to the island; unhides the Conquest pillar.
- **M9.4 Envoys:** every 2 h, 20-30 crates across the field, loot tiers.
- **M9.5 Duels:** see DUELS.md.
- **M9.6 Automatic events:** treasure hunt, money event (+sell), farming event, mining rush, double XP. Each 15-30 min, island-safe.
- **M9.7 Island competitions (EV1)** weekly, **M9.8 chat games (EV3)**, **M9.9 fishing contests (EV2,** with Phase 8 M8.9).
- **M9.10 Parties:** see FRIENDS_PARTIES.md.
- **M9.11 New-player fairness:** "Rookie" events and a separate rookie KOTH? Or events that don't need gear (chat games, treasure hunt).
- **M9.12 Balance pass** (shards and keys per week vs. ECONOMY_BALANCE).

## Questions for you
1. One event engine with an `/events` menu and the Gatehouse board? **Suggested: Yes.**
2. KOTH 2-3 times a day, 10-minute capture? **Suggested: Yes.**
3. Envoys every 2 hours? **Suggested: Yes.**
4. Automatic events every ~2 hours (treasure hunt, money, farming, mining rush, double XP), never at the same time as KOTH? **Suggested: Yes.**
5. Warzone killer gets half of the victim's 2% loss, once per pair per hour (R3-42)? **Suggested: Yes.**
6. Rewards: Conqueror keys, shards and Conquest XP from KOTH; shards and keys from envoys? **Suggested: Yes.**
7. Events for new players that don't need gear (chat games, treasure hunt) instead of a rookie KOTH? **Suggested: Yes.**
8. Keep EV1-EV3 as decided (weekly competitions, fishing every 4 h, chat games every 20 min)? **Suggested: Yes.**
9. Kill streak messages in the warzone only? **Suggested: Yes.**
10. Anything you want in events that's not listed? Write below.

## Your notes (write here)


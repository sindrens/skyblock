# Phase 12: Seasons (outline)

**Status:** needs owner input (outline only)

## What you already decided
- **DESIGN §1:** seasons last **3 months**. Pre-season: 1 week with faster progress (global boost), then a full wipe. Final Week: double Conquest XP + extra KOTHs.
- **Wiped:** islands, outposts, levels, pillars, skills, tools, money, crystals, shards, items, loot. **Kept:** titles, cosmetics, ranks (gems: open, see PHASE-11).
- **414:** wipe island and outpost worlds only; keep Depths, spawn and warzone worlds (reset player data).
- **354 / RTA-44:** achievement progress and boosts reset each season; titles stay.
- **387 / RTA-12:** won ranks last one season.
- **P10-43:** unused keys are wiped with a warning in the Final Week. **N2-3:** `/box` keeps items until the reset. **563:** AH collection bin never expires during the season.
- **P5-14:** island sizes above 9x9 last one season. **959:** gem sets claimed free once per season.
- **V2:** island value history saved after the season; you can export it.
- **RTA-32:** "The First Flame" title for 1 h played in Season 1's first week.
- **DESIGN §1:** season rewards in store credit (solo 150/100/50, team 400 split by contribution).
- **411:** backups every 6 h, 7 days, off-site, before every update.

## What exists in the game now
Season name in the tab list and scoreboard (`season` config). Profiles, backups, audit log. No season service or reset tool.

## The problem / what to decide
A reset is the riskiest thing the server does: it must be one tested, logged, undoable process.

## Our proposal (milestones)
- **M12.1 Season service:** season number, start/end dates, phase (pre-season, live, Final Week, ended), shown everywhere.
- **M12.2 Final Week:** warnings (keys, box, sizes), double Conquest XP, extra KOTHs, leaderboards locked at the end time.
- **M12.3 End of season:** freeze economy and trades, final snapshot, export leaderboards and island value history (CSV), give titles, won ranks and store-credit prizes.
- **M12.4 Reset tool:** full backup first, then wipe by a checked list, keep by a checked list, reset island/outpost worlds. Dry run mode that only counts what it would change. Owner-only, two-step confirm.
- **M12.5 Pre-season:** opens with the global boost for one week, then wipes again (or not, see question 2).
- **M12.6 Carry-over check:** titles, tags, cosmetics, ranks, gems, unclaimed won ranks, battle pass premium per season.

## Questions for you
1. Season length 3 months? **Suggested: Yes.**
2. Pre-season: one week with faster progress, then a full wipe before the season starts? **Suggested: Yes.** (Or skip the pre-season wipe and let it flow into the season: write below.)
3. Final Week warnings for keys, `/box`, bought island sizes and unspent money? **Suggested: Yes.**
4. Reset tool with a dry run first, owner-only, two-step confirm? **Suggested: Yes.**
5. A full off-site backup before every reset, kept for the whole next season? **Suggested: Yes.**
6. Unspent gems kept, everything earned wiped? **Suggested: Yes.**
7. Season prizes as store credit (gems) as in DESIGN §1? **Suggested: Yes.** Team places 2 and 3 still need numbers: write below.
8. Export each season's leaderboards and island history for you? **Suggested: Yes.**
9. A "Season N veteran" title for everyone who played 10 h in a season? **Suggested: Yes.**

## Your notes (write here)


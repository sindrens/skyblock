# Progress and Depths pace · id: PACE · status: Review 1 · version: 1

New card (31 Oct 2026, cluster ECON-RANKS-PACE). Owner: "ask me more about Depths and progress: how fast should it progress before we start this big implementation; make the tutorial fast progress and more levels, it has to feel rewarding just like everything else." This card sets **how fast** things progress and **how it feels**, so E1/E3 (Depths v2), B13/B14 (island level, upgrades), E9 (skills) and A3 (tutorial) are built to one pace. Linked: E1, E3, E9, E10, E12, B13, B14, A3, F1, F5, F6, F7, C14. Questions: EP-Q18 to EP-Q27 in `questions/ECON-RANKS-PACE.json`. Nothing is locked; all numbers are model estimates (`tools/economy_model.py` must be rerun before lock).

## In one sentence
Every system should hand out a visible reward often at the start, ease off to a steady rhythm in the middle, and keep a reward at least every 45 minutes in the late season, all inside the existing 3-month economy.

## How it works

### Reality check: how long things take today
Sources: `ECONOMY_BALANCE.md` 2.1 to 2.6, `skills.yml`, `tools.yml`, `pillars.yml`, `tutorial.yml`, `upgrades.yml`, `masteries.yml`, `tasks.yml` and a single-skill simulation of the same curves (regular player, 3 h per day, Mining only; "earliest" = skill gate, the Forge can add up to 3 h at tiers 6 and 8). A season is 3 months (about 90 days): a casual plays 1 h per day, a regular 3 h, a grinder 8 h.

| Milestone (regular, played hours) | Today |
|---|---|
| Tutorial | 11 steps, 2 min 50 s by design, reward $2,500 + 25 crystals + title Firstflight (one hour of play pays $53,000 to $60,000). The `mine` step needs 15 Mining XP of the 600 for Mining level 1: **the tutorial gives 0 level-ups.** |
| First level-up of any kind | tool level 2 at about 12 min, **Mining level 1 at 21 min**, first pillar level at about 8 min |
| First hour | Mining 2, tool 3, pillar 5, island level 1, 6 to 9 island upgrade levels bought ($53,000 income). **No unlock** (Depths tier 2 needs Mining 5) |
| Depths tier 2 / 3 / 4 / 5 | 2.2 h / 5.3 h / 9.2 h / 14.5 h (day 1, 2, 3, 5) |
| Depths tier 6 / 7 / 8 | 22.4 h / 34.5 h / 52.5 h (day 8, 12, 18) |
| Depths tier 9 / 10 | 93 h (day 31) / 160 h (day 54); tiers 11 and 12 not this season (grinder: tier 12 on day 51) |
| Mining level 5 / 10 / 20 / 30 / 50 | 2.3 h / 5.5 h / 14.5 h / 32 h / 142 h |
| Time per Mining level | 21 min (level 1), 40 min (levels 5 to 10), 1 h (10 to 20), 1.8 h (20 to 30), 3.8 h (30 to 40), 7.2 h (40 to 50) |
| Tool level 30 (3 perk slots) | 30 h (day 10); tool level 41 at month 1 and 50 at season end (grinder: 50 at month 1) |
| Island level (points divided by 10) | about 1 at 1 h, 3 at 4 h, 15 at week 1, 38 at month 1 (91 h), 70 at season end (casual 40, grinder 105) |
| Island upgrade levels / mastery ranks | 161 levels in 18 upgrades, 67 ranks in 13 masteries, then endless Ascension; a regular owns about 96 levels at month 1 |
| Challenge book | 180 tasks in 12 tiers |
| Popups per hour (level-up, upgrade, unlock) | first hour 11 (one per 5.5 min), day 1 13 (4.6 min), week 1 6 (10 min), month 1 1.9 (32 min), month 2 to 3 0.6 (**98 min**) |

**Where the grind is flat**
1. **The first 8 minutes have no level-up** (the tutorial gives none), the first tool level comes at 12 min, and the skill that unlocks things (Mining 1) only at 21 min. The first unlock (tier 2) comes at 2.2 h.
2. **Month 2 to 3 is the flat part:** one popup per 98 minutes. Tier 10 arrives on day 54, the tool is at level 48 to 50, a skill level takes 4 to 10 hours, and what is left is masteries, Ascension and achievements.
3. **Casual players (1 h per day) wait days between unlocks:** tier 2 on day 2, tier 4 on day 12, tier 8 on day 68; a tier unlock every 5 to 12 days after the first week.
4. **Grinders run out of tiers:** tier 12 on day 51, then 40 days of endless sink only.
5. **Island level moves in steps of 10 points**, so the number rises slowly even when the island grows fast.

### Three pacing models (regular player; the late season is the same in all three)
All three keep the sheet's late economy (tier gaps growing x1.3 to x2.2, the Forge, sets, Ascension) and change only the early shape, the number of levels and the reward rhythm.

| Stage (played hours) | Fast and generous | **Balanced (recommended)** | Slow burn (today) |
|---|---|---|---|
| Tutorial length and reward | 11 steps + 5 quick wins, 6 min; $10,000, 75 crystals, Ember key, 8 level-ups | 11 steps, 4 to 5 min, a small reward on every step; finish $7,500, 50 crystals, bound Ember key, 5 level-ups | 11 steps, 3 min; $2,500 and 25 crystals; 0 level-ups |
| Level-ups during the tutorial | Mining 2, tool 3, pillar 3, island 1 | Mining 1, tool 2, pillar 2, island 1 | none |
| Mining level at 1 h / 4 h / 22 h / 91 h / 270 h | 6 / 15 / 30 / 45 / 61 | 4 / 12 / 28 / 44 / 60 | 2 / 8 / 25 / 43 / 60 |
| Tool level at 1 h / 4 h / 22 h / 91 h | 7 / 16 / 30 / 44 | 5 / 13 / 28 / 43 | 3 / 9 / 26 / 42 |
| Island level at 1 h / 4 h / 22 h / 91 h / 270 h | 5 / 13 / 50 / 128 / 233 (points / 3) | 3 / 8 / 30 / 77 / 140 (points / 5) | 1 / 4 / 15 / 38 / 70 (points / 10) |
| Depths tier 2 / 4 / 6 / 8 / 10 (earliest hours) | 0.75 / 4.0 / 12.7 / 36.5 / 130 | 1.1 / 5.6 / 16.8 / 43 / 137 | 2.35 / 9.4 / 22 / 48 / 142 |
| First island mastery rank | same day | day 1 | day 2 |
| Level-ups and unlocks in the first hour (count) | 29 | 20 | 11 |
| Reward at least every (first hour / day 1 / week 1 / month 1 / month 2 to 3) | 2 / 4 / 8 / 15 / 30 min | 3 / 6 / 12 / 25 / 45 min | 5.5 / 4.6 / 10 / 32 / 98 min (measured) |
| Levels (shown numbers) | island 233, skills 150 (tiers stop at 66), tool 50, 161 upgrade levels | island 140, skills 150, tool 50, 161 upgrade levels | island 70, skills 150, tool 50, 161 upgrade levels |
| XP and cost curve | first levels cost 25% of today, rising linearly to 100% at skill level 35, tool and pillar level 30 | first levels cost 40%, rising to 100% at skill level 25, tool and pillar level 20 | as built: skill 600 x 1.2^L to level 35, x1.13 to 66; tool 400 x 1.21^L; pillar 250 x 1.12^L |
| Casual (1 h per day) reaches Depths tier 8 | about day 45 | about day 55 | day 68 |
| Effect on season income (estimate) | +12% to +18% (tiers reached 15% to 35% earlier) | +5% to +10% | 0 |
| Risk | early game feels like a sprint, then a cliff at tier 6; grinders reach the end by day 40 | small; the sheet's late economy is untouched | the owner's complaint stays |

Why the late columns are equal: a skill level costs 600 x 1.2^L XP up to level 35 and 1.13 per level up to 66, so by level 25 the discount is gone and the time is set by the same big numbers.

**Recommendation: Balanced.** It removes the flat first minutes (no level-up for 8 min today), gives a reward every 3 minutes in the first hour, doubles the island level numbers so every point of growth shows, and leaves the late economy of the sheet as it is. Fast is a good tutorial and a rough season (nothing left to chase on day 40); Slow keeps the feeling the owner dislikes. The late flat part (month 2 to 3, one popup per 78 minutes even in Balanced) is closed by the toolkit below and by smaller Ascension, mastery and achievement steps, not by faster tiers.

### Island level: more levels, same island
Island level = floor(points / divisor), `level.divisor` today 10. Balanced sets 5: the number doubles (140 at season end for a regular, 80 casual, 210 grinder) and every level is worth half the work. The thresholds of island titles and achievements (Architect at 50, "Rising Island" at 10 / 25 / 50 / 75 / 100) double with it (ripple to F3 and B16). Leaderboard order does not change (the order of islands is the same).

### Rewards: the "reward feeling" toolkit (as simple as possible)
Three sizes, one service, nothing new to learn:

| Size | When | What the player gets |
|---|---|---|
| **Small** | every normal level-up (skill, tool, pillar, island level, upgrade bought) | action-bar line with a dot bar `Mining 7 · next: Copper at 10 ●●●●●○○○○○` + a soft level sound; nothing in chat |
| **Medium** | every 5th level, a new Depths tier opens, first time something happens, a mastery rank | small title (at most 20 characters) + the unlock sound + one ring of flame particles around the player + a chat line with the next goal |
| **Big** | every 25th level, island level x10, a tier 8 or higher unlock, the first of a mastery tree branch | big title with a short fanfare + a flame burst + a chat line to the island (and global at island levels x10) + a small crystal gift |

- **Progress bar:** the one progress line on the scoreboard `Next: Mining 8 · 62%` (G7-R1-01 allows one progress line); the existing action-bar dot bar after each gain. No new boss bars (the tutorial owns the yellow bar).
- **Milestone popups:** one per goal, at 50% and 100% of the next big goal only (no spam).
- **Rate limit:** at most one title per 3 seconds per player (the biggest wins, the rest becomes chat lines), sounds merge, particles once.
- **Switch:** `/settings` "Level-up effects: Full / Quiet / Off" (default Full); Quiet = action bar only.
- **Bedrock-safe:** text, vanilla sounds and vanilla particles; titles up to 20 characters; no custom font.
- **Late season:** the same toolkit, with Ascension, mastery, achievement and collection steps counted as Medium so a Medium moment arrives at least every 45 minutes.

### Tutorial: fast progress and more levels
- **Every step pays something** visible in the boss bar flow (a few hundred money, a few crystals, or XP) with a Small popup; the finish is a Medium: title "Tutorial complete", the key and the money.
- **The tutorial gives the first levels:** the `mine` step grants Mining XP for level 1 and tool XP for level 2; the `chest` step the first pillar level; the finish island level 1. These are one-time gifts to the profile finishing the tutorial (progress belongs to the profile), paid with the step flag in one transaction.
- **Length:** 11 core steps stay (TUT-1 about 3 minutes); the real first-run time is 4 to 5 minutes because of the cameras and the walk; the XP and rewards make it feel like progress, not a lesson.
- **Optional steps O1 to O9** keep 5 crystals each and become Small popups.

### Catch-up for late joiners (proposed)
A profile created after day 14 of a season gets a **Rookie boost**: skill, tool and pillar XP x1.5 for its first 10 played hours (fading to x1.0), counted inside the skill XP family cap. No money, no items, nothing tradeable. It never touches leaderboards of earlier islands.

## What the player sees
- First minute after the tutorial: Mining level 1, a tool level, island level 1, "Next: Mining 2 · 0%" on the scoreboard.
- First hour: a small popup every 3 minutes and one big moment (Depths tier 2 at about 1 hour).
- Island menu: island level with the new numbers; the level list shows the next 3 goals.
- `/settings`: "Level-up effects".

## Rules and limits
- No waiting or timers (principle 1); levels follow play only; credits never buy levels or XP (C1-R2-06).
- Progress is per profile (profiles are islands); the Rookie boost and tutorial gifts are per profile.
- A rank never gives XP or levels (R3-7, RTA-20).
- Seasons reset skills, tool progress, pillars and island level; titles stay (354).
- Everything is config: curve discount, level divisor, tutorial rewards, popup sizes and limits.

## Developer decisions (defaults; the owner only vetoes)
- **One `RewardFx` service in `common`.** Listens to the existing events (skill, tool, pillar, island level, tier unlock, upgrade, mastery, achievement); size rules in `rewardfx.yml`; rate limiter and per-player setting `levelfx` (full, quiet, off). Sounds from `sounds.yml` (`notify`, `unlock`), titles from `messages/`. No per-module popup code after this; the modules only raise events.
- **Curves.** New keys `curve.early-discount` and `curve.early-levels` in `skills.yml`, `tools.yml`, `pillars.yml` (discount d(L) = start + (1 - start) x L / early-levels, 1 after); defaults of the chosen model; `level.divisor` in `progression` (5 for Balanced). Levels are recomputed from stored total XP at load, so a changed curve moves players up and never down; one toast "Your levels were updated".
- **Tutorial rewards** as a `rewards` list per step in `tutorial.yml` (money, crystals, skill XP, tool XP, key), paid in the same transaction as the step flag with key `tutorial_step:<uuid>:<n>`; the finish reward keeps its existing key. Staff skip gives nothing.
- **Rookie boost** as an XP family member with its own tiny cap; reads the profile's season day and played hours.
- **Economy.** `tools/economy_model.py` is rerun with the new curves (earlier tiers) and must keep the sink ratio at 80% or more and the spawner and island shares in their bands (C14).
- **Tests (no server).** Curve monotone and joins today's curve at the early-levels point, level recompute never lowers a level, tier times of the model match the table, popup size rules, rate limiter, tutorial reward atomic and idempotent, Rookie boost fades to 1.0, `level.divisor` change keeps island order.
- **Performance.** Event-driven only, no scans; the particle ring is one packet burst per player per Medium or Big moment.
- **Staff.** `/admin` edits curves and the divisor with a preview of the model's hours per stage; `/skills set|add` stays admin and owner.

## Questions for the owner
EP-Q18 which pacing model (Balanced), EP-Q19 tutorial length and reward, EP-Q20 levels given by the tutorial, EP-Q21 time to the first new Depths tier (about 1 h), EP-Q22 how many island levels (points / 5), EP-Q23 reward rhythm, EP-Q24 how big a level-up moment is, EP-Q25 the endless late game, EP-Q26 late joiners, EP-Q27 how far a casual player gets. All in `questions/ECON-RANKS-PACE.json`.

## Connections
- Depths zones (E1) and tiers (E3): the tier unlock hours in the table; E3 open question "unlock numbers in four places" stays.
- Skills (E9): the early XP discount; "crystals on every level-up" (#924) uses the Medium moment.
- Tools and Forge (E10), enchants (E12): tool level discount; first enchant slot at tool level 5.
- Island level and pillars (B13): divisor 5 and the pillar discount; upgrades and masteries (B14): first mastery rank earlier, Ascension steps as Medium moments.
- Tutorial (A3): step rewards and the first levels. Progression track (F1): the track steps are the same moments. Crates (F5), votes (F6), daily rewards (F7): the Ember key at the tutorial finish.
- Scoreboard (G7): the one progress line. Economy curve (C14): rerun.

## Decided
- #61: fast at first, progressively harder, with ups and downs. TUT-1: 11 core steps. TUT-7: finish reward (this card proposes to raise it; EP-Q19).
- G7-R1-01: one progress line on the scoreboard. A3-R1-02: the tutorial boss bar is yellow, fill = steps done.
- C1-R2-17: visible gain popups in the action bar. C1-R2-06: credits never grant levels or XP. R3-7: ranks never give XP.
- Principle 0 (fun first, less is more) and Owner Profile 1 (fun, visible progress).

## Open questions
Moved into EP-Q18 to EP-Q27. Developer defaults not asked: keep the 12 Depths tiers and tier unlock levels (E3); keep the Forge costs; keep skill max level 150.

## Rejected ideas
- Faster late tiers to fill the flat late season: breaks the sheet's economy (tier gaps, sink ratio); the toolkit and smaller late steps are used instead.
- A new XP bar or a separate "rank-up" ladder (DESIGN 9): island level and skills already are the progression (F1).
- Paid XP, paid levels or paid boosters of levels (C1-R2-06).
- Popups on every action (spam): one size per moment, rate limited.

## Change log
- Review 1 (31 Oct, ECON-RANKS-PACE): new card. Reality check, three pacing models, recommendation (Balanced), island level divisor, reward toolkit, tutorial rewards, catch-up, developer decisions, questions EP-Q18 to EP-Q27. Not locked.

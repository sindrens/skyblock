# Improvement plan: the farming hoe (7 Nov 2026)

How we plan from now on (owner, 7 Nov 2026): for every part we work on, Claude brings a list of suggested changes
(`<PART>_SUGGESTIONS.html`, one click Yes / No / Other each); the answers are written down here; nothing is built
before the answers. First part: the island hoe (/farm and island crops).

## Decided (owner answers, round 1)
1. **Breakthroughs become Milestones.** No waiting for 2% / 10% / 30% / 100% any more: every level is stronger (steady
   growth) **and** every 4% of the track an **extra +10%** that stays (25 milestones per enchant: every 100 levels on a
   2,500 track, every 40 on 1,000, every 20 on 500; about x11 at the max on top of the steady growth).
2. Those numbers: +10% every 4% of the track.
3. Area enchants (Explode, Row Reaper, Tornado, Meteor Shower): a milestone raises the **chance** by 10%, not the blocks
   (no lag).
4. A small moment at each milestone: action bar + sound, a title only at every 5th. The tile shows "Next boost: +10% at
   level 300 (14 to go)".
5. Shard Finder gets milestones too: +10% extra every 25 levels (10 up to level 250), on top of its +3% a level.
6. **Buy Max** button next to x1 / x10 / x100 (as many levels as your shards pay for).
7. **Turn an enchant on or off** in the menu (levels stay).
8. **One action bar line for procs** (all procs of a second together, not one replacing the last).
9. Owner: **crops can always be broken, also with a full bag and a full storage** (no pause at /farm any more). What
   happens to the drops is question 1 on `HOE_SUGGESTIONS_2.html` (suggested: what does not fit is sold right away at the
   Auto-Sell price).

## Steady growth must feel progressive (owner: "the Shard Burst example is weird, why is it so slow?")
The compound curve of update164 is slow at first (Shard Burst 100 → 111 at level 100) and only fast near the end. New
rule (suggested, question 2): **steady growth = +1% of the level-1 value every level, in even steps**, times the
milestones:

| Shard Burst level | 1 | 10 | 50 | 100 | 250 | 500 | 1,000 | 2,500 |
|---|---|---|---|---|---|---|---|---|
| update164 (compound) | 100 | 101 | 105 | 111 | 131 | 172 | 295 | 1,500 |
| **suggested (+1% a level + milestones)** | 100 | 109 | 149 | 219 | 422 | 965 | 2,851 | 28,159 |
| half as fast (+0.5% a level + milestones) | 100 | 104 | 125 | 164 | 272 | 563 | 1,555 | 14,621 |

Every enchant uses the same rule (its own level-1 value). Chances keep growing evenly to their cap; area enchants keep
their block counts and get the milestone chance boost.

## Decided (owner answers, round 2)
10. **Full bag and full storage:** crops are still broken; what does not fit **drops on the ground for that player only**
    (other players can't see or pick it up; it disappears after the normal item time).
11. **Pick your crop at /farm:** a button to farm any crop you have unlocked, not only the newest.
12. **Hot Crop of the hour:** one crop sells +50% at /farm for an hour (one chat line, a scoreboard line).
13. **Fresh Field:** your first 2,000 crops each day give double shards.
14. **Crop Golem:** every 20-40 minutes a hay golem walks over /farm; everyone hits it with the hoe; it bursts into crops
    and shards, the top 3 hitters get a key; gone after 2 minutes.
15. **Combo Fever:** at a x500 combo the field around you glows gold for 10 seconds and every crop counts double (drops,
    shards, progress), then the combo starts again.
16. **Lucky Clover:** a very rare drop (about 1 in 20,000 crops); right-click for 60 seconds of x2 enchant chances;
    tradeable (auction house).
17. **Fertilizer:** Harvest Basket sometimes gives Fertilizer: right-click an island crop and the 5x5 around it is ripe.
18. **Hoe titles:** Farmhand (prestige 1), Harvester (10), Field Lord (25), Harvest King (50), Reborn Reaper (first rebirth).
19. **Juicy hits:** big procs pop with particles and a rising sound, only for you.
20. **Milestone progress bar** on each enchant tile.
21. **Top Harvesters board** at /farm: the top 10 this week, no prizes, resets on Monday.

## Decided (owner answers, round 3)
22. **Combo counter** in the action bar ("x84 · +8.4% shards").
23. **Next crop progress:** the /farm scoreboard already shows it; the tool menu's hoe tile shows "Carrots: 1,240 / 3,000"
    too (the item lore can't change on every crop).
24. **Sweep without a prestige:** unlocks at tool level 30.
25. **Hoe levels stay 15x slower** (crops break much faster than blocks). Later, own plan: mining easier and faster, a
    cheaper Efficiency.
26. **Storage warning at 90%:** one action bar line, the storage tile turns orange.
27. **Prestige reward:** a Blaze key every prestige, a Phoenix key every 10th (every tool, the same rule).
28. **Shard bonus cap higher than +300%** ("we will have a high economy"): **+1,000%**.
29. **Growth: "something better"** (Claude's pick): a **fast start that keeps going**. Steady growth follows a curve that
    gives the most per level at the start and still grows to the end (shape 0.7), plus the milestones:

| Shard Burst level | 1 | 2 | 10 | 50 | 100 | 250 | 500 | 1,000 | 2,500 |
|---|---|---|---|---|---|---|---|---|---|
| even steps (+1% a level) | 100 | 101 | 109 | 149 | 219 | 422 | 965 | 2,852 | 28,170 |
| **built: fast start** | 100 | 110 | 147 | 253 | 385 | 699 | 1,412 | 3,536 | 27,087 |

    Level 2 is already +10%, level 10 +47%, level 100 almost 4x, and the milestones make the second half fly.
    Milestones raise: the amount (shards, items, money, progress, XP, streak and echo bonuses, crystals, rain, Golden
    Touch and Duplicator drops); the chance (area enchants, Shard Storm, Frenzy, Booster Find, Geode/Basket); nothing
    (Keyfinder stays rare, Lucky Streak, Chain Reaction).

## Releases
- **update165, the enchant core:** 1-9, 22-29 (milestones, the growth curve, Buy Max, on/off, one action bar line,
  combo counter, full bag drops for you only, Sweep at level 30, +1,000% cap, prestige keys, hoe titles, juicy hits,
  milestone bar, Shard Finder milestones, next crop on the hoe tile, storage warning).
- **update166, /farm fun:** 11-17 and 21 (pick your crop, Hot Crop, Fresh Field, Crop Golem, Combo Fever, Lucky Clover,
  Fertilizer, Top Harvesters board).

# Plan: Tools v6, compound growth (7 Nov 2026)

## Owner answers (7 Nov 2026) and what was built (update164)
1. Shard Finder: "a lot smaller compound, maybe 3%, and the default shards higher than 6": **+3% compound a level, 250
   levels (about 1,600x at the max), 10 shards a block** (was 6; rod 300 a catch, sword 80 a kill), always rounded up.
   Its price compounds too (+3.5% a level from 150), so a level costs about the same number of blocks from start to end.
   The GUI shows only the level.
2. "50% less sell price at level 1": not my reading. "I'm at tier 1 in wheat and it already makes thousands" = **every
   /farm crop and /mine ore sells for half** (wheat $200, nether wart $1,200, coal $500, diamond $3,050, /mine emerald
   $4,100; island crops and generator ores use the same prices).
3. More enchants, not hidden behind many prestiges: **9 new enchants**, pickaxe and hoe 26, rod 19, sword 15. **No
   prestige gates**; unlocks over tool levels 1-48, closer together near the top; an unlock reached once stays open after
   a prestige (lifetime levels). The level cap grows with lifetime levels too: max x (lifetime / 100)^1.5 (level 50 at
   prestige 0 opens about a third, prestige 2 the whole track). Every other point: the suggested answer below.


Owner, 7 Nov 2026: Shard Finder +10% a level **compounding** (6 → 6.6 → 7.26 ..., shards always rounded up), no "how
much more" in the GUI; the same compound logic on the enchants and wherever it is fun; enchants 500-2,500 levels by
relevance; Auto-Sell bought once and kept forever (prestige too); Shard Burst gives more and compounds; more enchants
(35); a nice enchant GUI with unlock levels that make sense (faster progress the higher you get); "50% less sell price at
level 1"; fix the small notes. Principle 0: simple, fun, fast. Open points are on `TOOLS_V6_QUESTIONS.html`; the
suggested answers are written below and are what gets built when you say yes.

## 1. Compound growth: the one rule
- **Effect at level L = base x (1 + rate)^L, rounded up** (shards, items, money: whole numbers, always up).
- Shard Finder: base = the shards a block gives (6 for the hoe and pickaxe), rate **10%**:

| Level | 0 | 1 | 2 | 5 | 10 | 25 | 50 | 75 | 100 |
|---|---|---|---|---|---|---|---|---|---|
| Shards a find | 6 | 7 | 8 | 10 | 16 | 66 | 705 | 7,632 | 82,684 |

- **Why Shard Finder can't keep 2,500 levels at +10%:** 1.1^2500 is a number with 104 digits. Suggested: **100 levels**
  (Q1). Every perk and enchant price in shards compounds as well (Q2), so the next level always takes about the same time
  to earn and the tool track never "breaks".
- The GUI shows only the level ("Shard Finder 37"), no percent (the "+111%" line from update156 goes).

## 2. Enchants: 500-2,500 levels, compound values
- **Max level by relevance:** everyday ones **2,500** (Blast/Explode, Shard Burst, Scholar, Rich Vein/Bountiful, Quick
  Study, Combo), mid ones **1,000** (Cash Vein/Crop, Compound Interest, Shard Echo, Lightning/Row Reaper,
  Motherlode/Golden Crop, Lucky Streak), rare ones **500** (Geode/Harvest Basket, Keyfinder, Meteor/Tornado,
  Nuke/Meteor Shower, Shard Jackpot). Rod and sword the same way.
- **Values compound:** from level 1 to max the effect grows **x15** (per level: +0.11% for 2,500 levels, +0.27% for
  1,000, +0.54% for 500), every level a little more than the last. Chances keep growing evenly up to their cap.
  Breakthroughs (x1.5 / x2 / x2.5 / x3) stay on top.
- **Prices compound:** the max level costs about 20,000x level 1.
- Players keep the levels they have (a level above the new max is set to the new max).

## 3. Shard Burst: more, and compounding
- Level 1 gives **100 shards** (was 30), compounding to **1,500** at level 2,500 (x15), before breakthroughs and Shard
  Finder. Chance 0.4% → 1% as now. Rod and sword the same (per catch / kill).

## 4. More enchants (Q4, Q5)
- New ones (pick on the question page): Shard Storm, Frenzy, Golden Touch, Duplicator, XP Surge, Crystal Find, Booster
  Find, Chain Reaction, Shard Rain, Treasure Pouch, Overtime, Second Wind.
- **Unlock levels that make sense** (tool level 1-50): 5 enchants at level 1; then gaps of 4 levels early, 3 in the
  middle, 2 near the top and one every level from 42 up: the higher your tool, the faster new enchants come. Prestige
  enchants (Meteor, Nuke, Jackpot ...) keep their prestige gate.
- **Nicer GUI:** grouped rows with a coloured header per group (Area, Shards, Loot, Boosts, New), locked enchants as one
  dim tile with "Tool level 24", two pages when a tool has more than 28.

## 5. Auto-Sell (and Auto-Compactor) bought once, kept forever
- A one-level perk you bought stays through prestige and rebirth. Players who bought it before keep it.

## 6. "50% less sell price at level 1" (Q3, needs your meaning)
- Suggested reading: at /farm and /mine every crop and ore sells for **50% of its price at level 1 of that crop/ore**
  and goes back up to 100% as you level it (its unlock progress). Island crops and ores are not touched.

## 7. The small notes from update163
- Angler mastery: island-level gates (like Fish Mastery) instead of Farming pillar levels.
- Mob Loot +12% a level (up to +120%) stays as it is (Q-page asks).

## Tests
GrindEnchants and perk math (compound, rounding up, max-level clamp), price compounding, keep-one-time perks through
prestige/rebirth, config value changes for live files (islandtools.yml), message keys.

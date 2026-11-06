# Plan: bigger shard numbers and enchant breakthroughs (7 Nov 2026)

Owner, 7 Nov 2026 (screenshot: "+1 ◆", "+2 ◆"): "it only gives 1-3 shards even at fully maxed ... higher numbers, low to
high ... every enchant costs a little, then slowly more, so progress always takes a little longer ... and some goodies
where it suddenly feels a lot more impressive (3x), then flattens, then gets good again ... psychology".

## What the research says (and how we use it)
- **Numbers that keep rising feel like progress.** Big, growing numbers ("+6" early, "+60" later) beat "+1" forever.
- **Each step a bit more expensive** (a smooth curve) keeps a steady rhythm: never a wall, always a next buy.
- **Goal-gradient effect:** people speed up as a goal gets close, so the next goal must be visible with a progress bar
  ("Breakthrough at level 100: 42 levels to go").
- **Power spikes after plateaus:** a long flat stretch followed by a sudden big jump is remembered and talked about;
  then it flattens again and the next spike is already on the horizon.
- **Random procs** (Shard Burst, Jackpot) give the surprise on top; the rest stays predictable.

## 1. Shards x10 (bigger numbers, same balance)
- Every shard amount x10: a ripe crop gives 6 shards on average (was 0.6), ores 3, a /fish catch 30, a /pve kill 15;
  Shard Burst 30-60, Shard Jackpot 400-1,000, the Lucky pouch 250-2,500.
- Every shard price x10 too: perks (Shard Finder level 1: 460 shards) and enchants.
- Balances x10 once (database migration, every shard type), quest book shard rewards x10.
- With Shard Finder (up to 4x), prestige and the procs, a maxed hoe shows "+20" to "+60" a crop instead of "+1".

## 2. Enchants: a little more each level, plus breakthroughs
- Price per level grows smoothly (base x level^1.2): level 1 is cheap, every level costs a bit more than the last.
- **Breakthroughs** (`enchants.breakthroughs`, percent of the max level : multiplier): at 2% of the max the enchant's main
  effect is x1.5, at 10% x2, at 30% x2.5, at the max x3 (the chance for procs like Explode, the value for steady ones like
  Combo; not Lucky Streak's block count). For a 500-level enchant: levels 10, 50, 150, 500; for a 100-level one: 2, 10,
  30, 100. The first comes within minutes, then the gaps grow: slow growth, a jump, slow again, the next jump in sight.
- The enchant menu shows the next breakthrough and how many levels are left ("Breakthrough at 100: x2 (42 to go)"),
  and reaching one gives a title, a sound and a chat line.

- Also x10: shard block pay (10 a break, 1,000 a day), tutorial shard rewards (1,000 / 5,000), the shard thresholds of
  /pay confirm and the economy watch alerts.

## 3. Unchanged
Tool XP (15x slower hoe), Shard Finder 3% a level (lore without numbers), prestige caps (20 levels a prestige).

# Plan: tools v5, spawner limits, island level v2, clean GUIs (7 Nov 2026, night)

Owner, 7 Nov 2026 (answers before going to sleep): perks personal and working everywhere (/farm too), "more than +0.1%
each upgrade", 500-2,500 levels with a big gap between level 1 and the max ("VERY op at the end"), balanced against the
farm money and spawner prices; "do the math with an average of 13 blocks broken per second"; spawners 64 at the first
level +32 a level and an upgrade for more; cooked meat when mobs die burning; show how many kills are left; rebuild
/is level and /is upgrade; GUIs clean like the tool lore and title; no spam; "you are the boss now, don't stop".

## 1. Tools v5 (built, update150)
**Perks are personal.** Each player buys their own perks with their own shards, like the enchants. They work on any
island and at /farm /mine /fish /pve. Stored in `island_tool_enchants` as `perk.<id>` rows (one table, one purchase
guard). The first time someone who already played loads, the island's old shared levels are copied to them, converted
to the same effect on the new tracks (Shard Finder 37 = 3% x 37 = 111% → level 111 at 1%), once (marker row `perk._v5`).
Brand-new players start at 0, so joining a maxed island gives nothing for free.

**The math (13 blocks a second).** Every block the tool breaks counts for shards: the block you hit, Sweep/Vein and the
area enchants (Explode, Blast ...; they still give no tool XP). Base: 6 shards a block = 78 a second = 280,000 an hour.
A simulation that buys the cheapest next level each time (`/tmp` script, numbers below) gives:

| Time played | Shard Finder | Extra Drops | Sell Bonus | Enchant levels (17) | Shards a second |
|---|---|---|---|---|---|
| 1 minute | 7 | 4 | 0 | 6 | 83 |
| 1 hour | 50 | 40 | 18 | 90 | 120 |
| 10 hours | 171 | 137 | 68 | 383 | 264 |
| 50 hours | 526 | 424 | 214 | 1,423 | 1,100 |
| 100 hours | 1,136 | 896 | 454 | 3,281 | 3,400 |
| ~200 hours | max 2,500 | max 1,000 | max 500 | all 5,500 | 7,100 |

A new upgrade every few seconds at the start, then a little slower all the time, never a wall; the popups go from "+6"
to "+500" a block. Perk tracks (hoe; the pickaxe, rod and sword use the same prices):

| Perk | Levels | Per level | At the max | Price of level L |
|---|---|---|---|---|
| Shard Finder | 2,500 | +1% shards | 26x shards | 80 + 12 x L^1.35 |
| Extra Drops | 1,000 | +0.15% drops | +150% | 120 + 16 x L^1.35 |
| Wisdom | 500 | +1% tool XP | +500% | 160 + 20 x L^1.35 |
| Growth | 500 | +0.2% island crop growth | +100% (best online member) | 200 + 20 x L^1.35 |
| Storage | 500 | +500 items | 252,000 items | 120 + 12 x L^1.35 |
| Sell Bonus | 500 | +0.2% | +100% | 400 + 40 x L^1.35 |
| Lucky | 1,000 | +0.001% pouch chance | 1 pouch in 100 blocks | 600 + 50 x L^1.35 |
| Efficiency / Fortune / Haste / Auto-Sell / Sweep | 5 / 3 / 2 / 1 / 100 | as before | | 300-30,000 / 3,000-60,000 / 10,000-60,000 / 40,000 / 6,000 + 200 x L^1.35 |

**Money stays balanced.** Extra Drops and Sell Bonus together make late-game farming about 5x the base, but the daily
island price (3,000 an item at full price, then down to 60%) and the spawner prices ($5M-$75M, ~50 h payback) stay;
farming money buys spawners, spawners pay while you are away. Rod: 200 shards a catch, sword: 50 a /pve kill (a catch
and a kill are much slower than a crop).

**No spam.** Buying a perk or enchant level is an action bar line (no chat line per click). A Shard Pouch is a popup
on the block with a sound (no title). Breakthroughs keep their title (rare).

## 2. Spawners (built, update150)
- **Limit per mob type: 64 at the first level, +32 a level** (96, 128 ... 544 at level 16; a prestige keeps the top).
  One block holds them all (stack 2,048).
- **Spawner Capacity** (new island upgrade, /is upgrade → Production): +32 spawners of every mob type a level, 10 levels
  ($10M to $13.75B). The old Stack Limit upgrade stays hidden.
- **Kills to go everywhere:** the spawner hologram has a second line "Lv 3 · 1,240 kills to +32" (refreshed every 2 s);
  the spawner menu shows "Next: 1,240 kills, +32 spawners"; the limit message says how many kills are left and points
  to Spawner Capacity. Every kill of a mob from the island's spawners on the island counts, lava and fall too.
- **Cooked meat:** a spawner mob that dies burning (lava, fire, on fire, Fire Aspect) drops cooked beef, porkchop,
  chicken, mutton, rabbit (same sell price as raw).

## 3. Island level v2 (next, update151)
The FINISH_PLAN Batch B concept, with the owner's "relevant to farm, mine, island crops and mined blocks":
- **Island level = island XP**, live (no scans, no Recalculate button, no 5-minute wait). Sources, members only:
  ripe island crops (1 XP), natural island ores and stone from generators (1, ores 2-5 by tier), /farm crops and /mine
  blocks (1), /fish catches (5), /pve kills (3), spawner kills on the island (0.2 each, stack-aware). Placed blocks,
  lava/fall farms and coops give nothing (existing rules).
- **Curve:** level n needs 1,000 x n^1.5 XP (level 10 ≈ 31,600 XP ≈ 40 min at 13 blocks a second; level 50 ≈ 350,000).
- **Rewards:** every level money to the island bank; every 5 levels crystals per member and a crate key; every 10 a
  server line. Old island level counts nothing more; island worth stays only in /is top → Value.
- `/is level` rebuilt in the tool style: the bar, "how to earn island XP" (one row per source with today's XP), the next
  5 rewards. Mastery gates re-checked (the `island-level N` lines move to the new pace).

## 4. Clean GUIs, less spam, events (update152+)
- Every menu in the tool style: `<bold><area:...>Name`, a muted subtitle, `| Section` headers, `| Label: value` rows,
  one `» CLICK to ...` hint, Title Case titles, lore within 36 characters, 6 rows with the standard bottom bar.
- Spam pass: one-line action bars instead of chat for routine results (buys, sells, collects); chat only for rare
  moments (level ups, breakthroughs, prestiges, keys).
- Grind events that make sense: announced 5 minutes before, one boss bar while active, no repeated chat lines.
- FINISH_PLAN Batch A (spawn holograms, NPC spots, leaderboards, quests) after that.

# Plan P-05: Tools v3 (draft for owner approval, 6 Nov 2026)

Sources: owner answers 6 Nov (`planning/TOOL_ENCHANTS_6NOV.md`), research (`planning/RESEARCH_6NOV.md` §1), owner principle 0.

## 1. The model
- Every player has **one pickaxe and one hoe**. Each tool has **two enchant sets**: the **grind set** (17 enchants, works in /mine or /farm) and the **island set** (7 enchants, works on your island). Each set has its own levels. The tool shows and uses the set for where you are.
- **Right-click opens one clean menu**: the set for where you are opens first; a tab switches to the other set. Both can be upgraded anywhere.
- **Core upgrades** (always on, both places, 4 per tool) sit on a third tab:
  - Pickaxe: Efficiency, Fortune, Shard Finder, Sell Bonus.
  - Hoe: Haste, Fortune (more crops), Shard Finder, Sell Bonus.
- **Shards are personal (owner, 6 Nov):** Farming, Mining and (later) Fishing Shards belong to the player, like the tools; they are never shared with the island. A player can **pay** shards to a member of their own island (`/pay <member> <amount> shards` and the island menu), never to anyone else. Today's code already keeps shards per profile; the pay-to-member path is new (atomic, audit-logged, tab-completes only members).
- Old perks that go: Extra Drops (merged into Fortune), Lucky (replaced by Shard Burst), Wisdom (replaced by Scholar), Smelter/Auto-Sell/Storage (move to the farm and mine storage, P-06), Growth (island hoe set), Sweep/Vein (island sets).
- **Personal tools (owner, 6 Nov):** each player owns, levels and enchants their own pickaxe and hoe (replaces the 4 Nov island-owned tools). Migration: every member starts from the island's current tool levels.

## 2. Grind sets (17 each, levels 1 to 1000, linear)
Rules kept from the enchant pages: one area enchant per break; area-broken blocks give drops only; all money enchants together max +25% income; new shard enchants together max +65%; rare finds once per 2,500-10,000 blocks at max; big procs have cooldowns. Every proc shows a short actionbar line and sound ("+30 Shards, Shard Burst"); big ones a title.

| # | Mine pickaxe | Farm hoe | Level 1 → 1000 | Cooldown |
|---|---|---|---|---|
| 1 | Blast (5x5 face) | Explode (5x5) | 0.5% → 1%, 3-5 → 15-20 blocks | - |
| 2 | Lightning Strike (2 bolts 3x3) | Row Reaper (row of 4-7) | 0.05% → 0.15% / 0.3% → 0.7% | 20 s / - |
| 3 | Meteor (crater 15-25) | Tornado (ring 20-28) | 0.03% → 0.08% | 20 s |
| 4 | Nuke (up to 100 in 15x15) | Meteor Shower (4 x 3x3) | 0.003% → 0.01% / 0.01% → 0.03% | 5 min / 60 s |
| 5 | Rich Vein (+1-2 items) | Bountiful (+1-2 items) | 1% → 5% | - |
| 6 | Motherlode (counts as 10 toward next ore) | Golden Crop (sells for 5x) | 0.2% → 0.6% / 0.1% → 0.4% | - |
| 7 | Shard Burst (+3-6 shards) | Shard Burst | 0.4% → 1% | - |
| 8 | Shard Echo (+10-25% shards for 10 s after a Burst) | Shard Echo | per Burst | - |
| 9 | Shard Jackpot (40-75 shards) | Shard Jackpot (40-100) | 0.01% → 0.04% | - |
| 10 | Compound Interest (streak, cap +5% → +15% shards) | Compound Interest | streak | 3 s gap ends it |
| 11 | Cash Vein (5x sell price bonus) | Cash Crop | 0.5% → 2% | - |
| 12 | Scholar (+5-25% skill XP) | Scholar | flat | - |
| 13 | Quick Study (counts as 2 toward next ore) | Quick Study | 1% → 5% | - |
| 14 | Combo (+5-20% tool XP while chaining) | Combo | streak | 2 s |
| 15 | Collector (+10-50% collection progress) | Harvest Basket (one of every unlocked crop) | flat / 0.05% → 0.2% | - |
| 16 | Lucky Streak (pity: next rare chance doubles) | Lucky Streak | 100 → 50 blocks | - |
| 17 | Keyfinder (crate key) | Keyfinder | 0.001% → 0.02% (about 1 per 5,000 blocks at max) | - |

The hoe differs where farming needs it: Row Reaper and Tornado instead of Lightning and Nuke shapes, Golden Crop instead of Motherlode, Harvest Basket instead of Collector.

## 3. Island sets (7 each, levels 1 to 500)
| Island pickaxe | Island hoe |
|---|---|
| Vein (3x3 on generator ores) | Sweep (3x3 crops) |
| Rich Vein | Bountiful |
| Shard Burst | Shard Burst |
| Cash Vein | Cash Crop |
| Scholar | Scholar |
| Generator Boost (mining X ores makes your generators faster for 10 min) | Growth (island crops grow faster) |
| Keyfinder (half chance) | Keyfinder (half chance) |

## 4. Cost, unlocks and pace (faster progress)
- Cost per level `c x L^1.2` shards (c = 2 for most, 3-8 for big procs; island set c = 1). Level 1 costs 2 shards, so the first upgrade comes within a minute. Bulk buttons +1 / +10 / +100 / MAX.
- Unlocks: 3 enchants at tool level 1 (Shard Burst, Blast/Explode, Scholar), then a new one every 2-3 tool levels; Meteor, Nuke / Meteor Shower and Shard Jackpot at prestige 1, 5 and 10.
- **More shards** (owner: "so hard to get"): target 60-120 shards a minute in the first minutes, 1,500-4,000 mid game, 15,000-30,000 late.

## 5. Prestige and rebirth
- **Prestige** at tool level 50, **up to 50 times**. Resets tool level and XP. Keeps enchants and shards. Each prestige: +5% shards and money for that tool, and the enchant level cap rises by 20 (1000 at prestige 50). Something new every 10 prestiges (an enchant, a cosmetic, a tier).
- **Rebirth** after prestige 50, **infinite**. Resets prestige to 0. Keeps enchants, caps and cosmetics. Each rebirth: +10% shards and money for good, a badge, and the next rebirth needs about 10% more.
- Pace target: prestiges 1-10 take 10-20 min each, later ones up to 1-2 h; first rebirth about 15-25 h. Tuned in playtests.

## 6. Events (owner, 6 Nov)
6 events: Double XP, Double Drops, Double Shards, Instant Regrow, Proc Frenzy (x2 proc chance), Golden Blocks (x5 value). Each lasts about 30 s; 2 to 4 run at once, picked at random; a 30-60 s break between rounds. Same system in /farm, /mine and later /fish. Boss bar per active event. Event bonuses sit outside the +25% / +65% caps.

## 7. Build notes
- Module `islandtools`; the old `tools` module is removed (C-02).
- Proc maths in pure classes with unit tests (chance at level, caps, cooldowns, pity).
- Per-block work is cached; area procs are batched and capped per tick.
- All numbers in config; all text in messages; Bedrock: the same menu works as a chest menu.
- Migration: today's perk levels convert to shards refunded, so nobody loses progress.

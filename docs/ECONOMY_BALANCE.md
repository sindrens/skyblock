# Economy Balance Sheet (M3.0)

> **Status: DRAFT, waiting for owner approval.** No price from this sheet goes live (M3.4 selling, M3.5 shop, Phase 4 Forge, Phase 5 upgrades) until the owner approves it. All numbers are proposals and will live in typed configs, never in code.
>
> **Every table is generated** by [`tools/economy_model.py`](../tools/economy_model.py) from the formulas in §1. Change a parameter there, then run:
> ```bash
> python3 tools/economy_model.py --write docs/ECONOMY_BALANCE.md   # refresh all tables in this file
> python3 tools/economy_model.py --check docs/ECONOMY_BALANCE.md   # fails if this file is out of date
> python3 tools/economy_model.py --table targets                   # print one table
> ```
> Sources: `CONTEXT.md` §3 (income meta), `DESIGN.md` §3–6, `WORLD.md` §4–5, `phases/PHASE-3-ECONOMY.md`, `phases/PHASE-4-DEPTHS.md`.

---

## 0. Summary

- **Income meta holds** for an active player over the season: The Depths ~49%, island production ~35%, quests/events ~15%, interest under 1% (§2.3).
- **Owner targets** (top player, money earned so far): first hour, week 2 and month 2 land within 0.9–1.1× of the targets. **Day 3 comes out at ~3× the target** ($15M instead of $5M) because a top player has played ~30 h by then; an active player (4 h/day) hits ~$2M. Proposal: keep the model as is and read the day-3 target as "an active player", see §2.2.
- **Big numbers** work as intended: start $1,000, tens of thousands in the first hour, millions in week 1, hundreds of millions in week 2, tens of billions in month 2 for top players. Season totals stay far below the `long` limit.
- **The Depths pacing:** a top player reaches tier 12 (Voidstone) around day 45; an active player reaches tier 11 around day 66; a casual player reaches tier 9 by season end. Perk slots land at ~1 h / day 2 / day 7 / day 28 / day 45 for an active player, matching Phase 4.
- **Sinks:** the economy removes 75–90% of the money it creates on every checked day (target ≥ 75%). The big sinks are island level upgrades, late projects (outposts) and, in months 2–3, **Mastery**. **Without an endless late sink the sink ratio collapses to ~25% after day 60** (§7.3). This is the main decision for the owner.
- **Interest** is capped per payout so it stays a small bonus (0.6–3% of season income, at most ~5% on a single day for casual players) and can't be farmed by idle alts (§6).

**Decisions the owner needs to make** are listed in §12 and added to `docs/OPEN_QUESTIONS.md`.

---

## 1. How the model works

### 1.1 Players and time
Three archetypes play every day of a 90-day season. All money values are whole dollars.

| Archetype | Played h/day | Breaking uptime | Production h/day (online + AFK + fuel) | Rank sell bonus | Saves (never spent) |
|---|---|---|---|---|---|
| Casual | 1.5 (day 1: 2) | 60% | 8 | 0% | 20% of income |
| Active | 4 (day 1: 5) | 75% | 14 | 4% | 15% |
| Top | 9 (day 1: 12) | 85% | 22 | 8% | 10% |

Population behind a 300-player peak: **900 daily unique players = 540 casual + 270 active + 90 top** (average ~112 online, peak ≈ 2.5–2.7× average).

Each played hour: 70% breaking in The Depths, the rest is island work, menus, selling and events. Island production runs for the archetype's production hours per day. Players greedily buy in this order whenever they can afford it: next Forge tool → production levels that pay back within 3.5 days → the cheapest island level upgrade / late project → the next personal bank tier → Mastery. They also spend 4% of income in `/shop` and trade 10% of income on the AH.

**"Income per played hour"** in the tables = Depths + events per played hour, plus that day's island production spread over the played hours.

### 1.2 Parameters and formulas
Everything below can be rebuilt in a spreadsheet from this table. `t` = tier (1–12), `REF(t)` = reference income per played hour at tier t (all sources), used to price upgrades in "hours of income" so prices follow income automatically.

<!-- BEGIN table:params -->
| Parameter | Formula / value | Note |
|---|---|---|
| Start money | $1k | decided |
| Mine drop price | P(t) = 20 x 1.35^(min(t,8)-1) x 2.4^max(0,t-8) | tiers 1-8 / 9-12 |
| Reference income | REF(t) = 2,500 x 1.3^(t-1) x P(t) | per played hour, all sources; prices are 'hours of REF' |
| Break rate | bps = min(6.0, 1.0 x 1.05^(tool-1) x (1+0.01 x level) x (1+0.12 x slots)) | blocks/s |
| Fortune | drops/block = 1 + 0.05 x (tool-1) |  |
| Depths time | 70% | share of a played hour spent breaking |
| Uptime casual / active / top | 60% / 75% / 85% | breaking vs walking/menus |
| Dynamic price (Depths / island) | 0.85 / 0.9 | average realised multiplier |
| Sell multiplier | 1 + min(3.0, rank + 0.03 x (tier-1)) | rank 0/4/8% by archetype |
| Skill XP per level | x(L) = 600 x 1.2^min(L,35) x 1.13^clamp(L-35,0,31) x 1.02^max(0,L-66) | total = sum of steps; max level 150 |
| Tool XP to level L | XT(L) = 400 x (1.21^(L-1) - 1) / (1.21 - 1) | XP per block = tier XP |
| Forge money | F(t) = $15k x 2.35^(t-2), t >= 2 | tier 1 = free starter kit |
| Forge materials | M(t) = 256 x 1.35^(t-2) drops of tier t-1 |  |
| Island output | Q(PL) = 170 x 1.12^PL items per production hour | each worth 0.5 x P(tier-1) |
| Production level | C_p(k) = $2.5k x 1.3^(k-1) | max 6 x tier; bought if payback <= 3.5 days |
| Level upgrade | C_u(u,n) = W_u x 0.067 x 1.49^(n-1) x REF(n) | 12 upgrades x 12 levels, level n needs tier n |
| Late projects | C_late(o,j) = 1.0 x 1.22^j x REF(gate_o) | 3 outposts x 18 steps, gates tier 7/9/11 |
| Mastery (proposal) | C_m(m) = 4.0 x 1.12^(m-1) x REF(tier) | endless, after all levels are maxed |
| Events/quests | 0.3 x Depths income per played hour | rewards defined per tier |
| Shop spend | 4% of earned | blocks, decor, seeds |
| AH | 10% of earned traded, fee 1% + tax 5% | only fee+tax leave the economy |
| Interest | personal 0.15% / island 0.25% per 3 h | capped per payout |
| Savings casual / active / top | 20% / 15% / 10% | of income, banked, never spent |
| Production hours/day casual / active / top | 8 / 14 / 22 | online + AFK + fuel |
| Played hours/day casual / active / top | 1.5 (day 1: 2) / 4 (day 1: 5) / 9 (day 1: 12) |  |
| Daily uniques casual / active / top | 540 / 270 / 90 | avg ~112 online, peak ~300 |
<!-- END table:params -->

Notes:
- **Vanilla tiers 1–8** grow ×1.35 per tier (a bit slower than XP's ×1.45), **custom tiers 9–12** grow ×2.4: Amethyst, Quartz, Netherite and Voidstone are the big-money endgame and the reason to push the skill.
- The sell multiplier proxy (+3% per tier) stands for the Sell Multiplier upgrade, Merchant set and boosters together. The total cap stays **+100% (×2.0)** (`OPEN_QUESTIONS.md` #4).
- **Forest and combat are priced for parity:** the same money per played hour as the mine at the same tier, so players pick the wing they enjoy (§3.2).

---

## 2. Income targets per stage

### 2.1 Income per played hour and money earned so far
<!-- BEGIN table:targets -->
| Stage | Casual (income per played hour · earned so far) | Active | Top |
|---|---|---|---|
| First hour | $46.2k/h · $36.2k | $53.9k/h · $46.8k | $67.2k/h · $55.8k |
| Day 1 | $54.1k/h · $92.6k | $101.2k/h · $400.3k | $321.1k/h · $2.2M |
| Day 3 | $80.3k/h · $316.7k | $285.9k/h · $2.2M | $927k/h · $14.9M |
| Week 1 | $142.5k/h · $1.1M | $791.4k/h · $11.3M | $2.4M/h · $85.5M |
| Week 2 | $366.4k/h · $4M | $1.9M/h · $48.5M | $6.5M/h · $428.4M |
| Month 1 | $1M/h · $18.9M | $5.6M/h · $282.8M | $62.6M/h · $4.9B |
| Month 2 | $1.7M/h · $84.6M | $17.5M/h · $2B | $215.9M/h · $43.8B |
| Month 3 | $4.2M/h · $220.7M | $56.7M/h · $8B | $215.9M/h · $102.5B |
<!-- END table:targets -->

### 2.2 Owner targets vs. model
<!-- BEGIN table:targets_check -->
| Stage | Owner target (top, earned so far) | Model top | Top / target | Model active | Active / target |
|---|---|---|---|---|---|
| First hour | $50k | $55.8k | 1.1x | $46.8k | 0.9x |
| Day 3 | $5M | $14.9M | 3.0x | $2.2M | 0.4x |
| Week 2 | $500M | $428.4M | 0.9x | $48.5M | 0.1x |
| Month 2 | $50B | $43.8B | 0.9x | $2B | 0.0x |
<!-- END table:targets_check -->

Why day 3 is off: a top player has played ~30 h by day 3 and is already at tier 6 with 3 perk slots. Hitting $5M there while still reaching $500M in week 2 needs a 100× jump in 4 days, which feels like a cliff. The model has a smooth curve instead. **Options:** (a) accept ~$15M for top players at day 3 (proposal; an active player is at ~$2M), or (b) lower tiers 1–8 prices by ~40% and raise tiers 9–12 to match week 2 (steeper "custom tier" cliff).

### 2.3 Where the money comes from (share of money earned so far)
<!-- BEGIN table:shares -->
| Archetype | Earned up to | Depths | Island | Events/quests | Interest |
|---|---|---|---|---|---|
| casual | Week 1 | 59% | 22% | 18% | 1.0% |
| casual | Month 1 | 56% | 24% | 17% | 2.6% |
| casual | Month 3 | 55% | 25% | 17% | 2.9% |
| active | Week 1 | 60% | 21% | 18% | 0.6% |
| active | Month 1 | 60% | 21% | 18% | 0.8% |
| active | Month 3 | 49% | 35% | 15% | 0.7% |
| top | Week 1 | 59% | 23% | 18% | 0.3% |
| top | Month 1 | 54% | 30% | 16% | 0.4% |
| top | Month 3 | 39% | 48% | 12% | 0.6% |
| **target** | season | ~50% | ~35% | ~15% | <= 5% |
<!-- END table:shares -->

- Early on the island is small, so The Depths carries ~60%. The island catches up after month 1, exactly the "Depths main, island steady" meta.
- **Top players drift towards ~50% island by month 3** because they keep production running ~22 h/day (AFK on the island + fuel). That is the AFK lever: see risk R5 in §10.
- Casual players earn relatively more interest (up to ~3%) because they save more and their income is small. In absolute terms it is tiny (≤ $126k/day).

### 2.4 Where each archetype is at each stage
<!-- BEGIN table:progress -->
| Stage | Casual (played h · Depths tier · tool level · production levels · level upgrades · late projects · mastery) | Active | Top |
|---|---|---|---|
| First hour | 1 h · T1 · Lv4 (0 slots) · PL 3 · UL 7 · late 0 · M 0 | 1 h · T1 · Lv4 (0 slots) · PL 4 · UL 8 · late 0 · M 0 | 1 h · T1 · Lv5 (1 slots) · PL 5 · UL 9 · late 0 · M 0 |
| Day 1 | 2 h · T1 · Lv6 (1 slots) · PL 5 · UL 12 · late 0 · M 0 | 5 h · T2 · Lv12 (1 slots) · PL 10 · UL 24 · late 0 · M 0 | 12 h · T4 · Lv21 (2 slots) · PL 16 · UL 48 · late 0 · M 0 |
| Day 3 | 5 h · T2 · Lv11 (1 slots) · PL 6 · UL 24 · late 0 · M 0 | 13 h · T4 · Lv21 (2 slots) · PL 14 · UL 48 · late 0 · M 0 | 30 h · T6 · Lv31 (3 slots) · PL 22 · UL 72 · late 0 · M 0 |
| Week 1 | 11 h · T3 · Lv17 (2 slots) · PL 8 · UL 36 · late 0 · M 0 | 29 h · T6 · Lv30 (3 slots) · PL 18 · UL 70 · late 0 · M 0 | 66 h · T8 · Lv41 (3 slots) · PL 26 · UL 94 · late 5 · M 0 |
| Week 2 | 22 h · T5 · Lv24 (2 slots) · PL 12 · UL 56 · late 0 · M 0 | 57 h · T8 · Lv38 (3 slots) · PL 23 · UL 87 · late 3 · M 0 | 129 h · T9 · Lv47 (4 slots) · PL 28 · UL 107 · late 18 · M 0 |
| Month 1 | 46 h · T7 · Lv32 (3 slots) · PL 17 · UL 76 · late 0 · M 0 | 121 h · T9 · Lv46 (4 slots) · PL 25 · UL 102 · late 15 · M 0 | 273 h · T11 · Lv50 (4 slots) · PL 40 · UL 124 · late 41 · M 0 |
| Month 2 | 90 h · T8 · Lv40 (3 slots) · PL 19 · UL 93 · late 5 · M 0 | 241 h · T10 · Lv50 (4 slots) · PL 31 · UL 119 · late 31 · M 0 | 543 h · T12 · Lv50 (4 slots) · PL 46 · UL 144 · late 54 · M 1 |
| Month 3 | 135 h · T9 · Lv44 (3 slots) · PL 21 · UL 100 · late 12 · M 0 | 361 h · T11 · Lv50 (4 slots) · PL 37 · UL 129 · late 44 · M 0 | 813 h · T12 · Lv50 (4 slots) · PL 46 · UL 144 · late 54 · M 17 |
<!-- END table:progress -->

---

## 3. The Depths

### 3.1 Mine: sell value per drop and per hour
One ore block = one drop (plus fortune, §1.2). "Base $/played h" is before the sell multiplier and events, with the typical tool an active player has when they unlock that tier.

<!-- BEGIN table:mine -->
| Tier | Ore | Mining lv | XP | Sell price / drop | vs prev | Blocks per played h (active) | Base Depths $/played h (no multipliers) |
|---|---|---|---|---|---|---|---|
| 1 | Coal | 0 | 1 | $20 | - | 1,909 | $32.5k |
| 2 | Copper | 5 | 1.5 | $27 | x1.35 | 2,378 | $57.3k |
| 3 | Iron | 10 | 2.5 | $36 | x1.35 | 2,614 | $89.1k |
| 4 | Gold | 15 | 4 | $49 | x1.35 | 3,174 | $152.7k |
| 5 | Redstone | 20 | 6 | $66 | x1.35 | 3,475 | $235.5k |
| 6 | Lapis | 25 | 9 | $90 | x1.35 | 3,799 | $362k |
| 7 | Diamond | 30 | 13 | $121 | x1.35 | 4,547 | $608.3k |
| 8 | Emerald | 35 | 19 | $163 | x1.35 | 4,919 | $922.5k |
| 9 | Amethyst | 42 | 28 | $392 | x2.40 | 5,393 | $2.5M |
| 10 | Quartz | 50 | 40 | $941 | x2.40 | 6,422 | $7.5M |
| 11 | Netherite | 58 | 58 | $2.3k | x2.40 | 6,835 | $19.7M |
| 12 | Voidstone | 66 | 85 | $5.4k | x2.40 | 7,176 | $51.3M |
<!-- END table:mine -->

### 3.2 Forest and combat (parity with the mine)
A log takes as long as an ore block, so log prices equal ore prices of the same tier. Forest tier 11 (Warped) is the top; there is no forest tier 12.

<!-- BEGIN table:forest -->
| Tier | Tree | Foraging lv | XP/log | Sell price / log | Money per hour |
|---|---|---|---|---|---|
| 1 | Oak | 0 | 1 | $20 | same as mine tier 1 |
| 2 | Birch | 5 | 1.5 | $27 | same as mine tier 2 |
| 3 | Spruce | 10 | 2.5 | $36 | same as mine tier 3 |
| 4 | Jungle | 15 | 4 | $49 | same as mine tier 4 |
| 5 | Acacia | 20 | 6 | $66 | same as mine tier 5 |
| 6 | Dark Oak | 25 | 9 | $90 | same as mine tier 6 |
| 7 | Mangrove | 30 | 13 | $121 | same as mine tier 7 |
| 8 | Cherry | 35 | 19 | $163 | same as mine tier 8 |
| 9 | Pale Oak | 42 | 28 | $392 | same as mine tier 9 |
| 10 | Crimson | 50 | 40 | $941 | same as mine tier 10 |
| 11 | Warped | 58 | 58 | $2.3k | same as mine tier 11 |
<!-- END table:forest -->

Combat: kills take longer than blocks (~700 kills per hour of pure fighting at full uptime, +4% per tier from better swords), so each kill is worth several drops. Combat skill thresholds are a proposal that spreads the 10 tiers up to level 66 like the mine (the current `WORLD.md` table has no combat thresholds).

<!-- BEGIN table:combat -->
| Tier | Zone | Combat lv (proposal) | Kills per played h (active) | Drop value / kill | = mine drops |
|---|---|---|---|---|---|
| 1 | Graveyard | 0 | 368 | $104 | 5.2x |
| 2 | Bone Crypt | 6 | 382 | $176 | 6.5x |
| 3 | Spider Den | 12 | 397 | $264 | 7.2x |
| 4 | Slime Bog | 18 | 412 | $436 | 8.9x |
| 5 | Sunken Ruins | 24 | 426 | $650 | 9.8x |
| 6 | Witch Hollow | 30 | 441 | $966 | 10.8x |
| 7 | Pillager Outpost | 38 | 456 | $1.6k | 13.0x |
| 8 | Blaze Forge | 46 | 470 | $2.3k | 14.1x |
| 9 | Wither Keep | 55 | 485 | $6.1k | 15.6x |
| 10 | Warlord's Citadel | 66 | 500 | $17.5k | 18.6x |
<!-- END table:combat -->

Mini-boss and world boss rewards are paid as **event income** (§1.2, 30% of Depths income per played hour across all event types), not on top of it.

### 3.3 Blocks per hour per tool tier
Respawn is **10 s per player** and a field is **~40×40 = 1,600 blocks**, so the respawn timer never limits a single player: even the best tool only has ~2–3% of the field depleted at any moment. The practical limit is reach: ~60 ore blocks in reach / 10 s = **6 blocks/s**; the model caps at that and recommends the same server-side cap (anti-cheat and performance: even 300 players × ~2 blocks/s would be ≈ 600 block events/s at peak, handled by the timing wheel in M4.3).

<!-- BEGIN table:blocks -->
| Tool tier | Tool | Typical level | Slots | Blocks/s | Casual blocks/played h | Active | Top | Field depleted at once |
|---|---|---|---|---|---|---|---|---|
| 1 | Coal pickaxe | 1 | 0 | 1.01 | 1,527 | 1,909 | 2,163 | 0.6% |
| 2 | Copper pickaxe | 7 | 1 | 1.26 | 1,903 | 2,378 | 2,695 | 0.8% |
| 3 | Iron pickaxe | 12 | 1 | 1.38 | 2,091 | 2,614 | 2,962 | 0.9% |
| 4 | Gold pickaxe | 17 | 2 | 1.68 | 2,539 | 3,174 | 3,597 | 1.0% |
| 5 | Redstone pickaxe | 22 | 2 | 1.84 | 2,780 | 3,475 | 3,939 | 1.1% |
| 6 | Lapis pickaxe | 27 | 2 | 2.01 | 3,039 | 3,799 | 4,305 | 1.3% |
| 7 | Diamond pickaxe | 32 | 3 | 2.41 | 3,637 | 4,547 | 5,153 | 1.5% |
| 8 | Emerald pickaxe | 36 | 3 | 2.60 | 3,935 | 4,919 | 5,575 | 1.6% |
| 9 | Amethyst pickaxe | 42 | 3 | 2.85 | 4,314 | 5,393 | 6,112 | 1.8% |
| 10 | Quartz pickaxe | 48 | 4 | 3.40 | 5,138 | 6,422 | 7,279 | 2.1% |
| 11 | Netherite pickaxe | 50 | 4 | 3.62 | 5,468 | 6,835 | 7,746 | 2.3% |
| 12 | Voidstone pickaxe | 50 | 4 | 3.80 | 5,741 | 7,176 | 8,133 | 2.4% |
| max | Voidstone, Lv50, 5 slots | 50 | 5 | 4.10 | 6,206 | 7,758 | 8,793 | 2.6% |
<!-- END table:blocks -->

### 3.4 Skill pacing (tier unlocks)
Mining skill XP per block = the tier XP from `WORLD.md`. Unlock levels are unchanged (0, 5, 10 … 58, 66).

<!-- BEGIN table:skill -->
| Tier | Ore | Mining level | Total skill XP | Casual reaches it | Active | Top |
|---|---|---|---|---|---|---|
| 2 | Copper | 5 | 4,465 | 2.6 h (day 2) | 2.1 h (day 1) | 1.8 h (day 1) |
| 3 | Iron | 10 | 15,575 | 6.4 h (day 4) | 5.2 h (day 2) | 4.5 h (day 1) |
| 4 | Gold | 15 | 43,221 | 11.3 h (day 8) | 9.1 h (day 3) | 8.0 h (day 1) |
| 5 | Redstone | 20 | 112,013 | 17.9 h (day 12) | 14.4 h (day 4) | 12.8 h (day 2) |
| 6 | Lapis | 25 | 283,189 | 28.0 h (day 19) | 22.4 h (day 6) | 20.1 h (day 2) |
| 7 | Diamond | 30 | 709,129 | 42.5 h (day 29) | 34.1 h (day 9) | 30.4 h (day 4) |
| 8 | Emerald | 35 | 1.8M | 64.6 h (day 43) | 51.7 h (day 13) | 46.0 h (day 5) |
| 9 | Amethyst | 42 | 5.5M | 113.0 h (day 76) | 90.6 h (day 23) | 80.0 h (day 9) |
| 10 | Quartz | 50 | 16.1M | not this season | 155.3 h (day 39) | 137.1 h (day 15) |
| 11 | Netherite | 58 | 44.4M | not this season | 264.2 h (day 66) | 233.1 h (day 26) |
| 12 | Voidstone | 66 | 119.5M | not this season | not this season | 400.5 h (day 45) |
| - | (after tier 12) | 75 | 272.3M | not this season | not this season | 621.5 h (day 69) |
| - | (after tier 12) | 100 | 872M | not this season | not this season | not this season |
| - | (after tier 12) | 125 | 1.9B | not this season | not this season | not this season |
| - | (after tier 12) | 150 | 3.5B | not this season | not this season | not this season |
<!-- END table:skill -->

The curve is steep up to level 35 (×1.20 per level) and flatter after (×1.13), because late tiers are 7–8 levels apart instead of 5. The result: tier 12 is a month-2 goal for top players, and casual players see tier 9 by season end.

**Max level 150 (owner #65).** Tiers still unlock at the same levels (the XP up to level 66 is unchanged). After level 66 every level costs only 2% more than the one before (`curve.end-growth: 1.02`, `curve.end-break-level: 66`, `curve.max-level: 150` in `skills.yml`), so levels 67–150 are a long goal for the most active players and leaderboards. With today's XP sources the top archetype ends the season around level 81; level 150 needs ~3.5B XP (about 8.6× a top player's season). XP boosts, later content and events push this up; tune `curve.end-growth` if the owner wants 150 within one season (open question).

### 3.5 Tool levels and perk slots
Tool XP per block = tier XP, so a better tool on a better tier levels faster. Phase 4 suggested ×1.12 per level; with tier XP growing ×1.45 that made slot 4 arrive in week 1–2 and level 50 in week 1–3 (checked with the model). **This sheet uses ×1.21** so the slots land where Phase 4 wants them:

<!-- BEGIN table:tool_levels -->
| Slot | Tool level | Total tool XP | Phase-4 target (active) | Casual | Active | Top |
|---|---|---|---|---|---|---|
| 1 | 5 | 2,178 | ~1 h | 1.4 h (day 1) | 1.1 h (day 1) | 54 min (day 1) |
| 2 | 15 | 25,564 | ~day 2 | 8.3 h (day 6) | 6.7 h (day 2) | 5.9 h (day 1) |
| 3 | 30 | 477,405 | ~week 1 | 35.0 h (day 24) | 28.1 h (day 7) | 25.1 h (day 3) |
| 4 | 45 | 8.4M | ~week 3-4 | not this season | 109.7 h (day 28) | 96.8 h (day 11) |
| 5 (prestige) | 50 | 21.7M | ~week 6+ | not this season | 177.1 h (day 45) | 156.3 h (day 18) |
<!-- END table:tool_levels -->

The model does not prestige tools (slot 5 is ~week 6+ for active players and adds ~3% speed, not money). Prestige stays a time achievement, never a purchase.

---

## 4. Island production

### 4.1 Island products
Island items sell for **half** the Depths item of the same tier (generator ores one tier lower), so The Depths stays the main mining place (`WORLD.md` §5.2). Island products use the island's "product tier" = Depths tier − 1 (crops and generator tiers lag behind the player's skill).

<!-- BEGIN table:island_products -->
| Tier | Crop (Farming unlock) | Crop item | Generator block | Generator drop | Spawner type (by combat tier) | Spawner drop |
|---|---|---|---|---|---|---|
| 1 | Wheat | $10 | Cobblestone | $1 | Graveyard mob drop | $10 |
| 2 | Carrot, Potato | $14 | Coal | $10 | Bone Crypt mob drop | $14 |
| 3 | Beetroot | $18 | Copper | $14 | Spider Den mob drop | $18 |
| 4 | Sugar cane | $25 | Iron | $18 | Slime Bog mob drop | $25 |
| 5 | Melon, Pumpkin | $33 | Gold | $25 | Sunken Ruins mob drop | $33 |
| 6 | Cocoa | $45 | Redstone | $33 | Witch Hollow mob drop | $45 |
| 7 | Cactus | $61 | Lapis | $45 | Pillager Outpost mob drop | $61 |
| 8 | Sweet berries | $82 | Diamond | $61 | Blaze Forge mob drop | $82 |
| 9 | Nether wart | $196 | Emerald | $82 | Wither Keep mob drop | $196 |
| 10 | Torchflower | $471 | - | - | Warlord's Citadel mob drop | $471 |
| 11 | Pitcher plant | $1.1k | - | - | - | - |
<!-- END table:island_products -->

### 4.2 Production levels (Generator, Crop Growth, Spawner Rate, Harvest Yield, Mob Loot)
All five production upgrades plus spawner stacks are pooled into one **production level PL**: each level adds 12% output. Level k costs `$2.5k × 1.30^(k−1)` and needs tasks (max 6 × Depths tier). Players only buy a level that **pays back within 3.5 days** of production, so production grows mainly when the product tier rises. This table shows the worth-it ceiling per tier for an active player:

<!-- BEGIN table:upgrades_prod -->
| Depths tier | Worth-it production level (PL) | Cost of that level | All levels so far | Items per production h | Island income per production h | Per day (active, 14 h) |
|---|---|---|---|---|---|---|
| 1 | 6 | $9.3k | $31.9k | 336 | $3.1k/h | $44k/day |
| 2 | 10 | $26.5k | $106.5k | 528 | $5.1k/h | $71.2k/day |
| 3 | 12 | $44.8k | $185.8k | 662 | $8.9k/h | $123.9k/day |
| 4 | 14 | $75.7k | $319.8k | 831 | $15.4k/h | $215.6k/day |
| 5 | 16 | $128k | $546.2k | 1,042 | $26.8k/h | $374.8k/day |
| 6 | 18 | $216.3k | $928.8k | 1,307 | $46.5k/h | $651.1k/day |
| 7 | 20 | $365.5k | $1.6M | 1,640 | $80.7k/h | $1.1M/day |
| 8 | 23 | $803k | $3.5M | 2,304 | $156.9k/h | $2.2M/day |
| 9 | 25 | $1.4M | $5.9M | 2,890 | $272.1k/h | $3.8M/day |
| 10 | 31 | $6.5M | $28.4M | 5,704 | $1.3M/h | $18.5M/day |
| 11 | 37 | $31.6M | $137M | 11,259 | $6.4M/h | $89.5M/day |
| 12 | 43 | $152.6M | $661.3M | 22,224 | $31M/h | $433.4M/day |
<!-- END table:upgrades_prod -->

In Phase 5 each production upgrade gets its own price list built from the same curve: level n of production upgrade j (j = 0…4 in the order above) costs `C_p(5 × (n − 1) + j + 1)`.

---

## 5. Cost curves (Forge, island upgrades, late game)

### 5.1 The Forge (tool tiers)
Forging a tool costs money plus materials from the previous tier, so grinding a tier pays for the next tool (`DESIGN.md` §5.1). The money part is ~0.3–2.3 hours of income: noticeable, never a wall. Upgrading keeps level, perks and stats (Phase 4).

<!-- BEGIN table:forge -->
| Tool tier | Pickaxe | Money | Materials | Material sell value | Money = hours of REF(t-1) | Active forges it | Top forges it |
|---|---|---|---|---|---|---|---|
| 2 | Copper | $15k | 256 Coal | $5.1k | 0.3 h | 2.2 h (day 1) | 1.9 h (day 1) |
| 3 | Iron | $35.2k | 346 Copper | $9.3k | 0.4 h | 5.3 h (day 2) | 6.3 h (day 1) |
| 4 | Gold | $82.8k | 467 Iron | $17k | 0.5 h | 9.2 h (day 3) | 10.7 h (day 1) |
| 5 | Redstone | $194.7k | 630 Gold | $31k | 0.7 h | 14.5 h (day 4) | 16.6 h (day 2) |
| 6 | Lapis | $457.5k | 850 Redstone | $56.5k | 1.0 h | 22.5 h (day 6) | 21.0 h (day 2) |
| 7 | Diamond | $1.1M | 1,148 Lapis | $102.9k | 1.3 h | 34.2 h (day 9) | 30.5 h (day 4) |
| 8 | Emerald | $2.5M | 1,550 Diamond | $187.6k | 1.7 h | 61.0 h (day 15) | 48.0 h (day 5) |
| 9 | Amethyst | $5.9M | 2,092 Emerald | $341.9k | 2.3 h | 91.5 h (day 23) | 80.1 h (day 9) |
| 10 | Quartz | $14M | 2,824 Amethyst | $1.1M | 1.7 h | 157.0 h (day 39) | 138.0 h (day 15) |
| 11 | Netherite | $32.8M | 3,813 Quartz | $3.6M | 1.3 h | 264.3 h (day 66) | 233.2 h (day 26) |
| 12 | Voidstone | $77M | 5,147 Netherite | $11.6M | 1.0 h | not this season | 400.6 h (day 45) |
<!-- END table:forge -->

### 5.2 Island level upgrades (Phase 5 preview)
12 non-production upgrades × 12 levels. Level n needs Depths tier ≥ n in the model (in game: its tasks, `DESIGN.md` §3.5). Price = **weight × hours of REF(n)**, so every level costs about the same *effort* at the stage where players buy it. Buying all 12 at a level costs ~25–50% of the time an active player spends in that tier.

<!-- BEGIN table:upgrades_level -->
| Level | Needs Depths tier | One upgrade (Warp Slots → Sell Multiplier) | All 12 at this level | = hours of REF(n) | All 12 up to this level |
|---|---|---|---|---|---|
| 1 | 1 | $1.7k – $6k | $40.2k | 0.8 h | $40.2k |
| 2 | 2 | $4.4k – $15.8k | $105.1k | 1.2 h | $145.3k |
| 3 | 3 | $11.5k – $41.2k | $274.9k | 1.8 h | $420.2k |
| 4 | 4 | $30k – $107.8k | $718.8k | 2.7 h | $1.1M |
| 5 | 5 | $78.3k – $281.9k | $1.9M | 4.0 h | $3M |
| 6 | 6 | $204.8k – $737.3k | $4.9M | 5.9 h | $7.9M |
| 7 | 7 | $535.5k – $1.9M | $12.9M | 8.8 h | $20.8M |
| 8 | 8 | $1.4M – $5M | $33.6M | 13.1 h | $54.4M |
| 9 | 9 | $6.5M – $23.4M | $156.2M | 19.5 h | $210.6M |
| 10 | 10 | $30.3M – $109M | $726.4M | 29.1 h | $937M |
| 11 | 11 | $140.7M – $506.5M | $3.4B | 43.4 h | $4.3B |
| 12 | 12 | $654.1M – $2.4B | $15.7B | 64.6 h | $20B |
<!-- END table:upgrades_level -->

<!-- BEGIN table:upgrade_weights -->
| Upgrade | Weight W | Level 1 | Level 6 | Level 12 |
|---|---|---|---|---|
| Warp Slots | 0.5 | $1.7k | $204.8k | $654.1M |
| Island Vault | 0.6 | $2k | $245.8k | $784.9M |
| Member Slots | 0.8 | $2.7k | $327.7k | $1B |
| Fuel Tank | 0.8 | $2.7k | $327.7k | $1B |
| XP Boost | 0.9 | $3k | $368.6k | $1.2B |
| Lumberjack | 0.9 | $3k | $368.6k | $1.2B |
| Spawner Stack Limit | 1.0 | $3.4k | $409.6k | $1.3B |
| Island Size | 1.0 | $3.4k | $409.6k | $1.3B |
| Bank Capacity | 1.1 | $3.7k | $450.6k | $1.4B |
| Bank Interest | 1.2 | $4k | $491.5k | $1.6B |
| Mine Fortune | 1.4 | $4.7k | $573.4k | $1.8B |
| Sell Multiplier | 1.8 | $6k | $737.3k | $2.4B |
<!-- END table:upgrade_weights -->

Island level points per upgrade level stay weighted by tier (`REVIEW.md` A1: levels 1–3 = 1 point, 4–6 = 2, 7+ = 3) and can never be bought with gems.

### 5.3 Late projects: outposts, biomes, fusion
Each outpost = unlock + 3 biomes × 5 tiers + Fusion I/II = 18 steps, gated by pillars in game (Depths tier 7 / 9 / 11 in the model). All steps of one outpost ≈ 158 hours of REF at its gate.

<!-- BEGIN table:late -->
| Outpost | Gate (Depths tier, proxy for pillars) | Unlock | Fusion II (step 18) | All 18 steps | = hours of REF(gate) | Active reaches gate | Top reaches gate |
|---|---|---|---|---|---|---|---|
| Greenhold (farming) | 7 | $1.5M | $42.9M | $231.4M | 158 h | 34.1 h (day 9) | 30.4 h (day 4) |
| Wildwood (combat/spawners) | 9 | $8M | $235.1M | $1.3B | 158 h | 90.6 h (day 23) | 80.0 h (day 9) |
| Emberdeep (late game) | 11 | $77.9M | $2.3B | $12.3B | 158 h | 264.2 h (day 66) | 233.1 h (day 26) |
<!-- END table:late -->

### 5.4 Mastery (proposal, owner decision)
When every level upgrade is maxed, an upgrade can be **mastered** endlessly: each mastery level costs 4 h of REF × 1.12^(m−1) and gives a tiny bonus (+0.5% to that upgrade's effect) and island level points at the 7+ weight. It is the only sink that grows with top players' income in months 2–3. Without it, the money of top players has nowhere to go (§7.3).

<!-- BEGIN table:mastery -->
| Mastery level | Cost at tier 11 | Cost at tier 12 | = hours of REF(tier) |
|---|---|---|---|
| 1 | $311.5M | $971.8M | 4.0 h |
| 5 | $490.1M | $1.5B | 6.3 h |
| 10 | $863.8M | $2.7B | 11.1 h |
| 15 | $1.5B | $4.7B | 19.5 h |
| 20 | $2.7B | $8.4B | 34.5 h |
| 25 | $4.7B | $14.8B | 60.7 h |
<!-- END table:mastery -->

---

## 6. Banks and interest

Rules (all proposals, M3.3):
- Interest every 3 h, also offline, computed lazily from timestamps.
- **Min-balance rule (new, recommended):** interest is paid on the **lowest balance held during the whole 3 h cycle**. Depositing a fortune 1 minute before the payout earns nothing. This makes interest farming by timing impossible and is cheap to compute (track `min_balance` per cycle).
- Early withdrawal (before the next payout) forfeits that cycle and costs **2%** of the withdrawn amount (`PHASE-3` M3.3).
- **Activity rule (new, recommended):** an account earns interest only if its owner played ≥ 2 h in the last 7 days (stops idle alts parking money).
- Every payout has a **cap**. At high tiers the cap, not the rate, decides the payout, so effective rates fall as balances grow.

### 6.1 Personal bank (10 tiers)
Unlocking a tier costs 2% of its capacity (a small sink). Tier perks (lower AH tax, extra AH slots, small sell bonus) are still an open question (`OPEN_QUESTIONS.md` #3).

<!-- BEGIN table:interest_personal -->
| Tier | Capacity | Unlock cost | Payout cap (per 3 h) | Uncapped payout at full | Effective rate at full | Max interest / day |
|---|---|---|---|---|---|---|
| 1 | $100k | $0 | $150 | $150 | 0.150% | $1.2k |
| 2 | $500k | $10k | $480 | $750 | 0.096% | $3.8k |
| 3 | $2.5M | $50k | $1.5k | $3.8k | 0.061% | $12.3k |
| 4 | $12.5M | $250k | $4.9k | $18.8k | 0.039% | $39.3k |
| 5 | $62.5M | $1.2M | $15.7k | $93.8k | 0.025% | $125.8k |
| 6 | $312.5M | $6.2M | $50.3k | $468.8k | 0.016% | $402.7k |
| 7 | $1.6B | $31.2M | $161.1k | $2.3M | 0.010% | $1.3M |
| 8 | $7.8B | $156.2M | $515.4k | $11.7M | 0.007% | $4.1M |
| 9 | $39.1B | $781.2M | $1.6M | $58.6M | 0.004% | $13.2M |
| 10 | $195.3B | $3.9B | $5.3M | $293M | 0.003% | $42.2M |
<!-- END table:interest_personal -->

### 6.2 Island bank (Bank Capacity / Bank Interest upgrades)
<!-- BEGIN table:interest_island -->
| Bank Capacity / Interest level | Capacity | Payout cap (per 3 h) | Uncapped payout at full | Effective rate at full | Max interest / day |
|---|---|---|---|---|---|
| 0 | $1M | $2.5k | $2.5k | 0.250% | $20k |
| 1 | $4M | $7.5k | $10k | 0.188% | $60k |
| 2 | $16M | $22.5k | $40k | 0.141% | $180k |
| 3 | $64M | $67.5k | $160k | 0.105% | $540k |
| 4 | $256M | $202.5k | $640k | 0.079% | $1.6M |
| 5 | $1B | $607.5k | $2.6M | 0.059% | $4.9M |
| 6 | $4.1B | $1.8M | $10.2M | 0.044% | $14.6M |
| 7 | $16.4B | $5.5M | $41M | 0.033% | $43.7M |
| 8 | $65.5B | $16.4M | $163.8M | 0.025% | $131.2M |
| 9 | $262.1B | $49.2M | $655.4M | 0.019% | $393.7M |
| 10 | $1T | $147.6M | $2.6B | 0.014% | $1.2B |
<!-- END table:interest_island -->

### 6.3 Interest vs. income
<!-- BEGIN table:interest_vs_income -->
| Day | Casual interest (share of that day's income) | Active | Top |
|---|---|---|---|
| Day 1 | $264/day (0.3%) | $1.1k/day (0.3%) | $2.7k/day (0.1%) |
| Day 3 | $965/day (0.8%) | $4.8k/day (0.4%) | $28.5k/day (0.4%) |
| Week 1 | $3.9k/day (1.8%) | $23.4k/day (0.8%) | $125.8k/day (0.6%) |
| Week 2 | $9.8k/day (1.8%) | $39.3k/day (0.5%) | $402.7k/day (0.7%) |
| Month 1 | $39.3k/day (2.5%) | $125.8k/day (0.6%) | $1.3M/day (0.2%) |
| Month 2 | $125.8k/day (4.7%) | $1.3M/day (1.8%) | $13.2M/day (0.7%) |
| Month 3 | $125.8k/day (2.0%) | $1.3M/day (0.6%) | $13.2M/day (0.7%) |
<!-- END table:interest_vs_income -->

Interest stays a small "your money works while you sleep" bonus. Without the payout caps it would reach ~5% of a top player's income and much more for idle hoarders (§7.3).

---

## 7. Money sources vs. sinks (inflation check)

Every sink in this sheet is a gameplay cost (tools, upgrades, outposts, bank tiers, shop blocks, AH fee/tax), in line with the owner's "no extra money sinks" rule; there are no pure money burns.

All 900 daily players, averaged over the 7 days ending on that day (purchases are lumpy). "Money supply" = money held (cash + savings + banks) at the end of the day.

### 7.1 Created and removed per day, by reason
<!-- BEGIN table:sources_sinks -->
| Day (7-day avg) | Depths | Island | Events | Interest | Forge | Production | Level upg. | Late proj. | Mastery | Bank tiers | Shop | AH fee+tax |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | $232.6M | $54.1M | $69.8M | $681.4k | $16M | $90.1M | $163.5M | $0 | $0 | $8.1M | $14.3M | $2.1M |
| 3 | $434.2M | $134M | $130.3M | $2.3M | $38.2M | $114.6M | $366.7M | $0 | $0 | $16.5M | $27.9M | $4.2M |
| 7 | $959.4M | $363.2M | $287.8M | $7.1M | $90.6M | $138.6M | $872M | $145.4M | $0 | $36.6M | $64.4M | $9.7M |
| 14 | $3.8B | $1.1B | $1.1B | $35.9M | $139.2M | $175.6M | $2.7B | $1.9B | $0 | $99.6M | $241.4M | $36.2M |
| 30 | $23.2B | $14.6B | $7B | $169.9M | $504.5M | $3.1B | $13.5B | $20.7B | $0 | $0 | $1.8B | $268.6M |
| 45 | $40.4B | $32B | $12.1B | $501M | $1.5B | $15.7B | $10.4B | $43.1B | $0 | $0 | $3.4B | $507.1M |
| 60 | $77.8B | $94B | $23.3B | $1.4B | $0 | $0 | $83.3B | $59.9B | $12.5B | $11.2B | $7.8B | $1.2B |
| 75 | $95.6B | $113.2B | $28.7B | $1.6B | $0 | $0 | $12.3B | $39.2B | $116.9B | $0 | $9.5B | $1.4B |
| 90 | $96.9B | $113.5B | $29.1B | $1.6B | $0 | $0 | $35.3B | $13.5B | $145B | $0 | $9.6B | $1.4B |
<!-- END table:sources_sinks -->

### 7.2 Inflation check
<!-- BEGIN table:inflation -->
| Day (7-day avg) | Created / day | Removed / day | Sink ratio | Net / day | Money supply (held) | Held per player | Held per player in days of active income | Check |
|---|---|---|---|---|---|---|---|---|
| 1 | $357.1M | $294.1M | 82% | $63M | $63.9M | $70.9k | 0.2 | OK |
| 3 | $700.8M | $568.1M | 81% | $132.7M | $399M | $443.3k | 0.4 | OK |
| 7 | $1.6B | $1.4B | 84% | $260.2M | $1.8B | $2M | 0.7 | OK |
| 14 | $6.1B | $5.3B | 87% | $762.9M | $7.2B | $8M | 1.0 | OK |
| 30 | $44.9B | $39.9B | 89% | $5.1B | $61.5B | $68.3M | 3.0 | OK |
| 45 | $85B | $74.6B | 88% | $10.5B | $222.2B | $246.9M | 3.5 | OK |
| 60 | $196.5B | $175.9B | 90% | $20.6B | $494.7B | $549.6M | 7.7 | OK |
| 75 | $239.1B | $179.4B | 75% | $59.7B | $1.1T | $1.2B | 5.5 | OK |
| 90 | $241.1B | $204.8B | 85% | $36.3B | $1.7T | $1.8B | 8.1 | OK |
<!-- END table:inflation -->

- **Target sink ratio: ≥ 75%** of created money removed again (7-day average). **Watch** at 60–75%, **act** below 60% for 3 days in a row.
- **Held per player in days of active income:** how many days an active player needs to earn the average player's money. It drives AH prices. **Watch above 10.** It grows in months 2–3 because top players save billions; that is fine for a seasonal wipe as long as server prices (sell, Forge, upgrades) are fixed and only AH prices float.
- Server-set prices do not inflate: sell values, Forge and upgrade costs are fixed numbers. Inflation only shows on the AH and in `/trade`, which the M3.8 dashboard tracks.

### 7.3 Sensitivity (what if we're wrong?)
<!-- BEGIN table:sensitivity -->
| Scenario | Lowest sink ratio, days 60-90 | Money supply day 90 | Interest share (top) | Island share (active) | Top earned by month 2 |
|---|---|---|---|---|---|
| Baseline (this sheet) | 75% | $1.7T | 0.6% | 35% | $43.8B |
| No mastery (owner says no endless sink) | 25% | $5.7T | 1.3% | 35% | $43.8B |
| Players save twice as much | 68% | $3T | 0.6% | 34% | $43.8B |
| Interest without payout caps | 71% | $2.2T | 4.8% | 34% | $44.7B |
| Production payback 8 days (players over-invest) | 76% | $2T | 0.6% | 48% | $60.8B |
| Sell prices +25% | 74% | $2.3T | 0.5% | 38% | $61.1B |
<!-- END table:sensitivity -->

What this says:
- **Mastery (or another endless late sink) is load-bearing.** Without it the sink ratio falls to ~25% after day 60 and money supply more than triples.
- If players hoard twice as much as assumed, the late sink ratio drops into the **watch** zone (~68%): the first lever is pricier late projects/mastery.
- Interest caps matter most for hoarders and alts (an uncapped tier-10 personal bank pays $293M per payout).
- If players keep buying production levels with a longer payback than assumed, the island share rises to ~48%: raise production level costs (`CP1`/`CPG`) or lower the output growth per level (`QG`).

### 7.4 Levers (if the M3.8 dashboard disagrees with this sheet)
| Symptom | First lever | Second lever |
|---|---|---|
| Sink ratio < 60% for 3 days | Raise mastery/late-project prices (hours of REF) | Lower custom-tier sell prices (P for tiers 9–12) |
| Income per hour > 2× the table | Check dupes/anomalies first; then lower the global sell multiplier cap | Raise dynamic price impact |
| Island share > 45% | Raise production level costs (`CP1`/`CPG`) | Shorter offline fuel tank |
| Interest > 5% of income | Lower payout caps | Lower rates |
| AH prices running away | Raise AH tax for listings above X | Add more late sinks |

---

## 8. Dynamic sell prices (M3.4)

Each item has a multiplier `m` (floor 0.40, ceiling 1.50). Every item sold lowers `m` by `impact`; every 10 minutes the gap to base closes by 2%: `m ← 1 − (1 − m) × 0.98` (half-life ~5.7 h).

**Recommendation: recover by a share of the gap, not by a fixed 2 points.** With a fixed step the price has no stable point: if sales push harder than 12 points per hour it crashes to the floor, otherwise it snaps back to base. With the share-of-gap rule the price settles where `1 − m = impact × volume / 0.1212 per hour`. The model assumes it settles around **0.85** (15% under base) at normal volume.

**Impact per item = 0.15 × 0.1212 / (expected drops sold per hour)**. Because volume depends on how many players sit on a tier, the plugin should **recompute `impact` hourly from the rolling 24 h volume** (adaptive), so the same table works at 30 or 300 players:

<!-- BEGIN table:dynamic -->
| Tier | Ore | Drops sold per player per played h | Impact per item, 10 players on tier | 25 players | 50 players |
|---|---|---|---|---|---|
| 1 | Coal | 1,909 | 0.95 ppm | 0.38 ppm | 0.19 ppm |
| 2 | Copper | 2,497 | 0.73 ppm | 0.29 ppm | 0.15 ppm |
| 3 | Iron | 2,875 | 0.63 ppm | 0.25 ppm | 0.13 ppm |
| 4 | Gold | 3,650 | 0.50 ppm | 0.20 ppm | 0.10 ppm |
| 5 | Redstone | 4,170 | 0.44 ppm | 0.17 ppm | 0.09 ppm |
| 6 | Lapis | 4,748 | 0.38 ppm | 0.15 ppm | 0.08 ppm |
| 7 | Diamond | 5,911 | 0.31 ppm | 0.12 ppm | 0.06 ppm |
| 8 | Emerald | 6,640 | 0.27 ppm | 0.11 ppm | 0.05 ppm |
| 9 | Amethyst | 7,550 | 0.24 ppm | 0.10 ppm | 0.05 ppm |
| 10 | Quartz | 9,312 | 0.20 ppm | 0.08 ppm | 0.04 ppm |
| 11 | Netherite | 10,252 | 0.18 ppm | 0.07 ppm | 0.04 ppm |
| 12 | Voidstone | 11,123 | 0.16 ppm | 0.07 ppm | 0.03 ppm |
<!-- END table:dynamic -->

**Max total sell multiplier: ×4.00 (+300%, owner #46).** Upgrades, rank, boosters (personal and global, O11), sets and events add up, and the sum is capped at +300%; only a player with the best of everything reaches it. The model's archetypes stay far below the cap (rank + 0.03 per tier ≤ +41%). Setting: `economy.yml → sell.multiplier-cap-percent: 300`; sell code applies it through `MultiplierCap` (common).

(ppm = parts per million of base price per item sold.) Items nobody sells drift up towards the ceiling (×1.10 target after 24 h without sales, then capped at ×1.50), which rewards selling a mix. The daily **Hot Item** gets +25% for 24 h (proposal).

---

## 9. Crystals, gems and shards

### 9.1 Crystals (island crystals, replaced tokens: earned by using custom tools and island missions, spent on the island shop, enchants, Forge extras)
Crystals drop as a chance per block/log/kill; the average grows ×1.30 per tier.

<!-- BEGIN table:crystal_rates -->
| Tier | Ore | Crystals per block (avg) | Feels like |
|---|---|---|---|
| 1 | Coal | 0.050 | 1 per 20 blocks |
| 2 | Copper | 0.065 | 1 per 15 blocks |
| 3 | Iron | 0.085 | 1 per 12 blocks |
| 4 | Gold | 0.110 | 1 per 9 blocks |
| 5 | Redstone | 0.143 | 1 per 7 blocks |
| 6 | Lapis | 0.186 | 1 per 5 blocks |
| 7 | Diamond | 0.241 | 1 per 4 blocks |
| 8 | Emerald | 0.314 | 1 per 3 blocks |
| 9 | Amethyst | 0.408 | 1 per 2 blocks |
| 10 | Quartz | 0.530 | 1 per 2 blocks |
| 11 | Netherite | 0.689 | 1 per 1 blocks |
| 12 | Voidstone | 0.896 | 1 per 1 blocks |
<!-- END table:crystal_rates -->

Custom enchants have 10 levels; each level costs ×1.8 the previous. The first level (100 crystals) is reachable in the first hour, so slot 1 gets an enchant right away.

<!-- BEGIN table:enchant_costs -->
| Enchant level | Crystal cost | Total to this level |
|---|---|---|
| 1 | 100 | 100 |
| 2 | 180 | 280 |
| 3 | 324 | 604 |
| 4 | 583 | 1,187 |
| 5 | 1,050 | 2,237 |
| 6 | 1,890 | 4,127 |
| 7 | 3,401 | 7,528 |
| 8 | 6,122 | 13,650 |
| 9 | 11,020 | 24,670 |
| 10 | 19,836 | 44,506 |
<!-- END table:enchant_costs -->

<!-- BEGIN table:crystals -->
| Stage | Casual (crystals per played h · hours to max one enchant) | Active | Top |
|---|---|---|---|
| First hour | 79/h · 566 h per maxed enchant | 98/h · 453 h per maxed enchant | 126/h · 353 h per maxed enchant |
| Day 1 | 90/h · 496 h per maxed enchant | 162/h · 275 h per maxed enchant | 409/h · 109 h per maxed enchant |
| Day 3 | 128/h · 347 h per maxed enchant | 361/h · 123 h per maxed enchant | 904/h · 49 h per maxed enchant |
| Week 1 | 204/h · 218 h per maxed enchant | 792/h · 56 h per maxed enchant | 1,813/h · 25 h per maxed enchant |
| Week 2 | 404/h · 110 h per maxed enchant | 1,491/h · 30 h per maxed enchant | 2,808/h · 16 h per maxed enchant |
| Month 1 | 878/h · 51 h per maxed enchant | 2,461/h · 18 h per maxed enchant | 5,339/h · 8 h per maxed enchant |
| Month 2 | 1,271/h · 35 h per maxed enchant | 3,451/h · 13 h per maxed enchant | 7,288/h · 6 h per maxed enchant |
| Month 3 | 1,784/h · 25 h per maxed enchant | 4,711/h · 9 h per maxed enchant | 7,288/h · 6 h per maxed enchant |
<!-- END table:crystals -->

Filling 4 slots with maxed enchants takes an active player until ~day 32 (~126 played hours) and a top player until ~day 12; casual players never max all four. Over a season an active player earns ~1.1M crystals, about 6× what 4 maxed enchants cost, so **the island shop and Forge extras must absorb most crystals**. That is safe as long as crystals are not tradeable (surplus crystals can't leak into the money economy).

**Crystals are not tradeable** (no `/pay`, not on the AH; owner decision CR3): otherwise alts farm tier-1 crystals and feed a main, and crystals become a second, uncontrolled money. Enchanted tools stay tradeable, so crystal value still reaches the market through items. Island missions (CR1) are an extra source not in this model yet.

### 9.2 Gems (premium, not tradeable, decided)
<!-- BEGIN table:gems -->
| Gem source | Gems | Typical | Type |
|---|---|---|---|
| Vote (per site, ~5 sites) | 3 | 15 / day | free |
| Say gg after a store purchase | 1 | max 5 / day | free, 60 s window |
| Daily login streak day 7 | 10 | ~10 / week | free |
| Store credit | set in Tebex | - | paid |
<!-- END table:gems -->

Gem uses: cosmetics, tool skins, extra `/pv` vaults, nickname colours, personal boosters (count towards the sell multiplier cap). **Gems never buy money, upgrade levels, task progress, crystals or crate keys** (`REVIEW.md` A1, keys are earned only). There is no gem→money exchange, so gems can't cause inflation.

### 9.3 Shards (tradeable, decided; earning is an open question)
| Source (proposal, `OPEN_QUESTIONS.md` #1) | Shards |
|---|---|
| World boss, top-3 damage | 20 / 15 / 10, everyone else with ≥ 1% damage: 3 |
| KOTH capture | 10 |
| Envoy crate | 1–3 |
| Depths tier 9+ rare drop | 1 (≈ 1 per hour of play) |

Shards only buy from the **Black Market** (rotating, limited stock), so supply is set by events, not by grinding. Being tradeable, they get an AH price; the M3.8 dashboard tracks shard supply too.

### 9.4 Money notes
Notes (`/withdraw`) are only a transfer, so **no fee** (a fee would push trades to `/pay`). Each note has a unique id, redeeming marks it used; copies are confiscated (M3.2).

---

## 10. Risks and mitigations

| # | Risk | What could happen | Mitigation |
|---|---|---|---|
| R1 | **Runaway inflation** | Late-season income piles up, AH prices explode, new players can't buy anything from players | Endless late sink (Mastery, §5.4); sink ratio ≥ 75% target with dashboard alerts (M3.8); levers table (§7.4); seasonal wipe resets everything; server prices fixed |
| R2 | **Interest abuse** | Deposit right before the payout; park money on idle alts; compound interest snowballs | Min-balance rule; payout caps (effective rate falls to ~0.003% at tier 10); activity rule (≥ 2 h in 7 days); early-withdrawal forfeit + 2% fee; bank tier unlock costs |
| R3 | **Alt farming** | Alts mine tier 1, claim starter kits, collect daily rewards/votes and `/pay` the main; alts as coop members add fuel | Depths income needs active breaking (no AFK income in The Depths); starter kits not on the AH (Phase 4); crystals and gems not tradeable; new accounts (< 30 min played, owner #40) can't `/pay`, `/withdraw` or use the AH; alt detection flags same-IP transfers (staff alert, not ban); fuel is per island, not per member; anomaly alerts for transfers > 10× the receiver's hourly income |
| R4 | **AH manipulation** | Laundering money via fake listings (dirt for $1B), price fixing by buying out a category, wash trading for baltop | 1% listing fee + 5% tax make laundering cost 6%; staff alert for any sale > 20× the item's 7-day median or between linked accounts; max 5 listings (more by rank) limits buy-out-and-relist; full listing log in `/logs` |
| R5 | **AFK island production** | Players AFK on the island 24/7 (online production + fuel) and island income overtakes The Depths | Fuel tank cap (12 h start); AFK detection: production online rate only while the player was active in the last 15 min, otherwise it burns fuel like offline; Fuel Tank upgrade is priced as a level upgrade |
| R6 | **Dupes / exploits** | A dupe creates money from nothing and wrecks the curve | Atomic DB transactions (M3.1); unique ids for notes, tools, AH items; anomaly alert when a player earns > 3× the table's income per hour for their tier (this sheet is the baseline) |
| R7 | **Sell price table errors** | One item priced 100× too high becomes the only thing anyone sells | All prices from this sheet's formulas (`price(t)` per tier, 50% for island items); dynamic prices pull overselling down; a config validation test checks every sellable item has a price within its tier band |
| R8 | **Villager trading** (allowed) | Villager trades turn shop items into emeralds/items worth more than they cost | Emeralds from villagers are vanilla emeralds, not Depths drops; vanilla emerald sells for the generator price only; watch trade volume in the dashboard |
| R9 | **Model is wrong** | Real players play differently than the archetypes | M3.8 dashboard compares real income per hour per tier with §2.1 weekly; re-run this model with measured values; the levers in §7.4 are all config values |

---

## 11. Model self-checks
Generated on every run; all must say PASS before this sheet goes to the owner.

<!-- BEGIN table:checks -->
| Check | Result | Value |
|---|---|---|
| Depths share 40-60% (active, season) | PASS | 49% |
| Island share 25-45% (active, season) | PASS | 35% |
| Events share 10-20% (active, season) | PASS | 15% |
| Interest <= 5% of income (all, season) | PASS | 2.9%, 0.7%, 0.6% |
| First hour 30k-100k (top) | PASS | $55.8k |
| Month 2 within 0.5x-2x of $50B (top) | PASS | $43.8B |
| Week 2 within 0.5x-2x of $500M (top) | PASS | $428.4M |
| Sink ratio >= 75% every checked day | PASS | min 75% |
| Slot 1 within ~1-2 h (active) | PASS | 1.1 h (day 1) |
| Slot 4 in week 3-6 (active) | PASS | 109.7 h (day 28) |
| Max blocks/s under reach cap | PASS | 4.10 <= 6.0 |
| Money fits in a long | PASS | season total, all tops |
| Tier 12 not before week 4 (top) | PASS | 400.5 h (day 45) |
<!-- END table:checks -->

---

## 12. Owner approval checklist

Approve or change:
1. Income per stage (§2.1) and the day-3 answer (§2.2: accept ~$15M for top players, or reshape).
2. Mine/forest/combat sell values and the two-slope price curve (×1.35 vanilla, ×2.4 custom) (§3).
3. Tool level curve ×1.21 instead of ×1.12 (§3.5) and the skill curve (§3.4).
4. Combat skill thresholds 0/6/12/18/24/30/38/46/55/66 (§3.2).
5. Island items at 50% of Depths items (§4.1) and the production level pricing (§4.2).
6. Forge, level upgrade, late project costs as "hours of income" (§5).
7. **Mastery as the endless late sink** (§5.4), or another endless sink.
8. Bank capacities, unlock costs, payout caps, min-balance rule and activity rule (§6).
9. Sink ratio target ≥ 75% and the dashboard thresholds (§7.2).
10. Share-of-gap recovery and adaptive impact for dynamic prices; Hot Item +25% (§8).
11. Crystals not tradeable (decided, CR3); enchant cost curve (§9.1); shard sources (§9.3); notes without fee (§9.4).
12. Risk mitigations R1–R9, especially: new accounts can't `/pay`, `/withdraw` or use the AH before 30 min played (owner #40); AFK production rule (§10).

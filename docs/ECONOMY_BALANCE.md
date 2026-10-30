# Economy Balance Sheet (M3.0, re-run BAL2)

> **Status: DRAFT, waiting for owner approval.** No price from this sheet goes live (M3.4 selling, M3.5 shop, Phase 4 Forge, Phase 5 upgrades) until the owner approves it. All numbers are proposals and live in typed configs, never in code.
>
> **Every table is generated** by [`tools/economy_model.py`](../tools/economy_model.py) from the formulas in §1. Change a parameter there, then run:
> ```bash
> python3 tools/economy_model.py --write docs/ECONOMY_BALANCE.md   # refresh all tables in this file
> python3 tools/economy_model.py --check docs/ECONOMY_BALANCE.md   # fails if this file is out of date
> python3 tools/economy_model.py --table milestones                # print one table
> python3 tools/economy_model.py                                   # print everything
> ```
> Every run ends with a self-check (asserts on the cap math, the stack steps, determinism and the pacing curve). §13 reads the current config defaults straight from the Java config classes, so a config change shows up there on the next run.
>
> Sources: `CONTEXT.md` §3, `DESIGN.md` §3–6, `WORLD.md` §4–5, `phases/PHASE-3-ECONOMY.md`, `phases/PHASE-4-DEPTHS.md`, `DECISIONS_2026-10-30.md` (round 2 + 3), `plans/ENCHANTS.md`, `plans/WARDROBE.md`.

---

## 0. Summary

**What BAL2 changed** (owner decisions of 2026-10-30):
- **Three new archetypes:** casual 1 h/day, regular 3 h/day, grinder 8 h/day (§1.1).
- **Real sell bonuses with the +300% cap (#46, O11).** Rank, the Sell Multiplier upgrade, the Market Contacts mastery, the Coinmaster set (by set level), Gilded Touch, personal and global boosters and sell events are **added up, then capped once** (§2.7). The old "+3% per tier" stand-in is gone.
- **Spawners on their own** (§4.3): stack limit 8 → 512 in 5 steps, 5 spawner tiers, a shop price that rises with every spawner of that tier the island owns.
- **Crystals as a currency** (§9.1): earned per block plus island missions, spent on enchants, the crystal set, rolls and the island shop.
- **Pacing curve (#61):** fast start, every tier takes longer than the one before, ups at every new tier and Forge, plateaus in between (§2.6).
- Custom tier prices now grow **×2.2** per tier instead of ×2.4, because the real sell bonuses sit on top late in the season.

**Results:**
- **Fast start:** every archetype reaches tier 2 within 3 played hours and earns ~$40–70k in the first hour.
- **Progressively harder:** the regular's gap between tiers grows from 2 h (tier 2) to 109 h (tier 11). The longest step is tier 8 → 9 (x2.2 the gap before it), and it is followed by the biggest jump (+120% after the Forge).
- **Income meta still holds** for the regular player over the season: The Depths ~46%, island ~38% (production 19% + spawners 19%), events ~14%, interest ~2% (§2.3).
- **Owner targets** (most active player, money earned so far): first hour, week 2 and month 2 land within 0.8–1.3× of the targets; day 3 is still ~2.7× (§2.2).
- **The +300% cap is never reached by normal play.** A grinder ends the season at about +143% without boosters (average ×2.7 with boosters). Only the best of everything at once (+305%) touches the cap (§2.7).
- **Biggest risk found: spawner output.** `stacking.yml → spawners.mobs-per-spawner: 4` makes one spawner earn 4× what this sheet assumes. With 4, spawners become ~69% of a regular player's income. **Suggested: 1** (§4.3, §14). Also needed: the shop price of spawners must rise per spawner the island owns, otherwise spawners pay for each other and money compounds (§7.3).
- **Sinks:** the economy removes **85% of the money it creates over the season**. The 14-day average never drops into the "act" zone (lowest 61% around day 29, when regular players save for the Coinmaster set). Mastery is still load-bearing: without it the season sink ratio falls to ~42% (§7.3).

**Decisions for the owner** are in §12, suggested config changes in §14, new questions in `docs/OPEN_QUESTIONS.md`.

---

## 1. How the model works

### 1.1 Players and time
Three archetypes play every day of a 90-day season. All money values are whole dollars.

<!-- BEGIN table:archetypes -->
| Archetype | Played h/day | Breaking uptime | Production h/day (online + AFK + fuel) | Rank sell bonus | Personal booster uptime | Saves (never spent) | Daily players |
|---|---|---|---|---|---|---|---|
| Casual (1 h/day) | 1 (day 1: 1.5) | 60% | 8 | 0% | 5% | 20% | 540 |
| Regular (3 h/day) | 3 (day 1: 4) | 75% | 12 | 4% | 15% | 15% | 270 |
| Grinder (8 h/day) | 8 (day 1: 10) | 85% | 20 | 8% | 30% | 10% | 90 |
<!-- END table:archetypes -->

Each played hour: 70% breaking in The Depths, the rest is island work, menus, selling and events. Island production and spawners run for the archetype's production hours per day (online + AFK pool + fuel; the AFK pool is capped at 8 h/day, O10). Players buy in this order whenever they can afford it:
1. the next Forge tool (money + materials of the previous tier),
2. the Coinmaster set once they reach tier 8 (they save for it if it costs at most 3 days of income),
3. production levels and spawners that pay back within 3.5 days,
4. the cheapest island level upgrade or late project,
5. the next personal bank tier,
6. mastery.

They also spend 4% of income in `/shop` and trade 10% of income on the AH. Crystals go to enchants first (up to what the tool level allows), the crystal set at tier 6, then rolls and the island shop.

**"Income per played hour"** = Depths + events per played hour, plus that day's island and spawner production spread over the played hours.

### 1.2 Parameters and formulas
`t` = tier (1–12), `REF(t)` = reference income per played hour at tier t, used to price upgrades in "hours of income" so prices follow income automatically.

<!-- BEGIN table:params -->
| Parameter | Formula / value | Note |
|---|---|---|
| Start money | $1k | decided |
| Mine drop price | P(t) = 20 x 1.35^(min(t,8)-1) x 2.2^max(0,t-8) | tiers 1-8 / 9-12 |
| Reference income | REF(t) = 2,500 x 1.3^(t-1) x P(t) | per played hour, all sources; prices are 'hours of REF' |
| Break rate | bps = min(6.0, 1.0 x 1.05^(tool-1) x (1+0.01 x level) x (1+0.12 x slots)) | blocks/s |
| New ore before its tool | break speed x 0.7 | until the next tool is forged (#61 'down') |
| Fortune | drops/block = 1 + 0.05 x (tool-1) |  |
| Depths time | 70% | share of a played hour spent breaking |
| Dynamic price (Depths / island) | 0.85 / 0.9 | average realised multiplier |
| Sell multiplier | 1 + min(3, sum of all sell bonuses) | §2.7; cap applied in every booster state |
| Skill XP per level | x(L) = 600 x 1.2^min(L,35) x 1.13^clamp(L-35,0,31) x 1.02^max(0,L-66) | total = sum of steps; max level 150 |
| Tool XP to level L | XT(L) = 400 x (1.21^(L-1) - 1) / (1.21 - 1) | XP per block = tier XP |
| Forge money | F(t) = $15k x 2.35^(t-2), t >= 2 | tier 1 = free starter kit |
| Forge materials | M(t) = 256 x 1.35^(t-2) drops of tier t-1 | checked in the model |
| Island output | Q(PL) = 120 x 1.12^PL items per production hour | each worth 0.5 x P(tier-1); spawners separate |
| Production level | C_p(k) = $2.5k x 1.3^(k-1) | max 6 x tier; bought if payback <= 3.5 days |
| Spawner output | 1 mob per spawner every 10-40 s = 144 mobs/h | each mob 1.5 items worth 0.5 x P(2s-1) |
| Spawner price | base(s) x 1.1^(spawners of that tier on the island) | base = 60 production h of its own output: $117k / $213k / $387k / $706k / $2.1M |
| Spawners found | 0.1 per played hour | Depths drops + crates, best tier in reach |
| Spawner stack limit | 8 -> 32 -> 64 -> 128 -> 256 -> 512 | per block, by Spawner Stack Limit level |
| Level upgrade | C_u(u,n) = W_u x 0.067 x 1.49^(n-1) x REF(n) | 12 upgrades x 12 levels, level n needs tier n |
| Late projects | C_late(o,j) = 1.0 x 1.22^j x REF(gate_o) | 3 outposts x 18 steps, gates tier 7/9/11 |
| Mastery | C_m(m) = 4.0 x 1.12^(m-1) x REF(tier) | endless, after all levels are maxed |
| Events/quests/crates | 0.3 x Depths income per played hour | rewards defined per tier |
| Shop spend | 4% of earned | blocks, decor, seeds |
| AH | 10% of earned traded, fee 1% + tax 5% | only fee+tax leave the economy |
| Interest | personal 0.15% / island 0.25% per 3 h | capped per payout |
| Crystals | 0.05 x 1.3^(t-1) per block, +20% island missions | not tradeable (CR3) |
| Daily uniques casual / regular / grinder | 540 / 270 / 90 | avg ~112 online, peak ~300 |
<!-- END table:params -->

Notes:
- **Vanilla tiers 1–8** grow ×1.35 per tier, **custom tiers 9–12** grow ×2.2 (was ×2.4). The big-money endgame stays, but the sell bonuses that pile up late in the season no longer push month 2–3 far past the targets.
- **Forest and combat are priced for parity:** the same money per played hour as the mine at the same tier (§3.2).
- **New ore is harder.** Until the next tool is forged, a new tier's ore breaks at 70% speed. On vanilla tiers players keep mining the old ore for those few minutes; on custom tiers the new ore pays more even when slow.

---

## 2. Progression

### 2.1 Money per hour per stage (and money earned so far)
<!-- BEGIN table:targets -->
| Stage | Casual (income per played hour · earned so far) | Regular | Grinder |
|---|---|---|---|
| First hour | $53.8k/h · $39.6k | $60.4k/h · $53.4k | $79.6k/h · $67.2k |
| Day 1 | $61.5k/h · $72.8k | $113.1k/h · $333.1k | $412k/h · $2.2M |
| Day 3 | $85.7k/h · $216.7k | $346.1k/h · $1.6M | $1.2M/h · $13.6M |
| Week 1 | $145.8k/h · $646.1k | $669.6k/h · $8M | $3.7M/h · $96.9M |
| Week 2 | $322.4k/h · $2.4M | $1.8M/h · $36.7M | $14.8M/h · $639.6M |
| Month 1 | $905.1k/h · $11.6M | $3.4M/h · $175.9M | $86.8M/h · $5B |
| Month 2 | $2M/h · $56.9M | $30.2M/h · $1.6B | $250.6M/h · $39B |
| Month 3 | $3.3M/h · $143.2M | $78.4M/h · $4.9B | $276.2M/h · $104.1B |
<!-- END table:targets -->

### 2.2 Owner targets vs. model
<!-- BEGIN table:targets_check -->
| Stage | Owner target (most active, earned so far) | Model grinder | Grinder / target | Model regular | Regular / target |
|---|---|---|---|---|---|
| First hour | $50k | $67.2k | 1.3x | $53.4k | 1.1x |
| Day 3 | $5M | $13.6M | 2.7x | $1.6M | 0.3x |
| Week 2 | $500M | $639.6M | 1.3x | $36.7M | 0.1x |
| Month 2 | $50B | $39B | 0.8x | $1.6B | 0.0x |
<!-- END table:targets_check -->

Day 3 is still high for the most active players: a grinder has played ~26 h by day 3 and is at tier 6. Hitting $5M there while still reaching $500M in week 2 needs a 100× jump in 11 days, which feels like a cliff. **Proposal (unchanged):** read the day-3 target as "a regular player" (~$1.6M), keep the smooth curve.

### 2.3 Where the money comes from (share of money earned so far)
<!-- BEGIN table:shares -->
| Archetype | Earned up to | Depths | Island production | Spawners | Island total | Events/quests | Interest |
|---|---|---|---|---|---|---|---|
| casual | Week 1 | 60% | 21% | 0% | 21% | 18% | 1.0% |
| casual | Month 1 | 49% | 19% | 15% | 34% | 15% | 3.3% |
| casual | Month 3 | 40% | 20% | 23% | 44% | 12% | 4.8% |
| regular | Week 1 | 59% | 14% | 9% | 23% | 18% | 0.8% |
| regular | Month 1 | 52% | 15% | 16% | 31% | 15% | 1.6% |
| regular | Month 3 | 46% | 19% | 19% | 38% | 14% | 1.8% |
| grinder | Week 1 | 50% | 14% | 22% | 35% | 15% | 0.4% |
| grinder | Month 1 | 49% | 18% | 18% | 36% | 15% | 0.7% |
| grinder | Month 3 | 42% | 38% | 7% | 45% | 13% | 0.6% |
| **target** | season | ~50% |  |  | ~35% | ~15% | <= 5% |
<!-- END table:shares -->

- Early on the island is small, so The Depths carries ~60%. The island and spawners catch up after week 1.
- **Casual players lean on the island** (44% by month 3): they play 1 h but production runs 8 h. That is intended (a casual player's island keeps working), and the absolute amounts stay small.
- **Grinders drift to ~45% island by month 3** because production runs ~20 h/day. That is the AFK lever (risk R5).

### 2.4 Where each archetype is at each stage
<!-- BEGIN table:progress -->
| Stage | Casual (played h · tier · tool level (slots) · production levels · spawners/stack limit · level upgrades · late projects · mastery · Coinmaster level) | Regular | Grinder |
|---|---|---|---|
| First hour | 1 h · T1 · Lv4 (0) · PL 4 · SP 0 (8/block) · UL 6 · late 0 · M 0 · CM - | 1 h · T1 · Lv4 (0) · PL 4 · SP 0 (8/block) · UL 9 · late 0 · M 0 · CM - | 1 h · T1 · Lv5 (1) · PL 5 · SP 0 (8/block) · UL 11 · late 0 · M 0 · CM - |
| Day 1 | 2 h · T1 · Lv5 (1) · PL 4 · SP 0 (8/block) · UL 12 · late 0 · M 0 · CM - | 4 h · T2 · Lv11 (1) · PL 7 · SP 0 (32/block) · UL 24 · late 0 · M 0 · CM - | 10 h · T4 · Lv20 (2) · PL 16 · SP 1 (64/block) · UL 47 · late 0 · M 0 · CM - |
| Day 3 | 4 h · T2 · Lv8 (1) · PL 4 · SP 0 (32/block) · UL 22 · late 0 · M 0 · CM - | 10 h · T4 · Lv18 (2) · PL 12 · SP 1 (64/block) · UL 45 · late 0 · M 0 · CM - | 26 h · T6 · Lv29 (2) · PL 20 · SP 10 (128/block) · UL 68 · late 0 · M 0 · CM - |
| Week 1 | 7 h · T3 · Lv14 (1) · PL 6 · SP 0 (32/block) · UL 34 · late 0 · M 0 · CM - | 22 h · T5 · Lv27 (2) · PL 15 · SP 3 (64/block) · UL 60 · late 0 · M 0 · CM - | 58 h · T8 · Lv39 (3) · PL 25 · SP 21 (128/block) · UL 84 · late 5 · M 0 · CM - |
| Week 2 | 14 h · T4 · Lv20 (2) · PL 9 · SP 1 (64/block) · UL 48 · late 0 · M 0 · CM - | 43 h · T7 · Lv34 (3) · PL 19 · SP 6 (128/block) · UL 83 · late 2 · M 0 · CM - | 114 h · T9 · Lv46 (4) · PL 29 · SP 38 (256/block) · UL 108 · late 22 · M 0 · CM 50 |
| Month 1 | 31 h · T6 · Lv28 (2) · PL 13 · SP 3 (128/block) · UL 68 · late 0 · M 0 · CM - | 91 h · T8 · Lv42 (3) · PL 21 · SP 11 (128/block) · UL 96 · late 12 · M 0 · CM - | 242 h · T11 · Lv50 (4) · PL 40 · SP 51 (256/block) · UL 124 · late 42 · M 0 · CM 50 |
| Month 2 | 61 h · T7 · Lv36 (3) · PL 16 · SP 6 (128/block) · UL 84 · late 6 · M 0 · CM - | 181 h · T10 · Lv50 (4) · PL 31 · SP 24 (256/block) · UL 118 · late 30 · M 0 · CM 50 | 482 h · T12 · Lv50 (4) · PL 45 · SP 75 (512/block) · UL 144 · late 54 · M 5 · CM 50 |
| Month 3 | 90 h · T8 · Lv40 (3) · PL 18 · SP 9 (128/block) · UL 96 · late 10 · M 0 · CM - | 271 h · T11 · Lv50 (4) · PL 36 · SP 33 (256/block) · UL 124 · late 42 · M 0 · CM 50 | 722 h · T12 · Lv50 (4) · PL 46 · SP 99 (512/block) · UL 144 · late 54 · M 21 · CM 50 |
<!-- END table:progress -->

### 2.5 Time to milestone
Played hours (and the day it happens) for each archetype.

<!-- BEGIN table:milestones -->
| Milestone | Casual (1 h/day): played h (day) | Regular (3 h/day) | Grinder (8 h/day) |
|---|---|---|---|
| Earned $100k | 2.0 h (day 2) | 1.7 h (day 1) | 1.4 h (day 1) |
| Earned $1M | 9.6 h (day 10) | 8.0 h (day 3) | 7.0 h (day 1) |
| Earned $10M | 28.7 h (day 29) | 24.6 h (day 8) | 23.2 h (day 3) |
| Earned $100M | 77.5 h (day 77) | 68.3 h (day 23) | 59.0 h (day 8) |
| Earned $1B | not this season | 159.9 h (day 53) | 138.0 h (day 17) |
| Earned $10B | not this season | not this season | 299.3 h (day 38) |
| Earned $100B | not this season | not this season | 707.9 h (day 89) |
| Depths tier 4 (Gold) | 11.3 h (day 11) | 9.1 h (day 3) | 8.0 h (day 1) |
| Depths tier 8 (Emerald) | 64.6 h (day 65) | 51.7 h (day 17) | 48.8 h (day 6) |
| Depths tier 10 (Quartz) | not this season | 156.6 h (day 52) | 140.1 h (day 18) |
| Depths tier 12 (Voidstone) | not this season | not this season | 403.5 h (day 51) |
| Tool Lv 30 (3 perk slots) | 35.0 h (day 35) | 28.1 h (day 10) | 27.9 h (day 4) |
| Spawner stack 64 | 11.5 h (day 11) | 9.8 h (day 3) | 9.1 h (day 1) |
| Spawner stack 512 | not this season | not this season | 427.9 h (day 54) |
| Coinmaster set bought | not this season | 100.0 h (day 33) | 62.6 h (day 8) |
| Coinmaster Lv 50 (2x) | not this season | 129.0 h (day 43) | 91.6 h (day 12) |
| Sell bonus +100% (no boosters) | not this season | 112.2 h (day 38) | 74.1 h (day 10) |
| Sell bonus +200% (no boosters) | not this season | not this season | not this season |
| First enchant at V (crystals) | 73.5 h (day 74) | 58.3 h (day 20) | 53.6 h (day 7) |
| Crystal set (Deepforge) | 56.1 h (day 56) | 44.9 h (day 15) | 42.5 h (day 6) |
<!-- END table:milestones -->

Read: a casual player (1 h/day) reaches tier 8 around day 65 and earns ~$140M over the season; a regular (3 h/day) reaches tier 11 at the very end; a grinder (8 h/day) reaches tier 12 around day 51. Nobody reaches +200% sell bonus without boosters.

### 2.6 Pacing: fast start, harder every tier, ups and downs (#61)
The owner wants every system to be fast at first, then progressively harder, with ups and downs that keep players going. In the model this comes from:

| Up (a jump) | Down / plateau (a grind) |
|---|---|
| New Depths tier: ore +35% (vanilla) or +120% (custom) after the Forge | Each tier takes longer than the one before (gap ×1.3–2.2) |
| Forge: new tool = faster + more fortune | New ore is slow until the next tool is forged (custom tiers) |
| Spawner stack step (8 → 32 → 64 → 128 → 256 → 512 per block) | Saving phases: the Coinmaster set ($40M) pauses upgrades for ~3 days |
| Coinmaster set: +50% sell at level 50 | Set level starts at 0× (0 bonus on day one of a new set) |
| Perk slot unlock (tool Lv 5/15/30/45) | Merging two sets resets the level to 0 (WARDROBE §5), prestige restarts tool speed at Lv 1 |

**Tier gaps (regular player):**
<!-- BEGIN table:pacing -->
| Tier | Ore | Played h since last tier (regular) | vs last gap | Up at unlock (new ore, old tool) | Plateau until the tool is forged | Up after the forge (vs old ore) |
|---|---|---|---|---|---|---|
| 2 | Copper | 2.1 h (day 1) | - | 0% (keeps old ore) | 0.1 h | +35% |
| 3 | Iron | 3.1 h (day 2) | x1.5 | 0% (keeps old ore) | 0.1 h | +35% |
| 4 | Gold | 3.9 h (day 3) | x1.3 | 0% (keeps old ore) | 0.1 h | +35% |
| 5 | Redstone | 5.3 h (day 5) | x1.4 | 0% (keeps old ore) | 0.1 h | +35% |
| 6 | Lapis | 8.0 h (day 8) | x1.5 | 0% (keeps old ore) | 0.1 h | +35% |
| 7 | Diamond | 11.7 h (day 12) | x1.5 | 0% (keeps old ore) | 0.1 h | +35% |
| 8 | Emerald | 17.6 h (day 17) | x1.5 | 0% (keeps old ore) | 3.3 h | +35% |
| 9 | Amethyst | 39.5 h (day 31) | x2.2 | +54% | 2.1 h | +120% |
| 10 | Quartz | 65.4 h (day 52) | x1.7 | +54% | 0.1 h | +120% |
| 11 | Netherite | 108.8 h (day 89) | x1.7 | +54% | 0.1 h | +120% |
| 12 | Voidstone | not this season | - | - | - | not this season |
<!-- END table:pacing -->

**Income per played hour over the season** ("UP" = +10% or more per day since the last row, "climb" = +2–10%, "plateau" = under +2% per day):
<!-- BEGIN table:curve -->
| Day | Casual (income per played h · tier · trend per day) | Regular | Grinder |
|---|---|---|---|
| 1 | $61.5k/h · T1 · start | $113.1k/h · T2 · start | $412k/h · T4 · start |
| 2 | $62.4k/h · T1 · plateau | $207k/h · T3 · UP | $745.6k/h · T5 · UP |
| 3 | $85.7k/h · T2 · UP | $346.1k/h · T4 · UP | $1.2M/h · T6 · UP |
| 5 | $91.5k/h · T2 · climb | $585.9k/h · T5 · UP | $2.5M/h · T7 · UP |
| 7 | $145.8k/h · T3 · UP | $669.6k/h · T5 · climb | $3.7M/h · T8 · UP |
| 10 | $207.3k/h · T3 · UP | $1.1M/h · T6 · UP | $5.2M/h · T8 · UP |
| 14 | $322.4k/h · T4 · UP | $1.8M/h · T7 · UP | $14.8M/h · T9 · UP |
| 21 | $573.1k/h · T5 · climb | $3M/h · T8 · climb | $35M/h · T10 · UP |
| 30 | $905.1k/h · T6 · climb | $3.4M/h · T8 · plateau | $86.8M/h · T11 · UP |
| 45 | $1.6M/h · T7 · climb | $12.7M/h · T9 · climb | $91.1M/h · T11 · plateau |
| 60 | $2M/h · T7 · plateau | $30.2M/h · T10 · climb | $250.6M/h · T12 · climb |
| 75 | $2.9M/h · T8 · climb | $32.1M/h · T10 · plateau | $271.4M/h · T12 · plateau |
| 90 | $3.3M/h · T8 · plateau | $78.4M/h · T11 · climb | $276.2M/h · T12 · plateau |
<!-- END table:curve -->

The curve has the right shape: steep ups in the first week, then each archetype alternates between ups (new tier, Coinmaster) and plateaus (grinding the next tier). Late in the season the grinder plateaus at tier 12; mastery and leaderboards are the goal there, not more income.

### 2.7 Sell multiplier and the +300% cap
All sell bonuses belong to **one family**: they add up, then the cap applies **once** (`MultiplierCap` in common, `economy.yml → sell.multiplier-cap-percent: 300`). Boosters count too (O11). The model applies the cap in every booster/event state, then averages over the day.

<!-- BEGIN table:sell_sources -->
| Source (all add up, then one cap) | Bonus | Where it comes from | Model assumption |
|---|---|---|---|
| Rank | 15% | store ranks (Ember/freerank: 0%) | model: 0 / 4 / 8% |
| Sell Multiplier island upgrade | 5% per level, 60% at Lv 12 | level n needs tier n | bought with money |
| Market Contacts mastery (MA5) | 2% per level, max 40% | after the upgrades are maxed | model: 25% of mastery levels |
| Coinmaster Silks (set) | 0 at Lv 0 → 25% at Lv 25 → 50% at Lv 50 | $40M, Depths tier 8 | levels from items sold (max 8,000 XP/h) |
| Gilded Touch (enchant) | 3% per level, 15% at V | Mining T3 book, crystals | model: 25% of enchant crystals |
| Personal sell booster | 50% | crates, AFK pool, votes, island shop | model uptime 5 / 15 / 30% |
| Global booster | 50% | store purchase (O11 counts toward the cap) | model uptime 15% |
| Sell event | 25% | weekend events, KOTH reward | model uptime 5% |
| **Best of everything** | **305% → capped at 300% (x4.00)** | all sources maxed at once | only then does the cap bite |
<!-- END table:sell_sources -->

**What players actually have** (without boosters, then the day's average with boosters):
<!-- BEGIN table:sell_stack -->
| Stage | Casual: sell bonus without boosters (R rank, U upgrade, M mastery, S set, E enchant) · average multiplier with boosters | Regular | Grinder |
|---|---|---|---|
| Day 1 | +5% (U5) · avg x1.16 | +14% (R4 U10) · avg x1.30 | +29% (R8 U15 E6) · avg x1.53 |
| Day 3 | +5% (U5) · avg x1.16 | +25% (R4 U15 E6) · avg x1.41 | +39% (R8 U25 E6) · avg x1.63 |
| Week 1 | +13% (U10 E3) · avg x1.24 | +35% (R4 U25 E6) · avg x1.51 | +55% (R8 U35 E12) · avg x1.79 |
| Week 2 | +26% (U20 E6) · avg x1.37 | +40% (R4 U30 E6) · avg x1.56 | +118% (R8 U45 S50 E15) · avg x2.42 |
| Month 1 | +31% (U25 E6) · avg x1.42 | +56% (R4 U40 E12) · avg x1.72 | +123% (R8 U50 S50 E15) · avg x2.47 |
| Month 2 | +44% (U35 E9) · avg x1.55 | +114% (R4 U45 S50 E15) · avg x2.30 | +135% (R8 U60 M2 S50 E15) · avg x2.59 |
| Month 3 | +52% (U40 E12) · avg x1.63 | +119% (R4 U50 S50 E15) · avg x2.35 | +143% (R8 U60 M10 S50 E15) · avg x2.67 |
<!-- END table:sell_stack -->

- The cap is a safety rail, not a target. With the values above nobody reaches it by playing normally; the best a grinder sees for a short time (all boosters + event at once) is ×3.68.
- **Hot Item (+25% on one item for 24 h, §8) is a price change, not part of this family.** It never pushes the total past the cap because it changes the base price.
- Gems never buy sell bonuses directly: personal boosters come from crates, the AFK pool, votes and the island shop (crystals); global boosters from the store (they help everyone online).

---

## 3. The Depths

### 3.1 Mine: sell value per drop and per hour
One ore block = one drop (plus fortune). "Base $/played h" is before the sell multiplier and events, with the typical tool a regular player has when they unlock that tier.

<!-- BEGIN table:mine -->
| Tier | Ore | Mining lv | XP | Sell price / drop | vs prev | Blocks per played h (regular) | Base Depths $/played h (no multipliers) |
|---|---|---|---|---|---|---|---|
| 1 | Coal | 0 | 1 | $20 | - | 1,909 | $32.5k |
| 2 | Copper | 5 | 1.5 | $27 | x1.35 | 2,378 | $57.3k |
| 3 | Iron | 10 | 2.5 | $36 | x1.35 | 2,614 | $89.1k |
| 4 | Gold | 15 | 4 | $49 | x1.35 | 3,174 | $152.7k |
| 5 | Redstone | 20 | 6 | $66 | x1.35 | 3,475 | $235.5k |
| 6 | Lapis | 25 | 9 | $90 | x1.35 | 3,799 | $362k |
| 7 | Diamond | 30 | 13 | $121 | x1.35 | 4,547 | $608.3k |
| 8 | Emerald | 35 | 19 | $163 | x1.35 | 4,919 | $922.5k |
| 9 | Amethyst | 42 | 28 | $360 | x2.20 | 5,393 | $2.3M |
| 10 | Quartz | 50 | 40 | $791 | x2.20 | 6,422 | $6.3M |
| 11 | Netherite | 58 | 58 | $1.7k | x2.20 | 6,835 | $15.2M |
| 12 | Voidstone | 66 | 85 | $3.8k | x2.20 | 7,176 | $36.2M |
<!-- END table:mine -->

### 3.2 Forest and combat (parity with the mine)
A log takes as long as an ore block, so log prices equal ore prices of the same tier. Forest tier 11 (Warped) is the top.

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
| 9 | Pale Oak | 42 | 28 | $360 | same as mine tier 9 |
| 10 | Crimson | 50 | 40 | $791 | same as mine tier 10 |
| 11 | Warped | 58 | 58 | $1.7k | same as mine tier 11 |
<!-- END table:forest -->

Combat: kills take longer than blocks (~700 kills per hour of pure fighting at full uptime, +4% per tier), so each kill is worth several drops. Combat unlock levels 0/6/12/18/24/30/38/46/55/66 are decided (owner, round 2 #61).

<!-- BEGIN table:combat -->
| Tier | Zone | Combat lv | Kills per played h (regular) | Drop value / kill | = mine drops |
|---|---|---|---|---|---|
| 1 | Graveyard | 0 | 368 | $104 | 5.2x |
| 2 | Bone Crypt | 6 | 382 | $176 | 6.5x |
| 3 | Spider Den | 12 | 397 | $264 | 7.2x |
| 4 | Slime Bog | 18 | 412 | $436 | 8.9x |
| 5 | Sunken Ruins | 24 | 426 | $650 | 9.8x |
| 6 | Witch Hollow | 30 | 441 | $966 | 10.8x |
| 7 | Pillager Outpost | 38 | 456 | $1.6k | 13.0x |
| 8 | Blaze Forge | 46 | 470 | $2.3k | 14.1x |
| 9 | Wither Keep | 55 | 485 | $5.6k | 15.6x |
| 10 | Warlord's Citadel | 66 | 500 | $14.7k | 18.6x |
<!-- END table:combat -->

Mini-boss and world boss rewards are paid as **event income** (30% of Depths income per played hour across all event types), not on top of it.

### 3.3 Blocks per hour per tool tier
Respawn is 10 s per player and a field is ~1,600 blocks, so the respawn timer never limits a single player. The practical limit is reach: **6 blocks/s** (model cap and recommended server cap).

<!-- BEGIN table:blocks -->
| Tool tier | Tool | Typical level | Slots | Blocks/s | Casual blocks/played h | Regular | Grinder | Field depleted at once |
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

Area enchants (Vein Rend, Dragonfire Blast …, up to +52% throughput) are not in this table yet; E4 adds them inside the 6 blocks/s cap.

### 3.4 Skill pacing (tier unlocks)
Mining skill XP per block = the tier XP from `WORLD.md`. Unlock levels are unchanged (0, 5, 10 … 58, 66).

<!-- BEGIN table:skill -->
| Tier | Ore | Mining level | Total skill XP | Casual reaches it | Regular | Grinder |
|---|---|---|---|---|---|---|
| 2 | Copper | 5 | 4,465 | 2.6 h (day 3) | 2.1 h (day 1) | 1.8 h (day 1) |
| 3 | Iron | 10 | 15,575 | 6.4 h (day 6) | 5.2 h (day 2) | 4.5 h (day 1) |
| 4 | Gold | 15 | 43,221 | 11.3 h (day 11) | 9.1 h (day 3) | 8.0 h (day 1) |
| 5 | Redstone | 20 | 112,013 | 17.9 h (day 18) | 14.4 h (day 5) | 12.7 h (day 2) |
| 6 | Lapis | 25 | 283,189 | 28.0 h (day 28) | 22.4 h (day 8) | 21.2 h (day 3) |
| 7 | Diamond | 30 | 709,129 | 42.5 h (day 43) | 34.1 h (day 12) | 33.2 h (day 4) |
| 8 | Emerald | 35 | 1.8M | 64.6 h (day 65) | 51.7 h (day 17) | 48.8 h (day 6) |
| 9 | Amethyst | 42 | 5.5M | not this season | 91.2 h (day 31) | 83.0 h (day 11) |
| 10 | Quartz | 50 | 16.1M | not this season | 156.6 h (day 52) | 140.1 h (day 18) |
| 11 | Netherite | 58 | 44.4M | not this season | 265.4 h (day 89) | 236.1 h (day 30) |
| 12 | Voidstone | 66 | 119.5M | not this season | not this season | 403.5 h (day 51) |
| - | (after tier 12) | 75 | 272.3M | not this season | not this season | 624.5 h (day 78) |
| - | (after tier 12) | 100 | 872M | not this season | not this season | not this season |
| - | (after tier 12) | 125 | 1.9B | not this season | not this season | not this season |
| - | (after tier 12) | 150 | 3.5B | not this season | not this season | not this season |
<!-- END table:skill -->

Steep up to level 35 (×1.20 per level), flatter after (×1.13), and only +2% per level after level 66 up to the max level 150 (owner #65, `skills.yml → curve.*`). Level 150 stays a far goal for leaderboards.

### 3.5 Tool levels and perk slots
Tool XP per block = tier XP. `tools.yml → curve.growth: 1.21` places the slots where Phase 4 wants them:

<!-- BEGIN table:tool_levels -->
| Slot | Tool level | Total tool XP | Phase-4 target | Casual | Regular | Grinder |
|---|---|---|---|---|---|---|
| 1 | 5 | 2,178 | ~1 h | 1.4 h (day 1) | 1.1 h (day 1) | 54 min (day 1) |
| 2 | 15 | 25,564 | ~day 2 | 8.3 h (day 8) | 6.7 h (day 2) | 5.9 h (day 1) |
| 3 | 30 | 477,405 | ~week 1 | 35.0 h (day 35) | 28.1 h (day 10) | 27.9 h (day 4) |
| 4 | 45 | 8.4M | ~week 3-4 | not this season | 111.0 h (day 37) | 99.9 h (day 13) |
| 5 (prestige) | 50 | 21.7M | ~week 6+ | not this season | 178.3 h (day 60) | 159.3 h (day 20) |
<!-- END table:tool_levels -->

The model does not prestige tools (slot 5 adds ~3% speed, not money). Prestige stays a time achievement.

---

## 4. Island production and spawners

### 4.1 Island products
Island items sell for **half** the Depths item of the same tier (generator ores one tier lower), so The Depths stays the main mining place. Island products use the island's product tier = Depths tier − 1.

<!-- BEGIN table:island_products -->
| Tier | Crop (Farming unlock) | Crop item | Generator block | Generator drop |
|---|---|---|---|---|
| 1 | Wheat | $10 | Cobblestone | $1 |
| 2 | Carrot, Potato | $14 | Coal | $10 |
| 3 | Beetroot | $18 | Copper | $14 |
| 4 | Sugar cane | $25 | Iron | $18 |
| 5 | Melon, Pumpkin | $33 | Gold | $25 |
| 6 | Cocoa | $45 | Redstone | $33 |
| 7 | Cactus | $61 | Lapis | $45 |
| 8 | Sweet berries | $82 | Diamond | $61 |
| 9 | Nether wart | $180 | Emerald | $82 |
| 10 | Torchflower | $396 | - | - |
| 11 | Pitcher plant | $870 | - | - |
<!-- END table:island_products -->

### 4.2 Production levels (Generator, Crop Growth, Harvest Yield, Mob Loot, Spawner Rate)
The five production upgrades are pooled into one **production level PL**: each level adds 12% output. Level k costs `$2.5k × 1.30^(k−1)` and needs tasks (max 6 × Depths tier). Players only buy a level that pays back within 3.5 days. Spawners are no longer inside PL (they have their own model below), so the base output went from 170 to 120 items per hour.

<!-- BEGIN table:upgrades_prod -->
| Depths tier | Worth-it production level (PL) | Cost of that level | All levels so far | Items per production h | Island income per production h | Per day (regular, 12 h) |
|---|---|---|---|---|---|---|
| 1 | 6 | $9.3k | $31.9k | 237 | $2.6k/h | $30.8k/day |
| 2 | 7 | $12.1k | $44k | 265 | $3.1k/h | $37.3k/day |
| 3 | 10 | $26.5k | $106.5k | 373 | $6.4k/h | $76.8k/day |
| 4 | 12 | $44.8k | $185.8k | 468 | $10.8k/h | $130k/day |
| 5 | 15 | $98.4k | $418.2k | 657 | $22k/h | $264k/day |
| 6 | 17 | $166.4k | $712.5k | 824 | $38.5k/h | $461.8k/day |
| 7 | 19 | $281.1k | $1.2M | 1,034 | $65.2k/h | $782.1k/day |
| 8 | 21 | $475.1k | $2.1M | 1,296 | $121.7k/h | $1.5M/day |
| 9 | 25 | $1.4M | $5.9M | 2,040 | $345.5k/h | $4.1M/day |
| 10 | 31 | $6.5M | $28.4M | 4,027 | $1.5M/h | $18M/day |
| 11 | 36 | $24.3M | $105.4M | 7,096 | $5.9M/h | $71.3M/day |
| 12 | 37 | $31.6M | $137M | 7,948 | $7.5M/h | $89.8M/day |
<!-- END table:upgrades_prod -->

In Phase 5 each production upgrade gets its own price list from the same curve: level n of production upgrade j (j = 0…4) costs `C_p(5 × (n − 1) + j + 1)`.

### 4.3 Spawners (5 tiers, owner SK3–SK8)
Each spawner in a stack adds `mobs-per-spawner` mobs every 10–40 s (`stacking.yml`). Mobs from spawners have vanilla loot (~1.5 sellable items per mob) worth the island price of a Depths tier (tier 1 spawner = product of tier 1, tier 2 = tier 3, …, tier 5 = tier 9).

<!-- BEGIN table:spawners -->
| Spawner tier | Types (stacking.yml) | Source | Model: in reach at Depths tier | Loot per mob | One spawner, 1 mob/spawn (recommended) | One spawner, config today (4 mobs/spawn) | Price: first of this tier | 10th | 25th |
|---|---|---|---|---|---|---|---|---|---|
| 1 | pig, cow, chicken, sheep, rabbit | shop | 1 | $15 | $1.9k/h | $7.8k/h | $117k | $275.9k | $1.2M |
| 2 | zombie, skeleton, spider, slime … | shop + The Depths | 3 | $27 | $3.5k/h | $14.2k/h | $213k | $502.2k | $2.1M |
| 3 | creeper, witch, drowned, husk … | The Depths + crates | 5 | $50 | $6.5k/h | $25.8k/h | $387k | $912.5k | $3.8M |
| 4 | blaze, enderman, guardian, piglin … | crates | 7 | $91 | $11.8k/h | $47.1k/h | $706k | $1.7M | $7M |
| 5 | iron golem, wither skeleton, shulker … | crates + Black Market | 9 | $270 | $35k/h | $139.8k/h | $2.1M | $4.9M | $20.7M |
<!-- END table:spawners -->

Why this matters:
- **Grinders make the kill rate unlimited.** Fall and lava kill a whole stack (S5), so a spawner farm with a drop kills everything it spawns. Spawner income is therefore *spawn rate × spawners owned*, nothing else.
- **With today's config (4 mobs per spawn)** one tier 1 spawner earns ~$7.8k per production hour, more than a whole tier-1 island. Spawners would make ~69% of a regular player's income and money supply would double (§7.3). **Suggested: `spawners.mobs-per-spawner: 1`** (§14).
- **The shop price must rise per spawner owned.** A spawner bought at a fixed price pays back in days and then earns forever, so players buy more with the money spawners make: money grows like compound interest (season sink ratio 73%, money supply ×6, §7.3). The model uses: price of the next spawner = base × 1.10^(spawners of that tier the island already owns), base = 60 production hours of that spawner's own output.
- **Spawners are also found** (Depths drops, crates): the model gives 1 spawner of the best tier in reach per 10 played hours.
- **The Slaughter keep-up table (ENCHANTS §7.3)** assumed ~0.1 mobs/s per spawner; at 1 mob per spawn it is ~0.04 mobs/s, so one player keeps up with 2.5× more spawners. E3 re-checks it.

### 4.4 Spawner stack steps (8 → 512 in 5 steps)
The Spawner Stack Limit upgrade raises how many spawners one block holds (owner S20: start 8, max 512). There is no block limit (O1), so the stack limit packs spawners (less lag, fewer blocks); in the model an island fills about 4 spawner blocks, which caps the count at 4 × the stack limit.

<!-- BEGIN table:stack_steps -->
| Stack step | Spawner Stack Limit level | Spawners per block | Model island cap (4 blocks) | Upgrade money to get there | Regular reaches it | Grinder reaches it |
|---|---|---|---|---|---|---|
| start | - | 8 | 32 | $0 | start | start |
| step 1 | Lv 2 | 32 | 128 | $12.1k | 2.9 h (day 1) | 2.5 h (day 1) |
| step 2 | Lv 4 | 64 | 256 | $94.9k | 9.8 h (day 3) | 9.1 h (day 1) |
| step 3 | Lv 6 | 128 | 512 | $661.2k | 23.8 h (day 8) | 25.7 h (day 3) |
| step 4 | Lv 9 | 256 | 1024 | $16.5M | 112.5 h (day 38) | 96.7 h (day 12) |
| step 5 | Lv 12 | 512 | 2048 | $1.2B | not this season | 427.9 h (day 54) |
<!-- END table:stack_steps -->

---

## 5. Cost curves (Forge, island upgrades, late game)

### 5.1 The Forge (tool tiers)
Forging costs money plus materials from the previous tier (now checked in the model). The money part is ~0.3–2.3 hours of income: noticeable, never a wall. These numbers are exactly `tools.yml → tiers.pickaxe` today (§13).

<!-- BEGIN table:forge -->
| Tool tier | Pickaxe | Money | Materials | Material sell value | Money = hours of REF(t-1) | Regular forges it | Grinder forges it |
|---|---|---|---|---|---|---|---|
| 2 | Copper | $15k | 256 Coal | $5.1k | 0.3 h | 2.2 h (day 1) | 1.9 h (day 1) |
| 3 | Iron | $35.2k | 346 Copper | $9.3k | 0.4 h | 5.3 h (day 2) | 4.6 h (day 1) |
| 4 | Gold | $82.8k | 467 Iron | $17k | 0.5 h | 9.2 h (day 3) | 8.1 h (day 1) |
| 5 | Redstone | $194.7k | 630 Gold | $31k | 0.7 h | 14.5 h (day 5) | 16.6 h (day 2) |
| 6 | Lapis | $457.5k | 850 Redstone | $56.5k | 1.0 h | 22.5 h (day 8) | 26.0 h (day 3) |
| 7 | Diamond | $1.1M | 1,148 Lapis | $102.9k | 1.3 h | 34.2 h (day 12) | 33.3 h (day 4) |
| 8 | Emerald | $2.5M | 1,550 Diamond | $187.6k | 1.7 h | 55.0 h (day 18) | 50.0 h (day 6) |
| 9 | Amethyst | $5.9M | 2,092 Emerald | $341.9k | 2.3 h | 93.3 h (day 31) | 83.1 h (day 11) |
| 10 | Quartz | $14M | 2,824 Amethyst | $1M | 1.9 h | 156.7 h (day 52) | 140.2 h (day 18) |
| 11 | Netherite | $32.8M | 3,813 Quartz | $3M | 1.6 h | 265.5 h (day 89) | 236.2 h (day 30) |
| 12 | Voidstone | $77M | 5,147 Netherite | $9M | 1.3 h | not this season | 403.6 h (day 51) |
<!-- END table:forge -->

### 5.2 Island level upgrades (Phase 5 preview)
12 non-production upgrades × 12 levels. Level n needs Depths tier ≥ n in the model (in game: its tasks). Price = weight × hours of REF(n).

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
| 9 | 9 | $6M – $21.5M | $143.2M | 19.5 h | $197.6M |
| 10 | 10 | $25.4M – $91.6M | $610.3M | 29.1 h | $808M |
| 11 | 11 | $108.4M – $390.1M | $2.6B | 43.4 h | $3.4B |
| 12 | 12 | $461.8M – $1.7B | $11.1B | 64.6 h | $14.5B |
<!-- END table:upgrades_level -->

<!-- BEGIN table:upgrade_weights -->
| Upgrade | Weight W | Level 1 | Level 6 | Level 12 |
|---|---|---|---|---|
| Warp Slots | 0.5 | $1.7k | $204.8k | $461.8M |
| Island Vault | 0.6 | $2k | $245.8k | $554.2M |
| Member Slots | 0.8 | $2.7k | $327.7k | $738.9M |
| Fuel Tank | 0.8 | $2.7k | $327.7k | $738.9M |
| XP Boost | 0.9 | $3k | $368.6k | $831.3M |
| Lumberjack | 0.9 | $3k | $368.6k | $831.3M |
| Spawner Stack Limit | 1.0 | $3.4k | $409.6k | $923.6M |
| Island Size | 1.0 | $3.4k | $409.6k | $923.6M |
| Bank Capacity | 1.1 | $3.7k | $450.6k | $1B |
| Bank Interest | 1.2 | $4k | $491.5k | $1.1B |
| Mine Fortune | 1.4 | $4.7k | $573.4k | $1.3B |
| Sell Multiplier | 1.8 | $6k | $737.3k | $1.7B |
<!-- END table:upgrade_weights -->

The Sell Multiplier upgrade gives +5% per level (+60% at level 12) and the Spawner Stack Limit upgrade drives the stack steps in §4.4. Island level points per upgrade level stay weighted by tier (`REVIEW.md` A1) and can never be bought with gems.

### 5.3 Late projects: outposts, biomes, fusion
Each outpost = unlock + 3 biomes × 5 tiers + Fusion I/II = 18 steps, gated by pillars (Depths tier 7 / 9 / 11 in the model).

<!-- BEGIN table:late -->
| Outpost | Gate (Depths tier, proxy for pillars) | Unlock | Fusion II (step 18) | All 18 steps | = hours of REF(gate) | Regular reaches gate | Grinder reaches gate |
|---|---|---|---|---|---|---|---|
| Greenhold (farming) | 7 | $1.5M | $42.9M | $231.4M | 158 h | 34.1 h (day 12) | 33.2 h (day 4) |
| Wildwood (combat/spawners) | 9 | $7.3M | $215.5M | $1.2B | 158 h | 91.2 h (day 31) | 83.0 h (day 11) |
| Emberdeep (late game) | 11 | $60M | $1.8B | $9.5B | 158 h | 265.4 h (day 89) | 236.1 h (day 30) |
<!-- END table:late -->

### 5.4 Mastery (the endless late sink)
When every level upgrade is maxed, upgrades and masteries (MA1–MA12) can be levelled endlessly: each mastery level costs 4 h of REF × 1.12^(m−1). Market Contacts (MA5) adds +2% sell per level, max +40%; the model puts a quarter of a player's mastery levels there.

<!-- BEGIN table:mastery -->
| Mastery level | Cost at tier 11 | Cost at tier 12 | = hours of REF(tier) |
|---|---|---|---|
| 1 | $239.9M | $686.2M | 4.0 h |
| 5 | $377.5M | $1.1B | 6.3 h |
| 10 | $665.3M | $1.9B | 11.1 h |
| 15 | $1.2B | $3.4B | 19.5 h |
| 20 | $2.1B | $5.9B | 34.5 h |
| 25 | $3.6B | $10.4B | 60.7 h |
<!-- END table:mastery -->

---

## 6. Banks and interest

Rules (proposals, M3.3): interest every 3 h (also offline, lazily computed); paid on the **lowest balance during the cycle** (no deposit-before-payout trick); early withdrawal forfeits the cycle and costs 2%; only accounts that played ≥ 2 h in the last 7 days earn interest; every payout is capped.

### 6.1 Personal bank (10 tiers)
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
| Day | Casual interest (share of that day's income) | Regular | Grinder |
|---|---|---|---|
| Day 1 | $171/day (0.2%) | $1k/day (0.3%) | $3.5k/day (0.2%) |
| Day 3 | $548/day (0.7%) | $3.1k/day (0.4%) | $16.4k/day (0.2%) |
| Week 1 | $1.5k/day (1.1%) | $31k/day (1.5%) | $125.8k/day (0.4%) |
| Week 2 | $8k/day (2.4%) | $79.7k/day (1.4%) | $402.7k/day (0.3%) |
| Month 1 | $31.7k/day (3.4%) | $369.3k/day (3.5%) | $4.1M/day (0.7%) |
| Month 2 | $125.8k/day (6.1%) | $402.7k/day (0.4%) | $13.2M/day (0.7%) |
| Month 3 | $125.8k/day (3.6%) | $4.1M/day (1.7%) | $13.2M/day (0.6%) |
<!-- END table:interest_vs_income -->

Interest stays a small bonus (≤ 5% of season income for every archetype). It is relatively highest for casual players because they save more and earn less; in money it is tiny.

---

## 7. Money sources vs. sinks (inflation check)

Every sink is a gameplay cost (tools, sets, spawners, upgrades, outposts, mastery, bank tiers, shop blocks, AH fee/tax); there are no pure money burns. All 900 daily players, averaged over the days ending on that day. "Money supply" = money held (cash + savings + banks) at the end of the day.

### 7.1 Created and removed per day, by reason
<!-- BEGIN table:sources_sinks -->
| Day (7-day avg) | Depths | Island | Spawners | Events | Interest | Forge | Production | Spawners bought | Sets | Level upg. | Late proj. | Mastery | Bank tiers | Shop | AH fee+tax |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | $215.1M | $39.5M | $4.9M | $64.5M | $686.7k | $16M | $69.4M | $0 | $0 | $153.8M | $0 | $0 | $8.1M | $13M | $1.9M |
| 3 | $341.6M | $84.8M | $61.4M | $102.5M | $1.6M | $38.2M | $66.8M | $97.4M | $0 | $270.3M | $0 | $0 | $16.5M | $23.6M | $3.5M |
| 7 | $829.3M | $222.3M | $297.5M | $248.8M | $7.6M | $72.9M | $94.1M | $153.2M | $0 | $410.5M | $145.4M | $0 | $36.6M | $63.9M | $9.6M |
| 14 | $4B | $968.5M | $2B | $1.2B | $35.3M | $141.8M | $174.6M | $526.9M | $514.3M | $2.9B | $2.9B | $0 | $128.6M | $327.3M | $49.1M |
| 30 | $16.1B | $6.7B | $5.2B | $4.8B | $368.3M | $456.8M | $2.8B | $0 | $0 | $7.7B | $12.6B | $0 | $2.3B | $1.3B | $197.5M |
| 45 | $36.2B | $19.2B | $9.4B | $10.8B | $527M | $82.9M | $17.5M | $0 | $0 | $3.3B | $61.6B | $0 | $0 | $3B | $454M |
| 60 | $87.3B | $75.6B | $12.7B | $26.2B | $1.4B | $0 | $0 | $0 | $0 | $104.2B | $5.3B | $56B | $0 | $8.1B | $1.2B |
| 75 | $90.4B | $87.1B | $16.5B | $27.1B | $1.6B | $194.9M | $0 | $0 | $0 | $821M | $20.7B | $129.9B | $0 | $8.8B | $1.3B |
| 90 | $95.9B | $92.1B | $19.9B | $28.8B | $2.4B | $1.3B | $3B | $0 | $0 | $22.6B | $25.8B | $161.1B | $0 | $9.5B | $1.4B |
<!-- END table:sources_sinks -->

### 7.2 Inflation check
<!-- BEGIN table:inflation -->
| Day (7-day avg) | Created / day | Removed / day | Sink ratio (7 days) | Sink ratio (14 days) | Net / day | Money supply (held) | Held per player | Held per player in days of regular income | Check (14 days) |
|---|---|---|---|---|---|---|---|---|---|
| 1 | $324.7M | $262.2M | 81% | 81% | $62.6M | $63.5M | $70.5k | 0.2 | OK |
| 3 | $591.9M | $516.4M | 87% | 87% | $75.5M | $227.3M | $252.6k | 0.3 | OK |
| 7 | $1.6B | $986.2M | 61% | 61% | $619.3M | $4.3B | $4.8M | 2.4 | watch |
| 14 | $8.2B | $7.7B | 94% | 88% | $533.5M | $8.1B | $9M | 1.6 | OK |
| 30 | $33.3B | $27.5B | 82% | 86% | $5.8B | $71.1B | $79.1M | 7.6 | OK |
| 45 | $76.2B | $68.5B | 90% | 88% | $7.7B | $200.6B | $222.9M | 5.8 | OK |
| 60 | $203.1B | $174.8B | 86% | 88% | $28.3B | $538.6B | $598.4M | 6.6 | OK |
| 75 | $222.7B | $161.8B | 73% | 80% | $60.9B | $1.1T | $1.2B | 12.6 | OK |
| 90 | $239.1B | $224.6B | 94% | 80% | $14.4B | $1.6T | $1.8B | 7.5 | OK |
<!-- END table:inflation -->

- **Target:** ≥ 80% of created money removed over the season, and the 14-day average never below 60%. **Watch** at 60–75%, **act** below 60% for 3 days in a row. The dashboard (M3.8) should use the **14-day** average: the 7-day average dips whenever many players save for the same big purchase (day 7: grinders saving for Coinmaster; day 29: regular players).
- **Held per player in days of regular income** drives AH prices. **Watch above 10:** it touches ~12–13 around day 75 when grinders hold billions between mastery levels. That is fine for a seasonal wipe while server prices stay fixed; if AH prices run away, raise mastery prices first.
- Server-set prices do not inflate: sell values, Forge, sets and upgrade costs are fixed numbers. Inflation only shows on the AH and in `/trade`.

### 7.3 Sensitivity (what if we're wrong?)
<!-- BEGIN table:sensitivity -->
| Scenario | Sink ratio, season | Lowest 7-day sink ratio, days 60-90 | Money supply day 90 | Interest share (grinder) | Island total share (regular) | Spawner share (regular) | Grinder earned by month 2 |
|---|---|---|---|---|---|---|---|
| Baseline (this sheet) | 85% | 73% | $1.6T | 0.6% | 38% | 19% | $39B |
| Spawners at today's config (4 mobs per spawn) | 82% | 66% | $3T | 1.6% | 76% | 69% | $58.2B |
| Shop spawner price does not rise per owned spawner | 73% | 55% | $10.3T | 1.1% | 89% | 88% | $116.7B |
| No mastery (no endless sink) | 42% | 15% | $6T | 1.5% | 38% | 19% | $39B |
| Boosters always on (personal + global) | 87% | 83% | $2.1T | 0.9% | 46% | 25% | $57.9B |
| Old cap +100% | 86% | 85% | $1.1T | 0.7% | 36% | 18% | $29.5B |
| Custom tiers x2.4 (old curve) | 87% | 91% | $2T | 0.6% | 37% | 19% | $53.4B |
| Players save twice as much | 79% | 72% | $2.3T | 0.6% | 38% | 19% | $38.1B |
| Interest without payout caps | 81% | 69% | $2.1T | 4.8% | 36% | 18% | $39.7B |
| Production payback 8 days (players over-invest) | 88% | 69% | $1.7T | 0.7% | 53% | 27% | $51.2B |
<!-- END table:sensitivity -->

What this says:
- **Spawners are the biggest new risk.** At today's 4 mobs per spawn they become ~69% of a regular's income and money supply doubles. Without a rising shop price they compound: 88% of income and 6× the money supply.
- **Mastery (the endless late sink) is still load-bearing.** Without it the season sink ratio falls to ~42%.
- **Boosters always on** (personal + global): month-2 income of grinders +48%, island share 46%. Still under control because the cap holds. Global booster uptime is the lever.
- **The cap itself barely matters at today's values** (old +100% cap: grinders earn ~24% less by month 2). The sources decide, not the cap.
- **The old ×2.4 custom curve** puts grinders at $53B by month 2 (1.1× target) with today's sell bonuses. ×2.2 keeps them at 0.8×; either is inside the target band, ×2.2 leaves room for area enchants (E4) and set factors (W4).

### 7.4 Levers (if the M3.8 dashboard disagrees with this sheet)
| Symptom | First lever | Second lever |
|---|---|---|
| Sink ratio (14 days) < 60% for 3 days | Raise mastery/late-project prices (hours of REF) | Lower custom-tier sell prices (P for tiers 9–12) |
| Income per hour > 2× the table | Check dupes/anomalies first; then lower booster values or global booster uptime | Lower the sell cap |
| Island share > 45% | Raise production level costs (`CP1`/`CPG`) | Shorter offline fuel tank |
| Spawner share > 25% | Lower `spawners.mobs-per-spawner` | Raise the shop spawner price growth |
| Interest > 5% of income | Lower payout caps | Lower rates |
| AH prices running away | Raise AH tax for listings above X | Add more late sinks |
| Crystals piling up | More island shop items (fuel, boosters, decor) | Raise roll prices |

---

## 8. Dynamic sell prices (M3.4)

Each item has a multiplier `m` (floor 0.40, ceiling 1.50). Every item sold lowers `m` by `impact`; every 10 minutes the gap to base closes by 2%: `m ← 1 − (1 − m) × 0.98` (half-life ~5.7 h). The price settles where `1 − m = impact × volume / 0.1212 per hour`; the model assumes ~0.85 at normal volume. The plugin should **recompute `impact` hourly from the rolling 24 h volume** so the same table works at 30 or 300 players:

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

(ppm = parts per million of base price per item sold.) Items nobody sells drift up towards the ceiling (×1.10 after 24 h without sales, capped at ×1.50). The daily **Hot Item** gets +25% for 24 h (proposal); it changes the price, not the sell multiplier family. **The sell multiplier (§2.7) applies after the dynamic price**, so the cap never interacts with dynamic prices.

---

## 9. Crystals, gems and shards

### 9.1 Crystals (replaced tokens: earned by mining/chopping/killing and island missions, spent on enchants, crystal sets, rolls, island shop, Forge extras)
Crystals drop as a chance per block/log/kill; the average grows ×1.30 per tier. Island missions (CR1) add +20% on top in the model. **Crystals are not tradeable** (CR3, `economy.yml → tradeable.crystals: false`), so a surplus can never leak into the money economy.

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

Enchant level costs follow 10 steps × a rarity factor (ENCHANTS §5):

<!-- BEGIN table:enchant_costs -->
| Enchant step | Crystal cost (x rarity factor) | Total to this step |
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
| Stage | Casual (crystals per played h incl. missions · hours for one Rare enchant I→V) | Regular | Grinder |
|---|---|---|---|
| First hour | 94/h · 300 h per Rare V enchant | 118/h · 240 h per Rare V enchant | 151/h · 187 h per Rare V enchant |
| Day 1 | 107/h · 265 h per Rare V enchant | 192/h · 147 h per Rare V enchant | 486/h · 58 h per Rare V enchant |
| Day 3 | 150/h · 189 h per Rare V enchant | 422/h · 67 h per Rare V enchant | 974/h · 29 h per Rare V enchant |
| Week 1 | 216/h · 131 h per Rare V enchant | 620/h · 46 h per Rare V enchant | 2,145/h · 13 h per Rare V enchant |
| Week 2 | 343/h · 82 h per Rare V enchant | 1,337/h · 21 h per Rare V enchant | 3,347/h · 8 h per Rare V enchant |
| Month 1 | 682/h · 41 h per Rare V enchant | 1,934/h · 15 h per Rare V enchant | 6,407/h · 4 h per Rare V enchant |
| Month 2 | 1,085/h · 26 h per Rare V enchant | 4,142/h · 7 h per Rare V enchant | 8,745/h · 3 h per Rare V enchant |
| Month 3 | 1,525/h · 19 h per Rare V enchant | 5,653/h · 5 h per Rare V enchant | 8,745/h · 3 h per Rare V enchant |
<!-- END table:crystals -->

**Where crystals go** (the model spends on enchants first, up to what the tool level and gear allow, saves for the crystal set at tier 6, then rolls and the island shop):
<!-- BEGIN table:crystal_balance -->
| Archetype | Up to | Crystals earned | Enchants | Crystal sets | Rolls | Island shop | Still held | Held share |
|---|---|---|---|---|---|---|---|---|
| casual | Week 1 | 1,061 | 56% | 0% | 0% | 44% | 0 | 0% |
| casual | Month 1 | 10,797 | 71% | 0% | 0% | 13% | 1,679 | 16% |
| casual | Month 3 | 80,335 | 67% | 31% | 0% | 2% | 0 | 0% |
| regular | Week 1 | 8,828 | 93% | 0% | 0% | 7% | 0 | 0% |
| regular | Month 1 | 116,305 | 78% | 21% | 0% | 1% | 0 | 0% |
| regular | Month 3 | 783,844 | 95% | 3% | 1% | 1% | 4,399 | 1% |
| grinder | Week 1 | 62,824 | 60% | 40% | 0% | 0% | 0 | 0% |
| grinder | Month 1 | 790,257 | 94% | 3% | 1% | 1% | 6,396 | 1% |
| grinder | Month 3 | 4.6M | 16% | 1% | 51% | 31% | 37,446 | 1% |
<!-- END table:crystal_balance -->

- Casual and regular players spend almost everything on enchants and one crystal set: **crystals are scarce for them**, which is the intended feel (choices matter).
- Grinders out-earn the enchant demand from month 2: half their crystals go to rolls and a third to the island shop. **The island shop is the crystal sink that must scale** (fuel, personal sell boosters, decor). If it has too few items, grinders sit on crystals and buy every book from rolls.
- Crystal income is ~20% below what ENCHANTS §12 and WARDROBE §2.4 assumed for an "active" (4 h/day) player, because the regular plays 3 h/day. Deepforge (◆25,000) lands around day 15 for a regular and day 6 for a grinder; the price can stay.

### 9.2 Gems (premium, not tradeable, decided)
<!-- BEGIN table:gems -->
| Gem source | Gems | Typical | Type |
|---|---|---|---|
| Vote (per site, ~5 sites) | 3 | 15 / day | free |
| Say gg after a store purchase | 1 | max 5 / day | free, 60 s window |
| Daily login streak day 7 | 10 | ~10 / week | free |
| Store credit | set in Tebex | - | paid |
<!-- END table:gems -->

Gem uses: cosmetics, tool skins, extra `/pv` vaults, nickname colours, gem sets (tier II–III, also in crates), profile slots. **Gems never buy money, upgrade levels, task progress, crystals, set XP or crate keys.** There is no gem→money exchange, so gems can't cause inflation.

### 9.3 Shards (tradeable, decided; earning is an open question)
| Source (proposal, `OPEN_QUESTIONS.md` #1) | Shards |
|---|---|
| World boss, top-3 damage | 20 / 15 / 10, everyone else with ≥ 1% damage: 3 |
| KOTH capture | 10 |
| Envoy crate | 1–3 |
| Depths tier 9+ rare drop | 1 (≈ 1 per hour of play) |

Shards only buy from the **Black Market** (rotating, limited stock: tier 5 spawners, crate sets), so supply is set by events, not by grinding.

### 9.4 Money notes and crates
- Notes (`/withdraw`) are only a transfer, so **no fee**. Each note has a unique id; copies are confiscated (M3.2).
- Crates (Ember, Cinder, Blaze, Inferno, Phoenix, Eclipse, Conqueror; keys are earned only) give boosters, books, spawners of tiers 3–5, Wardrobe Tomes and sets. Their money value is inside the 30% event income; **crate money rewards must stay small** (a crate is never a money printer). The crate plan sets exact tables.

---

## 10. Risks and mitigations

| # | Risk | What could happen | Mitigation |
|---|---|---|---|
| R1 | **Runaway inflation** | Late-season income piles up, AH prices explode | Endless late sink (mastery, §5.4); season sink ratio ≥ 80% and 14-day dashboard alerts (M3.8); levers (§7.4); seasonal wipe; server prices fixed |
| R2 | **Interest abuse** | Deposit before payout; idle alts; snowballing | Min-balance rule; payout caps; activity rule (≥ 2 h in 7 days); early-withdrawal forfeit + 2% fee; bank tier unlock costs |
| R3 | **Alt farming** | Alts mine tier 1, claim kits/votes and `/pay` the main | Depths income needs active breaking; starter kits not on the AH; crystals and gems not tradeable; new accounts (< 30 min, #40) can't `/pay`, `/withdraw` or use the AH; same-IP transfer alerts; AFK pool rewards one per IP (RW3) |
| R4 | **AH manipulation** | Laundering via fake listings, price fixing, wash trading | 1% fee + 5% tax; alerts for sales > 20× the 7-day median or between linked accounts; listing limits; full logs |
| R5 | **AFK island production** | Island income overtakes The Depths | Fuel tank cap; AFK pool max 8 h/day (O10); production online rate only while active in the last 15 min; Fuel Tank priced as a level upgrade |
| R6 | **Dupes / exploits** | A dupe creates money from nothing | Atomic DB transactions; unique ids for notes, tools, books, sets; anomaly alert above 3× the table's income per hour |
| R7 | **Price table errors** | One item priced 100× too high | All prices from this sheet's formulas; dynamic prices; config validation test per tier band |
| R8 | **Villager trading** | Trades beat shop prices | Vanilla emeralds sell at generator price; watch trade volume |
| R9 | **Model is wrong** | Real players differ from the archetypes | M3.8 dashboard compares real income per hour per tier with §2.1 weekly; re-run this model with measured values |
| R10 | **Spawner compounding** (new) | Spawners bought with spawner money grow like compound interest; one block with 512 spawners at 4 mobs/spawn makes ~295k mobs per hour | `mobs-per-spawner: 1`; shop price rises 10% per spawner of that tier owned; tiers 3–5 only from drops/crates/Black Market; spawner share > 25% is a dashboard alert; loot sampling (`mobs.loot-samples`) keeps it cheap for the server |
| R11 | **Booster stacking** (new) | Personal + global boosters + event on top of a maxed set push everyone to the cap during events | One cap for everything (O11); global booster uptime is a config lever; boosters never sold for gems directly (crates, AFK pool, island shop, store global boosters help everyone) |
| R12 | **Sell set XP farming** (new) | Selling the same cheap items to level Coinmaster | 8,000 set XP per hour cap (WARDROBE); island items give half XP; dynamic prices push cheap spam down |

---

## 11. Model self-checks
Generated on every run; all must say PASS before this sheet goes to the owner. The script also runs hard asserts (cap math, stack steps 8 → 512, determinism, no player above the cap, progressively harder tiers) and fails if any breaks.

<!-- BEGIN table:checks -->
| Check | Result | Value |
|---|---|---|
| Depths share 40-60% (regular, season) | PASS | 46% |
| Island + spawner share 25-45% (regular, season) | PASS | 38% |
| Spawners 8-25% of income (regular, season) | PASS | 19% |
| Events share 10-20% (regular, season) | PASS | 14% |
| Interest <= 5% of income (all, season) | PASS | 4.8%, 1.8%, 0.6% |
| First hour 30k-100k (grinder) | PASS | $67.2k |
| Week 2 within 0.5x-2x of $500M (grinder) | PASS | $639.6M |
| Month 2 within 0.5x-2x of $50B (grinder) | PASS | $39B |
| Sink ratio over the whole season >= 80% | PASS | 85% |
| Sink ratio (14-day average) never below 60% ('act') | PASS | min 61% (day 29) |
| Slot 1 within ~1-2 h (regular) | PASS | 1.1 h (day 1) |
| Slot 4 in week 3-8 (regular) | PASS | 111.0 h (day 37) |
| Max blocks/s under reach cap | PASS | 4.10 <= 6.0 |
| Money fits in a long | PASS | season total, all grinders |
| Tier 12 not before week 4 (grinder) | PASS | 403.5 h (day 51) |
| Fast start: tier 2 within 3 h (all) | PASS | 2.6 h (day 3), 2.1 h (day 1), 1.8 h (day 1) |
| Progressively harder: each tier gap >= previous (regular) | PASS | 2 → 3 → 4 → 5 → 8 → 12 → 18 → 39 → 65 → 109 h |
| Sell multiplier never above the cap | PASS | peak x3.68 <= x4.00 |
| Cap only reached with the best of everything | PASS | all sources 305%; drop any one → under cap |
| Crystals: sinks take >= 70% (regular, season) | PASS | 99% |
<!-- END table:checks -->

---

## 12. Owner approval checklist

Approve or change:
1. Income per stage (§2.1) and the day-3 answer (§2.2: read the target as a regular player).
2. The pacing curve (§2.6): new ore at 70% speed until the Forge; tier gaps growing ×1.3–2.2.
3. Mine/forest/combat sell values; custom tiers ×2.2 instead of ×2.4 (§3).
4. Tool level curve ×1.21 and the skill curve (§3.4–3.5) (already in the configs).
5. Island items at 50% of Depths items, production pricing (§4.1–4.2).
6. **Spawners (§4.3–4.4): 1 mob per spawn (not 4), shop price +10% per spawner of that tier owned, base = 60 production hours, stack steps 8/32/64/128/256/512 at Spawner Stack Limit Lv 0/2/4/6/9/12.**
7. **Sell bonus values (§2.7):** best rank +15%, Sell Multiplier upgrade +5%/level, Market Contacts +2%/level (max 20), Coinmaster +50% at Lv 50, Gilded Touch +15%, personal and global boosters +50%, sell events +25%; all summed, then +300% cap.
8. Forge, level upgrade, late project costs as "hours of income" (§5); mastery as the endless sink (§5.4).
9. Bank rules and caps (§6).
10. Sink targets: ≥ 80% per season, 14-day average for the dashboard (§7.2).
11. Dynamic prices with adaptive impact; Hot Item +25% outside the sell family (§8).
12. Crystal sinks: the island shop must scale for grinders (§9.1); shard sources (§9.3); crate money rewards stay small (§9.4).
13. Risk mitigations R1–R12 (§10).

---

## 13. Recommended config numbers
Read from the Java config classes and `tools-defaults.yml` on every run, compared with this model. "ok" = the config already matches.

<!-- BEGIN table:config_check -->
| File | Key | Config today | Model | Status | Note |
|---|---|---|---|---|---|
| `economy.yml` | `start.money` | 1,000 | 1,000 | ok | start money |
| `economy.yml` | `start.crystals` | 0 | 0 | ok | first enchant step comes from the first ~20 min of mining |
| `economy.yml` | `sell.multiplier-cap-percent` | 300 | 300 | ok | #46 +300%, summed then capped once |
| `auction.yml` | `fee.percent` | 1 | 1 | ok | listing fee |
| `auction.yml` | `tax.percent` | 5 | 5 | ok | sales tax |
| `skills.yml` | `curve.base` | 600 | 600 | ok |  |
| `skills.yml` | `curve.early-growth` | 1.2 | 1.2 | ok |  |
| `skills.yml` | `curve.break-level` | 35 | 35 | ok |  |
| `skills.yml` | `curve.late-growth` | 1.13 | 1.13 | ok |  |
| `skills.yml` | `curve.end-break-level` | 66 | 66 | ok |  |
| `skills.yml` | `curve.end-growth` | 1.02 | 1.02 | ok |  |
| `skills.yml` | `curve.max-level` | 150 | 150 | ok | #65 |
| `tools.yml` | `curve.base` | 400 | 400 | ok |  |
| `tools.yml` | `curve.growth` | 1.21 | 1.21 | ok |  |
| `tools.yml` | `curve.max-level` | 50 | 50 | ok |  |
| `tools.yml` | `perk-slots.levels` | 5, 15, 30, 45 | 5, 15, 30, 45 | ok |  |
| `tools.yml` | `xp.tier-xp` | 1, 1.5, 2.5, 4, 6, 9, 13, 19, 28, 40, 58, 85 | 1, 1.5, 2.5, 4, 6, 9, 13, 19, 28, 40, 58, 85 | ok |  |
| `tools.yml` | `speed.per-level-percent` | 1 | 1 | ok |  |
| `tools.yml` | `fortune.per-tier-percent` | 5 | 5 | ok |  |
| `depths.yml` | `engine.default-respawn-seconds` | 10 | 10 | ok | reach cap 6 blocks/s |
| `stacking.yml` | `mobs.max-stack` | 1,024 | 1,024 | ok | S2 |
| `stacking.yml` | `spawners.stack-start` | 8 | 8 | ok | S20 |
| `stacking.yml` | `spawners.stack-max` | 512 | 512 | ok | S20 |
| `stacking.yml` | `spawners.min-interval-seconds` | 10 | 10 | ok |  |
| `stacking.yml` | `spawners.max-interval-seconds` | 40 | 40 | ok |  |
| `stacking.yml` | `spawners.mobs-per-spawner` | 4 | 1 | **CHANGE** | 4 makes one spawner earn 4x the model |
| `tools.yml` | `tiers.pickaxe[2].money` | 15,000 | 15,000 | ok | Forge §5.1 |
| `tools.yml` | `tiers.pickaxe[3].money` | 35,200 | 35,200 | ok | Forge §5.1 |
| `tools.yml` | `tiers.pickaxe[4].money` | 82,800 | 82,800 | ok | Forge §5.1 |
| `tools.yml` | `tiers.pickaxe[5].money` | 194,700 | 194,700 | ok | Forge §5.1 |
| `tools.yml` | `tiers.pickaxe[6].money` | 457,500 | 457,500 | ok | Forge §5.1 |
| `tools.yml` | `tiers.pickaxe[7].money` | 1,075,100 | 1,075,100 | ok | Forge §5.1 |
| `tools.yml` | `tiers.pickaxe[8].money` | 2,526,400 | 2,526,400 | ok | Forge §5.1 |
| `tools.yml` | `tiers.pickaxe[9].money` | 5,937,000 | 5,937,000 | ok | Forge §5.1 |
| `tools.yml` | `tiers.pickaxe[10].money` | 13,952,000 | 13,951,900 | ok | Forge §5.1 |
| `tools.yml` | `tiers.pickaxe[11].money` | 32,787,000 | 32,787,000 | ok | Forge §5.1 |
| `tools.yml` | `tiers.pickaxe[12].money` | 77,049,500 | 77,049,500 | ok | Forge §5.1 |
<!-- END table:config_check -->

## 14. Config changes suggested
Not changed by BAL2 (configs belong to their milestones). Existing keys first, then keys for systems that are not built yet.

<!-- BEGIN table:config_changes -->
| File | Key | Today | Suggested | Why |
|---|---|---|---|---|
| `stacking.yml` | `spawners.mobs-per-spawner` | 4 | 1 | 4 makes one spawner earn 4x the model |
| `shop.yml` (M3.5, new) | `spawners.price-growth-per-owned` | not built | 1.1 | price of a shop spawner rises 10% per spawner of that tier the island owns; without it spawners compound (§4.3) |
| `shop.yml` (M3.5, new) | `spawners.price.tier-1..2` | not built | $117k / $213k | first spawner of that tier |
| `boosters.yml` (new) | `sell.personal-percent` / `sell.global-percent` | not built | 50 / 50 | both count toward the +300% cap (O11) |
| `islands` upgrades (Phase 5) | `upgrades.sell-multiplier.percent-per-level` | not built | 5 | 12 levels = +60% |
| `islands` upgrades (Phase 5) | `upgrades.spawner-stack-limit.steps` | not built | Lv2:32, Lv4:64, Lv6:128, Lv9:256, Lv12:512 | feeds `IslandService#spawnerStackLimit` |
| `masteries` (Phase 5) | `market-contacts.percent-per-level` / `max-level` | not built | 2 / 20 | MA5 |
| `sets.yml` (W1) | `coinmaster.source.price` | not built | $40M | WARDROBE §2.4 (no change) |
<!-- END table:config_changes -->

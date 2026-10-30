# Economy Balance Sheet (M3.0, re-run BAL2, enchants + sets TUNE)

> **Status: DRAFT, waiting for owner approval.** No price from this sheet goes live (M3.4 selling, M3.5 shop, Phase 4 Forge, Phase 5 upgrades) until the owner approves it. All numbers are proposals and live in typed configs, never in code.
>
> **Every table is generated** by [`tools/economy_model.py`](../tools/economy_model.py) from the formulas in §1. Change a parameter there, then run:
> ```bash
> python3 tools/economy_model.py --write docs/ECONOMY_BALANCE.md   # refresh all tables in this file
> python3 tools/economy_model.py --check docs/ECONOMY_BALANCE.md   # fails if this file is out of date
> python3 tools/economy_model.py --table milestones                # print one table
> python3 tools/economy_model.py                                   # print everything
> ```
> Every run ends with a self-check (asserts on the cap math, the stack steps, the enchant cost table, the set level curve, determinism and the pacing curve). §13 reads the current config defaults straight from the Java config classes, so a config change shows up there on the next run. **TUNE:** the enchant factors are read from `enchants.yml` + `EnchanterConfig` and the set factors from `sets.yml` + `WardrobeConfig` (the bundled defaults), so the sheet follows those files too.
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

**Results** (numbers from the TUNE re-run):
- **Fast start:** every archetype reaches tier 2 within 3 played hours and earns ~$40–70k in the first hour.
- **Progressively harder:** the regular's gap between tiers grows from 2 h (tier 2) to 112 h (tier 11). The longest step is tier 8 → 9 (x2.2 the gap before it), and it is followed by the biggest jump (+120% after the Forge).
- **Income meta still holds** for the regular player over the season: The Depths ~50%, island ~33% (production 16% + spawners 18%), events ~15%, interest ~3% (§2.3; BANK2 raised interest +50% per day, §6).
- **Owner targets** (most active player, money earned so far): first hour, week 2 and month 2 land within 0.8–1.3× of the targets; day 3 is still ~2.6× (§2.2).
- **The +300% cap is never reached by normal play.** A grinder ends the season at about +128% without boosters (average ×2.5 with boosters). Only the best of everything at once (+305%) touches the cap (§2.7).
- **Biggest risk found: spawner output.** `stacking.yml → spawners.mobs-per-spawner: 4` makes one spawner earn 4× what this sheet assumes. With 4, spawners become ~69% of a regular player's income. **Suggested: 1** (§4.3, §14). Also needed: the shop price of spawners must rise per spawner the island owns, otherwise spawners pay for each other and money compounds (§7.3).
- **Sinks:** the economy removes **82% of the money it creates over the season** (85% before the UX3 bank tier rebalance, because bank tiers 9–10 now cost $50M/$200M instead of $781M/$3.9B). The 14-day average never stays in the "act" zone (lowest 60% on day 30 since UX3, 57% before, when grinders have bought everything tier 10 allows). Mastery is still load-bearing: without it the season sink ratio falls to ~39% (§7.3).

**What TUNE changed** (E4 + W4, 2026-10-30): the enchants and sets as built in E1–E3 and W1–W2 are now in the model.
- **Perk slots hold real enchants.** BAL2 used a stand-in of +12% break speed per perk slot. TUNE replaced it with a pickaxe build read from `enchants.yml`: **Swiftness, Scholar's Insight, Prosperity, Vein Rend** (Gilded Touch takes a slot only while a book is missing). Levels come from the crystals the player has and the tool-level gate; a slot is filled once the median player has found that book (§9.5).
- **Sets are worn and levelled.** Delver's Garb from the first hours, Deepforge Plate once bought with crystals (mine tier 6), Coinmaster Silks at tier 8. Set XP and bonuses come from `sets.yml` + `wardrobe.yml` (§9.6).
- **Book drops, pity and rolls are modelled** per activity and band (§9.7), family caps are checked (§9.8), and the Depths combat drops (`mobs.yml` × `sell.yml`) are compared with the mine (§9.9).

**TUNE results:**
- **Enchants + set add ~+10–20% income per played hour in the first week and ×1.7–2.0 late in the season** for regular and grinder (enchants +33–58%, set +25–30%); casual players get +5–15%. The pacing stays as approved: the regular's tier gaps are 2 → 3 → 4 → 5 → 8 → 11 → 18 → 40 → 67 → 112 h (BAL2: 2 → … → 109 h) and the grinder still reaches tier 12 on day 51.
- **Scholar's Insight is load-bearing.** Area blocks give items and crystals but **no skill XP** (as built), so a pure money build (Gilded Touch instead of Scholar's Insight) reaches tier 12 later and the grinder has nothing to buy for weeks: the lowest 7-day sink ratio in days 60–90 drops to 13% (§7.3). The model's reference build therefore carries one XP enchant, which is what reproduces BAL2's pacing.
- **Combat books were twice too frequent:** a regular kills ~370–500 mobs per played hour, so 1 in 150–300 gave 1.8–2.9 books/h (target 1–1.4). **Changed** the bundled `enchanter.yml → drops.combat.one-in` to 300/350/400/450 and the pity to 750/875/1,000/1,125 (§9.7, §14.1). Mine books are on target except band D (1.35–1.9/h with Vein Rend II–III): watch, suggested only.
- **No family cap is reached by enchants + sets alone** (at most 60% of a cap, tool XP); the +300% sell cap still needs the best of everything (§9.8). Set levels match WARDROBE §3.2: a mining set reaches 1× after ~4–9 played hours and 2× after ~50–110 h; Coinmaster maxes ~29 h after purchase.
- **Combat drop prices hold:** value per kill is within ±11% of the regular's enchanted mine at every tier (16–28% above the bare mine at tiers 7–10, from BAL2's stand-in); `/shop` sells no combat drop and `BuySellInvariantTest` passes (§9.9).
- **BANK2 (owner #169):** interest every 8 h with 4× the rate and cap per payout = +50% interest per day; casual interest share 7.7% of season income (new limit ~8%), sink ratio unchanged (§6.0).
- **UX3 bank tiers (owner feedback 31 Oct):** personal tier 10 holds $10B (was $195.3B), island bank level 10 $50B (was $1T); interest shares unchanged within ±0.5 points (§6.2a).
- **Watch:** one day (day 30) with the 14-day sink ratio at 60% (UX3; 57% before) while grinders save between tier 10 and 11 (§7.2).

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
2. Delver's Garb ($20k, the first hours), then the Coinmaster set once they reach tier 8 (they save for it if it costs at most 3 days of income),
3. production levels and spawners that pay back within 3.5 days,
4. the cheapest island level upgrade or late project,
5. the next personal bank tier,
6. mastery.

They also spend 4% of income in `/shop` and trade 10% of income on the AH. Crystals go to enchants first (up to what the books found, the gear and the tool level allow), the crystal set (Deepforge) at mine tier 6, then rolls and the island shop. The newest set is worn with all 4 pieces (Delver → Deepforge → Coinmaster, §9.6).

**"Income per played hour"** = Depths + events per played hour, plus that day's island and spawner production spread over the played hours.

### 1.2 Parameters and formulas
`t` = tier (1–12), `REF(t)` = reference income per played hour at tier t, used to price upgrades in "hours of income" so prices follow income automatically.

<!-- BEGIN table:params -->
| Parameter | Formula / value | Note |
|---|---|---|
| Start money | $1k | decided |
| Mine drop price | P(t) = 20 x 1.35^(min(t,8)-1) x 2.2^max(0,t-8) | tiers 1-8 / 9-12 |
| Reference income | REF(t) = 2,500 x 1.3^(t-1) x P(t) | per played hour, all sources; prices are 'hours of REF' |
| Break rate | bps = min(6.0, 1.0 x 1.05^(tool-1) x (1 + 0.01 x level + speed family)) | blocks/s; speed family = Swiftness + set, capped (§9.5) |
| Drops per ore block | (1 + 0.05 x (tool-1) + extra drops family) x (1 + area family) | Prosperity + Deepforge; Vein Rend blocks at 50% yield (§9.5) |
| New ore before its tool | break speed x 0.7 | until the next tool is forged (#61 'down') |
| Depths time | 70% | share of a played hour spent breaking |
| Dynamic price (Depths / island) | 0.85 / 0.9 | average realised multiplier |
| Sell multiplier | 1 + min(3, sum of all sell bonuses) | §2.7; cap applied in every booster state |
| Enchant crystals | step s = 100 x 1.8^(s-1) x rarity factor; level n of max M at step round(1+(n-1)x9/(M-1)) | enchanter.yml costs.*; tool-level gate ceil(M x level / 50) |
| Book drops | 1 in 2000/3000/4000/5000 ore blocks by band, pity 5000/7500/10000/12500 | enchanter.yml drops.*; first finds at 100 / 1,000 actions |
| Set level bonus | value x level x 2 / 50; XP to next level 200 x 1.1^L | wardrobe.yml levels.*; sets.yml values at 1x |
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
| Interest | personal 0.60% / island 1.00% per 8 h | capped per payout; withdrawal fee 2% -> 1.5% -> 1% -> 0.5% by island mastery |
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
| First hour | $53.8k/h · $39.6k | $60.4k/h · $53.4k | $75.5k/h · $66.8k |
| Day 1 | $58.9k/h · $72.6k | $107.9k/h · $316.7k | $360.4k/h · $2M |
| Day 3 | $82.5k/h · $208.7k | $308.6k/h · $1.5M | $1.1M/h · $12M |
| Week 1 | $142.6k/h · $617.2k | $610.8k/h · $7M | $3.4M/h · $86.5M |
| Week 2 | $290.3k/h · $2.2M | $1.6M/h · $33.5M | $13.8M/h · $530.9M |
| Month 1 | $822k/h · $10.6M | $3.3M/h · $164.2M | $41M/h · $4.5B |
| Month 2 | $1.7M/h · $51.5M | $28.5M/h · $1.5B | $283.9M/h · $41.6B |
| Month 3 | $3.1M/h · $132.4M | $37.3M/h · $4.6B | $296.1M/h · $112.7B |
<!-- END table:targets -->

### 2.2 Owner targets vs. model
<!-- BEGIN table:targets_check -->
| Stage | Owner target (most active, earned so far) | Model grinder | Grinder / target | Model regular | Regular / target |
|---|---|---|---|---|---|
| First hour | $50k | $66.8k | 1.3x | $53.4k | 1.1x |
| Day 3 | $5M | $12M | 2.4x | $1.5M | 0.3x |
| Week 2 | $500M | $530.9M | 1.1x | $33.5M | 0.1x |
| Month 2 | $50B | $41.6B | 0.8x | $1.5B | 0.0x |
<!-- END table:targets_check -->

Day 3 is still high for the most active players: a grinder has played ~26 h by day 3 and is at tier 6. Hitting $5M there while still reaching $500M in week 2 needs a 100× jump in 11 days, which feels like a cliff. **Proposal (unchanged):** read the day-3 target as "a regular player" (~$1.6M), keep the smooth curve.

### 2.3 Where the money comes from (share of money earned so far)
<!-- BEGIN table:shares -->
| Archetype | Earned up to | Depths | Island production | Spawners | Island total | Events/quests | Interest |
|---|---|---|---|---|---|---|---|
| casual | Week 1 | 60% | 20% | 0% | 20% | 18% | 1.6% |
| casual | Month 1 | 46% | 20% | 15% | 35% | 14% | 4.7% |
| casual | Month 3 | 38% | 20% | 24% | 43% | 11% | 7.5% |
| regular | Week 1 | 60% | 14% | 7% | 21% | 18% | 1.1% |
| regular | Month 1 | 51% | 15% | 16% | 31% | 15% | 2.3% |
| regular | Month 3 | 50% | 15% | 18% | 33% | 15% | 2.7% |
| grinder | Week 1 | 50% | 14% | 20% | 34% | 15% | 0.6% |
| grinder | Month 1 | 53% | 14% | 17% | 31% | 16% | 1.0% |
| grinder | Month 3 | 48% | 30% | 6% | 36% | 14% | 1.4% |
| **target** | season | ~50% |  |  | ~35% | ~15% | <= 5% |
<!-- END table:shares -->

- Early on the island is small, so The Depths carries ~60%. The island and spawners catch up after week 1.
- **Casual players lean on the island** (44% by month 3): they play 1 h but production runs 8 h. That is intended (a casual player's island keeps working), and the absolute amounts stay small.
- **Grinders sit at ~36% island by month 3** (BAL2: ~45%): production runs ~20 h/day (the AFK lever, risk R5), but the enchants (Prosperity, Vein Rend) now make the Depths pay more per block.

### 2.4 Where each archetype is at each stage
<!-- BEGIN table:progress -->
| Stage | Casual (played h · tier · tool level (slots) · production levels · spawners/stack limit · level upgrades · late projects · mastery · set worn + level) | Regular | Grinder |
|---|---|---|---|
| First hour | 1 h · T1 · Lv4 (0) · PL 4 · SP 0 (8/block) · UL 6 · late 0 · M 0 · set - | 1 h · T1 · Lv4 (0) · PL 4 · SP 0 (8/block) · UL 9 · late 0 · M 0 · set - | 1 h · T1 · Lv5 (1) · PL 5 · SP 0 (8/block) · UL 11 · late 0 · M 0 · set - |
| Day 1 | 2 h · T1 · Lv5 (1) · PL 4 · SP 0 (8/block) · UL 12 · late 0 · M 0 · set - | 4 h · T2 · Lv10 (1) · PL 7 · SP 0 (32/block) · UL 24 · late 0 · M 0 · Delver 12 | 10 h · T4 · Lv19 (2) · PL 15 · SP 1 (64/block) · UL 47 · late 0 · M 0 · Delver 27 |
| Day 3 | 4 h · T2 · Lv8 (1) · PL 4 · SP 0 (32/block) · UL 20 · late 0 · M 0 · Delver 8 | 10 h · T4 · Lv18 (2) · PL 12 · SP 1 (64/block) · UL 44 · late 0 · M 0 · Delver 25 | 26 h · T6 · Lv28 (2) · PL 20 · SP 8 (128/block) · UL 67 · late 0 · M 0 · Delver 39 |
| Week 1 | 7 h · T3 · Lv13 (1) · PL 6 · SP 0 (32/block) · UL 32 · late 0 · M 0 · Delver 19 | 22 h · T5 · Lv26 (2) · PL 14 · SP 3 (64/block) · UL 60 · late 0 · M 0 · Delver 35 | 58 h · T8 · Lv38 (3) · PL 25 · SP 18 (128/block) · UL 84 · late 4 · M 0 · Deepforge 37 |
| Week 2 | 14 h · T4 · Lv20 (2) · PL 9 · SP 1 (64/block) · UL 48 · late 0 · M 0 · Delver 27 | 43 h · T7 · Lv34 (3) · PL 19 · SP 6 (128/block) · UL 83 · late 1 · M 0 · Delver 44 | 114 h · T9 · Lv45 (4) · PL 29 · SP 33 (256/block) · UL 107 · late 19 · M 0 · Coinmaster 50 |
| Month 1 | 31 h · T6 · Lv27 (2) · PL 13 · SP 3 (128/block) · UL 68 · late 0 · M 0 · Delver 37 | 91 h · T8 · Lv41 (3) · PL 21 · SP 11 (128/block) · UL 96 · late 11 · M 0 · Deepforge 47 | 242 h · T10 · Lv50 (4) · PL 34 · SP 46 (256/block) · UL 120 · late 36 · M 0 · Coinmaster 50 |
| Month 2 | 61 h · T7 · Lv35 (3) · PL 16 · SP 6 (128/block) · UL 84 · late 4 · M 0 · Deepforge 16 | 181 h · T10 · Lv48 (4) · PL 30 · SP 23 (256/block) · UL 117 · late 29 · M 0 · Coinmaster 50 | 482 h · T12 · Lv50 (4) · PL 45 · SP 70 (512/block) · UL 144 · late 54 · M 8 · Coinmaster 50 |
| Month 3 | 90 h · T8 · Lv39 (3) · PL 18 · SP 9 (128/block) · UL 96 · late 8 · M 0 · Deepforge 41 | 271 h · T10 · Lv50 (4) · PL 30 · SP 32 (256/block) · UL 120 · late 36 · M 0 · Coinmaster 50 | 722 h · T12 · Lv50 (4) · PL 45 · SP 94 (512/block) · UL 144 · late 54 · M 22 · Coinmaster 50 |
<!-- END table:progress -->

### 2.5 Time to milestone
Played hours (and the day it happens) for each archetype.

<!-- BEGIN table:milestones -->
| Milestone | Casual (1 h/day): played h (day) | Regular (3 h/day) | Grinder (8 h/day) |
|---|---|---|---|
| Earned $100k | 2.1 h (day 2) | 1.7 h (day 1) | 1.4 h (day 1) |
| Earned $1M | 10.1 h (day 10) | 8.3 h (day 3) | 7.3 h (day 1) |
| Earned $10M | 29.9 h (day 30) | 25.6 h (day 9) | 24.9 h (day 3) |
| Earned $100M | 80.3 h (day 80) | 70.8 h (day 24) | 62.8 h (day 8) |
| Earned $1B | not this season | 163.1 h (day 55) | 146.0 h (day 18) |
| Earned $10B | not this season | not this season | 298.7 h (day 38) |
| Earned $100B | not this season | not this season | 682.0 h (day 85) |
| Depths tier 4 (Gold) | 11.5 h (day 12) | 9.2 h (day 3) | 8.1 h (day 1) |
| Depths tier 8 (Emerald) | 67.6 h (day 68) | 51.5 h (day 17) | 48.6 h (day 6) |
| Depths tier 10 (Quartz) | not this season | 159.7 h (day 53) | 145.5 h (day 18) |
| Depths tier 12 (Voidstone) | not this season | not this season | 410.0 h (day 52) |
| Tool Lv 30 (3 perk slots) | 36.7 h (day 37) | 29.0 h (day 10) | 28.7 h (day 4) |
| Spawner stack 64 | 12.5 h (day 12) | 10.0 h (day 3) | 9.2 h (day 1) |
| Spawner stack 512 | not this season | not this season | 421.7 h (day 53) |
| Delver's Garb bought | 2.1 h (day 2) | 2.0 h (day 1) | 1.6 h (day 1) |
| Delver's Garb Lv 25 (1x) | 11.7 h (day 12) | 9.6 h (day 3) | 8.4 h (day 1) |
| Crystal set (Deepforge) bought | 57.8 h (day 58) | 44.8 h (day 15) | 42.0 h (day 6) |
| Deepforge Lv 25 | 64.5 h (day 65) | 49.9 h (day 17) | 46.5 h (day 6) |
| Deepforge Lv 50 | not this season | not this season | not this season |
| Coinmaster set bought | not this season | 99.9 h (day 33) | 64.2 h (day 8) |
| Coinmaster Lv 25 (1x) | not this season | 102.3 h (day 34) | 66.6 h (day 9) |
| Coinmaster Lv 50 (2x) | not this season | 128.9 h (day 43) | 93.2 h (day 12) |
| Sell bonus +100% (no boosters) | not this season | 196.8 h (day 66) | 117.7 h (day 15) |
| Sell bonus +200% (no boosters) | not this season | not this season | not this season |
| Median player owns a Rare book | 13.0 h (day 13) | 10.4 h (day 4) | 9.2 h (day 1) |
| ... an Epic book | 30.3 h (day 30) | 24.1 h (day 8) | 21.6 h (day 3) |
| ... a Legendary book | 71.0 h (day 71) | 55.2 h (day 19) | 51.7 h (day 7) |
| Vein Rend on the pickaxe | not this season | 120.3 h (day 40) | 110.7 h (day 14) |
| Gilded Touch on the pickaxe | not this season | not this season | not this season |
| First pickaxe enchant at its max level | not this season | 225.6 h (day 75) | 206.5 h (day 26) |
<!-- END table:milestones -->

Read: a casual player (1 h/day) reaches tier 8 around day 68 and earns ~$130M over the season; a regular (3 h/day) reaches tier 11 at the very end; a grinder (8 h/day) reaches tier 12 around day 51. Nobody reaches +200% sell bonus without boosters. Book milestones are for the **median** player (expected books found ≥ ln 2); Gilded Touch never gets a slot in the model's 4-slot build, Deepforge Lv 50 is not reached because players switch to Coinmaster at tier 8 (§9.6).

### 2.6 Pacing: fast start, harder every tier, ups and downs (#61)
The owner wants every system to be fast at first, then progressively harder, with ups and downs that keep players going. In the model this comes from:

| Up (a jump) | Down / plateau (a grind) |
|---|---|
| New Depths tier: ore +35% (vanilla) or +120% (custom) after the Forge | Each tier takes longer than the one before (gap ×1.3–2.2) |
| Forge: new tool = faster + more fortune | New ore is slow until the next tool is forged (custom tiers) |
| Spawner stack step (8 → 32 → 64 → 128 → 256 → 512 per block) | Saving phases: the Coinmaster set ($40M) pauses upgrades for ~3 days |
| Coinmaster set: +50% sell at level 50 | Set level starts at 0× (0 bonus on day one of a new set; switching Delver → Deepforge → Coinmaster each starts at 0) |
| Perk slot unlock (tool Lv 5/15/30/45) + a new enchant in it; each new rarity band (tiers 4/7/10) | Merging two sets resets the level to 0 (WARDROBE §5), prestige restarts tool speed at Lv 1 |
| Set level milestones (10, 25 = 1×, 50 = 2×), Vein Rend arriving (+24–48% items) | Enchant levels cost ×1.8 per step; the tool-level gate holds levels back until the tool catches up |

**Tier gaps (regular player):**
<!-- BEGIN table:pacing -->
| Tier | Ore | Played h since last tier (regular) | vs last gap | Up at unlock (new ore, old tool) | Plateau until the tool is forged | Up after the forge (vs old ore) |
|---|---|---|---|---|---|---|
| 2 | Copper | 2.2 h (day 1) | - | 0% (keeps old ore) | 0.1 h | +35% |
| 3 | Iron | 3.1 h (day 2) | x1.4 | 0% (keeps old ore) | 0.1 h | +35% |
| 4 | Gold | 3.9 h (day 3) | x1.3 | 0% (keeps old ore) | 0.1 h | +35% |
| 5 | Redstone | 5.3 h (day 5) | x1.4 | 0% (keeps old ore) | 0.1 h | +35% |
| 6 | Lapis | 7.9 h (day 8) | x1.5 | 0% (keeps old ore) | 0.1 h | +35% |
| 7 | Diamond | 11.2 h (day 11) | x1.4 | 0% (keeps old ore) | 0.1 h | +35% |
| 8 | Emerald | 17.9 h (day 17) | x1.6 | 0% (keeps old ore) | 3.5 h | +35% |
| 9 | Amethyst | 41.0 h (day 31) | x2.3 | +54% | 0.1 h | +120% |
| 10 | Quartz | 67.2 h (day 53) | x1.6 | +54% | 0.1 h | +120% |
| 11 | Netherite | not this season | - | - | - | not this season |
| 12 | Voidstone | not this season | - | - | - | not this season |
<!-- END table:pacing -->

**Income per played hour over the season** ("UP" = +10% or more per day since the last row, "climb" = +2–10%, "plateau" = under +2% per day):
<!-- BEGIN table:curve -->
| Day | Casual (income per played h · tier · trend per day) | Regular | Grinder |
|---|---|---|---|
| 1 | $58.9k/h · T1 · start | $107.9k/h · T2 · start | $360.4k/h · T4 · start |
| 2 | $59.5k/h · T1 · plateau | $180.2k/h · T3 · UP | $638.1k/h · T5 · UP |
| 3 | $82.5k/h · T2 · UP | $308.6k/h · T4 · UP | $1.1M/h · T6 · UP |
| 5 | $92k/h · T2 · climb | $490k/h · T5 · UP | $2.2M/h · T7 · UP |
| 7 | $142.6k/h · T3 · UP | $610.8k/h · T5 · UP | $3.4M/h · T8 · UP |
| 10 | $191.1k/h · T3 · UP | $1M/h · T6 · UP | $4.6M/h · T8 · UP |
| 14 | $290.3k/h · T4 · UP | $1.6M/h · T7 · UP | $13.8M/h · T9 · UP |
| 21 | $501.9k/h · T5 · climb | $2.8M/h · T8 · climb | $32.6M/h · T10 · UP |
| 30 | $822k/h · T6 · climb | $3.3M/h · T8 · plateau | $41M/h · T10 · climb |
| 45 | $1.4M/h · T7 · climb | $12.2M/h · T9 · climb | $104.2M/h · T11 · climb |
| 60 | $1.7M/h · T7 · plateau | $28.5M/h · T10 · climb | $283.9M/h · T12 · climb |
| 75 | $2.7M/h · T8 · climb | $35.8M/h · T10 · plateau | $291.2M/h · T12 · plateau |
| 90 | $3.1M/h · T8 · plateau | $37.3M/h · T10 · plateau | $296.1M/h · T12 · plateau |
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
| Gilded Touch (enchant) | 3% per level, 15% at V | Mining T3 book, crystals | not in the model's 4-slot build (§9.5); a money build is in §7.3 |
| Personal sell booster | 50% | crates, AFK pool, votes, island shop | model uptime 5 / 15 / 30% |
| Global booster | 50% | store purchase (O11 counts toward the cap) | model uptime 15% |
| Sell event | 25% | weekend events, KOTH reward | model uptime 5% |
| **Best of everything** | **305% → capped at 300% (x4.00)** | all sources maxed at once | only then does the cap bite |
<!-- END table:sell_sources -->

**What players actually have** (without boosters, then the day's average with boosters):
<!-- BEGIN table:sell_stack -->
| Stage | Casual: sell bonus without boosters (R rank, U upgrade, M mastery, S set, E enchant) · average multiplier with boosters | Regular | Grinder |
|---|---|---|---|
| Day 1 | +5% (U5) · avg x1.16 | +14% (R4 U10) · avg x1.30 | +23% (R8 U15) · avg x1.47 |
| Day 3 | +5% (U5) · avg x1.16 | +19% (R4 U15) · avg x1.35 | +33% (R8 U25) · avg x1.57 |
| Week 1 | +10% (U10) · avg x1.21 | +29% (R4 U25) · avg x1.45 | +43% (R8 U35) · avg x1.67 |
| Week 2 | +20% (U20) · avg x1.31 | +34% (R4 U30) · avg x1.50 | +98% (R8 U40 S50) · avg x2.22 |
| Month 1 | +25% (U25) · avg x1.36 | +44% (R4 U40) · avg x1.60 | +108% (R8 U50 S50) · avg x2.32 |
| Month 2 | +35% (U35) · avg x1.46 | +99% (R4 U45 S50) · avg x2.15 | +122% (R8 U60 M4 S50) · avg x2.46 |
| Month 3 | +40% (U40) · avg x1.51 | +104% (R4 U50 S50) · avg x2.20 | +128% (R8 U60 M10 S50) · avg x2.52 |
<!-- END table:sell_stack -->

- The cap is a safety rail, not a target. With the values above nobody reaches it by playing normally; the best a grinder sees for a short time (all boosters + event at once) is ×3.53. Enchants and sets together can add at most +65% to this family (Gilded Touch V + Coinmaster Lv 50, §9.8).
- **Gilded Touch is not in the model's build** (a 4-slot pickaxe prefers Swiftness, Scholar's Insight, Prosperity and Vein Rend), so the "E" column stays empty. As built it only works on Depths drops, so the model never applies it to island income.
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
| 2 | Copper | 5 | 1.5 | $27 | x1.35 | 2,249 | $54.2k |
| 3 | Iron | 10 | 2.5 | $36 | x1.35 | 2,637 | $89.9k |
| 4 | Gold | 15 | 4 | $49 | x1.35 | 2,906 | $139.8k |
| 5 | Redstone | 20 | 6 | $66 | x1.35 | 3,189 | $216.1k |
| 6 | Lapis | 25 | 9 | $90 | x1.35 | 3,609 | $343.8k |
| 7 | Diamond | 30 | 13 | $121 | x1.35 | 3,940 | $527.1k |
| 8 | Emerald | 35 | 19 | $163 | x1.35 | 4,096 | $768.1k |
| 9 | Amethyst | 42 | 28 | $360 | x2.20 | 4,635 | $2M |
| 10 | Quartz | 50 | 40 | $791 | x2.20 | 5,014 | $4.9M |
| 11 | Netherite | 58 | 58 | $1.7k | x2.20 | 5,541 | $12.3M |
| 12 | Voidstone | 66 | 85 | $3.8k | x2.20 | 5,819 | $29.4M |
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
| 2 | Bone Crypt | 6 | 382 | $167 | 6.2x |
| 3 | Spider Den | 12 | 397 | $266 | 7.3x |
| 4 | Slime Bog | 18 | 412 | $400 | 8.1x |
| 5 | Sunken Ruins | 24 | 426 | $596 | 9.0x |
| 6 | Witch Hollow | 30 | 441 | $917 | 10.2x |
| 7 | Pillager Outpost | 38 | 456 | $1.4k | 11.2x |
| 8 | Blaze Forge | 46 | 470 | $1.9k | 11.8x |
| 9 | Wither Keep | 55 | 485 | $4.8k | 13.4x |
| 10 | Warlord's Citadel | 66 | 500 | $11.5k | 14.5x |
<!-- END table:combat -->

Mini-boss and world boss rewards are paid as **event income** (30% of Depths income per played hour across all event types), not on top of it.

This table is the parity with the **bare** pickaxe (tool tier fortune, the regular's Swiftness). `sell.yml → custom-items.depths-combat` keeps the BAL2 prices ($104 … $14.7k per kill): at tiers 7–10 they are 16–28% above the bare mine but within ±11% of the regular's **enchanted** mine (Prosperity + Vein Rend), and combat has no area enchant. They stay (§9.9).

### 3.3 Blocks per hour per tool tier
Respawn is 10 s per player and a field is ~1,600 blocks, so the respawn timer never limits a single player. The practical limit is reach: **6 blocks/s** (model cap and recommended server cap).

<!-- BEGIN table:blocks -->
| Tool tier | Tool | Typical level | Slots | Speed family (Swiftness + set) | Blocks/s | Casual blocks/played h | Regular | Grinder | Field depleted at once |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Coal pickaxe | 1 | 0 | +0% | 1.01 | 1,527 | 1,909 | 2,163 | 0.6% |
| 2 | Copper pickaxe | 7 | 1 | +6% | 1.19 | 1,799 | 2,249 | 2,549 | 0.7% |
| 3 | Iron pickaxe | 12 | 1 | +15% | 1.40 | 2,110 | 2,637 | 2,989 | 0.9% |
| 4 | Gold pickaxe | 17 | 2 | +16% | 1.54 | 2,325 | 2,906 | 3,294 | 1.0% |
| 5 | Redstone pickaxe | 22 | 2 | +17% | 1.69 | 2,551 | 3,189 | 3,614 | 1.1% |
| 6 | Lapis pickaxe | 26 | 2 | +24% | 1.91 | 2,887 | 3,609 | 4,090 | 1.2% |
| 7 | Diamond pickaxe | 31 | 3 | +25% | 2.08 | 3,152 | 3,940 | 4,465 | 1.3% |
| 8 | Emerald pickaxe | 36 | 3 | +18% | 2.17 | 3,276 | 4,096 | 4,642 | 1.4% |
| 9 | Amethyst pickaxe | 42 | 3 | +24% | 2.45 | 3,708 | 4,635 | 5,253 | 1.5% |
| 10 | Quartz pickaxe | 47 | 4 | +24% | 2.65 | 4,011 | 5,014 | 5,682 | 1.7% |
| 11 | Netherite pickaxe | 50 | 4 | +30% | 2.93 | 4,433 | 5,541 | 6,280 | 1.8% |
| 12 | Voidstone pickaxe | 50 | 4 | +30% | 3.08 | 4,655 | 5,819 | 6,594 | 1.9% |
| max | Voidstone, Lv50, best speed build | 50 | 5 | +78% | 3.90 | 5,896 | 7,370 | 8,353 | 2.4% |
<!-- END table:blocks -->

The speed family (Swiftness on the pickaxe + Delver's Garb) adds to the tool level bonus (as `ToolItems#speedBonus` does), so even the best speed build (Swiftness V + Ember Momentum III + a Lv 50 speed set) stays at ~3.9 blocks/s, under the 6 blocks/s cap. Area enchants don't use break time: Vein Rend III adds +48% items and crystals per block on top (§9.5) and gives no skill XP.

### 3.4 Skill pacing (tier unlocks)
Mining skill XP per block = the tier XP from `WORLD.md`. Unlock levels are unchanged (0, 5, 10 … 58, 66).

<!-- BEGIN table:skill -->
| Tier | Ore | Mining level | Total skill XP | Casual reaches it | Regular | Grinder |
|---|---|---|---|---|---|---|
| 2 | Copper | 5 | 4,465 | 2.7 h (day 3) | 2.2 h (day 1) | 1.9 h (day 1) |
| 3 | Iron | 10 | 15,575 | 6.6 h (day 7) | 5.3 h (day 2) | 4.7 h (day 1) |
| 4 | Gold | 15 | 43,221 | 11.5 h (day 12) | 9.2 h (day 3) | 8.1 h (day 1) |
| 5 | Redstone | 20 | 112,013 | 18.1 h (day 18) | 14.5 h (day 5) | 12.8 h (day 2) |
| 6 | Lapis | 25 | 283,189 | 28.1 h (day 28) | 22.4 h (day 8) | 21.1 h (day 3) |
| 7 | Diamond | 30 | 709,129 | 43.2 h (day 43) | 33.6 h (day 11) | 32.7 h (day 4) |
| 8 | Emerald | 35 | 1.8M | 67.6 h (day 68) | 51.5 h (day 17) | 48.6 h (day 6) |
| 9 | Amethyst | 42 | 5.5M | not this season | 92.5 h (day 31) | 84.3 h (day 11) |
| 10 | Quartz | 50 | 16.1M | not this season | 159.7 h (day 53) | 145.5 h (day 18) |
| 11 | Netherite | 58 | 44.4M | not this season | not this season | 244.9 h (day 31) |
| 12 | Voidstone | 66 | 119.5M | not this season | not this season | 410.0 h (day 52) |
| - | (after tier 12) | 75 | 272.3M | not this season | not this season | 628.1 h (day 79) |
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
| 2 | 15 | 25,564 | ~day 2 | 8.5 h (day 9) | 6.8 h (day 2) | 6.0 h (day 1) |
| 3 | 30 | 477,405 | ~week 1 | 36.7 h (day 37) | 29.0 h (day 10) | 28.7 h (day 4) |
| 4 | 45 | 8.4M | ~week 3-4 | not this season | 120.3 h (day 40) | 110.7 h (day 14) |
| 5 (prestige) | 50 | 21.7M | ~week 6+ | not this season | 199.8 h (day 67) | 180.9 h (day 23) |
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
| 3 | 10 | $26.5k | $106.5k | 373 | $6.1k/h | $73.5k/day |
| 4 | 12 | $44.8k | $185.8k | 468 | $10.4k/h | $124.5k/day |
| 5 | 14 | $75.7k | $319.8k | 586 | $18.9k/h | $226.3k/day |
| 6 | 17 | $166.4k | $712.5k | 824 | $37k/h | $444.1k/day |
| 7 | 19 | $281.1k | $1.2M | 1,034 | $62.7k/h | $752k/day |
| 8 | 21 | $475.1k | $2.1M | 1,296 | $113.2k/h | $1.4M/day |
| 9 | 25 | $1.4M | $5.9M | 2,040 | $323k/h | $3.9M/day |
| 10 | 30 | $5M | $21.8M | 3,595 | $1.3M/h | $15M/day |
| 11 | 32 | $8.5M | $36.9M | 4,510 | $1.9M/h | $23.2M/day |
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
| step 1 | Lv 2 | 32 | 128 | $12.1k | 3.1 h (day 1) | 2.8 h (day 1) |
| step 2 | Lv 4 | 64 | 256 | $94.9k | 10.0 h (day 3) | 9.2 h (day 1) |
| step 3 | Lv 6 | 128 | 512 | $661.2k | 25.1 h (day 9) | 26.0 h (day 3) |
| step 4 | Lv 9 | 256 | 1024 | $16.5M | 114.1 h (day 38) | 101.4 h (day 13) |
| step 5 | Lv 12 | 512 | 2048 | $1.2B | not this season | 421.7 h (day 53) |
<!-- END table:stack_steps -->

---

## 5. Cost curves (Forge, island upgrades, late game)

### 5.1 The Forge (tool tiers)
Forging costs money plus materials from the previous tier (now checked in the model). The money part is ~0.3–2.3 hours of income: noticeable, never a wall. These numbers are exactly `tools.yml → tiers.pickaxe` today (§13).

<!-- BEGIN table:forge -->
| Tool tier | Pickaxe | Money | Materials | Material sell value | Money = hours of REF(t-1) | Regular forges it | Grinder forges it |
|---|---|---|---|---|---|---|---|
| 2 | Copper | $15k | 256 Coal | $5.1k | 0.3 h | 2.3 h (day 1) | 2.0 h (day 1) |
| 3 | Iron | $35.2k | 346 Copper | $9.3k | 0.4 h | 5.4 h (day 2) | 4.8 h (day 1) |
| 4 | Gold | $82.8k | 467 Iron | $17k | 0.5 h | 9.3 h (day 3) | 8.2 h (day 1) |
| 5 | Redstone | $194.7k | 630 Gold | $31k | 0.7 h | 14.6 h (day 5) | 16.7 h (day 2) |
| 6 | Lapis | $457.5k | 850 Redstone | $56.5k | 1.0 h | 22.5 h (day 8) | 26.0 h (day 3) |
| 7 | Diamond | $1.1M | 1,148 Lapis | $102.9k | 1.3 h | 33.7 h (day 11) | 32.8 h (day 4) |
| 8 | Emerald | $2.5M | 1,550 Diamond | $187.6k | 1.7 h | 55.0 h (day 18) | 50.0 h (day 6) |
| 9 | Amethyst | $5.9M | 2,092 Emerald | $341.9k | 2.3 h | 92.6 h (day 31) | 90.0 h (day 11) |
| 10 | Quartz | $14M | 2,824 Amethyst | $1M | 1.9 h | 159.8 h (day 53) | 146.0 h (day 18) |
| 11 | Netherite | $32.8M | 3,813 Quartz | $3M | 1.6 h | not this season | 245.0 h (day 31) |
| 12 | Voidstone | $77M | 5,147 Netherite | $9M | 1.3 h | not this season | 410.1 h (day 52) |
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
| Greenhold (farming) | 7 | $1.5M | $42.9M | $231.4M | 158 h | 33.6 h (day 11) | 32.7 h (day 4) |
| Wildwood (combat/spawners) | 9 | $7.3M | $215.5M | $1.2B | 158 h | 92.5 h (day 31) | 84.3 h (day 11) |
| Emberdeep (late game) | 11 | $60M | $1.8B | $9.5B | 158 h | not this season | 244.9 h (day 31) |
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

### 5.5 Phase 5 catalog note (PLAN5, no model change yet)
`docs/phases/PHASE-5-PROGRESSION.md` turns the 12 × 12 level grid into the real catalog. Some upgrades have fewer natural steps: Island Size 3 (9x9 cap, owner 157), Member Slots 4 (8 + leader, owner 242), Warp Slots 6, Island Vault 5, Entity Limits 1, Bank Capacity/Interest 10. Those levels cost **$8.9B** together instead of the grid's **$14.5B**. The missing **$5.6B** moves to the mastery ranks (MA1–MA12 + Treasury), priced on the same curve `W × 0.067 × 1.49^(n−1) × REF(n)` with their last rank at tier 12, so upgrades + masteries cost **$14.5B** again (ratio 1.00) and the sink ratio stays as modelled. The endless "mastery" of §5.4 is called **Ascension** in the plan and is unchanged (Market Contacts stays on that curve). Production upgrades stay on `C_p` with 10 levels each (PL 50; the grinder reaches PL 45).
**Not modelled yet (BAL3, M5.9):** the effects of Mine Fortune, Lumberjack and XP Boost (+2% per level each), the income masteries (Timber, Swift Hands, Crystal Rush, Lucky Strike, Deep Pockets), the earlier Treasury fee drop, and pillar/island level pacing. They sit inside the existing bonus family caps, but the next model run must add them before Phase 5 prices go live.

---

## 6. Banks and interest

Rules (M3.3, changed by BANK2 = owner answer 169): interest every **8 h** (00:00, 08:00, 16:00 UTC; also offline, lazily computed); paid on the **lowest balance during the period** (no deposit-before-payout trick); a withdrawal costs a **2% fee** (personal and island bank) and forfeits the period, except in the 10 free minutes after each payout; **island masteries lower the fee** one step per bank-fee mastery level: 2% → 1.5% → 1.0% → 0.5% (`bank.yml → withdraw.mastery-fee-percent`; the `/is masteries` hook `IslandService.bankFeeMasteryLevel` returns 0 until masteries exist); only accounts that played ≥ 2 h in the last 7 days earn interest; every payout is capped.

**BANK2 numbers.** The owner asked for higher interest paid every 8 hours. Fewer payouts alone would have cut interest to 3/8, so the rate and the payout cap per payout are both **×4** (personal 0.15% → 0.6%, island 0.25% → 1.0%; caps ×4): with 3 payouts a day instead of 8 that is **+50% interest per day** at every tier. The model keeps every money check: the casual's season interest share rises from 5.2% to ~7.7% (limit ~8%), the regular's to ~2.8%, the grinder's to ~0.9%; the season sink ratio stays 85% (interest is new money, but a small part of income). Catch-up after downtime stays one day (3 payouts). Existing `bank.yml` files with the untouched M3.3 values get the new ones on the first start (owner-changed values are kept), and bank states are converted from 3 h to 8 h period numbers once (no stretch of time is paid twice; at most a part of one period is skipped at the change).

### 6.0 Before and after (BANK2)
<!-- BEGIN table:interest_before_after -->
|  | M3.3 (before) | BANK2 (now) |
|---|---|---|
| Payouts | every 3 h (8 a day) | every 8 h (3 a day) |
| Rate per payout, personal / island | 0.15% / 0.25% | 0.6% / 1% |
| Max per day, personal tier 1 (full) | $1.2k | $1.8k |
| Max per day, personal tier 5 (full) | $125.8k | $82.5k |
| Max per day, island level 0 (full) | $20k | $30k |
| Max per day, island level 5 (full) | $4.9M | $2.7M |
| Casual (1 h/day): interest per day at month 1 | $28.2k/day | $45.6k/day |
| Regular (3 h/day): interest per day at month 1 | $387.4k/day | $435k/day |
| Grinder (8 h/day): interest per day at month 1 | $4.1M/day | $6.3M/day |
| Casual (1 h/day): interest share of season income | 5.2% | 7.5% |
| Regular (3 h/day): interest share of season income | 1.9% | 2.7% |
| Grinder (8 h/day): interest share of season income | 0.6% | 1.4% |
| Sink ratio over the season | 85% | 82% |
| Lowest 14-day sink ratio | 57% | 60% |
| Withdrawal fee | 2% | 2% -> 1.5% -> 1% -> 0.5% (island mastery 0-3) |
<!-- END table:interest_before_after -->

### 6.1 Personal bank (10 tiers)
<!-- BEGIN table:interest_personal -->
| Tier | Capacity | Unlock cost | Payout cap (per 8 h) | Uncapped payout at full | Effective rate at full | Max interest / day |
|---|---|---|---|---|---|---|
| 1 | $100k | $0 | $600 | $600 | 0.600% | $1.8k |
| 2 | $500k | $10k | $1.6k | $3k | 0.320% | $4.8k |
| 3 | $2M | $40k | $4.4k | $12k | 0.220% | $13.2k |
| 4 | $7.5M | $150k | $11.6k | $45k | 0.155% | $34.8k |
| 5 | $25M | $500k | $27.5k | $150k | 0.110% | $82.5k |
| 6 | $75M | $1.5M | $61k | $450k | 0.081% | $183k |
| 7 | $250M | $5M | $145k | $1.5M | 0.058% | $435k |
| 8 | $750M | $15M | $320k | $4.5M | 0.043% | $960k |
| 9 | $2.5B | $50M | $770k | $15M | 0.031% | $2.3M |
| 10 | $10B | $200M | $2.1M | $60M | 0.021% | $6.3M |
<!-- END table:interest_personal -->

### 6.2 Island bank (Bank Capacity / Bank Interest upgrades)
<!-- BEGIN table:interest_island -->
| Bank Capacity / Interest level | Capacity | Payout cap (per 8 h) | Uncapped payout at full | Effective rate at full | Max interest / day |
|---|---|---|---|---|---|
| 0 | $1M | $10k | $10k | 1.000% | $30k |
| 1 | $3M | $24k | $30k | 0.800% | $72k |
| 2 | $10M | $62k | $100k | 0.620% | $186k |
| 3 | $30M | $150k | $300k | 0.500% | $450k |
| 4 | $100M | $380k | $1M | 0.380% | $1.1M |
| 5 | $300M | $900k | $3M | 0.300% | $2.7M |
| 6 | $1B | $2.4M | $10M | 0.240% | $7.2M |
| 7 | $3B | $5.7M | $30M | 0.190% | $17.1M |
| 8 | $8B | $12M | $80M | 0.150% | $36M |
| 9 | $20B | $26M | $200M | 0.130% | $78M |
| 10 | $50B | $53M | $500M | 0.106% | $159M |
<!-- END table:interest_island -->

### 6.2a Bank tier rebalance (UX3, owner feedback 31 Oct)
The owner saw "tier 10 = $195.3B" in game. The BANK2 tables grew ×5 per personal tier and ×4 per island level, so the
top steps held far more money than anyone has: a grinder has ~$17B at the end of the season, a regular ~$1–2B, a casual
~$40M. UX3 keeps the shape and the first two tiers (tier 1 = $100k free, tier 2 = $500k for $10k; cost = 2% of capacity,
owner #42; island level 0 = $1M) but then steps ×3–4 per personal tier up to **$10B** and ×3 per island level up to
**$50B**. The payout caps follow the old cap-per-capacity curve (a little lower, so the interest share stays where BANK2
put it: ≤ 8% of season income). Pacing: tier 2–3 cost what they did, so the first days are unchanged; the regular buys
tier 9–10 in month 2–3 (cheaper than before), which moves their Depths tier 11 from day 90 to just after the season
(+~3% played hours). Every other milestone moves by less than 6%. The tables live only in `bank.yml` (`BankConfig` defaults); this
model reads them from there, and `/is upgrade` shows the island amounts (capacity, interest cap, cost) from the same
file. Existing `bank.yml` files that still hold the BANK2 tables get the new ones on the first start (config version 3);
owner-changed tables stay. A bank above its new capacity keeps its money: deposits and interest pause until it is
below the capacity again.

<!-- BEGIN table:bank_rebalance -->
| Step | Before UX3 (capacity / cost / payout cap per 8 h) | Now (bank.yml) |
|---|---|---|
| Personal tier 1 | $100k / $0 / $600 | $100k / $0 / $600 |
| Personal tier 2 | $500k / $10k / $1.9k | $500k / $10k / $1.6k |
| Personal tier 3 | $2.5M / $50k / $6.1k | $2M / $40k / $4.4k |
| Personal tier 4 | $12.5M / $250k / $19.7k | $7.5M / $150k / $11.6k |
| Personal tier 5 | $62.5M / $1.2M / $62.9k | $25M / $500k / $27.5k |
| Personal tier 6 | $312.5M / $6.2M / $201.3k | $75M / $1.5M / $61k |
| Personal tier 7 | $1.6B / $31.2M / $644.2k | $250M / $5M / $145k |
| Personal tier 8 | $7.8B / $156.2M / $2.1M | $750M / $15M / $320k |
| Personal tier 9 | $39.1B / $781.2M / $6.6M | $2.5B / $50M / $770k |
| Personal tier 10 | $195.3B / $3.9B / $21.1M | $10B / $200M / $2.1M |
| Island level 0 | $1M / - / $10k | $1M / - / $10k |
| Island level 1 | $4M / - / $30k | $3M / - / $24k |
| Island level 2 | $16M / - / $90k | $10M / - / $62k |
| Island level 3 | $64M / - / $270k | $30M / - / $150k |
| Island level 4 | $256M / - / $810k | $100M / - / $380k |
| Island level 5 | $1B / - / $2.4M | $300M / - / $900k |
| Island level 6 | $4.1B / - / $7.3M | $1B / - / $2.4M |
| Island level 7 | $16.4B / - / $21.9M | $3B / - / $5.7M |
| Island level 8 | $65.5B / - / $65.6M | $8B / - / $12M |
| Island level 9 | $262.1B / - / $196.8M | $20B / - / $26M |
| Island level 10 | $1T / - / $590.5M | $50B / - / $53M |
| Casual (1 h/day): interest share of season income | 7.7% | 7.5% |
| Regular (3 h/day): interest share of season income | 2.8% | 2.7% |
| Grinder (8 h/day): interest share of season income | 0.9% | 1.4% |
| Casual (1 h/day): personal tier at month 3 | tier 5 | tier 6 |
| Regular (3 h/day): personal tier at month 3 | tier 8 | tier 10 |
| Grinder (8 h/day): personal tier at month 3 | tier 9 | tier 10 |
| Sink ratio over the season | 85% | 82% |
<!-- END table:bank_rebalance -->

### 6.3 Interest vs. income
<!-- BEGIN table:interest_vs_income -->
| Day | Casual interest (share of that day's income) | Regular | Grinder |
|---|---|---|---|
| Day 1 | $254/day (0.3%) | $1.1k/day (0.3%) | $3.8k/day (0.2%) |
| Day 3 | $739/day (1.0%) | $3.9k/day (0.5%) | $21k/day (0.3%) |
| Week 1 | $2.4k/day (1.7%) | $29.5k/day (1.6%) | $275.3k/day (1.0%) |
| Week 2 | $8.4k/day (2.9%) | $92.8k/day (1.8%) | $435k/day (0.4%) |
| Month 1 | $45.6k/day (5.3%) | $435k/day (4.2%) | $6.3M/day (1.9%) |
| Month 2 | $183k/day (9.6%) | $960k/day (1.1%) | $6.3M/day (0.3%) |
| Month 3 | $183k/day (5.6%) | $6.3M/day (5.3%) | $165.3M/day (6.5%) |
<!-- END table:interest_vs_income -->

Interest stays a bonus (≤ 8% of season income for every archetype since BANK2). It is relatively highest for casual players because they save more and earn less; in money it is small. If the M3.8 dashboard shows interest above 8% of created money, lower the payout caps first (`bank.yml → personal.tiers` / `island.levels`, third/second number), then the rates.

---

## 7. Money sources vs. sinks (inflation check)

Every sink is a gameplay cost (tools, sets, spawners, upgrades, outposts, mastery, bank tiers, shop blocks, AH fee/tax); there are no pure money burns. All 900 daily players, averaged over the days ending on that day. "Money supply" = money held (cash + savings + banks) at the end of the day.

### 7.1 Created and removed per day, by reason
<!-- BEGIN table:sources_sinks -->
| Day (7-day avg) | Depths | Island | Spawners | Events | Interest | Forge | Production | Spawners bought | Sets | Level upg. | Late proj. | Mastery | Bank tiers | Shop | AH fee+tax |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | $199.1M | $36.7M | $4.7M | $59.7M | $775.8k | $16M | $57.9M | $0 | $7.2M | $153.8M | $0 | $0 | $7.2M | $12M | $1.8M |
| 3 | $309.4M | $81.1M | $46.6M | $92.8M | $2M | $38.2M | $66.8M | $72.9M | $6M | $248.5M | $0 | $0 | $12.3M | $21.2M | $3.2M |
| 7 | $748.8M | $204.2M | $243.1M | $224.6M | $10.2M | $72.9M | $90.3M | $124.2M | $2.6M | $406.4M | $103.8M | $0 | $39.9M | $56.8M | $8.5M |
| 14 | $3.3B | $892.3M | $1.6B | $994M | $44.7M | $141.8M | $178.4M | $404.4M | $514.3M | $2.7B | $2B | $0 | $153M | $272.3M | $40.8M |
| 30 | $16.6B | $4.3B | $4.5B | $5B | $504M | $35.3M | $7.9M | $0 | $0 | $399.8M | $8.3B | $0 | $2.8B | $1.2B | $181.9M |
| 45 | $44.6B | $18.1B | $8.2B | $13.4B | $747.8M | $82.9M | $13.4M | $0 | $0 | $3.4B | $61.2B | $0 | $0 | $3.4B | $505.5M |
| 60 | $108.4B | $71.1B | $11.4B | $32.5B | $925M | $0 | $9.9M | $0 | $0 | $80.4B | $4.1B | $108.5B | $0 | $8.9B | $1.3B |
| 75 | $112.5B | $73.6B | $14.9B | $33.7B | $2B | $0 | $0 | $0 | $0 | $950.7M | $17B | $145.5B | $0 | $9.4B | $1.4B |
| 90 | $116.8B | $74.3B | $18.1B | $35B | $11.6B | $0 | $0 | $0 | $0 | $388.9M | $453.4M | $180.4B | $0 | $9.8B | $1.5B |
<!-- END table:sources_sinks -->

### 7.2 Inflation check
<!-- BEGIN table:inflation -->
| Day (7-day avg) | Created / day | Removed / day | Sink ratio (7 days) | Sink ratio (14 days) | Net / day | Money supply (held) | Held per player | Held per player in days of regular income | Check (14 days) |
|---|---|---|---|---|---|---|---|---|---|
| 1 | $301.1M | $255.9M | 85% | 85% | $45.2M | $46.1M | $51.2k | 0.2 | OK |
| 3 | $531.9M | $469M | 88% | 88% | $62.9M | $189.5M | $210.6k | 0.3 | OK |
| 7 | $1.4B | $905.4M | 63% | 63% | $525.6M | $3.7B | $4.1M | 2.3 | watch |
| 14 | $6.9B | $6.4B | 93% | 88% | $495.8M | $7.2B | $7.9M | 1.6 | OK |
| 30 | $30.8B | $13B | 42% | 60% | $17.8B | $159.4B | $177.2M | 17.3 | watch |
| 45 | $85B | $68.6B | 81% | 83% | $16.4B | $264.6B | $294M | 8.0 | OK |
| 60 | $224.3B | $203.3B | 91% | 95% | $21B | $460.7B | $511.8M | 5.9 | OK |
| 75 | $236.6B | $174.2B | 74% | 77% | $62.4B | $1.3T | $1.4B | 14.7 | OK |
| 90 | $255.9B | $192.5B | 75% | 71% | $63.4B | $2.1T | $2.3B | 19.3 | watch |
<!-- END table:inflation -->

- **Target:** ≥ 80% of created money removed over the season. **Watch** at 60–75%, **act** below 60% for 3 days in a row (14-day average). The dashboard (M3.8) should use the **14-day** average: the 7-day average dips whenever many players save for the same big purchase (day 7: grinders saving for Coinmaster; day 28–30: grinders who have bought everything tier 10 allows and wait for tier 11).
- **TUNE:** the enchants make grinders earn more per tier, so they run out of tier-10 purchases a day earlier than in BAL2: the 14-day average touches 57% on day 30 (BAL2: 61% on day 29) for **one** day, then jumps back to 85%+ when tier 11 opens. That is a "watch", not an "act" (the check uses the 3-days-in-a-row rule since TUNE). If the dashboard shows it for longer, the lever is a tier-10 sink (e.g. the third outpost at tier 10 instead of 11, §5.3).
- **Held per player in days of regular income** drives AH prices. **Watch above 10:** it touches ~14–18 around days 30 and 75 when grinders hold billions between tiers and mastery levels. That is fine for a seasonal wipe while server prices stay fixed; if AH prices run away, raise mastery prices first.
- Server-set prices do not inflate: sell values, Forge, sets and upgrade costs are fixed numbers. Inflation only shows on the AH and in `/trade`.

### 7.3 Sensitivity (what if we're wrong?)
<!-- BEGIN table:sensitivity -->
| Scenario | Sink ratio, season | Lowest 7-day sink ratio, days 60-90 | Money supply day 90 | Interest share (grinder) | Island total share (regular) | Spawner share (regular) | Grinder earned by month 2 |
|---|---|---|---|---|---|---|---|
| Baseline (this sheet) | 82% | 74% | $2.1T | 1.4% | 33% | 18% | $41.6B |
| Spawners at today's config (4 mobs per spawn) | 71% | 70% | $5T | 2.6% | 75% | 69% | $59.9B |
| Shop spawner price does not rise per owned spawner | 59% | 40% | $15.6T | 3.5% | 83% | 81% | $117.8B |
| No mastery (no endless sink) | 32% | 5% | $7.9T | 4.6% | 33% | 18% | $41.6B |
| Boosters always on (personal + global) | 78% | 78% | $3.7T | 2.2% | 42% | 25% | $61.7B |
| Old cap +100% | 81% | 74% | $1.7T | 0.9% | 32% | 17% | $34.6B |
| Custom tiers x2.4 (old curve) | 80% | 42% | $3.3T | 2.0% | 33% | 18% | $57.5B |
| Players save twice as much | 75% | 73% | $2.9T | 3.6% | 32% | 17% | $41.8B |
| Interest without payout caps | 75% | 64% | $3.1T | 9.4% | 30% | 16% | $43.2B |
| Production payback 8 days (players over-invest) | 83% | 74% | $2.5T | 2.0% | 48% | 26% | $52B |
| No enchants (TUNE: pickaxe build off) | 84% | 62% | $832.2B | 0.8% | 45% | 27% | $15.5B |
| No sets (TUNE: Delver, Deepforge, Coinmaster off) | 86% | 77% | $1.2T | 0.6% | 31% | 18% | $31.1B |
| Money build: Gilded Touch instead of Scholar's Insight | 87% | 14% | $1.3T | 0.9% | 33% | 19% | $27.6B |
<!-- END table:sensitivity -->

What this says:
- **Spawners are the biggest risk.** At today's 4 mobs per spawn they become ~69% of a regular's income and money supply more than doubles. Without a rising shop price they compound: 87% of income and 7× the money supply.
- **Mastery (the endless late sink) is still load-bearing.** Without it the season sink ratio falls to ~39%.
- **Boosters always on** (personal + global): month-2 income of grinders +48%, island share 42%. Still under control because the cap holds. Global booster uptime is the lever.
- **The cap itself barely matters at today's values** (old +100% cap: grinders earn ~18% less by month 2). The sources decide, not the cap.
- **The old ×2.4 custom curve** puts grinders at $58B by month 2 (1.2× target) with the enchants and sets in. ×2.2 keeps them at 0.85×; both are inside the target band.
- **TUNE: enchants are worth ~2.8× by month 2 for a grinder** ($15B without them vs $42B), mostly through faster tiers (Swiftness, Scholar's Insight) and +68% items late (Prosperity, Vein Rend). **Sets** are worth ~+40% by month 2 (mostly Coinmaster).
- **TUNE: a pure money build** (Gilded Touch instead of Scholar's Insight) earns less ($28B by month 2) and leaves the grinder stuck at tier 11 with nothing to buy (lowest 7-day sink 13%): XP enchants matter as much as money enchants, and the guide/tooltips should say so.

### 7.4 Levers (if the M3.8 dashboard disagrees with this sheet)
| Symptom | First lever | Second lever |
|---|---|---|
| Sink ratio (14 days) < 60% for 3 days | Raise mastery/late-project prices (hours of REF) | Lower custom-tier sell prices (P for tiers 9–12) |
| Income per hour > 2× the table | Check dupes/anomalies first; then lower booster values or global booster uptime | Lower the sell cap |
| Island share > 45% | Raise production level costs (`CP1`/`CPG`) | Shorter offline fuel tank |
| Spawner share > 25% | Lower `spawners.mobs-per-spawner` | Raise the shop spawner price growth |
| Interest > 8% of income | Lower payout caps | Lower rates |
| AH prices running away | Raise AH tax for listings above X | Add more late sinks |
| Crystals piling up | More island shop items (fuel, boosters, decor) | Raise roll prices |

### 7.5 AFK pool (O10, RW3; modules/afk)
Standing in the pool at spawn pays every 15 min: a little money, island fuel (1:1 with the AFK time, so it feeds the production hours of §1.1 instead of adding new ones), keys at 1 h / 3 h / 6 h / 8 h (Ember, Ember, Cinder, Blaze) and a 2% chance per reward (after 1 h in the session) of a banked +50% / 10 min personal sell booster (counts toward the +300% cap, §2.7). Max 8 h per account per day, one earning account per IP and 8 h per IP per day (R3, R5).

**Money is scaled to the stage**, otherwise it is either useless late or a printer early: `afk.yml → money.per-reward-by-tier` = 2.5% of `REF(t)` per hour (÷ 4 rewards), tier from the player's best mining/foraging/combat level (the Depths unlock levels). The session step grows 100% → 180% (6 h), so the **top step pays 4.5% of REF**. Against what players really earn per active hour:

<!-- BEGIN table:afk -->
| Stage | Regular tier | Regular income (active) | AFK money at 100% | AFK money at the top step | Top step / regular income | Casual | Grinder |
|---|---|---|---|---|---|---|---|
| First hour | T1 | $60.4k/h | $1.2k/h | $2.2k/h | 3.7% | 4.1% | 3.0% |
| Day 1 | T2 | $107.9k/h | $2.2k/h | $4k/h | 3.7% | 3.8% | 3.4% |
| Day 3 | T4 | $308.6k/h | $6.8k/h | $12.2k/h | 3.9% | 4.8% | 3.4% |
| Week 1 | T5 | $610.8k/h | $11.9k/h | $21.4k/h | 3.5% | 4.8% | 3.4% |
| Week 2 | T7 | $1.6M/h | $36.5k/h | $65.7k/h | 4.0% | 4.2% | 2.4% |
| Month 1 | T8 | $3.3M/h | $64k/h | $115.2k/h | 3.5% | 4.6% | 2.3% |
| Month 2 | T10 | $28.5M/h | $524k/h | $943.2k/h | 3.3% | 3.8% | 2.7% |
| Month 3 | T10 | $37.3M/h | $524k/h | $943.2k/h | 2.5% | 3.7% | 2.6% |
<!-- END table:afk -->

- Never above 5% of an active hour for any archetype (check in §11). A full 8 h AFK day at the top step is worth about 1–1.5 active hours of a regular player, less than one Forge step; it never replaces playing.
- The AFK money is **not** added to the simulated income above (≤ 5% per AFK hour, and only for the hours actually spent in the pool); the pool's real effect on the economy is the island fuel, which §1.1 already counts in the production hours. The dashboard shows it as its own source (`afk_pool`).
- Levers: `money.per-reward-by-tier` (all tiers at once: scale the list), `money.session-steps`, `keys.milestones`, `booster.chance-percent`, `time.daily-cap-minutes`.

---

## 8. Dynamic sell prices (M3.4)

Each item has a multiplier `m` (floor 0.40, ceiling 1.50). Every item sold lowers `m` by `impact`; every 10 minutes the gap to base closes by 2%: `m ← 1 − (1 − m) × 0.98` (half-life ~5.7 h). The price settles where `1 − m = impact × volume / 0.1212 per hour`; the model assumes ~0.85 at normal volume. The plugin should **recompute `impact` hourly from the rolling 24 h volume** so the same table works at 30 or 300 players:

<!-- BEGIN table:dynamic -->
| Tier | Ore | Drops sold per player per played h | Impact per item, 10 players on tier | 25 players | 50 players |
|---|---|---|---|---|---|
| 1 | Coal | 1,909 | 0.95 ppm | 0.38 ppm | 0.19 ppm |
| 2 | Copper | 2,361 | 0.77 ppm | 0.31 ppm | 0.15 ppm |
| 3 | Iron | 2,901 | 0.63 ppm | 0.25 ppm | 0.13 ppm |
| 4 | Gold | 3,342 | 0.54 ppm | 0.22 ppm | 0.11 ppm |
| 5 | Redstone | 3,826 | 0.48 ppm | 0.19 ppm | 0.10 ppm |
| 6 | Lapis | 4,511 | 0.40 ppm | 0.16 ppm | 0.08 ppm |
| 7 | Diamond | 5,122 | 0.35 ppm | 0.14 ppm | 0.07 ppm |
| 8 | Emerald | 5,529 | 0.33 ppm | 0.13 ppm | 0.07 ppm |
| 9 | Amethyst | 6,490 | 0.28 ppm | 0.11 ppm | 0.06 ppm |
| 10 | Quartz | 7,270 | 0.25 ppm | 0.10 ppm | 0.05 ppm |
| 11 | Netherite | 8,312 | 0.22 ppm | 0.09 ppm | 0.04 ppm |
| 12 | Voidstone | 9,019 | 0.20 ppm | 0.08 ppm | 0.04 ppm |
<!-- END table:dynamic -->

(ppm = parts per million of base price per item sold.) Items nobody sells drift up towards the ceiling (×1.10 after 24 h without sales, capped at ×1.50). The daily **Hot Item** gets +25% for 24 h (proposal); it changes the price, not the sell multiplier family. **The sell multiplier (§2.7) applies after the dynamic price**, so the cap never interacts with dynamic prices.

---

## 9. Crystals, gems, shards, enchants and sets

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
| First hour | 94/h · 300 h per Rare V enchant | 118/h · 240 h per Rare V enchant | 143/h · 198 h per Rare V enchant |
| Day 1 | 101/h · 281 h per Rare V enchant | 188/h · 150 h per Rare V enchant | 474/h · 60 h per Rare V enchant |
| Day 3 | 146/h · 194 h per Rare V enchant | 412/h · 69 h per Rare V enchant | 1,024/h · 28 h per Rare V enchant |
| Week 1 | 227/h · 124 h per Rare V enchant | 644/h · 44 h per Rare V enchant | 2,120/h · 13 h per Rare V enchant |
| Week 2 | 337/h · 84 h per Rare V enchant | 1,303/h · 22 h per Rare V enchant | 3,141/h · 9 h per Rare V enchant |
| Month 1 | 684/h · 41 h per Rare V enchant | 2,066/h · 14 h per Rare V enchant | 5,328/h · 5 h per Rare V enchant |
| Month 2 | 936/h · 30 h per Rare V enchant | 3,850/h · 7 h per Rare V enchant | 9,927/h · 3 h per Rare V enchant |
| Month 3 | 1,533/h · 18 h per Rare V enchant | 4,701/h · 6 h per Rare V enchant | 9,927/h · 3 h per Rare V enchant |
<!-- END table:crystals -->

**Where crystals go** (the model spends on enchants first, up to what the tool level and gear allow, saves for the crystal set at tier 6, then rolls and the island shop):
<!-- BEGIN table:crystal_balance -->
| Archetype | Up to | Crystals earned | Enchants | Crystal sets | Rolls | Island shop | Still held | Held share |
|---|---|---|---|---|---|---|---|---|
| casual | Week 1 | 1,051 | 44% | 0% | 0% | 44% | 122 | 12% |
| casual | Month 1 | 10,655 | 69% | 0% | 0% | 16% | 1,618 | 15% |
| casual | Month 3 | 77,225 | 65% | 32% | 0% | 2% | 0 | 0% |
| regular | Week 1 | 8,721 | 90% | 0% | 0% | 8% | 146 | 2% |
| regular | Month 1 | 115,990 | 78% | 22% | 0% | 1% | 0 | 0% |
| regular | Month 3 | 765,191 | 69% | 3% | 15% | 12% | 9,153 | 1% |
| grinder | Week 1 | 61,383 | 58% | 41% | 0% | 1% | 0 | 0% |
| grinder | Month 1 | 740,669 | 71% | 3% | 12% | 10% | 30,342 | 4% |
| grinder | Month 3 | 5.1M | 11% | 0% | 51% | 37% | 51,627 | 1% |
<!-- END table:crystal_balance -->

- Casual and regular players spend almost everything on enchants and one crystal set: **crystals are scarce for them**, which is the intended feel (choices matter).
- Grinders out-earn the enchant demand from month 2: half their crystals go to rolls and a third to the island shop. **The island shop is the crystal sink that must scale** (fuel, personal sell boosters, decor). If it has too few items, grinders sit on crystals and buy every book from rolls.
- Crystal income is ~20% below what ENCHANTS §12 and WARDROBE §2.4 assumed for an "active" (4 h/day) player, because the regular plays 3 h/day. Deepforge (◆25,000) lands around day 15 for a regular and day 5 for a grinder; the price can stay.
- **TUNE:** Deepforge's 4-piece bonus (+16% → +32% crystals) and Vein Rend's area blocks (crystals at 50% yield) now raise crystal income: +25–68% for regular and grinder from month 1 (§9.6). The enchant demand also follows the real costs of the build (§9.10), so the shares above are the built rules, not a stand-in.

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

### 9.5 Enchants on the pickaxe (E4)
The model's players fill their perk slots (tool Lv 5 / 15 / 30 / 45; the prestige slot only takes a Mythic, which the model never assumes) in this order: **Swiftness** (speed), **Scholar's Insight** (skill XP), **Prosperity** (extra drops), **Vein Rend** (area, mine tier 5+), then **Gilded Touch** (sell). A slot holds the first enchant whose book the median player has found (books from drops, first finds and rolls, split by rarity and pool as in `enchants.yml`) and whose minimum tier is reached. Levels: the pickaxe gets 1 / gear factor of the enchant crystals (the rest goes to the other tools and armor), split so every enchant reaches the same share of the level the tool-level gate allows (`ceil(max × tool level / 50)`). Effects come from `enchants.yml`: Swiftness adds to the tool's speed bonus, Scholar's Insight multiplies skill XP, Prosperity adds to the extra drops family (tool tier fortune stays outside the cap), Vein Rend adds `chance × count × 50% yield` items and crystals per block (no skill XP, as built). Books and levels never drop the level of an enchant once bought.

<!-- BEGIN table:enchant_build -->
| Stage | Casual: pickaxe enchants (slots) · speed / extra drops / area family | Regular | Grinder |
|---|---|---|---|
| First hour | - (0 slots) · speed +0% · drops +0% · area +0% | - (0 slots) · speed +0% · drops +0% · area +0% | Swi I (1 slots) · speed +6% · drops +0% · area +0% |
| Day 1 | Swi I (1 slots) · speed +6% · drops +0% · area +0% | Swi I (1 slots) · speed +8% · drops +0% · area +0% | Swi II · Sch II (2 slots) · speed +16% · drops +0% · area +0% |
| Day 3 | Swi I (1 slots) · speed +7% · drops +0% · area +0% | Swi II · Sch II (2 slots) · speed +16% · drops +0% · area +0% | Swi III · Sch III (2 slots) · speed +24% · drops +0% · area +0% |
| Week 1 | Swi II (1 slots) · speed +15% · drops +0% · area +0% | Swi III · Sch III (2 slots) · speed +24% · drops +0% · area +0% | Swi III · Sch III · Pro III (3 slots) · speed +18% · drops +27% · area +0% |
| Week 2 | Swi II · Sch II (2 slots) · speed +16% · drops +0% · area +0% | Swi III · Sch III · Pro II (3 slots) · speed +25% · drops +8% · area +0% | Swi IV · Sch IV · Pro IV · Vein II (4 slots) · speed +24% · drops +16% · area +24% |
| Month 1 | Swi II · Sch II (2 slots) · speed +18% · drops +0% · area +0% | Swi IV · Sch IV · Pro IV (3 slots) · speed +24% · drops +35% · area +0% | Swi V · Sch V · Pro V · Vein III (4 slots) · speed +30% · drops +20% · area +48% |
| Month 2 | Swi II · Sch II · Pro II (3 slots) · speed +12% · drops +14% · area +0% | Swi IV · Sch IV · Pro IV · Vein II (4 slots) · speed +24% · drops +16% · area +24% | Swi V · Sch V · Pro V · Vein III (4 slots) · speed +30% · drops +20% · area +48% |
| Month 3 | Swi III · Sch III · Pro III (3 slots) · speed +18% · drops +28% · area +0% | Swi V · Sch V · Pro V · Vein III (4 slots) · speed +30% · drops +20% · area +48% | Swi V · Sch V · Pro V · Vein III (4 slots) · speed +30% · drops +20% · area +48% |
<!-- END table:enchant_build -->

- Day 1: one level-I enchant (Swiftness from the 100-action first find); week 1–2: two to three slots at II–III for regulars; Vein Rend arrives with the 4th slot (regular day ~40, grinder day ~14); the first enchant is maxed on day 75 (regular) / day 26 (grinder), the whole build by month 3 / month 1. That matches the "what it feels like" line in ENCHANTS §12.2, a little slower for the 3 h/day regular than for the plan's 4 h/day "active" player.
- **Why Scholar's Insight is in the build:** BAL2's +12% per slot sped up both XP and money. As built, only Swiftness speeds up XP; Prosperity and area blocks add items but no skill XP. With one XP enchant the regular's tier gaps are within ±10% of BAL2 at every tier (§2.6); without it, tiers 10–12 come 10+ days later (§7.3).

### 9.6 Sets (W4): levels and what they add
The model's players buy **Delver's Garb** ($20k) in their first 2 hours, **Deepforge Plate** (◆25,000) at mine tier 6 and **Coinmaster Silks** ($40M) at tier 8, and always wear the newest one with 4 pieces (100% set XP). Mining sets earn 1 set XP per own ore block (area blocks give none, as built); Coinmaster earns 1 per item sold (island items 0.5), at most 8,000 per hour. A bonus is `sets.yml value × level × 2 / 50`. Merging is not in the baseline (it resets to level 0; see the merge column below).

<!-- BEGIN table:set_levels -->
| Stage | Casual: set worn (4 pieces) · level · bonus now | Regular | Grinder |
|---|---|---|---|
| First hour | - | - | - |
| Day 1 | - | Delver Lv 12 (speed +2%, crystals +4%) | Delver Lv 27 (speed +4%, crystals +9%) |
| Day 3 | Delver Lv 8 (speed +1%, crystals +3%) | Delver Lv 25 (speed +4%, crystals +8%) | Delver Lv 39 (speed +6%, crystals +12%) |
| Week 1 | Delver Lv 19 (speed +3%, crystals +6%) | Delver Lv 35 (speed +6%, crystals +11%) | Deepforge Lv 37 (drops +15%, crystals +24%) |
| Week 2 | Delver Lv 27 (speed +4%, crystals +9%) | Delver Lv 44 (speed +7%, crystals +14%) | Coinmaster Lv 50 (sell +50%) |
| Month 1 | Delver Lv 37 (speed +6%, crystals +12%) | Deepforge Lv 47 (drops +19%, crystals +30%) | Coinmaster Lv 50 (sell +50%) |
| Month 2 | Deepforge Lv 16 (drops +6%, crystals +10%) | Coinmaster Lv 50 (sell +50%) | Coinmaster Lv 50 (sell +50%) |
| Month 3 | Deepforge Lv 41 (drops +16%, crystals +26%) | Coinmaster Lv 50 (sell +50%) | Coinmaster Lv 50 (sell +50%) |
<!-- END table:set_levels -->

**Hours from level 0 at each archetype's real XP rate** (WARDROBE §3.2 planned ~6 h to 1× and ~65 h to 2× for an active player):
<!-- BEGIN table:set_xp -->
| Set level | Multiplier | Total set XP | Mining set, casual (2,134 XP/h in week 1) | Regular (3,437 XP/h) | Grinder (4,702 XP/h) | Coinmaster at the cap (8,000 XP/h) | Merged Deepforge · Coinmaster (regular mining + sell cap) |
|---|---|---|---|---|---|---|---|
| 1 | 0.04x | 200 | 6 min | 3 min | 3 min | 2 min | 2 min |
| 5 | 0.20x | 1,221 | 34 min | 21 min | 16 min | 9 min | 10 min |
| 10 | 0.40x | 3,187 | 1.5 h | 56 min | 41 min | 24 min | 25 min |
| 25 | 1.00x | 19,669 | 9.2 h | 5.7 h | 4.2 h | 2.5 h | 2.6 h |
| 40 | 1.60x | 88,519 | 41.5 h | 25.8 h | 18.8 h | 11.1 h | 11.6 h |
| 50 | 2.00x | 232,782 | 109.1 h | 67.7 h | 49.5 h | 29.1 h | 30.5 h |
<!-- END table:set_xp -->

**What enchants and the worn set add to income per played hour** (same state, with the build or the set taken off; the last two numbers are crystals per hour):
<!-- BEGIN table:enchant_set_impact -->
| Stage | Casual: income per played h · what the enchants and the worn set add to it · crystals per h added by enchants / set | Regular | Grinder |
|---|---|---|---|
| First hour | $53.8k/h · enchants +0% · set +0% · crystals +0% / +0% | $60.4k/h · enchants +0% · set +0% · crystals +0% / +0% | $75.5k/h · enchants +5% · set +0% · crystals +6% / +0% |
| Day 1 | $58.9k/h · enchants +4% · set +0% · crystals +6% / +0% | $107.9k/h · enchants +5% · set +1% · crystals +5% / +5% | $360.4k/h · enchants +8% · set +3% · crystals +10% / +11% |
| Day 3 | $82.5k/h · enchants +4% · set +1% · crystals +5% / +3% | $308.6k/h · enchants +8% · set +2% · crystals +10% / +10% | $1.1M/h · enchants +9% · set +3% · crystals +13% / +15% |
| Week 1 | $142.6k/h · enchants +8% · set +2% · crystals +10% / +8% | $610.8k/h · enchants +9% · set +3% · crystals +14% / +14% | $3.4M/h · enchants +14% · set +7% · crystals +13% / +20% |
| Week 2 | $290.3k/h · enchants +6% · set +2% · crystals +10% / +11% | $1.6M/h · enchants +13% · set +3% · crystals +13% / +17% | $13.8M/h · enchants +33% · set +29% · crystals +40% / +0% |
| Month 1 | $822k/h · enchants +5% · set +2% · crystals +9% / +15% | $3.3M/h · enchants +17% · set +8% · crystals +17% / +25% | $41M/h · enchants +57% · set +28% · crystals +68% / +0% |
| Month 2 | $1.7M/h · enchants +7% · set +2% · crystals +9% / +9% | $28.5M/h · enchants +33% · set +30% · crystals +39% / +0% | $283.9M/h · enchants +46% · set +26% · crystals +68% / +0% |
| Month 3 | $3.1M/h · enchants +9% · set +5% · crystals +13% / +22% | $37.3M/h · enchants +51% · set +29% · crystals +68% / +0% | $296.1M/h · enchants +44% · set +25% · crystals +68% / +0% |
<!-- END table:enchant_set_impact -->

- **Early (week 1):** enchants +4–14%, set +1–7%. The first week is about tiers and the Forge, as intended (#61 fast start comes from tiers, not gear).
- **Late (month 2–3):** enchants +33–58% and the set +25–30% for regular and grinder: Vein Rend III (+48% items), Prosperity V (+20%) and Coinmaster Lv 50 (+50% sell). Together ×1.7–2.0 on top of a bare player at the same tier. BAL2's stand-in gave ~×1.5 plus Gilded and Coinmaster, so the late season is a little richer and the sink checks still pass.
- **The Deepforge → Coinmaster switch is a "down":** Deepforge (extra drops +20%, crystals +32% at Lv 50) goes off when Coinmaster is put on at level 0; Coinmaster reaches 1× in ~2.5 h and 2× in ~29 h at the 8,000 XP/h cap, so the dip lasts a few sessions. Deepforge is at Lv 38–48 by then, so a Deepforge · Coinmaster merge (◆25,000, both at Lv 50) needs extra Deepforge play; the model leaves merging out.
- Casual players stay on Delver's Garb for most of the season (+3–6% speed, +6–12% crystals) and buy Deepforge in month 2; Coinmaster is out of reach for them this season.

### 9.7 Book drops vs. pity (EN5, EN7)
Books per played hour for the regular at the first and last tier of each band. Actions = own ore blocks plus area blocks at their yield (as built), or Depths kills. Pity guarantees a book after K actions without one; with K = 2.5 × N it ends ~8% of gaps and adds ~9% books. First finds (a Common at the 100th, an Uncommon at the 1,000th action, once per account) come on top.

<!-- BEGIN table:book_rates -->
| Activity · band | Chance per action | Pity after | Gaps that end in pity | Pity adds | Actions per played h (regular, blocks incl. area at yield / kills) | Books per played h | vs target 1-1.4 |
|---|---|---|---|---|---|---|---|
| Mine A (T1-3) | 1 in 2,000 | 5,000 | 8% | +9% | 1,909-2,637 | 1.04-1.44 | ok |
| Mine B (T4-6) | 1 in 3,000 | 7,500 | 8% | +9% | 2,906-3,609 | 1.06-1.31 | ok |
| Mine C (T7-9) | 1 in 4,000 | 10,000 | 8% | +9% | 3,940-4,635 | 1.07-1.26 | ok |
| Mine D (T10-12) | 1 in 5,000 | 12,500 | 8% | +9% | 6,217-8,611 | 1.35-1.88 | watch |
| Combat (before TUNE) A (T1-3) | 1 in 150 | 375 | 8% | +9% | 368-397 | 2.67-2.88 | **off** |
| Combat (before TUNE) B (T4-6) | 1 in 200 | 500 | 8% | +9% | 412-441 | 2.24-2.40 | **off** |
| Combat (before TUNE) C (T7-9) | 1 in 250 | 625 | 8% | +9% | 456-485 | 1.98-2.11 | **off** |
| Combat (before TUNE) D (T10) | 1 in 300 | 750 | 8% | +9% | 500 | 1.81 | watch |
| Combat (bundled now) A (T1-3) | 1 in 300 | 750 | 8% | +9% | 368-397 | 1.33-1.44 | ok |
| Combat (bundled now) B (T4-6) | 1 in 350 | 875 | 8% | +9% | 412-441 | 1.28-1.37 | ok |
| Combat (bundled now) C (T7-9) | 1 in 400 | 1,000 | 8% | +9% | 456-485 | 1.24-1.32 | ok |
| Combat (bundled now) D (T10) | 1 in 450 | 1,125 | 8% | +9% | 500 | 1.21 | ok |
<!-- END table:book_rates -->

- **Combat was far off and is changed** (§14.1): the model's regular kills ~370–500 mobs per played hour (Depths mobs are sized for ~2.5 s per kill plus walking), so 1 in 150–300 gave 1.8–2.9 books/h. With 1 in 300/350/400/450 combat lands at 1.2–1.4 books/h like the mine. If the M3.8 dashboard measures more kills per hour, raise the numbers again.
- **Mine band D is a watch:** with Vein Rend II–III a regular breaks ~6,200–8,600 actions per hour at tiers 10–12, so 1 in 5,000 gives 1.35–1.9 books/h. The plan counts area blocks at yield on purpose (an area enchant should feel rewarding), and it only affects the last month, so the default stays; 1 in 6,500 would bring it to 1.0–1.45 (§14.2, open question).
- Farming and fishing pools come with Phase 7 (E6), with the same parity rule.

**Books found by stage** (expected, mining pool; rolls are the Adept and Master rolls the model buys from left-over crystals):
<!-- BEGIN table:book_finds -->
| Stage | Casual: mining books per played h (drops + pity) · expected books found so far by rarity (incl. first finds and rolls) · of them from rolls | Regular | Grinder |
|---|---|---|---|
| First hour | 0.86/h · 3 books (C 2 U 1 R 0.0 E 0.0 L 0.0) · rolls 0 | 1.07/h · 3 books (C 2 U 1 R 0.0 E 0.0 L 0.0) · rolls 0 | 1.30/h · 3 books (C 2 U 1 R 0.0 E 0.0 L 0.0) · rolls 0 |
| Day 1 | 0.91/h · 3 books (C 2 U 1 R 0.1 E 0.0 L 0.0) · rolls 0 | 1.27/h · 7 books (C 4 U 2 R 0.2 E 0.0 L 0.0) · rolls 0 | 1.22/h · 16 books (C 11 U 5 R 0.8 E 0.1 L 0.0) · rolls 0 |
| Day 3 | 1.00/h · 5 books (C 3 U 2 R 0.1 E 0.0 L 0.0) · rolls 0 | 1.06/h · 15 books (C 10 U 4 R 0.6 E 0.0 L 0.0) · rolls 0 | 1.51/h · 38 books (C 20 U 12 R 4.3 E 0.9 L 0.0) · rolls 0 |
| Week 1 | 1.16/h · 9 books (C 6 U 3 R 0.3 E 0.0 L 0.0) · rolls 0 | 1.25/h · 29 books (C 16 U 9 R 2.8 E 0.6 L 0.0) · rolls 0 | 1.28/h · 79 books (C 33 U 26 R 14.3 E 5.1 L 0.9) · rolls 0 |
| Week 2 | 0.87/h · 17 books (C 11 U 5 R 0.9 E 0.1 L 0.0) · rolls 0 | 1.10/h · 55 books (C 26 U 18 R 8.0 E 2.4 L 0.3) · rolls 0 | 1.81/h · 155 books (C 52 U 51 R 34.7 E 14.2 L 3.2) · rolls 0 |
| Month 1 | 1.02/h · 32 books (C 17 U 10 R 3.3 E 0.7 L 0.0) · rolls 0 | 1.20/h · 109 books (C 39 U 36 R 22.6 E 8.9 L 1.9) · rolls 0 | 1.93/h · 397 books (C 85 U 121 R 113.3 E 59.1 L 17.9) · rolls 22 |
| Month 2 | 0.81/h · 59 books (C 27 U 20 R 9.3 E 2.9 L 0.4) · rolls 0 | 1.36/h · 238 books (C 68 U 77 R 59.0 E 27.3 L 7.2) · rolls 0 | 2.13/h · 1061 books (C 145 U 310 R 354.5 E 193.0 L 59.5) · rolls 193 |
| Month 3 | 0.91/h · 86 books (C 34 U 28 R 16.4 E 6.1 L 1.2) · rolls 0 | 1.70/h · 405 books (C 84 U 124 R 117.8 E 60.7 L 18.3) · rolls 29 | 2.13/h · 1752 books (C 206 U 502 R 604.8 E 335.3 L 103.3) · rolls 373 |
<!-- END table:book_finds -->

### 9.8 Bonus family caps and the +300% sell cap
"Max from enchants" = the best enchants of that family on one tool at max level; "max from one set" = the best set at Lv 50 (a merge may not combine two sets of one family, WARDROBE §5.1). Regular and grinder peaks are the model's players (enchants + worn set, without masteries and boosters).

<!-- BEGIN table:family_caps -->
| Family | Cap | Max from enchants on one tool | Max from one set at Lv 50 (a merge can't stack a family) | Enchants + set at max | Regular peak (first day) | Grinder peak (first day) | Cap reached? |
|---|---|---|---|---|---|---|---|
| sell | +300% | +15% (Gilded Touch V) | +50% (Coinmaster Silks) | 22% of the cap | +104% (day 66) | +128% (day 81) | never by enchants + sets |
| speed | +200% | +66% (Swiftness V + Ember Momentum III) | +20% (Stormkeel Scale) | 43% of the cap | +30% (day 75) | +30% (day 26) | never by enchants + sets |
| extra drops | +200% | +20% (Prosperity V) | +24% (Thornward Mantle) | 22% of the cap | +36% (day 32) | +28% (day 8) | never by enchants + sets |
| crystals | +200% | +56% (Crystal Seeker V + Geode Sense IV) | +32% (Deepforge Plate) | 44% of the cap | +31% (day 32) | +26% (day 8) | never by enchants + sets |
| area | +100% | +54% (Dragonfire Blast III) | +0% (-) | 54% of the cap | +48% (day 75) | +48% (day 26) | never by enchants + sets |
| skill xp | +300% | +25% (Scholar's Insight V) | +16% (Tiller's Smock) | 14% of the cap | not modelled | not modelled | never by enchants + sets |
| tool xp | +100% | +40% (Tempering V) | +20% (Lorekeeper Robes) | 60% of the cap | not modelled | not modelled | never by enchants + sets |
| rare finds | +150% | +45% (Treasure Hunter III) | +20% (Netcaster's Garb) | 43% of the cap | not modelled | not modelled | never by enchants + sets |
| mob damage | +200% | +57% (Phoenix Edge V + Giant Slayer IV) | +32% (Dreadguard Mail) | 45% of the cap | not modelled | not modelled | never by enchants + sets |
<!-- END table:family_caps -->

- **No family cap is reached by enchants and sets alone**; the caps are there for masteries, ranks, boosters and events that come later. The closest is **tool XP** (Tempering V + Lorekeeper = 60% of the +100% cap).
- **Sell:** enchants + sets give at most +65% (Gilded Touch V + Coinmaster Lv 50), 22% of the cap. The model's players peak at +104% (regular) / +128% (grinder) without boosters, all sources together; the cap still needs the best of everything (§2.7). **The +300% cap holds.**
- **Speed:** the real limit is the 6 blocks/s reach cap, not the +200% family cap: the best mining speed build is ~3.9 blocks/s (§3.3).
- **Caps that do bite, by design** (not in the income model): health (+10 hearts) is full with 2–4 pieces of Vitality III–V at set level 21–50, and damage reduction (60%) is reached by 4 × Fortitude IV + Dreadguard Lv 50 (64%). Both are meant to stop armor stacking (ENCHANTS §8.1).

### 9.9 Depths combat drops vs. BAL2
Value per kill from the bundled configs (`mobs.yml` drops, average of min–max, × `sell.yml → custom-items.depths-combat`), compared with the mine at the same tier: "bare" = tool tier fortune and the regular's speed (the BAL2 rule), "enchanted" = with the regular's Prosperity and Vein Rend when they unlock that tier.

<!-- BEGIN table:combat_drops -->
| Tier | Zone | Drops per kill (mobs.yml) | Item, sell.yml price | Value per kill (configs) | Parity with the bare mine | Parity with the regular's enchanted mine | Configs / bare / enchanted | Crystals per kill (mobs.yml) | Model crystals (bare mine) | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Graveyard | 4 | `depths_rotten_flesh` $26 | $104 | $104 | $104 | 1.00 / 1.00 | 0.26 | 0.26 | ok |
| 2 | Bone Crypt | 4 | `depths_bone` $44 | $176 | $167 | $167 | 1.06 / 1.06 | 0.42 | 0.40 | ok |
| 3 | Spider Den | 4 | `depths_spider_eye` $66 | $264 | $266 | $266 | 0.99 / 0.99 | 0.61 | 0.62 | ok |
| 4 | Slime Bog | 4 | `depths_slime_ball` $109 | $436 | $400 | $400 | 1.09 / 1.09 | 0.98 | 0.89 | ok |
| 5 | Sunken Ruins | 4 | `depths_prismarine` $162 | $648 | $596 | $596 | 1.09 / 1.09 | 1.40 | 1.28 | ok |
| 6 | Witch Hollow | 4 | `depths_glowstone` $241 | $964 | $917 | $917 | 1.05 / 1.05 | 2.00 | 1.90 | ok |
| 7 | Pillager Outpost | 4 | `depths_raider_scrap` $400 | $1.6k | $1.4k | $1.4k | 1.18 / 1.11 | 3.10 | 2.71 | ok |
| 8 | Blaze Forge | 4 | `depths_blaze_rod` $575 | $2.3k | $1.9k | $2.2k | 1.20 / 1.05 | 4.40 | 3.69 | ok |
| 9 | Wither Keep | 4 | `depths_wither_ash` $1.4k | $5.6k | $4.8k | $6k | 1.16 / 0.93 | 6.40 | 5.46 | ok |
| 10 | Warlord's Citadel | 4 | `depths_warlord_sigil` $3.7k | $14.7k | $11.5k | $15.8k | 1.28 / 0.93 | 9.90 | 7.71 | ok |
<!-- END table:combat_drops -->

- Tiers 1–6 match the bare mine within ±10%. At tiers 7–10 the configs are 16–28% above the bare mine, because BAL2 priced them with the +12%/slot stand-in, but within ±11% of the enchanted mine (7–11% above at tiers 7–8, 7% below at 9–10), and combat has no area enchant. **No price change**: combat stays a fair alternative to a fully enchanted pickaxe, not better than it.
- Crystals per kill follow the same pattern (configs = BAL2 values, 10–28% above the bare mine at tiers 4–10).
- **Buy > sell holds:** combat drops are custom items (`custom:<id>`), never priced as their vanilla material, and `/shop` sells none of them, so no shop item can be bought for less than it sells for. `BuySellInvariantTest` passes with these defaults (`./gradlew build`).

### 9.10 Crystal sinks from enchants and sets
What the enchant and set prices cost in hours of each archetype's crystal income (the shares of crystals spent are in §9.1):

<!-- BEGIN table:crystal_prices -->
| Crystal price | Crystals | Casual, month-1 crystals/h | Regular, week 2 | Regular, month 1 | Grinder, week 2 |
|---|---|---|---|---|---|
| Deepforge Plate (crystal set, mine tier 6) | 25,000 | 37 h | 19 h | 12 h | 8 h |
| Merge fee I+III (e.g. Delver · Coinmaster) | 15,000 | 22 h | 12 h | 7 h | 5 h |
| Merge fee III+III (Deepforge · Coinmaster) | 25,000 | 37 h | 19 h | 12 h | 8 h |
| Pickaxe build to max (4 slots: Swi, Sch, Pro, Vein) | 82,214 | 120 h | 63 h | 40 h | 26 h |
| All gear to max (x6.6 the pickaxe) | 542,612 | 793 h | 416 h | 263 h | 173 h |
| Mythic I (prestige slot) | 79,344 | 116 h | 61 h | 38 h | 25 h |
| Disenchant Scroll | 2,500 | 4 h | 2 h | 1 h | 1 h |
| Adept Roll (tier 4) | 4,000 | 6 h | 3 h | 2 h | 1 h |
| Master Roll (tier 7) | 25,000 | 37 h | 19 h | 12 h | 8 h |
<!-- END table:crystal_prices -->

- The crystal set is ~19 h of a regular's week-2 crystals (about a week of play, saved next to enchants; bought on day 15) and ~37 h of a casual's month-1 crystals (bought day 58). The full pickaxe build costs ~40 h of a regular's month-1 crystals, all gear ~260 h: enchants stay the main crystal sink all season, as ENCHANTS §5 planned.
- Merge fees (◆15,000–25,000) are 7–12 h of a regular's month-1 crystals: a real but small sink, paid once per merged set.
- Grinders reach the end of the enchant demand by month 1; over the season rolls and the island shop take 88% of their crystals (§9.1).

---

## 10. Risks and mitigations

| # | Risk | What could happen | Mitigation |
|---|---|---|---|
| R1 | **Runaway inflation** | Late-season income piles up, AH prices explode | Endless late sink (mastery, §5.4); season sink ratio ≥ 80% and 14-day dashboard alerts (M3.8); levers (§7.4); seasonal wipe; server prices fixed |
| R2 | **Interest abuse** | Deposit before payout; idle alts; snowballing | Min-balance rule; payout caps; activity rule (≥ 2 h in 7 days); withdrawal forfeit + 2% fee (masteries lower it to 0.5%, only by island progress); bank tier unlock costs |
| R3 | **Alt farming** | Alts mine tier 1, claim kits/votes and `/pay` the main | Depths income needs active breaking; starter kits not on the AH; crystals and gems not tradeable; no new-account time limit any more (#40's 30 minutes was replaced by owner answer 131; R3-16 removed the keys, F1 plans a $10k self-earned rule for sending money instead); same-IP transfer alerts; AFK pool rewards one account per IP at a time and 8 h per IP per day (RW3, §7.5) |
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
Generated on every run; all must say PASS (or WATCH with a reason in the text) before this sheet goes to the owner. WATCH = missed by less than 10% of the limit. The script also runs hard asserts (cap math, stack steps 8 → 512, the ENCHANTS §5 cost table, the WARDROBE §3.2 level curve, the level gate, determinism, no player above the cap, family caps, progressively harder tiers) and fails if any breaks.

- **Interest (BANK2):** the limit is ~8% of season income since owner answer 169 asked for higher interest (it was 5%). The casual is highest (~7.7%) because a 1 h/day player saves more than they earn; interest is capped per payout. If it must come down, lower the personal bank payout caps of tiers 3–5 (`bank.yml → personal.tiers`, third number). The model reads the bank rates, period and fee from `BankConfig` and checks the tier lists against its payout-cap formulas.

<!-- BEGIN table:checks -->
| Check | Result | Value |
|---|---|---|
| Depths share 40-60% (regular, season) | PASS | 50% |
| Island + spawner share 25-45% (regular, season) | PASS | 33% |
| Spawners 8-25% of income (regular, season) | PASS | 18% |
| Events share 10-20% (regular, season) | PASS | 15% |
| Interest <= 8% of income (all, season) | PASS | 7.5%, 2.7%, 1.4% |
| bank.yml tiers are sensible steps (UX3) | PASS | 10 tiers + 11 levels, x3-5 / x2-4 steps, rates fall |
| Interest every 8 h, fee steps only go down (BANK2) | PASS | 8 h, fee [2.0, 1.5, 1.0, 0.5] |
| First hour 30k-100k (grinder) | PASS | $66.8k |
| Week 2 within 0.5x-2x of $500M (grinder) | PASS | $530.9M |
| Month 2 within 0.5x-2x of $50B (grinder) | PASS | $41.6B |
| Sink ratio over the whole season >= 80% | PASS | 82% |
| Sink ratio (14-day average) not below 60% for 3 days in a row ('act', §7.2) | PASS | min 60% (day 30), longest run below 60%: 0 days |
| Slot 1 within ~1-2 h (regular) | PASS | 1.1 h (day 1) |
| Slot 4 in week 3-8 (regular) | PASS | 120.3 h (day 40) |
| Max blocks/s under reach cap | PASS | 3.90 <= 6.0 |
| Money fits in a long | PASS | season total, all grinders |
| Tier 12 not before week 4 (grinder) | PASS | 410.0 h (day 52) |
| Fast start: tier 2 within 3 h (all) | PASS | 2.7 h (day 3), 2.2 h (day 1), 1.9 h (day 1) |
| Progressively harder: each tier gap >= previous (regular) | PASS | 2 → 3 → 4 → 5 → 8 → 11 → 18 → 41 → 67 h |
| Sell multiplier never above the cap | PASS | peak x3.53 <= x4.00 |
| Cap only reached with the best of everything | PASS | all sources 305%; drop any one → under cap |
| Crystals: sinks take >= 70% (regular, season) | PASS | 99% |
| Books per played h (regular): no band off the 1-1.4 target (mine + combat, §9.7) | PASS | 7 ok, 1 watch |
| No family cap reached by enchants + sets alone (§9.8) | PASS | 0 families at the cap |
| +300% sell cap: highest sell bonus without boosters stays under 2/3 of the cap | PASS | +128% (grinder, month 3) |
| Mining set Lv 25 (1x) within 4-10 played h of buying it (regular, WARDROBE §3.2: ~6 h) | PASS | 7.6 h (bought at 2.0 h) |
| Coinmaster Lv 50 within 40 played h of buying it (regular) | PASS | 29 h |
| Deepforge (◆25,000) bought in week 2-3 (regular, WARDROBE §2.4) | PASS | 44.8 h (day 15) |
| Combat drop value per kill (sell.yml x mobs.yml) matches the mine (§9.9) | PASS | 10/10 tiers ok |
| /shop sells no Depths combat drop (buy > sell) | PASS | none |
| AFK pool money at the top step <= 5% of a regular player's active income (every stage, O10) | PASS | max 4.0% |
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
9. Bank rules and caps (§6); BANK2: interest every 8 h, 0.6% / 1.0% per payout, caps ×4 (+50% per day), fee 2% → 1.5 / 1.0 / 0.5% by island mastery (§6.0).
10. Sink targets: ≥ 80% per season, 14-day average for the dashboard (§7.2).
11. Dynamic prices with adaptive impact; Hot Item +25% outside the sell family (§8).
12. Crystal sinks: the island shop must scale for grinders (§9.1); shard sources (§9.3); crate money rewards stay small (§9.4).
13. Risk mitigations R1–R12 (§10).
14. **TUNE:** the reference pickaxe build (Swiftness, Scholar's Insight, Prosperity, Vein Rend) and the set path (Delver → Deepforge → Coinmaster) (§9.5–9.6); combat books 1 in 300/350/400/450 with pity 2.5× (changed, §9.7); mine band D books stay at 1 in 5,000 (watch) or go to 1 in 6,500 (§14.2); family caps unchanged (§9.8); combat drop prices unchanged (§9.9).

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
| `enchanter.yml` | `costs.step-base` | 100 | 100 | ok | EN14 |
| `enchanter.yml` | `costs.step-growth` | 1.8 | 1.8 | ok | EN14 |
| `enchanter.yml` | `drops.mining.one-in` | 2000, 3000, 4000, 5000 | 2000, 3000, 4000, 5000 | ok | EN7; band D is 'watch' (§9.7) |
| `enchanter.yml` | `drops.mining.pity` | 5000, 7500, 10000, 12500 | 5000, 7500, 10000, 12500 | ok | EN5: 2.5x the gap |
| `enchanter.yml` | `drops.combat.one-in` | 300, 350, 400, 450 | 300, 350, 400, 450 | ok | TUNE (§9.7) |
| `enchanter.yml` | `drops.combat.pity` | 750, 875, 1000, 1125 | 750, 875, 1000, 1125 | ok | TUNE: 2.5x the gap |
| `enchanter.yml` | `rolls.adept.price` | 4,000 | 4,000 | ok | EN9 |
| `enchanter.yml` | `rolls.master.price` | 25,000 | 25,000 | ok | EN9 |
| `enchanter.yml` | `caps.speed` | 200 | 200 | ok | EN16 (6 blocks/s is the real limit) |
| `enchanter.yml` | `caps.extra-drops` | 200 | 200 | ok | EN16 |
| `enchanter.yml` | `caps.crystals` | 200 | 200 | ok | EN16 |
| `enchanter.yml` | `caps.area` | 100 | 100 | ok | EN16 (max +54%) |
| `wardrobe.yml` | `levels.base-xp` | 200 | 200 | ok | WD14 |
| `wardrobe.yml` | `levels.growth` | 1.1 | 1.1 | ok | WD14 |
| `wardrobe.yml` | `levels.max-multiplier` | 2 | 2 | ok | WD14 |
| `wardrobe.yml` | `merge.fee-per-step` | 5,000 | 5,000 | ok | WD23 |
| `wardrobe.yml` | `xp.actions.depths-ore` | 1 | 1 | ok | WD10 |
| `wardrobe.yml` | `xp.actions.sell-item` | 1 | 1 | ok | WD10 |
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

### 14.1 Changed by TUNE (bundled defaults only)
TUNE changed only the bundled default in `EnchanterConfig` where the model showed a clear problem; live server files are not touched. **A server that already has `plugins/PhoenixSkyblock/enchanter.yml` keeps its old values** until the owner edits those two keys (or deletes them so the new defaults are written).

Checked and **left unchanged** (the model shows no problem): enchant crystal costs and rarity factors, mining book chances for bands A–C, rolls, scroll price, all family caps (none reached in week 1, none reached by enchants + sets at all), set values, set prices, set level curve and XP values, merge fees, and the `sell.yml` combat drop prices.

<!-- BEGIN table:tune_changes -->
| File | Key | Bundled default today | Before TUNE | Suggested | Status | Why (one line) |
|---|---|---|---|---|---|---|
| `enchanter.yml` | `drops.combat.one-in` | 300, 350, 400, 450 | 150, 200, 250, 300 | 300, 350, 400, 450 | applied | a regular kills ~370-500 mobs per played hour, so 1 in 150-300 gave 1.8-2.9 books/h, twice the 1-1.4 target (EN7) |
| `enchanter.yml` | `drops.combat.pity` | 750, 875, 1000, 1125 | 375, 500, 625, 750 | 750, 875, 1000, 1125 | applied | pity stays 2.5x the new 1-in-N gap (EN5), so it fires in ~8% of gaps like the mine |
<!-- END table:tune_changes -->

### 14.2 Still suggested (not applied)
Existing keys first, then keys for systems that are not built yet.

<!-- BEGIN table:config_changes -->
| File | Key | Today | Suggested | Why |
|---|---|---|---|---|
| `stacking.yml` | `spawners.mobs-per-spawner` | 4 | 1 | 4 makes one spawner earn 4x the model |
| `enchanter.yml` | `drops.mining.one-in` (band D) / `drops.mining.pity` (band D) | 5,000 / 12,500 | 6,500 / 16,250 | watch, not applied: with Vein Rend II-III a regular gets 1.35-1.9 books/h at tiers 10-12 (target 1-1.4); the plan counts area blocks at yield on purpose (open question) |
| `shop.yml` (M3.5, new) | `spawners.price-growth-per-owned` | not built | 1.1 | price of a shop spawner rises 10% per spawner of that tier the island owns; without it spawners compound (§4.3) |
| `shop.yml` (M3.5, new) | `spawners.price.tier-1..2` | not built | $117k / $213k | first spawner of that tier |
| `boosters.yml` (new) | `sell.personal-percent` / `sell.global-percent` | not built | 50 / 50 | both count toward the +300% cap (O11) |
| `islands` upgrades (Phase 5) | `upgrades.sell-multiplier.percent-per-level` | not built | 5 | 12 levels = +60% |
| `islands` upgrades (Phase 5) | `upgrades.spawner-stack-limit.steps` | not built | Lv2:32, Lv4:64, Lv6:128, Lv9:256, Lv12:512 | feeds `IslandService#spawnerStackLimit` |
| `masteries` (Phase 5) | `market-contacts.percent-per-level` / `max-level` | not built | 2 / 20 | MA5 |
| `masteries` (Phase 5) | bank-fee mastery levels 1-3 | hook only (BANK2) | 1.5% / 1% / 0.5% | lowers the 2% bank withdrawal fee (owner #169); feeds `IslandService#bankFeeMasteryLevel`, steps in `bank.yml → withdraw.mastery-fee-percent` |
<!-- END table:config_changes -->

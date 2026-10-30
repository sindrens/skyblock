# Phase 5: Progression core

**Goal:** every island has a clear long-term goal. Members raise **5 pillars** by playing, buy **upgrades and masteries** with tasks and island bank money, and climb a **dynamic island level** that shows on **leaderboards** (solo, team, weekly) and in the Hall of Heroes.

Depends on Phases 2–4 (islands, stats, island bank, skills, The Depths). Design: `DESIGN.md` §3.5–3.6, `REVIEW.md` A1/A4/A5/B1, `ECONOMY_BALANCE.md` §4.2, §5.2, §5.4, §6, `plans/RANKS_TITLES_ACHIEVEMENTS.md` §C4.

Owner decisions this plan follows:
- **168** island level is **dynamic** and comes from many things, not only block value. **O3** block worth is at most **20%** of the level.
- **157/167/171** island size **3x3 → 9x9** with normal upgrades; bigger only with **gems or the Black Market**.
- **169** upgrades are paid from the **island bank only**; the 2% withdrawal fee is lowered by masteries (BANK2 hook `IslandService.bankFeeMasteryProvider`).
- **170** the leader can give members an **Upgrades** permission. **172** first size upgrade after ~30–60 min, member slots after a few hours.
- **242** team = **4 + leader**, upgradable (to 8 + leader). **O4** `/is storage` = Island Vault. **O6** the challenge book = the mastery task list. **MA1–MA12** yes, MA13 no. **RK20** ranks give no pillar or island XP.
- **O12** weekly top and weekly competition are different things. **V1** weekly top pays keys. **V2** island value history per season, exportable. **P7** leaderboards use a player's best profile. **412** Hall of Heroes holds the leaderboards.
- **Principles:** no waiting, nothing lost by accident, everything reachable from menus (Bedrock), English only, short lores.

New module: **`modules/progression`** (pillars, island level, tasks, upgrades, masteries, leaderboards), with a small shared API in `common/progression` (`ProgressionService`). It feeds the hooks that already exist in `IslandService` (size, member slots, spawner stack limit, spawner rate, bank capacity/interest, bank fee, Wings, entity limits, fuel) instead of changing the islands module's logic.

---

## How the pieces fit (one job each, REVIEW A2)

| System | Belongs to | Job |
|---|---|---|
| Skill | Profile | Personal perks and Depths tier unlocks; its **base** XP also feeds the pillar |
| Pillar | Island | Island level points; gates masteries; later outposts and biomes (Phase 7) |
| Upgrade | Island | Island numbers (size, slots, bank, production, sell bonus). Tasks + bank money |
| Mastery | Island | Island perks (Timber, Wings, Treasury ...). Pillar level + tasks + bank money |
| Ascension | Island | Endless late sink after everything is maxed (the balance sheet's "mastery") |
| Island level | Island | One number from 8 sources, for leaderboards and goals |

`/is upgrade` and `/is masteries` open the **same menu** (DESIGN §3.5), on different tabs.

---

## M5.1 Pillars
- Five pillars: **Mining, Foraging, Farming, Combat, Conquest**.
- **Pillar XP = the base skill XP members earn** while they are members (Depths, island crops, fishing later). Base means before rank and personal achievement boosts, so a donor never pushes the island (RK20). Island boosts (island achievements "Island XP" +5% cap, balance bonus) apply on top. One bonus family `ISLAND_XP`, cap **+25%**.
- **Coop players and visitors give nothing** (REVIEW A4). XP a member earned **stays** with the island (A5).
- Sources per pillar:

| Pillar | Fed by | Notes |
|---|---|---|
| Mining | Mining skill XP (Depths mine), island generator later | |
| Foraging | Foraging skill XP (Depths forest), island trees | |
| Farming | Farming + Fishing skill XP, island crops | Offline production later gives 25% (DESIGN) |
| Combat | Combat skill XP (Depths combat), spawner mobs killed **by a player** | Fall, lava and stack kills give **0** (anti-AFK farm) |
| Conquest | KOTH, envoys, events, treasure hunts (Phase 9–10) | **Hidden until its first source exists** |

- **Curve:** XP for the next level = `base × 1.12^level` (`pillars.yml`). No cap. The weakest pillar is always the cheapest place to gain levels.
- **Team size is damped by the curve:** 5 members earn 5× the XP, but that is only `ln 5 / ln 1.12` ≈ **14 pillar levels** more. Team and solo islands are on separate boards anyway.
- **Balance bonus:** +2% XP to all pillars for each pillar within 10 levels of the highest one (max +10%, counts in `ISLAND_XP`).
- **Synergy milestones:** all (shown) pillars ≥ 10 / 25 / 40 / 60. Phase 5 gives a toast, island chat line and island crystals; Phase 7 hangs outposts and biomes on the same milestones.
- While Conquest is hidden, "all pillars" means the four shown ones.
- **Pace targets** (tuned in M5.9, regular solo island): pillar 10 ≈ 1–2 h of that activity, 25 ≈ day 2–3, 40 ≈ week 2, 60 ≈ month 2. Grinders reach ~75–80 at the end of the season.
- Pillar levels never go down within a season.

**Data:** `island_pillars` (island_id, pillar, xp, pillar_level, updated_at), `island_pillar_weekly` (island_id, pillar, week_start, xp_gained, levels_gained). XP is buffered in memory and written in batches (like M2.6 stats).
**Config:** `pillars.yml` (curve, sources and multipliers, balance bonus, milestones, `conquest.enabled: false`).
**Permissions:** none new (menu is part of `/is level`).

## M5.2 Island level (dynamic, 8 sources)

**Island Level = floor(total level points ÷ 10).** The menu shows the progress to the next level ("Level 38 · 40% to 39").

Points come from eight sources. Earned sources never go down in a season. Live sources follow the island as it is now (owner 168: dynamic).

| # | Source | Points | Kind | Cap | Share target (mid-season) |
|---|---|---|---|---|---|
| 1 | Pillars | 1 per pillar level | earned | – | ~45% |
| 2 | Upgrades + masteries | per level: 0.5 (task tier 1–3), 1 (4–6), 1.5 (7+); Ascension 1.5 each | earned | – | ~25% |
| 3 | Island achievements | 1 per island achievement point (tiers give 1/2/3/5/8, RANKS §C2) | earned | 10% | ~7% |
| 4 | Collections | 1 per collection milestone (when the collection log exists, Phase 10) | earned | 5% | 0% now |
| 5 | Members' skills | average total skill level of members ÷ 10 | live | 8% | ~5% |
| 6 | Depths progress | best member's unlocked tier per aspect (Mine 12 + Forest 11 + Combat 10 = 33 max), 1 each | live | 6% | ~5% |
| 7 | Island bank | `4 × log10(lowest balance in the last 24 h ÷ $100k)`, min 0 | live | 5% | ~2% |
| 8 | Block worth | `0.5 × √(worth ÷ $1,000)` | live | **20%** (O3) | ≤ 20% |

How the caps work: a capped source can't be more than its share of the **total**. In code: compute the uncapped sources first, then each capped source is limited to `cap ÷ (1 − cap) × (sum of the other sources)`. Caps are applied once, smallest first (pure function, unit-tested).

**Why these rules**
- **Money can't buy the level** (REVIEW A1): upgrades also need tasks; bank uses a log scale and the lowest balance; block worth uses a square root and the 20% cap. A billionaire with an empty island has a low level.
- **Members' skills use the average**, so adding alts lowers it. Only members who have been on the island **≥ 3 days** count (same rule as island achievement titles). A member who leaves takes their profile with them (owner 141), so their skill and Depths points leave too.
- **Depths uses the best member per aspect**: a team that splits mine/forest/combat is rewarded (synergy).
- **Bank** uses the lowest balance of the last 24 h, so a big deposit just before a check does nothing.
- **Coop** players never count anywhere.

**Example: regular solo island, month 1** (91 h played, Depths tier 8; numbers from `ECONOMY_BALANCE.md` §2.4)

| Source | Value | Points | Share |
|---|---|---|---|
| Pillars | Mining 48, Foraging 44, Combat 42, Farming 36 (Conquest hidden) | 170 | 44% |
| Upgrades + masteries | 96 upgrade levels + 10 mastery ranks | 100 | 26% |
| Island achievements | 25 points | 25 | 7% |
| Members' skills | total skill level 190 | 19 | 5% |
| Depths progress | 8 + 7 + 7 | 22 | 6% |
| Bank | lowest $20M | 9 | 2% |
| Block worth | $6M (cap would be 86) | 39 | 10% |
| **Total** | | **384** | **Island level 38** |

Targets: casual end of season ~40, regular end ~70, grinder solo end ~105, active teams 110–140. This fits the island achievement "Rising Island" (10 / 25 / 50 / 75 / 100) and the title Architect at level 50.

**Block worth**
- Only **placed blocks inside the border** count. Items in chests never count.
- Values in `block-values.yml`: default **50% of the `/shop` buy price**; ore blocks (iron block ...) 9× the ingot's sell value × 50%; spawners **50% of the first shop price of their tier**, per spawner in a stack. Cheap utility blocks (hoppers, cobble, dirt, glass) are worth 0 by default. Staff items (owner 332) are worth 0.
- Tracked **live** from place/break/piston/explosion events per chunk; a full async rescan (chunk snapshots) runs nightly and after paste/reset; members get a **Recalculate** button (one rescan per island every 10 min, it is heavy work).
- Paid sizes above 9x9 give no points; more room only helps block worth, which is capped.

**Where the level shows:** `/is level` menu (breakdown per source), island menu header, `/is info`, island scoreboard (the hidden "Level" line appears now, owner 275), `/is visit` browser, placeholders `island_level`, `island_level_progress`, `island_rank` (solo or team board position).

**Level-ups:** toast + sound for online members; island chat line; every 10th level also a global chat line (owner O14: announcements in chat).

**Data:** `island_level` (island_id, season_id, total_points, earned_points, island_level, breakdown JSON, block_worth, updated_at), `island_block_worth` (island_id, chunk_x, chunk_z, worth), `island_level_history` (island_id, season_id, day, island_level, earned_points, block_worth) for V2 (owner export, CSV via staff command in M5.7).
**Config:** `progression.yml` (divisor 10, points per source, caps, 3-day member rule, bank formula, rescan times), `block-values.yml` (catalog list: keep plain `@Setting` keys out of the list section).
**Permissions:** `skyblock.progression.level` (everyone: `/is level`), `skyblock.progression.recalculate` (everyone, members only in game).

## M5.3 Task engine (the challenge book, O6)
- Every upgrade level and mastery rank has **1–3 tasks**. Tasks are in `tasks.yml` per level, never in code.
- **Task types:** break X of a block (Depths ore/log, island crops), kill X of a mob, catch X fish (later), harvest X crops on the island, reach skill level X (any member), reach pillar level X, own X spawners of a tier, earn $X from selling, **deliver** X items, island level X.
- **Progress is shared** by all members (never coop) and counts **from the island's creation**, so earlier play is never wasted (principle 1). Counters are per island and per task target, buffered and written in batches.
- **Deliver tasks:** one click hands in items from your inventory or from the island storage (decided: storage yes). Atomic: items are removed and progress saved in one step, logged.
- **Tier ladder:** tasks for "task tier n" use Depths tier n content and amounts that grow ~1.35× per level, so a level is reachable at about the Depths tier the balance sheet expects. Example: Island Size I = mine 300 Coal Ore, chop 200 Oak Logs, harvest 100 Wheat on your island.
- Members see task progress in the upgrade menu; a finished task gives a short action-bar line and a "Ready to buy" line in island chat.

**Data:** `island_task_progress` (island_id, task_target, amount, updated_at). Targets are shared by every task that reads them (one "coal_ore mined" counter for all levels).
**Config:** `tasks.yml` (catalog list per upgrade/mastery level).

## M5.4 Upgrades (`/is upgrade`)
- Each level needs **its tasks done** + **money from the island bank** (owner 169). One click → confirm screen (cost, bank after, what you get) → bought. Instant, no timers.
- **Payment is atomic** in the database (`UPDATE … WHERE balance >= ?`) with ledger reason `island_upgrade`; level and money change in one transaction. No 2% fee (it is not a withdrawal) and no forfeited interest (default, see questions).
- **Upgrades permission** (owner 170): a new box in `/is permissions`. Leader always; Admin on by default; Mod and Member off. Everyone can view and deliver.
- Prices follow the balance sheet: level at task tier n costs `W × 0.067 × 1.49^(n−1) × REF(n)` (`ECONOMY_BALANCE.md` §5.2); production upgrades use `C_p` (§4.2). All prices live in `upgrades.yml`.
- Bank capacity is a natural gate: late levels cost more than a small bank holds (Bank Capacity Lv 5 = $1B, Lv 6 = $4.1B).

**Upgrade catalog** (money numbers from the balance sheet formulas)

| Tab | Upgrade | Levels | Task tiers | Effect | First → last level | All levels |
|---|---|---|---|---|---|---|
| Island | Island Size | 3 | 1, 3, 5 | 3x3 → 5x5 → 7x7 → **9x9** (`setSize`) | $3.4k → $157k | $183k |
| Island | Member Slots | 4 | 2, 4, 6, 8 | 4 + leader → **8 + leader** (`setMemberSlots`) | $7.0k → $2.2M | $2.6M |
| Island | Warp Slots | 6 | 2, 4, 6, 8, 10, 12 | 3 → 9 warps | $4.4k → $462M | $489M |
| Island | Island Vault | 5 | 1, 3, 5, 7, 9 | storage 1 → 6 rows (I7) | $2.0k → $7.2M | $7.9M |
| Island | Entity Limits | 1 | 5 | +50% entity limits (SK) | $157k | $157k |
| Economy | Bank Capacity | 10 | 1–10 | §6.2 table | $3.7k → $55.9M | $74M |
| Economy | Bank Interest | 10 | 1–10 | §6.2 table | $4.0k → $61M | $81M |
| Economy | Sell Multiplier | 12 | 1–12 | +5% sell per level (+60%) | $6.0k → $1.7B | $2.2B |
| Depths | Mine Fortune | 12 | 1–12 | +2% extra drops in the mine per level | $4.7k → $1.3B | $1.7B |
| Depths | Lumberjack | 12 | 1–12 | +2% double logs per level | $3.0k → $831M | $1.1B |
| Depths | XP Boost | 12 | 1–12 | +2% skill XP for members per level | $3.0k → $831M | $1.1B |
| Production | Spawner Stack Limit | 12 | 1–12 | 8 → 32 (Lv2) → 64 (Lv4) → 128 (Lv6) → 256 (Lv9) → 512 (Lv12) | $3.4k → $924M | $1.2B |
| Production | Spawner Rate | 10 | PL rule | +12% spawner output per level (`spawnerRateMultiplier`) | $2.5k → … | `C_p` |
| Production | Mob Loot | 10 | PL rule | +12% spawner drops per level | `C_p` | |
| Production | Crop Growth | 10 | PL rule | +12% crop growth speed per level | `C_p` | |
| Production | Harvest Yield | 10 | PL rule | +12% double-crop chance step per level | `C_p` | |
| Production | Generator | 10 | PL rule | better generator ore tiers (Phase 7: "Coming soon") | `C_p` | |
| Production | Fuel Tank | 12 | 1–12 | bigger fuel tank (Phase 7: "Coming soon") | $2.7k → $739M | $966M |

- **PL rule:** production level k (all five counted together) needs task tier `ceil(k ÷ 6)` (§4.2). Level n of production upgrade j costs `C_p(5 × (n − 1) + j + 1)`. 10 levels each = PL 50; the model's grinder reaches PL 45.
- **Timing vs owner 172:** all tier-1 levels together cost $36k, a new player earns $40–67k in the first hour. Size I is gated by its tasks: ~30–45 min for a new player. Member Slots I (task tier 2) comes after ~2–4 h. 9x9 arrives around day 2 (grinder) to day 5 (regular), week 3 (casual).
- Later phases add their own upgrades in the same menu: Outpost Slots, Biome Tier, Biome Fusion (Phase 7), KOTH Banner and Envoy Luck (Phase 9). Their slots show "Coming soon".
- Every buy: toast + sound for online members, island chat line, audit `progression/upgrade_buy` (island, upgrade, level, cost, buyer, bank after).

**Data:** `island_upgrades` (island_id, upgrade_id, upgrade_level, bought_by, bought_at). Member slots, size and warp slots stay where the islands module keeps them; progression calls the existing setters.
**Config:** `upgrades.yml` (catalog list: id, tab, icon, levels with task tier, weight or price, effect value).
**Permissions:** `skyblock.progression.upgrade` (everyone: `/is upgrade`, `/is masteries`); island permission key `upgrades` in the role matrix.

## M5.5 Masteries and Ascension (`/is masteries`)
- A mastery rank needs: the **related pillar level** (default `5 × price tier`), its **tasks**, and **island bank money**. Same confirm, payment, permission and audit as upgrades (`progression/mastery_buy`).
- Effects go through `BonusService` families, so the existing family caps still hold. PvE damage never applies in PvP.
- Price: rank at price tier n costs `W × 0.067 × 1.49^(n−1) × REF(n)`. The ranks' last tiers are set so that **upgrades + masteries together cost the same as the balance sheet's 12 × 12 grid ($14.5B)**: the small upgrades (size, slots, vault) lost their late levels, the masteries take that money back. Sink ratio unchanged.

| Mastery | Ranks | Needs | Effect per rank (max) | Price tiers | First → last | All ranks |
|---|---|---|---|---|---|---|
| MA1 Timber | 5 | Foraging pillar | +4% logs (+20%), Depths + island | 2, 4, 7, 10, 12 | $5.3k → $554M | $585M |
| MA2 Swift Hands | 5 | Mining pillar | +4% break speed (+20%), 6 blocks/s cap stays | 2, 4, 7, 10, 12 | $5.3k → $554M | $585M |
| MA3 Warrior | 5 | Combat pillar | +4% PvE damage (+20%) | 2, 4, 7, 10, 12 | $5.3k → $554M | $585M |
| MA4 Deep Pockets | 5 | Mining pillar | +10% satchel size (+50%) | 1, 3, 6, 9, 12 | $1.7k → $462M | $468M |
| MA6 Crystal Rush | 5 | island level 10/20/35/50/70 | +3% crystals (+15%) | 2, 4, 7, 10, 12 | $6.1k → $647M | $683M |
| MA7 Lucky Strike | 5 | island level 15/30/45/60/80 | +4% rare finds: key fragments, blueprints (+20%) | 3, 5, 8, 10, 12 | $16k → $647M | $684M |
| MA8 Fuel Efficiency | 3 | Farming pillar | fuel lasts +10% (+30%); Phase 7 | 5, 9, 12 | $63k → $370M | $374M |
| MA9 Beast Tamer | 4 | Farming pillar | breeding −15% time, +5% animal drops | 2, 5, 8, 12 | $3.5k → $370M | $371M |
| MA10 Angler | 4 | Farming pillar | +5% fishing speed, +4% fishing loot; when fishing exists | 3, 6, 9, 12 | $9.2k → $370M | $374M |
| MA11 Homestead | 2 | island level 20/40 | see questions (owner 247 removed personal homes) | 4, 8 | $30k → $1.4M | $1.4M |
| MA12 Wings | 1 | island level 20 | island fly for everyone on the island (`wingsProvider`; visitors only with the visitor-fly flag, owner 186) | 4 | $60k | $60k |
| **Treasury** (new, owner 169) | 3 | island level 15/35/60 | bank withdrawal fee 2% → 1.5% → 1.0% → 0.5% (`bankFeeMasteryProvider`) | 3, 7, 12 | $23k → $924M | $925M |
| | | | | | **Total** | **$5.6B** |

- **Treasury tasks** fit the bank: "Deposit $X into the island bank in total", "Receive 3 interest payouts". Rank III at task tier 12 is a late goal for islands that move billions.
- **MA5 Market Contacts** is not a normal mastery: as in the balance sheet it opens when **Sell Multiplier is maxed**, +2% sell per level, max 20 levels (+40%), priced on the Ascension curve.
- Masteries whose feature doesn't exist yet (Fuel Efficiency, Angler) show "Coming soon". Homestead waits for the owner's answer.
- **Ascension (endless, the balance sheet's "mastery" sink, §5.4):** when every upgrade and mastery is maxed, the island can buy Ascension levels forever. Each costs `4 × 1.12^(m−1)` hours of REF at the island's best Depths tier, needs tier-12 tasks that grow 1.12× per level, and gives **1.5 level points**. Up to 20 of them can be spent as Market Contacts levels. This keeps money draining in months 2–3 (without it the season sink ratio falls to ~39%).
- Two new QoL mastery ideas for the owner (F-O5, no economy impact): **Hearthfire** (no hunger, slow regeneration on your island) and **Replanter** (fully grown crops replant themselves when harvested on your island). Only added if the owner says yes.

**Data:** `island_masteries` (island_id, mastery_id, mastery_rank, bought_by, bought_at), `island_ascension` (island_id, ascension_levels, market_contacts_levels).
**Config:** `masteries.yml` (catalog list: id, icon, ranks with requirement, price tier, weight, effect), `bank.yml → withdraw.mastery-fee-percent` stays the fee source (already built).
**Hooks filled:** `bankFeeMasteryProvider`, `wingsProvider`, `extraHomesProvider` (if Homestead is approved), satchel capacity, `BonusService` sources `mastery:<id>`.

## M5.6 Island sizes above 9x9 (gems / Black Market)
- **11x11, 13x13, 15x15** (`size.max-chunks` 15) only with **gems** or a **Black Market** item ("Island Charter", shards). One step at a time; 9x9 (normal max) must be reached first.
- Buying asks for a confirm with a warning: **islands are wiped at the season reset, so the size is lost then**.
- Gives **no** island level points (REVIEW A1, owner 171 only allows size, nothing else).
- Phase 5 builds the hook, the menu entry (gem price shown, "Black Market" note) and staff grant; the gem shop and Black Market UI come with Phases 10–11.
- Audit `progression/paid_size` (island, size, source, buyer). Gems are spent atomically through `EconomyService` (gem ledger).

**Data:** `island_paid_size` (island_id, size_chunks, source, bought_by, bought_at).
**Config:** `upgrades.yml → paid-sizes` (gem prices per step, Black Market item id).

## M5.7 Leaderboards (solo, team, weekly; Hall of Heroes)
- **Boards** (cached every 5 min, off the main thread):

| Board | Ranked by | Solo / team | Where |
|---|---|---|---|
| Island top | island level (tie: total points, then who reached it first) | separate | Hall `island_top_solo`, `island_top_team`; `/is top` |
| Weekly island gain | **earned** points gained this week (pillars, upgrades, masteries, achievements) | separate | Hall `weekly_island_gain` (switches Solo ↔ Team every 10 s); `/is top` |
| Weekly pillar gain | pillar levels gained this week | separate | Hall `weekly_pillar_gain` (switches Solo ↔ Team); `/is top` |
| Island value | block worth | separate | `/is top` only (no free Hall panel) |
| Per pillar | pillar level | separate | `/is top` |

- **Weekly boards ignore live sources** (block worth, bank, members), so moving blocks or money on Sunday night does nothing. Week = Monday 00:00 Europe/Oslo (same as island stats).
- **One island per leader account per board** (P7: a player's best profile counts). Staff islands are hidden (like `/baltop`, owner 576).
- **Weekly rewards (V1: keys)**: top 3 solo and top 3 team islands by weekly island gain. Every member who was on the island ≥ 3 days of that week and played ≥ 1 h gets the key. Paid through the crate service when it exists (PLAN10); until then the reward waits in `/box`-style pending rewards. Audit `progression/weekly_reward`.
- **Season end:** a final snapshot (top 10 solo + team) feeds won ranks (RANKS §A4), store-credit prizes (DESIGN §1) and the Hall of Fame statues (Phase 12). Phase 5 only stores the snapshot and the V2 history.
- **NPCHOLO:** new `leaderboards.sources` types: `island_level [all|solo|team]`, `island_gain this_week [solo|team|cycle]`, `pillar_gain this_week [solo|team|cycle]`, `island_value [solo|team]`. The four "Coming soon" boards are switched to them in the bundled defaults. `boss_damage` stays "Coming soon" (Phase 8).
- Rows show `#1 Island name · Lv 104` with the leader's name in the hover/second line; #1/#2/#3 colors as built.
- **V2 export:** staff command writes the season's level/value history as CSV for the owner.

**Data:** `leaderboard_weekly` (week_start, board, place, island_id, value, rewarded), `leaderboard_season` (season_id, board, place, island_id, value, members JSON).
**Config:** `leaderboards.yml` (refresh seconds, places, weekly rewards per place, cycle seconds, hidden groups).
**Permissions:** `skyblock.progression.top` (everyone: `/is top`).

## M5.8 Island achievements, help and menus (wiring)
- Island achievements from `plans/RANKS_TITLES_ACHIEVEMENTS.md` §C4 (AC3) get their metrics from this phase: island level (Rising Island), mastery levels (Mastery Path), bank deposits (Full Vault), spawners (Spawner Nest). Their points feed source 3 of the island level.
- Island menu: the gray "Upgrades" button becomes active; the header shows level and progress.
- `/help` topic "Island progress" (pillars, level, upgrades, masteries, leaderboards); Hall of Heroes holograms explain the boards on right-click (owner 44).
- Tutorial last step ("see `/is upgrade`", PLANTUT) opens the menu with Size I highlighted.
- Island scoreboard: Level line + "Next: Island Size I · 2/3 tasks" line (only when something is close).

## M5.9 Balance pass (BAL3)
- Add to `tools/economy_model.py`: the real catalog (level counts per upgrade, mastery ranks and weights), mastery and upgrade **effects** (Mine Fortune, Lumberjack, XP Boost, Timber, Swift Hands, Crystal Rush, Lucky Strike, Deep Pockets, Treasury fee), pillar XP pace and island level targets.
- Checks to keep: season sink ratio ≥ 80%, 14-day average not below 60% for 3 days, island share ~35%, +300% sell cap.
- Tune `pillars.yml → base` so the pace targets in M5.1 hold, and the level divisor so the M5.2 targets hold.
- No price goes live before the owner approves the balance sheet (as today).

## M5.10 Staff tools
- `/isadmin progression <player>` (needs approval, see questions): view pillars, levels, tasks, level breakdown; set an upgrade/mastery level, add pillar XP, recalculate, refund the last buy to the island bank. Mods view, admins edit. Every edit is logged with a reason (`progression/admin_*`).
- Disband/reset backups (principle 3) include the progression rows, so staff restores bring them back.

---

## Milestone order and dependencies

| # | Milestone | Depends on | Lane |
|---|---|---|---|
| M5.1 | Pillars | M4.5 skills, M2.6 stats | modules/progression, common/progression, skills hook |
| M5.2 | Island level engine | M5.1, M3.3 bank | progression, islands hooks, hud, npcs placeholders |
| M5.3 | Task engine | M5.1 | progression (+ islands storage when STORE2 exists) |
| M5.4 | Upgrades | M5.3, M3.3 | progression, islands (permission box), stacking hooks |
| M5.5 | Masteries + Ascension | M5.4 | progression, economy (fee hook), islands (Wings), depths (satchel) |
| M5.6 | Paid sizes | M5.4 | progression (hook now, shop UI in Phases 10–11) |
| M5.7 | Leaderboards | M5.2 | progression, npcs (board sources) |
| M5.8 | Achievements + menus + help | M5.2, M5.5, AC1 | achievements, islands, menus |
| M5.9 | Balance pass | M5.4, M5.5 configs | docs, tools |
| M5.10 | Staff tools | M5.4 | progression |

M5.1 → M5.2 and M5.3 can run in parallel; M5.7 can start once M5.2 is merged.

---

## UI mockups

### `/is upgrade` (6 rows, tabs on top)
```
Row 1  red frame · [Anvil: Island] [Gold: Economy] [Pickaxe: Depths] [Hopper: Production] [Nether star: Masteries] · red frame
Row 2  [black] Island Size  Member Slots  Warp Slots  Island Vault  Entity Limits  [black]
Row 3  [black] (empty slots keep their place)                                     [black]
Row 5  [Bank book: Island bank $1.2M]          [Paper: Level 12 · 60% to 13]
Row 6  45 back · 48 info · 49 close · 50 help
```
Upgrade item lore (≤ 36 characters per line):
```
Island Size II
| Level  1 / 3
| Next   7x7 border
| Tasks
  ✔ Mine 900 Copper Ore
  ✘ Chop 600 Birch Logs  412/600
| Cost   $22,907 (island bank)
» CLICK  to see the tasks
```
States: green = ready to buy, yellow = tasks open, gray = needs an earlier level, red = no Upgrades permission ("Ask your leader").

### Upgrade detail
```
Row 1  red frame · [item: Island Size II] · red frame
Row 2  [black] [task 1] [task 2] [task 3]  [chest: Deliver from storage] [black]
Row 4  [lime: » BUY $22,907]   (gray if not ready, with the reason)
Row 6  45 back · 49 close · 50 help
```
Buy → confirm screen (`Buy Island Size II for $22,907 from the island bank? Bank after: $1.18M`) → [Confirm] [Cancel]. Bedrock: two big buttons, no typing.

### `/is level`
```
Row 1  red frame · [Nether star: Island Level 38 · 40% to 39 · #14 solo] · red frame
Row 2  [black] Pillars 170  Upgrades 100  Achievements 25  Collections –  [black]
Row 3  [black] Skills 19    Depths 22     Bank 9           Blocks 39 (cap 86) [Recalculate] [black]
Row 4  [black] Mining 48  Foraging 44  Farming 36  Combat 42  Conquest (soon)  [black]
Row 6  45 back · 48 info · 49 close · 50 help
```
Each source lore says how to raise it and its cap.

### `/is top`
```
Row 1  red frame · [Level] [Weekly gain] [Value] [Pillars] · [Solo / Team toggle] · red frame
Row 2–5  places 1–28 as island icons ("#1 Ashreach · Lv 104 · leader Name")
Row 6  45 back · 47 your island (#14) · 49 close · 50 help
```

### Hall of Heroes board (TextDisplay)
```
 ✦ ISLAND TOP · SOLO ✦
 #1 Ashreach      Lv 104
 #2 Emberfall     Lv  98
 #3 Pyre Point    Lv  95
 ...
 updated every 5 min
```

### Island scoreboard lines
```
 | Island
  Level    38 (40%)
  Next     Size II 2/3
```

---

## Data (tables)
`island_pillars`, `island_pillar_weekly`, `island_level`, `island_block_worth`, `island_level_history`, `island_task_progress`, `island_upgrades`, `island_masteries`, `island_ascension`, `island_paid_size`, `leaderboard_weekly`, `leaderboard_season`. All rows carry `season_id`. Column names avoid MariaDB reserved words (`upgrade_level`, `mastery_rank`, `week_start`, `place`; never `rank`, `key`, `order`, `range`).

## Config files
`pillars.yml`, `progression.yml`, `block-values.yml`, `tasks.yml`, `upgrades.yml`, `masteries.yml`, `leaderboards.yml`. Numbers in typed configs; catalogs as lists with no plain `@Setting` keys inside the list section. Every player-facing text in `messages/progression.yml`.

## Permissions (module `progression`)
| Node | Default | Use |
|---|---|---|
| `skyblock.progression.upgrade` | everyone | `/is upgrade`, `/is masteries` |
| `skyblock.progression.level` | everyone | `/is level` |
| `skyblock.progression.recalculate` | everyone | Recalculate button (members only) |
| `skyblock.progression.top` | everyone | `/is top` |
| `skyblock.progression.admin.view` | mod | `/isadmin progression` view |
| `skyblock.progression.admin.edit` | admin | set levels, add XP, refund, CSV export |

Island permission key `upgrades` (role matrix, owner 170). LuckPerms grants in `server/permissions/progression.txt`; command docs in `docs/commands/progression.md`.

## Release / test checklists (owner)
| Milestone | You check (Java + Bedrock) |
|---|---|
| M5.1 | Mine/chop as a member → pillar XP rises; a coop friend's mining adds nothing; pillar level-up toast |
| M5.2 | `/is level` breakdown; place valuable blocks → block worth rises, remove them → it falls; level shown in the island menu, scoreboard, `/is info` |
| M5.3 | Task progress counts for the whole island; deliver items from inventory and storage |
| M5.4 | Buy Island Size I from the island bank (border grows at once); a member without the Upgrades box can't buy; bank history shows the payment |
| M5.5 | Buy Wings → everyone can fly on the island; Treasury I → bank withdrawal fee shows 1.5% |
| M5.6 | Staff grant of 11x11 after 9x9; warning about the season reset |
| M5.7 | Hall of Heroes boards fill (solo, team, weekly cycling); `/is top` tabs; weekly reward arrives |
| M5.8 | Upgrades button active, help topic, tutorial last step opens the menu |
| M5.10 | Staff view and refund, all in `/logs` |

## Decided
- Island level is dynamic from 8 sources; block worth max 20% (168, O3).
- Size 3x3 → 9x9 by upgrades, above only gems/Black Market (157/167/171).
- Island bank only (169); Upgrades permission box (170); first size ~30–60 min, member slots after a few hours (172); team 4 + leader up to 8 + leader (242).
- Masteries MA1–MA12 (not MA13); Treasury mastery lowers the bank fee (169, BANK2).
- Ranks give no pillar or island XP (RK20 default).

## QUESTIONS
Only decisions not answered yet. "s" is what this plan does if the owner doesn't answer. `p: 1` = important for launch.

```json
[
  {"q": "PG1: Island level = all level points divided by 10, from 8 sources: pillars, upgrades/masteries, island achievements, collections, members' skills, Depths tiers, island bank, block worth?", "s": "Yes", "why": "Many sources make the level dynamic (your answer 168) and nobody can win with one thing alone.", "p": 1},
  {"q": "PG2: Block worth counts with a square root (twice the blocks is not twice the points) and never more than 20% of the level?", "s": "Yes", "why": "Keeps your 20% rule and stops rich players from buying the top with blocks.", "p": 1},
  {"q": "PG3: Block worth, bank, members' skills and Depths tiers can go down; pillars, upgrades, masteries and achievements never go down in a season?", "s": "Yes", "why": "Dynamic where it should be, but earned progress is never lost by accident.", "p": 1},
  {"q": "PG4: Members' skills and Depths tiers count only members who have been on the island for 3 days or more?", "s": "Yes", "why": "Stops carrying a strong player in for a day to jump the board.", "p": 1},
  {"q": "PG5: The island bank counts its lowest balance of the last 24 hours, on a slow (log) scale, max 5% of the level?", "s": "Yes", "why": "A big deposit just before a check does nothing; money can't buy the level.", "p": 0},
  {"q": "PG6: Pillar XP comes from the base skill XP members earn, before rank and personal boosts?", "s": "Yes", "why": "Donors and personal boosts don't push the whole island (RK20).", "p": 1},
  {"q": "PG7: Spawner mobs killed by fall, lava or a whole-stack kill give no Combat pillar XP (player kills do)?", "s": "Yes", "why": "Otherwise an AFK mob farm decides the Combat pillar.", "p": 0},
  {"q": "PG8: Hide the Conquest pillar until KOTH or events exist, and count only the 4 other pillars until then?", "s": "Yes", "why": "An empty pillar would block 'all pillars' milestones for everyone.", "p": 1},
  {"q": "PG9: Task progress counts from the island's creation, so play before the upgrade appears is never wasted?", "s": "Yes", "why": "Fits 'no busywork'; the Depths tier in each task still gates the level.", "p": 1},
  {"q": "PG10: Upgrades permission defaults: Leader always, Admin on, Mod and Member off (leader can change it)?", "s": "Yes", "why": "Spending the island bank is a big right; you approved the box in answer 170.", "p": 1},
  {"q": "PG11: Paying an upgrade from the island bank has no 2% fee and does not cost that period's interest?", "s": "Yes", "why": "The fee is for taking money out; spending on the island should feel good.", "p": 1},
  {"q": "PG12: Island Size in 3 steps: 5x5 (~45 min), 7x7 (~day 1), 9x9 (day 2 to 5 for active players)?", "s": "Yes", "why": "Matches your answer 172 and the 9x9 cap.", "p": 1},
  {"q": "PG13: Member Slots in 4 steps of +1 member (first after ~3 hours), up to 8 members plus the leader?", "s": "Yes", "why": "Your 4 + leader start (242) and 'member slots after a few hours' (172).", "p": 1},
  {"q": "PG14: Sizes 11x11, 13x13 and 15x15 bought with gems or the Black Market last one season (islands are wiped), with a clear warning before buying?", "s": "Yes", "why": "Islands reset every season, so the size can't carry over; the warning avoids complaints.", "p": 1},
  {"q": "PG15: Gem prices for 11x11 / 13x13 / 15x15: 250 / 500 / 750 gems?", "s": "Yes", "why": "Cheaper than profile slot 2 (500 gems) because the size lasts one season only.", "p": 0},
  {"q": "PG16: Bought sizes above 9x9 give no island level points?", "s": "Yes", "why": "Gems must never buy level (REVIEW A1).", "p": 1},
  {"q": "PG17: Island level points per upgrade level 0.5 / 1 / 1.5 by task tier (1-3, 4-6, 7+), Ascension 1.5 each?", "s": "Yes", "why": "Late levels are worth more, but pillars stay the biggest part.", "p": 0},
  {"q": "PG18: Masteries need their pillar at 5 x the price tier (Timber rank I = Foraging pillar 10), plus tasks and money?", "s": "Yes", "why": "Links masteries to playing that part of the game (synergy).", "p": 0},
  {"q": "PG19: Treasury mastery: 3 ranks at island level 15 / 35 / 60 lower the bank fee to 1.5% / 1.0% / 0.5%?", "s": "Yes", "why": "Your answer 169: the fee drops as the island grows.", "p": 1},
  {"q": "PG20: Wings (island fly) at island level 20 for $60k?", "s": "Yes", "why": "Reachable in the first week for active islands; ranks get fly earlier (RK18).", "p": 1},
  {"q": "PG21: You removed personal homes (247). Make Homestead give shared island homes instead (/is home <name>, +1 per rank, 2 ranks)?", "s": "Yes", "why": "Keeps MA11 useful; the new sub-command needs your OK. If no, Homestead is dropped.", "p": 0},
  {"q": "PG22: Add two new masteries: Hearthfire (no hunger and slow regeneration on your island) and Replanter (grown crops replant themselves when harvested on your island)?", "s": "Yes", "why": "You asked for more creative masteries (F-O5); both are comfort only, no economy effect.", "p": 0},
  {"q": "PG23: Market Contacts (MA5) = +2% sell per level up to +40%, opened after Sell Multiplier is maxed (as in the balance sheet)?", "s": "Yes", "why": "The balance sheet needs it as a late money sink.", "p": 0},
  {"q": "PG24: After everything is maxed, endless Ascension levels (tasks + money, each a bit more) give level points?", "s": "Yes", "why": "Late money must keep draining or prices run away (balance sheet: sink falls to 39% without it).", "p": 1},
  {"q": "PG25: New sub-commands /is top (leaderboards menu) and /is level (level breakdown menu)?", "s": "Yes", "why": "Players expect both; also reachable from the island menu.", "p": 1},
  {"q": "PG26: Weekly boards count only earned points (pillars, upgrades, masteries, achievements), not block worth or bank?", "s": "Yes", "why": "Moving blocks or money on Sunday night can't win the week.", "p": 1},
  {"q": "PG27: Weekly top rewards: #1 Inferno key, #2 Blaze key, #3 Cinder key, solo and team, for every member with 3+ days on the island that week and 1+ hour played?", "s": "Yes", "why": "Your answer V1 (weekly top pays keys); the rules stop last-minute joins.", "p": 0},
  {"q": "PG28: The two weekly Hall of Heroes boards switch between Solo and Team every 10 seconds?", "s": "Yes", "why": "The Hall has one panel each; this shows both without a new build.", "p": 0},
  {"q": "PG29: Each board shows only one island per leader account (the best one)?", "s": "Yes", "why": "Matches 'best profile counts' (P7); one person can't fill the top with profiles.", "p": 1},
  {"q": "PG30: Island value (block worth) board only in /is top, not in the Hall?", "s": "Yes", "why": "The Hall has no free panel; the level board is the main one.", "p": 0},
  {"q": "PG31: Staff command /isadmin progression (mods view; admins set levels, add pillar XP, recalculate, refund, export the season CSV), all logged?", "s": "Yes", "why": "Staff need to fix bugs and restores; needs your OK as a new command.", "p": 1},
  {"q": "PG32: Global chat line for every 10th island level; other level-ups only to the island's members?", "s": "Yes", "why": "Big moments are public without spamming chat.", "p": 0},
  {"q": "PG33: Block values: placed blocks at 50% of their shop price, spawners at 50% of their tier's first shop price, utility blocks and staff items 0?", "s": "Yes", "why": "Simple rule; spawners and rare blocks matter, cheap filler doesn't.", "p": 0},
  {"q": "PG34: Block worth updates live, with a full rescan every night and a Recalculate button (once per 10 minutes per island)?", "s": "Yes", "why": "A full scan is heavy work; live tracking covers normal play.", "p": 0},
  {"q": "PG35: Mine Fortune, Lumberjack and XP Boost upgrades give +2% per level (+24% at level 12)?", "s": "Yes", "why": "Felt, but small next to tools and enchants; checked in the next balance run.", "p": 0},
  {"q": "PG36: Production upgrades (Spawner Rate, Mob Loot, Crop Growth, Harvest Yield, Generator) have 10 levels each, +12% per level, Generator 'Coming soon' until Phase 7?", "s": "Yes", "why": "Matches the balance sheet's production levels (grinders reach ~9 each).", "p": 0},
  {"q": "PG37: Entity Limits upgrade: one level, +50% entity limits, for $157k?", "s": "Yes", "why": "Your stacking answer (+50% with an upgrade), priced like other mid upgrades.", "p": 0}
]
```

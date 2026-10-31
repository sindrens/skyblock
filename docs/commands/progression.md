# Progression module (`modules/progression`): M5.1 pillars, M5.2 island level, M5.3 task engine, M5.4 upgrades, M5.5 masteries

Island pillars (PHASE-5 M5.1): Mining, Foraging, Farming, Combat, and Conquest (hidden until KOTH/events feed it,
`pillars.yml → conquest.enabled`). Dynamic island level (M5.2). Settings: `pillars.yml`, `progression.yml`,
`block-values.yml`; texts: `messages/progression.yml`; data: tables `island_pillars`, `island_pillar_weekly`,
`island_pillar_milestones`, `island_level`, `island_block_worth`, `island_level_history`, `island_bank_samples`.

`/is level` and `/is top` are sub-commands of the islands module's `/is` (tab completion there); they open the menus
of this module and need its permission nodes.

| Command | Description | Permission | Status |
|---|---|---|---|
| `/is level` | Island level menu: level, progress, board place, points per source (cap, how to raise it), Recalculate button (members, 10 min per island), the pillar row (M5.2). Also from the island menu's island item. | `skyblock.progression.level` (everyone); button: `skyblock.progression.recalculate` (everyone, members only) | ✅ built (owner P5-25) |
| Challenge Book (button in `/is level`) | The island task list (M5.3, O6): chapters per task tier, every task with its shared progress and rewards, hand-in screen for deliver tasks (from the inventory; from the island storage once STORE2 exists). No new command. | `skyblock.progression.tasks` (everyone; hand-ins members only) | ✅ built |
| `/is upgrade` | Island upgrades menu (M5.4): tabs Island, Economy, Depths, Production, Masteries (M5.5); every upgrade with its level, now/next effect, the next level's tasks and price; click for the detail menu (tasks, hand-ins, all levels, Buy → confirm). Paid from the island bank only. Also the island menu's Upgrades button and the Upgrades source in `/is level`. | `skyblock.progression.upgrade` (everyone); buying: island permission `upgrades` (leader always, admin by default) | ✅ built |
| `/is masteries` | Masteries (M5.5): the `/is upgrade` menu on its Masteries tab: the shown masteries (unbuilt ones hidden, GUI-2) and Ascension; click for the detail menu (requirements, tasks, hand-ins, ranks, Buy → confirm with the details on the middle item). Paid from the island bank only. | `skyblock.progression.upgrade` (everyone); buying: island permission `upgrades` | ✅ built |
| `/is top` | Island leaderboards: Level and Value tabs, Solo/Team toggle, places 1-28, your island; Weekly gain and Pillars tabs come with M5.7 | `skyblock.progression.top` (everyone) | ✅ built (owner P5-25) |
| `/isadmin progression <player>` | Staff view/edit (M5.10) | `skyblock.progression.admin.view` (mod), `.admin.edit` (admin) | ⬜ planned (owner P5-31) |

## How pillar XP works
- **Base skill XP of island members** (`SkillXpGainEvent#getBaseAmount`, before rank, booster, enchant and set boosts:
  RK20/P5-6). `skill-sources` maps skills to pillars (Fishing feeds Farming). Sources in `excluded-sources` give
  nothing (default `stack_kill`: the extra kills of stacked batch kills).
- **Island actions** on the player's own island (`island-sources`): ripe crops → Farming (paused while the Farming skill
  pays, Phase 7), logs → Foraging (logs placed by players give nothing), spawner mobs killed by the player's own hit or
  arrow → Combat (fall, lava, other causes and stack kills give 0, P5-7).
- Only **stored members** count (never coop players or visitors, REVIEW A4); creative/spectator never counts. XP a member
  earned stays with the island (A5).
- **ISLAND_XP family** (cap `island-xp.cap-percent`, 25%): the balance bonus (+2% per shown pillar within 10 levels of the
  highest, max +10%) plus boosts other modules register (`ProgressionService#islandXpBoost`, e.g. island achievements).
- **Curve:** XP for the next level = `curve.base × curve.growth^level` (250 × 1.12^L, no cap). Team size is damped by the
  curve: 5× the XP is only `ln 5 / ln 1.12` ≈ 14 levels more. Levels never go down within a season (also not after a
  curve change).
- **Synergy milestones** (`milestones.levels`: 10/25/40/60, all shown pillars): saved once per island and season in the
  same database transaction as the crystals each member gets (`milestones.crystals-per-member`, ledger reason
  `pillar_milestone`, idempotency key per member), then a chat line, title and sound for online members.

## How the island level works (M5.2)
**Island Level = floor(total points ÷ `level.divisor` (10)).** Eight sources (owner P5-1):

| # | Source | Points | Kind | Cap |
|---|---|---|---|---|
| 1 | Pillars | 1 per shown pillar level | earned | – |
| 2 | Upgrades + masteries | provider (`LevelSource.UPGRADES`), 0 until M5.4/M5.5 | earned | – |
| 3 | Island achievements | provider (`ACHIEVEMENTS`), 0 until AC feeds it | earned | 10% |
| 4 | Collections | provider (`COLLECTIONS`), 0 until M10.10 | earned | 5% |
| 5 | Members' skills | average total skill level of counted members ÷ 10 | live | 8% |
| 6 | Depths progress | best counted member's open tiers per wing, 1 each | live | 6% |
| 7 | Island bank | `4 × log10(lowest balance of 24 h ÷ $100k)`, min 0 | live | 5% |
| 8 | Block worth | `0.5 × √(worth ÷ $1,000)` | live | 20% (O3) |

- **Caps** are solved together: every capped source is at most its share of the final total (= `cap ÷ (1 − cap) ×`
  the other sources). The menu shows the limit ("Cap: 86").
- **Anti-abuse:** coop players are never members; members count for skills and Depths after `members.min-days` (3,
  P5-4); a member who leaves is no longer counted, so their skill and Depths points leave with them; the bank counts its
  lowest balance of `bank.window-hours` (24) and nothing until it was observed that long; block worth uses a square
  root and the 20% cap, items in chests never count. Money can't buy the level (REVIEW A1).
- **Recalculation:** pillar level-ups, members' skill level-ups, island bank deposits, new members, block worth changes
  and finished rescans request a recalculation; it waits `recalc.debounce-seconds` (5) for more changes, at most
  `recalc.max-wait-seconds` (30). Islands with an online member are refreshed every `recalc.refresh-minutes` (10). The
  database reads (offline members' skills, cached 10 min; the island bank, cached 15 min) run off the main thread.
- **Block worth** (`block-values.yml`): exact `values` → `zero` list → `storage-blocks` (sell base × amount × 50%) →
  `/shop` price × 50% → 0. Spawners: per spawner in a stack by tier (`spawner-tier-values`). Live from place, break,
  explosions, burning and pistons (spawner stacks counted again from the chunk's spawner blocks); a full rescan (a few
  chunks per tick, counted off the main thread) runs nightly at `blocks.rescan-hour` for islands that changed or had a
  member online, when a member joins an island that was never counted, and from the Recalculate button.
- **Notices:** a new season-high level gives online members a title, a chat line and a sound; every 10th level also a
  global chat line (P5-32). Never on the first calculation, never when the level drops.
- **Boards** (`top.refresh-seconds`, 300): ranked from memory; level (tie: points, then who reached it first) and block
  worth; solo and team separate; one island per player account (P5-29). The NPCHOLO boards `island_top_solo` and
  `island_top_team` use the new `npcs.yml → leaderboards.sources` types `island_level [all|solo|team]` and
  `island_value [all|solo|team]`.
- **Storage:** one `island_level` row per island and season (points per source, best level, reached time, block worth,
  lowest bank balance, last scan), one `island_level_history` row per island and day (V2), chunk worths in
  `island_block_worth` (written every `blocks.flush-seconds`), bank samples in `island_bank_samples` (pruned nightly).

## How the task engine works (M5.3, the challenge book)
- **Catalog:** `tasks.yml` (one section `tasks`, every key a task: `tier`, `type`, `target`, `amount`, `rewards`,
  `unlocks`, optional `icon`, `name`, `enabled`). Broken entries are reported at start and skipped; new bundled tasks
  are added to an existing file (turn one off with `enabled: false`). Settings: `tasks-settings.yml`. The default
  catalog (180 tasks, 12 tiers x 15 kinds) comes from `tools/gen_tasks_catalog.py` (counts grow 1.35x per tier, money
  tasks 0.5 h / 0.4 h of `REF(t)`).
- **Types:** `mine` (Depths ore via `ResourceBreakEvent`, island blocks), `chop` (Depths logs, natural island logs),
  `farm` (ripe crops and `natural-harvest` blocks on the island, sweet berry right-click harvests), `kill` (own hit or
  arrow, `kill-damage-causes`), `craft` (the vanilla crafted statistic: shift-clicks and smelting count), `sell`/`earn`
  (`ItemsSoldEvent`, AFK pool excluded by `excluded-sell-sources`), `deposit` (island bank deposits), `deliver` (hand
  in), `skill-level` (best online member), `pillar-level`, `island-level`. Targets can be `any` for mine/chop/farm/kill/craft.
- **Shared progress (owner P5-9):** one counter per island and counter key (`mine:coal_ore`, `earn:any`); every task
  reading it sees it, from the island's creation on. Only counted keys are tracked. Counters are buffered and written as
  deltas every `flush-seconds` (a crash loses at most one interval, nothing counts twice).
- **Anti-abuse (same as the pillars):** only stored members count (never coop players or visitors), never in
  creative/spectator; on islands only on their own island; blocks placed by players (last `placed-memory` positions per
  island) count nothing when broken; fall, lava and stack kills count nothing.
- **Completion:** exactly once per island, task and season. The `island_task_done` row and every member's crystals
  (ledger reason `island_task`, idempotency key per member and task) commit in one database transaction; pillar XP
  (`rewards.pillar-xp`, island boosts on top) is added after the commit. A failed save is retried by the next progress
  or the periodic check (`check-seconds`, islands with an online member: reach tasks and tasks a reload made ready).
  Notices: action bar + island chat line (`/settings` toggle `task-notices`) + sound.
- **Deliveries (crash can lose, never dupe):** room is reserved first (two members can't overfill a task), the items
  leave the inventory, the player file is saved (`PlayerSaves`), and only then the progress is written; a failed save or
  write gives the items back (inventory, then `/box`). Only plain items count: a material id matches the vanilla item
  without custom id, name, lore or enchants; `custom:<id>` matches that stackable custom item (Depths drops).
  Island storage (STORE2) plugs in through `TaskService#storage(TaskStorage)`: storage rows and progress in one transaction.
- **Unlock keys (for M5.4 / M5.5):** `upgrade.<id>.<level>` and `mastery.<id>.<rank>`; `TaskService#unlocked(island,
  key)` is true when every task listing the key is done. Default ids: island_size, member_slots, warp_slots,
  island_vault, entity_limits, bank_capacity, bank_interest, sell_multiplier, mine_fortune, lumberjack, xp_boost,
  spawner_stack_limit, fuel_tank, spawner_rate, mob_loot, crop_growth, harvest_yield, generator; masteries timber,
  swift_hands, warrior, deep_pockets, crystal_rush, lucky_strike, fuel_efficiency, beast_tamer, angler, homestead,
  wings, treasury.

## For other modules
`core.services().optional(ProgressionService.class)` (`common/progression`): `shownPillars`, `pillarLevel`, `pillarXp`,
`pillarProgress`, `pillarLevels`, `lowestPillarLevel`, `totalPillarLevels` (island level source 1, M5.2),
`balanceBonusPercent`, `islandXpBonusPercent`, `milestonesReached`, `addPillarXp` (Conquest sources, staff tools;
audited for staff actors), `islandXpBoost`. M5.2: `islandLevel`, `hasIslandLevel`, `islandLevelProgress`,
`islandLevelPoints`, `islandLevelBreakdown`, `islandRank`, `levelPointsProvider` (upgrades, achievements, collections),
`requestLevelUpdate`, `islandTop`, `openLevelMenu`, `openTopMenu`; event `IslandLevelUpEvent`. New hooks used:
`IslandService#memberSince`, `SkillService#loadProfileXp`, `DepthsZones#unlockedTiers`, `StackingService#spawnerTier`,
`ShopPrices` (shop module). Events: `PillarLevelUpEvent` (island, pillar, old/new level, cause profile,
shown) and `PillarMilestoneEvent` (Phase 7 outposts/biomes).

Placeholders: `<ph:island_level_progress>` (percent), `<ph:island_level_points>`; `island_level` and `island_rank`
are registered by the islands module and read this service (the scoreboard Level line shows once a level exists).
`<ph:pillar_mining_level>`, `<ph:pillar_mining_progress>` (percent; same for `foraging`, `farming`,
`combat`, `conquest`), `<ph:pillars_lowest>`, `<ph:pillars_total>`, `<ph:island_xp_bonus>` (percent).

`/settings` toggles: `pillar-notices` (Chat): the island pillar level-up lines; `task-notices` (Chat): the task done
lines (M5.3).

M5.3: `core.services().optional(TaskService.class)` (`common/progression`): `tasks`, `task`, `tasksFor(unlockKey)`,
`progress`, `completed`, `unlocked(island, unlockKey)`, `completedCount`, `taskName`, `taskLines` (ready-made lore lines
for upgrade/mastery menus), `openBook`, `openTask`, `storage(TaskStorage)` (STORE2 hook). Event `TaskCompletedEvent`
(island, task, cause profile, unlock keys that just became complete). Placeholders `<ph:island_tasks_done>`,
`<ph:island_tasks_total>`. Data: `island_task_progress` (counters), `island_task_done` (completions).

Audit (category `island`): `pillar_level_up` (every level-up), `pillar_milestone`, `pillar_xp_add` (staff/console adds
through the service), `island_level_change` (every level change, with the points per source), `island_block_rescan`
(Recalculate button: worth before/after), `task_complete` (M5.3: task, tier, crystals per member, pillar XP, unlock keys
completed), `task_deliver` (M5.3: task, item, amount, inventory/storage, profile). Economy ledger: `pillar_milestone`,
`island_task` (crystals).

Storage: XP is kept in memory and written as deltas every `flush-seconds` (10 s) and on shutdown (a crash loses at most
one interval, nothing is counted twice); weekly gains per pillar go to `island_pillar_weekly` for the weekly board (M5.7).
Removed islands keep their rows (restores bring them back).

Permission definitions: `modules/progression/src/main/resources/permissions/progression.yml`. Rank grants:
`server/permissions/progression.txt`.

## How island upgrades work (M5.4)
- **Catalog:** `upgrades.yml` (one section `upgrades`, every key an upgrade: `tab`, `icon`, `order`, `effect`, `enabled`,
  `levels` with `tier`, `cost`, `value`, optional `min-island-level`). Defaults from `tools/gen_upgrades_catalog.py`
  (balance model: level upgrades `W × 0.067 × 1.49^(n−1) × REF(n)`, production `C_p`). Settings: `upgrades-settings.yml`.
- **Gating:** a level needs every challenge book task that lists `upgrade.<id>.<level>` (`TaskService#unlocked`; locked
  while the task engine is still loading) and its `min-island-level` (0 by default). Levels are bought in order.
- **Who buys:** the island permission `upgrades` (owner 170 / P5-10: leader always, admin on, mod and member off; the
  leader changes it in `/is permissions`). Everyone can view and hand in task items.
- **Payment:** island bank only (owner 169), one economy transaction (`Transaction.withdraw(island bank)`, ledger reason
  `island_upgrade`, no 2% fee, the bank's interest state untouched, P5-11) with the `island_upgrades` row as its guard
  (`EconomyService#executeGuarded`): level and money commit together or not at all. Idempotency key
  `upgrade:<season>:<island>:<id>:<level>`; the guard refuses a level that is not exactly the next one; a second click
  while one runs is refused in memory. Audit `island/upgrade_buy` (island, upgrade, level, cost, buyer, bank after) next
  to the economy's own ledger and audit row.
- **Effects:** Island Size (5x5/7x7/9x9 → `IslandService#setSize`), Member Slots (6-9 with the leader →
  `#setMemberSlots`), Warp Slots (4-9 → `#setWarpSlots`) are raised right after the purchase and again on load and when a
  member joins (never lowered; a staff or paid size above stays). Live hooks (`IslandService#upgradeEffects`): Entity
  Limits (+50%), Bank Capacity / Bank Interest levels (bank.yml tables), Spawner Stack Limit (16 … 512 per block),
  Spawner Rate (+12% per level). BonusService source `upgrades` for members (never coop/visitors): Sell Multiplier
  (+5%/level, SELL family under the +300% cap); the same source already answers Mine Fortune (EXTRA_DROPS `mining`),
  Lumberjack (EXTRA_DROPS `foraging`) and XP Boost (SKILL_XP). **Coming soon** (shown gray, not buyable until their
  feature reads them): Island Vault, Mob Loot, Crop Growth, Harvest Yield, Generator, Fuel Tank, and Mine Fortune /
  Lumberjack / XP Boost (the Depths roll and skill gains don't ask the player's bonus families yet, OPEN_QUESTIONS M5-4).
- **Island level:** `LevelSource.UPGRADES` = 0.5 / 1 / 1.5 points per bought level by task tier 1-3 / 4-6 / 7+ (P5-17,
  `upgrades-settings.yml → level-points`).
- **Notices:** buyer gets a chat line; online members get a chat line (with an [Upgrades] button), an action bar line and
  the `notify.sound`; when the last task of an upgrade's next level is done, members get "… is ready to buy".
- **Placeholders:** `island_upgrades_bought` (levels bought), `island_upgrades_ready` (upgrades whose next level's tasks
  are done), `island_upgrade_<id>` (level of one upgrade).
- **Data:** `island_upgrades` (island_id, season_id, upgrade_id, upgrade_level, bought_by, bought_at). Size, member slots
  and warp slots also stay in the islands table.

## How island masteries work (M5.5)
- **Catalog:** `masteries.yml` (one section `masteries`, every key a mastery: `icon`, `order`, `effect`, optional `pillar`,
  optional `requires-maxed` (upgrade ids), `enabled`, `ranks` with `tier`, `cost`, `value`, optional `pillar-level`,
  `island-level`). Defaults from `tools/gen_masteries_catalog.py` (rank price `W × 0.067 × 1.49^(n−1) × REF(n)`, pillar
  gate `5 × price tier`; Market Contacts on the endless curve `4 h × REF(12) × 1.12^(k−1)`). Settings (Ascension too):
  `masteries-settings.yml`.
- **Shown (live):** Warrior (+4% PvE damage per rank, max +20%, Combat pillar), Deep Pockets (+10% satchel per rank, max
  +50%, Mining pillar), Crystal Rush (+3% crystals, island level 10/20/35/50/70), Lucky Strike (+4% rare finds, island
  level 15/30/45/60/80), Homestead (+1 shared island home per rank, island level 20/40), Wings (island fly for everyone on
  the island, level 20, ~$60k, P5-20), Treasury (bank fee 2% → 1.5% → 1.0% → 0.5%, level 15/35/60, P5-19), Market
  Contacts (+2% sell per level, 20 levels, opens when Sell Multiplier is maxed). **Hidden** until their feature reads them
  (GUI-2): Timber, Swift Hands (the Depths roll and break speed read the tool only), Fuel Efficiency (Phase 7), Beast
  Tamer (no breeding hook), Angler (no fishing).
- **Gating:** a rank needs its pillar level or island level, every task listing `mastery.<id>.<rank>` (challenge book;
  locked while the task engine loads) and, for Market Contacts, Sell Multiplier maxed. Ranks are bought in order.
- **Ascension** (P5-24): when every shown upgrade and mastery is maxed, endless levels; level `a` costs
  `ascension.base-cost × growth^(start-level + a − 2)` (686M × 1.12^(20 + a − 1): Ascension 1 ≈ $6.62B, the endless curve
  after the 20 Market Contacts levels), +1.5 island level points each (`ascension.level-points`), optional
  `ascension.max-levels` (0 = endless).
- **Who buys / payment:** exactly like upgrades: island permission `upgrades`; island bank only, one economy transaction
  (ledger reason `island_mastery`, no fee) with the `island_masteries` row as its guard (`EconomyService#executeGuarded`);
  idempotency key `mastery:<season>:<island>:<id>:<rank>` (Ascension: id `ascension`, rank = level); a second click while
  one runs is refused in memory. Audit `island/mastery_buy` (island, mastery, rank, cost, buyer, bank after).
- **Effects:** BonusService source `masteries` for members only (never coop/visitors): Warrior → MOB_DAMAGE (the enchants
  module applies it to hits on mobs only; PvE damage never reaches PvP), Crystal Rush → CRYSTALS, Lucky Strike →
  RARE_FINDS, Market Contacts → SELL (inside the +300% sell cap); every family cap holds. Treasury →
  `IslandService#bankFeeMasteryProvider` (the fee numbers stay in `bank.yml → withdraw.mastery-fee-percent`), Wings →
  `#wingsProvider` (visitors only with the visitor-fly setting, owner 186), Homestead → `#extraIslandHomesProvider`
  (islands module: `/is home|sethome|delhome <name>`), Deep Pockets → `SatchelService#capacityBonus("deep_pockets")`.
- **Island level:** in `LevelSource.UPGRADES` (upgrades + masteries): ranks by price tier like upgrades
  (`upgrades-settings.yml → level-points`, P5-17), Ascension 1.5 per level.
- **Notices:** buyer chat line; online members get a chat line ([Masteries] button), action bar and the `notify.sound`;
  when the last task of a rank is done and everything else is met, "… is ready to buy".
- **Placeholders:** `island_masteries_bought` (ranks without Ascension), `island_ascension`, `island_mastery_<id>`.
- **Data:** `island_masteries` (island_id, season_id, mastery_id, mastery_rank, bought_by, bought_at; Ascension is the row
  `ascension`). The plan's separate `island_ascension` table was not needed.


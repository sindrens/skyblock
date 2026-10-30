# Progression module (`modules/progression`): M5.1 pillars, M5.2 island level

Island pillars (PHASE-5 M5.1): Mining, Foraging, Farming, Combat, and Conquest (hidden until KOTH/events feed it,
`pillars.yml → conquest.enabled`). Dynamic island level (M5.2). Settings: `pillars.yml`, `progression.yml`,
`block-values.yml`; texts: `messages/progression.yml`; data: tables `island_pillars`, `island_pillar_weekly`,
`island_pillar_milestones`, `island_level`, `island_block_worth`, `island_level_history`, `island_bank_samples`.

`/is level` and `/is top` are sub-commands of the islands module's `/is` (tab completion there); they open the menus
of this module and need its permission nodes.

| Command | Description | Permission | Status |
|---|---|---|---|
| `/is level` | Island level menu: level, progress, board place, points per source (cap, how to raise it), Recalculate button (members, 10 min per island), the pillar row (M5.2). Also from the island menu's island item. | `skyblock.progression.level` (everyone); button: `skyblock.progression.recalculate` (everyone, members only) | ✅ built (owner P5-25) |
| `/is upgrade`, `/is masteries` | Upgrades and masteries (M5.4, M5.5) | `skyblock.progression.upgrade` (everyone) | ⬜ planned |
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

`/settings` toggle: `pillar-notices` (Chat): the island pillar level-up lines.

Audit (category `island`): `pillar_level_up` (every level-up), `pillar_milestone`, `pillar_xp_add` (staff/console adds
through the service), `island_level_change` (every level change, with the points per source), `island_block_rescan`
(Recalculate button: worth before/after). Economy ledger: `pillar_milestone` (crystals).

Storage: XP is kept in memory and written as deltas every `flush-seconds` (10 s) and on shutdown (a crash loses at most
one interval, nothing is counted twice); weekly gains per pillar go to `island_pillar_weekly` for the weekly board (M5.7).
Removed islands keep their rows (restores bring them back).

Permission definitions: `modules/progression/src/main/resources/permissions/progression.yml`. Rank grants:
`server/permissions/progression.txt`.

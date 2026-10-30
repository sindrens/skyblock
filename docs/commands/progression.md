# Progression module (`modules/progression`): M5.1 pillars

Island pillars (PHASE-5 M5.1): Mining, Foraging, Farming, Combat, and Conquest (hidden until KOTH/events feed it,
`pillars.yml → conquest.enabled`). Settings: `pillars.yml`; texts: `messages/progression.yml`; data: tables
`island_pillars`, `island_pillar_weekly`, `island_pillar_milestones`.

| Command | Description | Permission | Status |
|---|---|---|---|
| (none) | M5.1 has no command. The pillar breakdown is part of `/is level` (M5.2). | – | – |
| `/is level` | Island level menu: breakdown per source, the pillar row (M5.2) | `skyblock.progression.level` (everyone) | ⬜ planned (owner P5-25) |
| `/is upgrade`, `/is masteries` | Upgrades and masteries (M5.4, M5.5) | `skyblock.progression.upgrade` (everyone) | ⬜ planned |
| `/is top` | Island leaderboards (M5.7) | `skyblock.progression.top` (everyone) | ⬜ planned (owner P5-25) |
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

## For other modules
`core.services().optional(ProgressionService.class)` (`common/progression`): `shownPillars`, `pillarLevel`, `pillarXp`,
`pillarProgress`, `pillarLevels`, `lowestPillarLevel`, `totalPillarLevels` (island level source 1, M5.2),
`balanceBonusPercent`, `islandXpBonusPercent`, `milestonesReached`, `addPillarXp` (Conquest sources, staff tools;
audited for staff actors), `islandXpBoost`. Events: `PillarLevelUpEvent` (island, pillar, old/new level, cause profile,
shown) and `PillarMilestoneEvent` (Phase 7 outposts/biomes).

Placeholders: `<ph:pillar_mining_level>`, `<ph:pillar_mining_progress>` (percent; same for `foraging`, `farming`,
`combat`, `conquest`), `<ph:pillars_lowest>`, `<ph:pillars_total>`, `<ph:island_xp_bonus>` (percent).

`/settings` toggle: `pillar-notices` (Chat): the island pillar level-up lines.

Audit (category `island`): `pillar_level_up` (every level-up), `pillar_milestone`, `pillar_xp_add` (staff/console adds
through the service). Economy ledger: `pillar_milestone` (crystals).

Storage: XP is kept in memory and written as deltas every `flush-seconds` (10 s) and on shutdown (a crash loses at most
one interval, nothing is counted twice); weekly gains per pillar go to `island_pillar_weekly` for the weekly board (M5.7).
Removed islands keep their rows (restores bring them back).

Permission definitions: `modules/progression/src/main/resources/permissions/progression.yml` (none yet). Rank grants:
`server/permissions/progression.txt`.

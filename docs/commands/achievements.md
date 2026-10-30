# Achievements (`modules/achievements`)

About 30 personal achievements in six categories (Skills, Island, Combat, Economy, Gear, Exploring) and 6 **island achievements** shared by an island's members, most with tiers (I, II, III …). Every tier gives **boost points**; the last tier of most of them gives a **title**. Plan: `docs/plans/RANKS_TITLES_ACHIEVEMENTS.md` §C (AC1-AC4). Settings: `achievements-settings.yml`; the achievements, categories and island achievements: `achievements.yml` (validated on load, broken entries are skipped and reported); texts: `messages/achievements.yml`. Reload: `/skyblock reload achievements`.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/achievements` | The achievements menu (owner 356) with three tabs in the top row. **Personal**: summary head (tiers, total boost points) and one item per category with its `\| Boost` section (points, boost now / cap, per point); a tap opens the category (every achievement with tier, progress, goals, `\| Rewards`: next tier's points and the title). **Island**: island points and level points, the four island boosts (now / cap, maxed at N points), the island achievements list and the top 5 members. **History**: one book per finished season and profile (tiers, finished, boost points, island tiers/points, titles won). No arguments | `skyblock.achievements` | ✅ |
| `/tags` → Achievements | Opens the same menu (Personal tab) | `skyblock.achievements` | ✅ |

Placeholders: `<ph:achievements_done>` (achievements the active profile finished), `<ph:achievements_total>` (enabled achievements), `<ph:achievements_points>` (boost points of the active profile), `<ph:achievements_island_points>` (island points of the active profile's island).

## Boost points and personal boosts (AC2)
- Tier points (owner AC2, `points.tier-1..5`): **1 / 2 / 3 / 5 / 8**; tiers above V give the tier V value.
- A category's `boost` (in `achievements.yml`) says what its points give; each point adds a step up to a cap (`boosts.*`):

| Category | Boost (BonusService family) | Step per point | Cap |
|---|---|---|---|
| Skills | skill XP | +0.3% | +10% |
| Gear | tool XP | +0.4% | +10% |
| Combat | damage to mobs (the family is never applied in PvP) | +0.3% | +8% |
| Exploring | rare finds | +0.3% | +8% |
| Island (own stats) | crystals | +0.25% | +6% |
| Economy | auction listing slots | +1 at 12 points, +2 at 30 | +2 slots |

- Boosts are a `BonusService` source named `achievements` (family caps still apply on top). **Never sell or money** (RTA-39); there are no crystals, money or keys per tier (RTA-40). `boosts.enabled: false` turns every personal boost to 0 (points stay visible).
- Auction slots are added by the auction module on top of the rank slots (`AchievementService#extraAuctionSlots`).

## Island achievements (AC3)
| Island achievement | Metric | Goals | Title (members with 3+ days) |
|---|---|---|---|
| Rising Island | island level | 10 / 25 / 50 / 75 / 100 | Island Ascendant (legendary) |
| Deep Roots | crops harvested by all members | 10k / 100k / 1M / 5M | Ashbloom Grove (epic) |
| Stone Heart | blocks mined by all members | 10k / 100k / 1M / 10M | Forgeheart (epic) |
| Open Doors | unique visitors | 25 / 250 / 1,000 / 5,000 | Lighthouse (rare) |
| Loved Isle | likes received | 10 / 100 / 1,000 | Beloved Isle (rare) |
| Team Flame (team islands only) | members active (30 min each) on the same day, one per IP | 2 / 3 / 4 / 5 | Kindred (rare) |

- Island points (same 1/2/3/5/8) give four island boosts for the whole island (`island.boosts.*`): **production** +0.25%/point (cap 8%), **island crystals** +0.25% (6%), **island XP** +0.2% (5%), **fuel efficiency** +0.5% (10%). Island XP feeds the progression module's `ISLAND_XP` family (`ProgressionService#islandXpBoost`, source `achievements`); production, island crystals and fuel are published through `AchievementService#islandBoostPercent` for the systems that apply them (island production, island missions, Island Fuel) and show "starts to work with …" in the menu until then.
- Island level source `achievements` (M5.2): island points × `island.level-points-per-point` (progression caps it at `caps.achievements-percent` of the total).
- Anti-abuse: progress counts only members (the island stats keep one row per member key; coop players and visitors never count). A member who leaves takes nothing; the island keeps its progress and boosts. Titles only for members with `island.title-min-days` (3) on the island; members who reach it later get the title on the next island poll while online. Team Flame counts alts on one IP once. Likes and visits already follow the islands module's limits (one like per player per day, accounts per IP).
- Planned island achievements without a data source yet (added when the systems exist): Spawner Nest (stacked spawners), Mastery Path (mastery levels), Full Vault (island bank deposits), Weekly Fire (weekly competition wins), Night Shift (fuel hours).

## Season reset and history (AC4)
- `AchievementService#seasonReset(endedSeason, actor)` (for the season script, PLAN12; no command): saves what is in memory, writes one **history** row per profile (tiers, finished, boost points, its island's tiers and points, titles won), then wipes every personal and island progress row **in one transaction**. Titles stay on the accounts (owner 354 / RTA-44). Runs once per season (a second call only saves pending progress and returns -1). Online players get a chat line with a [History] link.
- Call it after the other season wipes (skills, islands): the gauges poll right after and would otherwise refill from the old values.

## Rules (personal, as before)
- **Progress is per profile** (owner decision PR1): a new profile starts at zero, a deleted profile's progress and unlocks are removed. **Titles are account-wide.**
- Each tier unlocks **once** per profile (database primary key). Values only go up: counters add, gauges keep the best value seen.
- Unlock toast: screen title, one chat line per unlock with the tier's points (max `toast.max-lines`, then "+N more"), sound `toast.sound`; island tiers show to every online member.
- Reward titles: an id from `tags-titles.yml`, or `{id, display, rarity}`; a missing title with a display is **added** to `tags-titles.yml` on start/reload (`titles.add-missing`); existing titles are never changed.
- New achievements, island achievements and new category fields (e.g. `boost`) from an update are added to the live `achievements.yml`; turn one off with `enabled: false` instead of deleting it.

## Where progress comes from (existing events and services only)
| Metric | Source |
|---|---|
| `skill_*`, `skill_total`, `skill_best` | `SkillLevelUpEvent` (instant) + `SkillService.level` (gauge poll, `tracking.gauge-poll-seconds`) |
| `island_blocks_mined`, `island_logs_chopped`, `island_crops_harvested`, `island_blocks_placed`, `island_mobs_killed`, `island_visitors` | `IslandStatsService.snapshot` (member key = profile key; visitors island-wide), one read per island every `tracking.island-poll-seconds` |
| island `crops_harvested`, `blocks_mined`, `logs_chopped`, `mobs_killed`, `visitors`, `island_level` | the same island snapshot (sum of every member key) + `ProgressionService.islandLevel` |
| island `likes` | audit row `island/island_like` |
| island `team_active` | the playtime minute (members of team islands, 30 min a day, one per IP) |
| `stacked_kills`, `best_stack_kill` | `StackKillEvent.killed()` |
| `items_sold`, `money_earned`, `best_sale` | audit row `economy/sell` (via `AuditLog.listen`) |
| `trades_completed` | audit row `trade/trade_complete` (both players) |
| `enchants_applied` | audit rows `item/enchant_apply`, `item/enchant_combine_tool` |
| `islands_visited` | audit row `island/island_visit` (one per island per day) |
| `wardrobe_sets_owned`, `wardrobe_best_level` | `WardrobeService.ownedSets` / `setLevel` |
| `tool_best_level`, `tool_best_prestige` | `ToolLevelUpEvent` + hotbar `ToolService.read` |
| `balance_peak` | `EconomyService.cached` |
| `playtime_minutes` | one minute per online minute |

## Storage and audit
- Tables `achievement_progress`, `achievement_unlocks` (per profile), `achievement_island_progress`, `achievement_island_unlocks` (per island; every island is loaded at start, the level source and island XP boost register only after that), `achievement_history` (season summaries). Saved in one batch every `storage.save-interval-seconds` and on quit, profile switch and shutdown; counters as deltas, gauges with `GREATEST`, all database work of the module in one ordered chain.
- Audit: `item/achievement_unlock` (achievement, tier, profile, value, points, boost, title), `island/island_achievement_unlock` (island, achievement, tier, value, points, title), `island/island_achievements_removed`, `item/achievements_profile_deleted`, `item/achievements_season_reset` (season, history rows, wiped rows) or `..._refused`; titles: `item/title_grant` (cosmetics, reason `achievement:<id>` or `island_achievement:<id>:<island>`).

Permissions: `skyblock.achievements` (the command and menu; default true; progress is counted for everyone).

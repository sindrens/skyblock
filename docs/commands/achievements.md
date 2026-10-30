# Achievements (`modules/achievements`)

About 30 achievements in six categories (Skills, Island, Combat, Economy, Gear, Exploring), most with tiers (I, II, III …). The last tier of most of them gives a **title**. Settings: `achievements-settings.yml`; the achievements and categories: `achievements.yml` (validated on load, broken entries are skipped and reported); texts: `messages/achievements.yml`. Reload: `/skyblock reload achievements`.

**No own command** (`/achievements` is not in the planned list and `/menu` is full): the menu opens from **`/tags` → Achievements** (book button, bottom row) and the `/help achievements` topic ("Take me there" opens `/tags`). See docs/OPEN_QUESTIONS.md.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/tags` → Achievements | The achievements menu: summary head, one item per category, then every achievement with tier, progress bar to the next goal, goals and reward title. Secret ones show as ??? until their first tier | `skyblock.achievements` | ✅ |

Placeholders: `<ph:achievements_done>` (achievements the active profile finished, every tier) and `<ph:achievements_total>` (enabled achievements).

## Rules
- **Progress is per profile** (`ProfileService` profile key, owner decision PR1): a new profile starts at zero, a deleted profile's progress and unlocks are removed. **Titles are account-wide**: when any profile finishes the last tier, the title goes to the account (all profiles). Finishing it again on another profile shows the unlock but grants nothing new.
- Each tier unlocks **once** per profile (database primary key). Values only go up: counters add, gauges keep the best value seen (a skill season reset never takes progress away).
- Unlock toast: screen title (best unlock of the batch), one chat line per unlock (max `toast.max-lines`, then "+N more"), sound `toast.sound`; a new reward title adds a "New title: … [Equip]" line.
- Reward titles: an id from `tags-titles.yml`, or `{id, display, rarity}`; a missing title with a display is **added** to `tags-titles.yml` on start/reload (`titles.add-missing`, audited `config/title_define`); existing titles are never changed.
- New achievements from an update are added to the live `achievements.yml`; turn one off with `enabled: false` instead of deleting it.

## Where progress comes from (existing events and services only)
| Metric | Source |
|---|---|
| `skill_*`, `skill_total`, `skill_best` | `SkillLevelUpEvent` (instant) + `SkillService.level` (gauge poll, `tracking.gauge-poll-seconds`) |
| `island_blocks_mined`, `island_logs_chopped`, `island_crops_harvested`, `island_blocks_placed`, `island_mobs_killed`, `island_visitors` | `IslandStatsService.snapshot` (member key = profile key; visitors island-wide), one read per island every `tracking.island-poll-seconds` |
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
- Tables `achievement_progress (profile_key, metric, amount)` and `achievement_unlocks (profile_key, achievement, tier, owner_uuid)`. Saved in one batch every `storage.save-interval-seconds` and on quit, profile switch and shutdown; counters as deltas, gauges with `GREATEST`, all database work of the module in one ordered chain.
- Audit: `item/achievement_unlock` (achievement, tier, profile, value, title), `item/achievements_profile_deleted`; titles: `item/title_grant` (cosmetics, reason `achievement:<id>`).

Permissions: `skyblock.achievements` (open the menu; default true; progress is counted for everyone).

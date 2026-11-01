# Milestones build plan (Complete Developer Spec §7-9)

Written 2026-11-01 after checking the UNVERIFIED points of the spec against the code (branch `q/release-v3`). The spec file the
owner pasted was cut after §9.3, so §10-29 (Eclipse, island tools, farming, build order 27.3) are not read yet.

## What the code has today (checked)
| Spec needs | Code today | Consequence |
|---|---|---|
| Placed blocks never count (§7.3) | Only `progression/PlacedBlocks` (last N **log** positions per island, private to progression) | **NEW** shared `PlacedBlockService` in `common` (chunk-keyed, TTL, all materials), published by one module; progression keeps its log rule on top |
| `GameActionEvent` HARVEST_CROP with the crop (§7.6 filter `material`) | Fired once per harvest from `production/Farming.java:319` with an **amount only**, no material | **CHANGE**: add `target` (material/entity key) to `GameActionEvent` (old constructor kept), fire per crop type |
| BREAK_BLOCK, KILL_MOB actions | Not fired anywhere (only forge, enchant, visit, like, harvest) | **NEW** actions; fired by one listener (milestones) with the §7.3 rules: acting online player, survival, not AFK / pool / staff mode, not placed, island member for island credit |
| `stacked_kills` counting (§7.3) | `achievements/Metric.STACKED_KILLS` exists | reuse its rule for spawner stacks |
| `BonusContext.target` (§9.1) | Record has player, item, tool, activity; **no target**; 14 `new BonusContext(` call sites | **CHANGE** (backward compatible): add `target`, then pass it at every extra-drops / crop-growth / sell / tool XP / skill XP / rare finds / production call site, one test per site |
| PRODUCTION and MILESTONE_PROGRESS families | Not in `BonusFamily` | **NEW** families (caps from the spec's implied caps: production 100, progress 25) |
| Per-profile weekly active minutes (§3) | Not there | **DONE** on `q/rewards-v2`: `profile_active_week` |

## Slices (each one commit series, each builds and tests green on its own; all behind `milestones.enabled: false`)
1. **Contracts:** `BonusContext.target`, `GameActionEvent.target` + BREAK_BLOCK / KILL_MOB, `BonusFamily.PRODUCTION` / `MILESTONE_PROGRESS`, `PlacedBlockService`. No behaviour change.
2. **Module `milestones`:** `milestones.yml` (settings, curves, templates with `for-each`), load validator (§7.7 list), tables (§7.10), objective engine (O(1) map, `LongAdder` per scope/chain), flush every 10 s / quit / switch / stop, tier completion + `MilestoneTierCompletedEvent`, discovery line, notice batching (30 s).
3. **Claims and perks:** island claim once per island (any member, lower tiers marked), personal claims, `BonusSource "milestones"` (highest claimed tier per chain, target match only), budget check against the live caps (15%).
4. **Menus:** `/milestones` (`/ms`): Overview (Island, Personal), categories, chains (current + next 2, rest `???`), chain detail with tier tiles, contribution %, action bar progress (crops, ores on; mobs off) and player toggle; `/rewards` gets the Milestones card and source.
5. **Boards and resets:** milestone points, two hologram boards via npcs (60 s), per-chain top 10, public/hidden (island permission `milestones.board`), island reset carry-over of unclaimed READY tiers, season wipe with history row.
6. **Call sites:** pass the target at every bonus roll (extra drops, crop growth, sell, tool XP, skill XP, rare finds, production); one test per site.

## Open points (safest default used until the owner answers)
- Island reset: island id reused or new? Carry-over is keyed to the new island either way (checked at slice 5).
- Alt-linking data for the wash-trading rule does not exist: only the flagged-account rule and `economy.pair-daily-cap` apply.
- Trails system does not exist: achievements get cosmetics/trails when it does.

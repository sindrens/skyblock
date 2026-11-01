# Build spec ECON-SPAWNERS: 4 earned tiers + premium tier, same defaults, upgrade hooks, per-island unlock by kills, custom drops, spawner shop tab

**Status: PROVISIONAL.** Cards D2, C4, C5 are still `Review 1` (lock blocked by open ripples, see the end of `LEDGER.md` batch `ECON-R1`). The design below is the owner's own answers of 1 Nov 2026 (`rounds/OWNER_ANSWERS_2026-11-01.md`: chat "4 tiers with 5 spawners in each tier and one 5th tier with the 3 best", EP-Q1 a + note, EP-Q3, EP-Q5, EP-Q6, EP-Q8 other, EP-Q9 a; EP-Q4 unanswered = suggested 32). Anything not answered is a developer default listed in section 2; the owner only vetoes.
Written 1 Nov 2026. Real code read: `/Users/sindreskogholt/Development/skyblock-wt-tutfix` (branch `q/gui-phase2`, deployed). No code in this file.
Tables (roster, drop names and prices, payback, unlock numbers): `ECON-TABLES.md` sections A and B. Companion specs: `ECON-SHARDS.md` (shard block, crate migration class), `ECON-SELL-SHOP-AH.md` (sell price knob, shop price knob, price table).

## 0. Goal
Spawners become a clean ladder: **tiers 1 to 4 (5 mobs each) are earned**, **tier 5 (3 mobs) is premium** (Black Market for shards, credit shop for credits). Every spawner of every tier starts **identical**: one mob every 10 s, 1 mob per spawner, 1.0x loot. All improvement comes from `/is upgrade` and `/is masteries`. Every mob drops one custom item that sells for a round price, so every mob pays. A tier unlocks per island by killing mobs of the previous tier. Prices are round, payback is 65 to 122 h.

## 1. Assumptions and lane boundaries
- **Progression lane** (parallel) writes the `/is upgrade` and `/is masteries` trees. This spec only defines the **hooks and config keys** it must call (section 3). Until they are wired every hook returns level 0 = the identical default.
- **GUI lane** (parallel) changes `common/menu` (Menu, MenuLayout, Centered, PagedMenu, MenuService, icons) and the shop menus. **Do NOT edit:** anything in `common/src/main/java/.../common/menu/`, `ShopMenu`, `ShopCategoryMenu`, `BuyMenu`, `SellItemMenu`, `ShopTooltips` (one allowed one-line seam, step 8f), `ShopCatalog.Category` layout code, the `groups:` lists of `shop.yml`. The Spawners tab stays the generic item grid (`ShopMenu` with 20 spawner items = 4 groups of 5 = 4 centred rows by the lane's rules); everything new reaches it through `ShopModule` methods the grid already calls (`state(...)`, `incomePerHour`, `nextOutputPercent`, `refuse`).
- Formats: `docs/FORMAT.md` section 4 (tile template, 36 characters per line, one `» CLICK` line last), section 1 tokens (`<cur:money>`, `<c:muted>` ...), `docs/UI_STYLE.md`. No hex, no new colour.
- Tier numbers 1 to 5 and item ids `spawner_1`..`spawner_5` are unchanged: **no item or row remap** (`ECON-TABLES.md` A).
- The dependency on the credit shop (C1/I10, not built) is a **contract only** (step 11).

## 2. Decisions (developer defaults; owner only vetoes)
| # | Decision | Why |
|---|---|---|
| S1 | One price per tier, round: $100,000 / $250,000 / $500,000 / $1,000,000; premium 400 shards or 2,500 credits | EP-Q1 a + "round numbers" |
| S2 | The 5 mobs of a tier have **equal drop prices** (`ECON-TABLES` A), so equal income, equal payback, one price per tier | no mob is a free upgrade inside a tier |
| S3 | Cap per island and tier **32** (counts every block, outposts later, legacy mobs count in their old tier). It does **not** conflict with 5 mob types: the cap is per tier, any mix | EP-Q4 suggested; reverses owner S20 (512 per block) as the owner delegated ("unless it conflicts") |
| S4 | Stack per block: start 8, hard max **32** (`spawners.stack-max`); the progression lane writes the upgrade steps 8, 12, 16, 24, 32. Grandfather: blocks already above 32 keep their spawners, nothing is deleted, only new adds/places are refused | one block can never exceed the tier cap |
| S5 | Unlock = kills of the previous tier's mobs on the island (any source), numbers 1,000 / 5,000 / 20,000, premium has none; gates only the **shop purchase** (AH, crates, trades, `/spawner give` still deliver items; the cap and place rules are the brake) | D2 gate text; owner EP-Q5 |
| S6 | Spawner mobs drop **only** their custom item (`mobdrop_<mob>`), reverses R3-21a ("vanilla only") as the owner said "we will have custom drops and it will say in shop" (EP-Q8 other) | owner |
| S7 | Legacy mobs (squid, magma_cube, glow_squid, piglin, shulker, ravager, breeze) keep working with vanilla loot, are not sold anywhere | EP-Q8 a ("keep working") |
| S8 | Speed, mob count and loot are **integer levels** the hooks return; the numbers per level live in `stacking.yml` step lists. Combined max = x6.0 | keeps the upgrade tree numbers out of stacking and gives one place to tune the ceiling (18% of late income) |
| S9 | The old hook `spawnerRateMultiplier` stays (deprecated, default 1.0), stacking no longer reads it | no break for other callers |
| S10 | Premium spawners leave the crates (rows `spawner_t5` removed, replaced by `shard_block` in ECON-SHARDS) | owner: sold by Black Market and the store |

## 3. Contract for the progression lane (hooks, effects, config)
New default methods (all main thread, memory only, default 0 = no upgrade):
| Where | Method | Meaning |
|---|---|---|
| `common/.../island/IslandUpgradeEffects` | `default int spawnerSpeedLevel(UUID islandId)` | level into `spawners.speed-steps-seconds` |
| | `default int spawnerMobsLevel(UUID islandId)` | level into `spawners.mob-steps` |
| | `default int mobLootLevel(UUID islandId)` | level into `spawners.loot-steps` |
| | `default int spawnerCapBonus(UUID islandId)` | credit perk `+N spawner cap per tier` (C1-R2-14); stacking clamps to `spawners.cap-perk-max` |
| `common/.../island/IslandService` (+ `IslandManager` delegating to `effects` like `spawnerStackLimit` at `IslandManager.java:867`) | the same four, same names | the stacking module reads them through `module.islands()` |
| `common/.../island/IslandUpgradeEffects` / `IslandService` | `spawnerRateMultiplier` | `@Deprecated`, unchanged behaviour |
Suggested effect ids for `UpgradeEffect` (the lane decides): `spawner-speed` (level), `spawner-mobs` (level), `mob-loot` (becomes `live = true`, level; today `UpgradeEffect.MOB_LOOT("mob-loot", false)`), existing `spawner-stack` (value = spawners per block, now 8 to 32), existing `spawner-rate` retired into `spawner-speed`.
Step lists (defaults, level 0 is first): speed seconds `[10, 9, 8, 7, 6, 5]` (max x2.0), mobs per spawner `[1.0, 1.25, 1.5, 1.75, 2.0]` (max x2.0), loot `[1.0, 1.1, 1.2, 1.3, 1.4, 1.5]` (max x1.5). A level above the list length is clamped to the last step. Floor `spawners.interval-floor-seconds: 4` protects the main thread whatever a config says.

## 4. Steps (ordered; each step builds on its own)
### 4a. `common`
1. `common/.../stacking/StackingService`: add default methods (all return neutral values in other implementations):
   - `default OptionalInt spawnerCap(UUID island, int tier)` (cap incl. perk bonus; empty = no limit info)
   - `default long spawnersOfTier(UUID island, int tier)` (placed count from memory)
   - `default Unlock spawnerUnlock(UUID island, int tier)` with `record Unlock(boolean unlocked, long kills, long needed, String mobsLabelKey, List<String> mobs)`; premium and tier 1 = unlocked
   - `default double spawnerIncomePerHour(UUID island, String mob)` (pure `SpawnerIncome`, 0 when no drop or not sold)
   - `default Optional<String> spawnerDropItem(String mob)`
   - `default boolean spawnerSellable(String mob)` (false for legacy and unknown)
2. `IslandUpgradeEffects`, `IslandService`, `IslandManager`: the four hooks of section 3.

### 4b. `modules/stacking` config and migration
3. `StackingConfig`: `version()` returns 2; `valueChanges()` for version 2 (only applies when the live value still equals the old default):
   `spawners.max-interval-seconds` 40 -> 10; `spawners.stack-max` 512 -> 32; `spawners.tier-2` [zombie, skeleton, spider, cave_spider, slime, squid] -> [zombie, skeleton, spider, cave_spider, slime]; `tier-3` [..., magma_cube] -> without magma_cube; `tier-4` [blaze, enderman, guardian, piglin, zombified_piglin, hoglin, glow_squid] -> [blaze, enderman, guardian, hoglin, zombified_piglin]; `tier-5` [iron_golem, wither_skeleton, shulker, evoker, ravager, breeze] -> [iron_golem, wither_skeleton, evoker]; `spawners.rare-drops` remove the shulker line. New keys listed in section 6.
4. `SpawnerTiers.load(...)`: new parameter `legacy` (`mob:tier` lines). Legacy mobs are `allowed()` and `tier()` returns their tier, `sellable()` false; `types()` still lists them (so `/spawner give` tab completion and staff restore keep working); a new `sellableTypes()` excludes them and is what `StackingModule#spawnerTypes(tier)` (crates, Black Market random pick) and the shop catalog predicate use. `TIERS` stays 5. A mob in both a tier list and `legacy` is reported once and the tier list wins. The `NEVER` set (SK8) still applies.
5. New pure class `SpawnerSteps` (parse the three step lists, `seconds(level)`, `mobs(level)`, `loot(level)`, clamp, floor) with the config problems reported like `SpawnerEfficiency.parse`.

### 4c. `modules/stacking` behaviour
6. **Output** (`SpawnerManager`): `interval()` -> per block `SpawnerSteps.seconds(islands.spawnerSpeedLevel(island))` x 20 ticks, jitter `spawners.interval-jitter-percent` (0); `StackMath.spawnerOutput(mobsPerSpawner x steps.mobs(level), stack, efficiencyFactor, carry)` (the `rateMultiplier` mastery factor leaves the call; `masteryMultiplier()` deleted or returns 1.0); `mobsPerMinute()` uses the step interval; `limit(sp)` = `StackMath.stackLimit(mastery, stackStart, min(stackMax, tier cap))`. Keep the 100 due blocks per tick budget and the 4 s floor.
7. **Cap** (`SpawnerManager.onPlace` and `add`): new pure `SpawnerRules.room(long ownedOfTier, int cap, int perkBonus)`. `onPlace`: if `owned >= cap + bonus` deny with `spawner.cap-reached` before anything else changes; `add(...)`: `room = min(limit - amount, cap + bonus - owned)`; a grandfathered island (owned > cap) gets room 0, never a removal. `StackingService.spawnerCap/spawnersOfTier` implemented from `SpawnerCounts`. Staff `/spawner give` unchanged (items only).
8. **Custom drops**: new `SpawnerDropItems` (registers `mobdrop_*` as `ItemType.MATERIAL` custom items from `spawners.drop-items`, names from `messages/stacking.yml drops.<id>`, rarity per line) and `SpawnerDrops` (pure: mob -> `(item id, amount per mob)`; `amount(killed, rolls, lootLevel, random)` = `(killed + rolls) x perMob x loot(level)`, whole part plus one random unit for the fraction, never negative). Hook in `MobStacker`: in `onDeath` (replace `event.getDrops()` and `extras` for `spawnerMob` with a custom drop when the mob has one, keep `applyRareDrops`), in `killBatch` (same for `loot`), and the `whole` path of `onDeath` (it already shares `killed`). The island for the loot level comes from `islandAt(location)` once per kill batch. Items go to `module.items().drop(at, prototype, amount)` (stacked item entities) exactly like the vanilla loot did. A mob without a custom drop (legacy, unknown) keeps vanilla loot. New small `MobDropGuard` (Listener): cancels `PrepareItemCraftEvent` results, `FurnaceSmeltEvent`, `BrewEvent`, `PlayerItemConsumeEvent`, `PlayerInteractEvent` on composters and `PlayerInteractEntityEvent` (feeding) when any involved stack carries an item id starting `mobdrop_`; base materials are inert (`ECON-TABLES` A). If a custom common guard for all `ItemType.MATERIAL` items exists later, this class is deleted.
9. **Kill tracking and unlock**: new `UnlockTracker` (in-memory `Map<UUID island, Map<String mob, long>>`, write-behind batch every `spawners.unlock.save-seconds` (30) with one `INSERT ... ON DUPLICATE KEY UPDATE kills = kills + VALUES(kills)` batch, loaded lazily per island on first read, flushed on island unload, disable and `IslandRemovedEvent`). `MobStacker` calls `module.unlock().record(entity.getLocation(), type, killed)` at three places: the early-return branch of `onDeath` (single mob = 1), after `killed` is known in `onDeath`, and in `killBatch`. Only mobs listed in a tier 1 to 4 list are stored (others are ignored, no map growth). Pure `UnlockRules.status(tier, kills, config)` returns `Unlock`. On `IslandRemovedEvent` with a clearing reason (`SpawnerManager.clearsSpawners(reason)`) the island's rows are deleted (progress is gone, profile principle); kept rows for staff restore are in the audit row (`spawner_unlock_reset`).
10. **Menus** (`SpawnerMenu`): replace the "Speed" line by `Every <s> s`, add `Mobs per spawn`, `Loot x<n>`, `On this island: <owned>/<cap>` and `Next one works at <p>%`; keep `FrozenSpawnerMenu` untouched. Texts in section 7.

### 4d. Shop (`modules/shop`) - no edits to GUI-lane classes
11. `ShopSettings`: delete `spawners.income-per-hour.tier-1..5` (migration drops the keys), keep `spawners.enabled`, set `spawners.max-tier` default 4, add `spawners.dynamic-factor` (0.85), keep `payback-good-hours` (100) and `payback-ok-hours` (300) (all five tiers are green at base; the 17th to 32nd show yellow).
12. `shop.yml` (category `spawners`, `kind: spawners`): items = the 20 mobs of tiers 1 to 4 with price `100000` / `250000` / `500000` / `1000000` (hand prices, round; `buy.price-multiplier` scales them). Premium mobs and legacy mobs are rejected by the catalog loader (`validSpawner` predicate = `StackingService.spawnerSellable(mob)` and its tier <= `spawners.max-tier` 4; `ShopModule#isSpawnerType` changes accordingly), with a console line. Update the file comment (tiers 1 to 4, round prices). Add the tab's groups only if the GUI lane's `groups:` exists, as 4 groups of 5 (ids `tier1`..`tier4`).
13. `ShopModule` (spawner helper section around lines 549-640 only): `incomePerHour(item)` = `StackingService.spawnerIncomePerHour(islandOf(viewer), mob)` x `dynamic-factor` rounded (so the **existing** `ShopTooltips.spawnerRows` shows the real payback with no edit); `state(...)`: for a spawner call `StackingService.spawnerUnlock(island, tier)` and `spawnersOfTier` vs `spawnerCap`; if locked add `entry.spawner-unlock` lines (`Unlock: kill 1,000 tier 1 mobs`, `312/1,000`) and return `locked`; if capped add `entry.spawner-cap` and return `locked`; `refuse(...)` already turns `locked` into `buy.locked-click` (add the spawner-specific message `buy.spawner-locked` / `buy.spawner-capped` when the item is a spawner). New method `spawnerRows(LoreBuilder.Section, Player, Item)` returns the extra rows (drop name + sell price, `On your island: 12/32`). **One allowed seam** in `ShopTooltips.spawnerRows`: add the single line `module.spawnerRows(info, viewer, item);` after the payback row (coordinate with the GUI lane; if the method moved put the call at its end). Nothing else in that file.
14. `ShopModule.buy` path for spawners stays (`StackingService.createSpawner`, engine, undo); the unlock and cap checks run in `state(...)` which `buy` already calls again before paying.

### 4e. Black Market and crates
15. `OfferCatalog`: `reward=spawner:<tier>[:<mob>]` (optional fixed mob; the existing random pick stays when absent; the mob must be in that tier's list at load, otherwise the offer is skipped with a console line). `Purchases` (line ~267 `case SPAWNER`) uses the fixed mob when present. Default offers (`BlackMarketSettings.offers`): remove `spawner_t5: slot=rare price=40 reward=spawner:5 limit=1/rotation`; add `spawner_iron_golem`, `spawner_wither_skeleton`, `spawner_evoker`: `slot=fixed price=400 reward=spawner:5:<mob> limit=1/day icon=<iron_block|wither_skeleton_skull|totem_of_undying>`. `BlackMarketSettings` gets `version()` + `valueChanges()` (untouched default list -> new list; an edited list stays and a console line tells staff to move the row).
16. Crates: the three `spawner_t5` rows (inferno 2%, phoenix 6%, eclipse 6% x2) are removed by the crates migration class of `ECON-SHARDS` step 7 (it also adds `shard_block` rows); `spawner_t3` and `spawner_t4` rows stay (they now pick one of 5 mobs).

### 4f. Credit shop contract (C1/I10; `CREDIT-SHOP.md`, nothing built here)
17. The owner wants premium spawners for credits. `CREDIT-SHOP.md` section 10 currently **forbids** `ItemType.SPAWNER` in the credit shop (closed set `{rank, item, tag, title}`; "power is never sold"). To allow it the credit shop lane must (a) add the entry type `spawner` (ref = a tier 5 mob id, validated against `StackingService.spawnerTypes(5)`) and (b) deliver with `StackingService.createSpawner(mob, 1, "credit_shop")`. Suggested catalogue lines: `spawner_iron_golem=spawner|iron_golem|2500|1|items`, `spawner_wither_skeleton=spawner|wither_skeleton|2500|1|items`, `spawner_evoker=spawner|evoker|2500|1|items` (2,500 credits each; a per-account season limit of 4 is a recommendation that needs a new field). **Until the credit shop lane agrees, premium spawners are Black Market only** (safe default); this spec adds no price key (the catalogue line is the one source).

### 4g. Docs, permissions, commands
18. `docs/commands/stacking.md` and `docs/commands/shop.md`: spawner section rewritten (tiers, unlock, cap, drops, premium); `docs/commands/blackmarket.md` offer syntax `spawner:<tier>[:<mob>]`; `CHANGELOG.md`. No new command and no new permission (placing and menus are island role `spawners` as today). `docs/commands/planned.md` untouched.
19. `help.yml` (menus module): the spawner topic gets 4 lines: tiers, unlock by kills, cap 32, premium. English, 36 characters per line.

## 5. DDL (module migration `modules/stacking/src/main/resources/db/stacking/V003__spawner_unlock.sql`)
```
-- Kills of tier 1-4 mobs per island (unlock of the next spawner tier). Written in batches, never trusted for money.
CREATE TABLE IF NOT EXISTS stacking_kill_counts (
  island_id BINARY(16) NOT NULL,
  mob VARCHAR(48) NOT NULL,
  kills BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (island_id, mob)
);
```
Idempotent, portable to H2 (MySQL mode) and MariaDB 11.8, never edits V001/V002. No data migration (kills start at 0; owners of existing tier 2 spawners are not gated because the gate only blocks shop **purchases**).

## 6. Config keys (`stacking.yml`, defaults; all `Setting` with desc, owner level where it moves the economy)
| Key | Default | Meaning |
|---|---|---|
| `spawners.min-interval-seconds` | 10 | unchanged |
| `spawners.max-interval-seconds` | 10 (was 40) | fixed 10 s |
| `spawners.interval-jitter-percent` | 0 | optional desync |
| `spawners.interval-floor-seconds` | 4 | hard floor |
| `spawners.mobs-per-spawner` | 1.0 | unchanged |
| `spawners.stack-start` / `spawners.stack-max` | 8 / 32 (was 512) | per block |
| `spawners.max-per-tier` | 32 | cap per island and tier |
| `spawners.cap-perk-max` | 64 | most the credit perk adds |
| `spawners.tier-1` .. `tier-4` / `tier-5` | roster of `ECON-TABLES` A | tier 5 = premium |
| `spawners.legacy` | `[squid:2, magma_cube:3, glow_squid:4, piglin:4, shulker:5, ravager:5, breeze:5]` | allowed, never sold |
| `spawners.speed-steps-seconds` | `[10, 9, 8, 7, 6, 5]` | by speed level |
| `spawners.mob-steps` | `[1.0, 1.25, 1.5, 1.75, 2.0]` | by mobs level |
| `spawners.loot-steps` | `[1.0, 1.1, 1.2, 1.3, 1.4, 1.5]` | by loot level |
| `spawners.efficiency` | unchanged `[1-16:100, 17-32:60, 33-64:35, 65-128:20, 129+:10]` | |
| `spawners.unlock.tier-2.kills` / `tier-3` / `tier-4` | 1000 / 5000 / 20000 | kills of the previous tier |
| `spawners.unlock.tier-N.mobs` | empty = every mob of tier N-1 | narrow to named mobs |
| `spawners.unlock.tier-5.kills` | 0 | premium: no gate |
| `spawners.unlock.save-seconds` | 30 | write-behind |
| `spawners.drops` | 23 lines `mob:item id:amount` e.g. `pig:mobdrop_pig:1` | custom drop per mob |
| `spawners.drop-items` | 23 lines `item id=material:rarity` e.g. `mobdrop_pig=clay_ball:common` | item definitions |
| `spawners.rare-drops` | `[evoker:totem_of_undying:0.02]` | shulker line removed |
`shop-settings.yml`: `spawners.max-tier` 4, `spawners.dynamic-factor` 0.85, `income-per-hour.*` removed. `blackmarket.yml offers`: step 15. `sell.yml custom-items.spawner-drops`: 23 entries (prices in `ECON-TABLES` A).

## 7. Messages (English; `messages/stacking.yml`, `messages/shop.yml`; FORMAT tokens)
- `spawner.cap-reached`: `<prefix><c:error><sym:cross></c:error> <c:text>This island holds <c:value><max></c:value> tier <tier> spawners.` (the fix: `Raise the cap with a Spawner Cap perk in /credit shop.` only once the perk exists; until then omit the second sentence)
- `spawner.placed` (exists) keeps `<max>` = block limit.
- Menu lines: `menu.speed`: `<c:text>Every <c:value><seconds> s`; `menu.mobs`: `<c:text>Mobs per spawn <c:value><mobs>`; `menu.loot`: `<c:text>Loot <c:value>x<loot>`; `menu.island-count`: `<c:text>On this island <c:value><owned>/<cap>`; `menu.next-works`: `<c:muted>Next one works at <percent>%`.
- `drops.<item id>` names (23, see `ECON-TABLES` A). Item lore keeps the FORMAT template; footer `<RARITY> MATERIAL`.
- Shop: `entry.spawner-unlock`: `["<c:text>Unlock: kill <c:value><needed></c:value> <what>", "<c:muted><have>/<needed>"]` where `<what>` = `tier <n> mobs` or the mob name; `entry.spawner-cap`: `<c:text>This island holds <c:value><max></c:value> of this tier`; `buy.spawner-locked`: `<prefix><c:error><sym:cross></c:error> <c:text>Locked. Kill <c:value><left></c:value> more <what> on this island.`; `buy.spawner-capped`: `<prefix><c:error><sym:cross></c:error> <c:text>This island already holds <c:value><max></c:value> tier <tier> spawners.`; `lore.spawner-drop`: `<c:text>Drops <c:value><item></c:value> <c:muted>$<price> each`; `lore.spawner-island`: `<c:text>On your island <c:value><owned>/<cap>`.
- Black Market premium offer lore: `<c:text>Tier 5 spawner. Works on every island.` (messages/blackmarket.yml `offers.spawner_*.lore`).

## 8. Permissions, commands, tab completion
No new permission or command. Existing `/spawner give <player> <type> [amount]` (`skyblock.spawner.give`) tab-completes `type` with `Args.oneOf("type", () -> tiers.types())` (`StackingModule.java:205`), which keeps listing legacy and premium mobs. Black Market admin tab completion of offer ids unchanged.

## 9. Tests (names; pure unless noted; none needs a server)
`SpawnerStepsTest` (clamp, floor, empty list), `SpawnerRulesTest` (room math, grandfather, perk clamp), `UnlockRulesTest` (tier 1 and 5 always open, narrowing to named mobs, kills at the threshold), `UnlockTrackerTest` (H2: batch upsert adds, flush on removal, island delete clears), `SpawnerDropsTest` (fraction rounding keeps the expected value over 100,000 rolls, loot level, never negative), `SpawnerTiersTest` (extend: legacy allowed but not sellable, never-list wins, duplicate reporting), `SpawnerIncomeTest` (pure: equal income inside a tier, payback table of `ECON-TABLES` B for the bundled config, monotone by tier, all five in 60 to 122 h at equal knobs `SpawnerPaybackTest`), `StackingConfigMigrationTest` (old default lists change, edited lists stay), `SpawnerRosterTest` (bundled `stacking.yml`: 20 sold mobs + 3 premium, every mob has a drop, every drop id is in `sell.yml custom-items.spawner-drops` with a price of at least 1 `SpawnerDropsSellableTest`), `MobDropMaterialTest` (no food, throwable, potion, spawn egg, placeable block, bucket, music disc or tool as a base material), `ShopSpawnerCatalogTest` (bundled `shop.yml`: 20 spawner items, prices 100k/250k/500k/1M, premium and legacy rejected, `BuySellInvariantTest` still green), `OfferCatalogTest` (extend: `spawner:5:iron_golem` parses, unknown mob refused), `StackingMessagesTest` (extend: every new key exists).

## 10. Manual checklist (Java + Bedrock; run by the owner or on the test server with permission)
1. Fresh island: `/shop` Spawners tab shows 4 rows of 5; tier 2 to 4 rows grey with `Unlock: kill 1,000 tier 1 mobs  0/1,000`; Bedrock shows the same lore.
2. Buy a pig spawner ($100,000), place it, wait 10 s: one pig appears per spawner per 10 s (check with 8 stacked: 8 per 10 s). Kill them: `Pig Bristle` drops; `/sell` pays $5 each.
3. Kill 1,000 tier 1 mobs (stack kills count): the tier 2 tile opens, buying works, progress survives a restart.
4. Place 32 tier 1 spawners across blocks: the 33rd place is refused; adding to a block is limited to the room left; a block above 32 (staff-made before the change) keeps working and cannot grow.
5. Legacy: an existing squid spawner still spawns and drops vanilla loot; it is not in /shop.
6. Premium: Black Market shows 3 fixed offers at 400 shards; a purchase gives the chosen mob; placing counts toward the tier 5 cap.
7. Island disband/reset: spawners and kill counts are cleared; an audit row `spawner_unlock_reset` exists.
8. Upgrade hooks return 0 by default: nothing changes until the progression lane wires levels (test with a debug `IslandUpgradeEffects` stub).
9. Drop items cannot be crafted, smelted, brewed, composted or eaten.

## 11. Risks and rollback
- **Economy:** income depends on the sell price of 23 new items and on the 360 mobs/h default (2.5x the old 144/h). Mitigation: all numbers in config; section B of `ECON-TABLES` is the check; C14 reruns the model before the owner locks. **Rollback of numbers:** edit `spawners.max-interval-seconds` back to 40 and drop prices; no migration needed.
- **Reversal of R3-21a and S20** (owner statements); recorded in the ledger batch.
- **Existing islands:** 30-mob roster shrinks to 23 sold mobs; legacy mobs keep working; stack blocks above 32 are grandfathered. If the owner dislikes the cap: `spawners.max-per-tier` 0 = unlimited (guard in `SpawnerRules`).
- **Mob load:** 360 mobs/h per spawner and 160 spawners can mean many stacks; the existing limits (8 stacks per type per chunk, 40 per chunk) stay; add the spike test `SpawnerLoadTest` (pure scheduler simulation, 384 due blocks) before release.
- **Shop seam** in `ShopTooltips` can conflict with the GUI lane's rewrite: if the file changed, re-apply the single call line.
- **Rollback of data:** V003 only adds a table; drop is safe.

## 12. Audit and ledger reasons
Audit (category `island`, like the existing `spawner_place`): `spawner_cap_refused` (new: actor, island, tier, owned, cap) and `spawner_unlock_reset` (new: island, rows deleted). Ledger: no new reason (spawner purchases keep `shop_buy`; Black Market `black_market`). Decision ids: see the `ECON-R1` batch in `LEDGER.md`.

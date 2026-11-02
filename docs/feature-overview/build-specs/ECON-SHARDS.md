# Build spec ECON-SHARDS: the shard block is the ONLY source of shards; every other shard payout is removed

**Status: PROVISIONAL.** Cards C1, C13, F5, I3, I4, E5 name other shard sources; the owner's chat answer of 1 Nov 2026 supersedes them: **"Shards only from blocks, no other rewards"**, EP-Q7 note (shard block: sold in the credit shop and found in crates, a stone block, breaking gives 1 shard, right-click upgrades respawn speed and chance of double), EP-Q6 note (Black Market stays in shards). Ledger batch `ECON-R1`. Real code read: `/Users/sindreskogholt/Development/skyblock-wt-tutfix` (branch `q/gui-phase2`). No code in this file. Numbers: `ECON-TABLES.md` section C. Written 1 Nov 2026.

## 0. Goal
A new module `shardblock`: a placeable island block `shard_block`. Breaking it gives **1 shard** (sometimes 2), it **regrows** after a configurable time, right-click opens a small **upgrade GUI** (respawn speed in crystals, double chance in shards), state is **persisted**, every dupe path is closed, inflow is **bounded** (4 blocks per island, 100 shards per profile and day). At the same time **every other shard payout is deleted** and every note that assumes shard income is rewritten.

## 1. Assumptions and boundaries
- GUI lane (parallel) owns `common/menu` and the shop menus. **Do NOT edit** `common/.../menu/*` or any shop class. The shard block GUI is a new `Menu` subclass in the new module built from public pieces only (`Menu`, `MenuButton`, `Centered.columns`, `MenuLayout`, `core.menus().icon/item`). Formats: `docs/FORMAT.md` (panel `P3`, wallet tile on slot 4, tile template, `» CLICK`), `docs/UI_STYLE.md`.
- The credit shop (C1/I10) is not built: the **sale** of the block there is a contract (section 12); until then staff give blocks and crates drop them.
- The progression lane is not involved. The ranks lane is not involved.
- Pack art is later: the block has a plain vanilla look now (`infested_stone` = looks like stone, `bedrock` while regrowing); the owner's pack retextures it.

## 2. Decisions (developer defaults; owner only vetoes)
| # | Decision | Why |
|---|---|---|
| H1 | Own module `shardblock` (clean ownership, own permissions, config, messages, DDL) | CLAUDE.md "Adding a module" |
| H2 | Active block `infested_stone` (unobtainable in survival, looks like stone, hardness 0.75), depleted block `bedrock` (cannot be mined or pushed); both configurable | the owner said "a stone block"; a block players cannot get avoids look-alike confusion; bedrock regrow state cannot be farmed or broken |
| H3 | **Vanilla breaking never happens**: `BlockBreakEvent` on a tracked position is always cancelled; the plugin pays and swaps the block itself | one code path to audit, no drops, no XP |
| H4 | Breaker's **active profile** gets the shard (deposit reason `shard_block`); placer and block belong to the island | profiles are islands; shards are per profile |
| H5 | Island role permission **new key `shard-blocks`** (IslandPerm `SHARD_BLOCKS("shard-blocks", true, true)`, stored members only, default Admin, Mod, Member; Coop and visitors never) for place, break, upgrade; pick-up needs it AND (you placed it OR you have `settings`) | "owned/protected by island rules" |
| H6 | Respawn upgrades cost **crystals**, double-chance upgrades cost **shards** (config), paid by the clicking player's profile, the block keeps the level | crystals sink + shard sink; both in `shardblock.yml upgrades.*` |
| H7 | Upgrade levels travel with the item: pick-up returns the same block with levels in item PDC, placing restores them | no value lost on a move, no dupe (row deleted in the same transaction as the item is created) |
| H8 | Caps: 4 blocks per island, 100 shards per profile per Oslo day (enforced in the payout transaction), 1 break per 400 ms per player | bounded inflow; anti auto-click spam |
| H9 | Nothing accumulates while away: the block only pays on a player break | no offline or AFK income |
| H10 | Block item is a normal tradeable item (AH, `/trade`) unless the credit shop binds it (C1-R2-29 rules); not in `/shop` (722) | one source of value (shards) already bounded |
| H11 | `RewardType.SHARDS` and every code path that can pay shards other than `shardblock` are **deleted**, a source-scan test fails the build if one returns | "no other rewards" |

## 3. Exhaustive audit: where shards are paid today and what changes
Verified by `grep` over `modules/`, `common/`, `plugin/`, `server/` on the deployed branch. **The owner's list (pass, daily, vote, milestones, tutorial, quests, collection log) does not pay shards in code today**: `RewardSpec.Kind` has only money, crystals, credits, key, fuel, booster, book, scroll, tome; pass, quests, tutorial, collection and achievements modules contain no shard code. So the real removals are few.

### 3.1 Code and bundled config (all changed in this build)
| # | Where | What it does today | Change | What it pays instead |
|---|---|---|---|---|
| 1 | `modules/crates/src/main/resources/crates.yml` line 169, crate `conqueror`: `{id: shards, type: shards, chance: 15, amount: 5}` | the only shard payout in the game (hidden crate until KOTH) | row deleted by migration (step 16) | `shard_block` 2% top reward |
| 2 | `crates/RewardType.java` enum value `SHARDS("shards", true)` + `OpenPlan.java:58,140` (`case SHARDS`), `OpenEngine.java:48` (currency loop), `CrateOpener.java:310,596`, `CrateCatalog.java:232`, `CratesModule.java:477,515` | crate reward type | **deleted**; a `type: shards` row in a live file is skipped with a console warning ("shards are only found in shard blocks") | nothing |
| 3 | `modules/rewards/.../ClaimEngine.java:90` loop `List.of(MONEY, CRYSTALS, CREDITS_EARNED, SHARDS)` | generic payout loop used by daily, streak, vote, playtime (no spec produces shards) | remove `Currency.SHARDS` from the list | nothing; daily, streak, vote, pass never pay shards |
| 4 | `economy.yml start.shards` (`EconomyConfig.startShards`, default 0) | start balance | keep 0; `EconomyConfigTest` asserts default 0 | nothing |
| 5 | Inflow from other modules (pass, quests, tutorial, collection log, achievements, milestones, island tasks, Depths, events) | none in code | `NoShardPayoutTest` (section 10) forbids it | nothing |
| 6 | Crates rows `spawner_t5` (inferno 2%, phoenix 6%, eclipse 6% x2) | premium spawners from crates | deleted by the same migration (ECON-SPAWNERS S10) | `shard_block` rows below |

### 3.2 Kept on purpose (not income)
`/eco give|set|reset shards` (reasons `admin_give`, `admin_set`, `admin_reset`; staff only), `/pay`, `/withdraw` shard notes and `/trade` (they only move existing shards), `black_market_refund`, profile delete and restore backups, sanction restore. `Currency.SHARDS` itself, balances, baltop, and the economy-watch thresholds stay.

### 3.3 Docs, cards and texts that assumed shard income (edited in step 13)
| File | Text now | New text |
|---|---|---|
| `features/C13.md` "How it works" last bullet, Open question 2, Connections | "Shard income today: none ... bosses, KOTH, envoy, tier 9+" | "Shards come only from shard blocks on islands (ECON-SHARDS). Regular about 10 a day, whale at most 100 a day." |
| `features/I3.md` lines 14, 49, 59 (KOTH, envoy rewards) | "Conqueror key, shards ..." / "#43 shards come from bosses, envoys, KOTH" | keys, XP, crystals, money and shard blocks as a rare reward; no shards |
| `features/I4.md` lines 19, 51 (boss bands "Shards: top 3 get 20/15/10 ...") | shard bands | remove the shard bands; bands pay money, books, set XP, a rare `shard_block` for Gold and top 3 (amounts decided in I4) |
| `features/E5.md` (geode, key fragments) | crystals only | unchanged, but remove "Rare Depths items give crystals or shards" in `C4.md` line 29 |
| `docs/ECONOMY_BALANCE.md` section 9.3 table ("World boss 20/15/10, KOTH 10, Envoy 1-3, Depths tier 9+ 1") | shard sources | replaced by the table of `ECON-TABLES.md` C |
| `docs/phases/PHASE-10-REWARDS.md` lines 24, 225, 385, 411; `docs/planning/PHASE-9-EVENTS.md` lines 10, 28, 43; `docs/planning/CUSTOM_MOB_DROPS.md` line 37 ("a few rare ones sell for shards"); `docs/planning/CURRENCIES_TRADING_BOOSTING.md` line 29; `docs/OPEN_QUESTIONS.md` item "Shards: how are they earned?"; `docs/QUESTIONS_ROUND2.md` 43; `docs/DESIGN.md` line 184 | boss/KOTH/envoy/tier 9+ sources | "shard blocks only" (one line each) |
| `modules/menus/src/main/resources/help.yml` (Black Market topic ~line 1335 "shards from bosses, events and the deepest Depths tiers"; currencies topic) and `modules/blackmarket/src/main/resources/messages/blackmarket.yml` (`no-shards` line 20 and the lore ~line 77 "Shards come from bosses, events ...") | boss/event sources | `Shards come from shard blocks on your island.` |
| `modules/economy/.../C1-R2-07` text in `features/C1.md` / LEDGER (shard reasons `boss_reward, koth, envoy, depths_rare, crate_reward, admin_*`) | | superseded: allowed shard reasons are `shard_block` and `admin_*` only (ledger line `ECON-R1-02`); when the C1 lane builds the closed-source enum it uses exactly that list |

## 4. Steps (ordered)
### 4a. `common`
1. `TxReason`: add `SHARD_BLOCK = "shard_block"` (payout, documented as the only shard income) and `SHARD_BLOCK_UPGRADE = "shardblk_upgrade"` (a sink, ≤ 32 chars). `BoxSource`: add `SHARD_BLOCK = "shard-block"`.
2. **No new `ItemType`**: the shard block is registered as `ItemType.MATERIAL` (footer `RARE MATERIAL`), because the credit shop's closed set (`CREDIT-SHOP.md` section 10) only sells MATERIAL, COLLECTOR, CONSUMABLE or COSMETIC items. `IslandPerm` (islands module) add `SHARD_BLOCKS("shard-blocks", true, true)` with default roles in `islands.yml permissions.role-defaults.shard-blocks: [admin, mod, member]` (migration default only for untouched files, like the other late keys), message names in `messages/islands.yml` (`permissions.shard-blocks`: `use shard blocks`), and `IslandService.PERM_SHARD_BLOCKS = "shard-blocks"`.
3. `common/.../stacking`: none.

### 4b. New module `modules/shardblock` (package `net.skybornmc.skyblock.shardblock`)
Gradle: `modules/shardblock/build.gradle.kts` copied from `modules/blackmarket/build.gradle.kts` (same H2 test dependencies), `include("modules:shardblock")` in `settings.gradle.kts`, `implementation(project(":modules:shardblock"))` in `plugin/build.gradle.kts`, `modules.register(new ShardBlockModule(core))` in `SkyblockPlugin#registerModules` after economy and islands; `dependencies()` = `economy`, `islands`, `profiles`.
4. `ShardBlockModule` (SkyblockModule): loads config/messages, registers the item, the listeners, the command, the regrow task, publishes `ShardBlockService` (small interface in the module, used by tests and the credit shop contract: `ItemStack create(int amount, String source)`).
5. `ShardBlockConfig extends ModuleConfig` (`shardblock.yml`, section 6) with `version() = 1`.
6. `ShardBlockItems`: registers id `shard_block` (`ItemDefinition.builder("shard_block", activeMaterial, ItemType.MATERIAL, Rarity.RARE)`, name `Shard Block`, subtitle/description/actions from `messages/shardblock.yml item.*`, renderer shows `Respawn level n` and `Double chance level n` when the PDC keys exist). Not unique (levels 0/0 stack); PDC keys `shard_block_resp` and `shard_block_dbl` (INT) set only by pick-up. `create(amount, source)` uses `core.items().create("shard_block", amount, source, pdc -> ...)`.
7. `ShardRules` (pure): `respawnSeconds(level)`, `doublePercent(level)`, `cost(kind, nextLevel)` returning `Cost(Currency, long)`, `rollAmount(double doublePercent, RandomGenerator)`, `dayKey(Instant, ZoneId)`, `capRemaining(long usedToday, int cap)`, config validation (list lengths: costs = levels - 1).
8. `ShardBlockStore` (async, `core.database()`): `loadIsland`/`loadAll`, `insert`, `archive(blockId, reason)` (insert into archive + delete, one transaction), `collect(...)` (guarded update, below), `upgrade(...)`, `regrow(blockId)`, `dailyUsed(profile, day)`.
9. `ShardBlockManager` (Listener): in-memory `Map<Pos, Block>` and a due-queue (`PriorityQueue` by `ready_at`, polled by a repeating task every `regrow.check-seconds`, at most `regrow.per-tick` per run; chunk-unloaded blocks wait and are fixed on `ChunkLoadEvent`). Handlers:
   - `BlockPlaceEvent` (HIGH, ignoreCancelled): item id `shard_block`; refuse (cancel + message) when: world not in `worlds`, no island at the position, role lacks `shard-blocks`, island already has `max-per-island`, profile switching/frozen sanction for items, `core.items()` dupe-flagged. One tick later (the spawner pattern of `SpawnerManager.onPlace`): block still present, then `PlayerSaves.commitAfterSave(player, "placing a shard block", () -> store.insert(...))`; on failure remove the block and give the item back. Levels from the item PDC go into the row.
   - `BlockBreakEvent` (HIGH): tracked position -> always cancel; `collect(player, block)` (below). Untracked blocks are not touched.
   - `BlockDamageEvent` and `PlayerInteractEvent` LEFT_CLICK_BLOCK on a depleted block: action bar `Regrows in <time>`, throttled to once per second.
   - `PlayerInteractEvent` RIGHT_CLICK_BLOCK (main hand) on a tracked block: cancel, open `ShardBlockMenu`; sneaking with a block in hand still opens the menu (never places against it).
   - `EntityExplodeEvent`, `BlockExplodeEvent`: remove tracked positions from the block list. `BlockPistonExtendEvent`, `BlockPistonRetractEvent`: cancel when a tracked block is among the moved blocks or the destination. `BlockBurnEvent`, `BlockFadeEvent`, `EntityChangeBlockEvent` (enderman, wither, falling block), `BlockFromToEvent`, `BlockDispenseEvent` (dispensing the item) and `PlayerBucketEmptyEvent`/`Fill` onto a tracked block: cancel. `IslandRemovedEvent`: archive the island's rows (reason `island_removed`) and set the blocks to air; `PlayerQuitEvent`: nothing (state is in the database).
10. `collect(player, block)` (the one place that pays shards; main thread start, async finish):
    1. refuse when the player is frozen/switching/not in an eligible state, lacks `shard-blocks`, the break gap is under `interaction.break-gap-ms`, or the row is `regrowing` (action bar `Regrows in <time>`).
    2. `amount = ShardRules.rollAmount(doublePercent(level))` (1 or 2); `remaining = daily cap - used today` from the cached counter; if `remaining <= 0` action bar `Daily shard limit reached (100). New day at 00:00 Oslo.` and the block stays ready; `amount = min(amount, remaining)`.
    3. one `economy.executeGuarded(Transaction.deposit(AccountRef.player(profile), Currency.SHARDS, amount, TxReason.SHARD_BLOCK).idempotencyKey("shard_block:" + blockId + ":" + breaks).actor(player).reference("shard_block:" + blockId), guard)` where `guard` (same database transaction, may run twice) does: `UPDATE shard_blocks SET state='regrowing', ready_at=?, breaks=breaks+1, shards_paid=shards_paid+?, updated_at=? WHERE block_id=? AND state='ready' AND breaks=?` (must hit 1 row) and the capped counter `UPDATE shard_block_daily SET shards=shards+? WHERE profile_key=? AND day=? AND shards+?<=?` (if 0 rows: `INSERT IGNORE` the day row with 0, repeat once; still 0 = cap reached = return false). `false` = nothing paid, nothing changed (`Status.DUPLICATE`: re-read the row and tell the player the real reason).
    4. on success (main thread): set the block to the depleted material, particle + sound `success`, action bar `✔ +1 shard · regrows in 30m`, update the cached daily counter, schedule regrow at `ready_at`, audit `shard_block_break`.
    A crash between payout and block swap leaves the database as truth: at the next chunk load the block is set to depleted or active from the row state.
11. `ShardBlockMenu` (extends `Menu`, 3 rows, `P3`): slot 4 wallet tile (crystals and shards balances, name `Your Wallet`), centred row 1 with two upgrade tiles at slots 12 and 14 (`Centered.columns(2)`): `Faster Respawn` (clock icon key `shard/respawn`, base item `clock`) and `Double Chance` (key `shard/double`, base item `amethyst_shard`); tile lines (≤ 36 characters): name, `| Level: 2/5`, `| Now: every 20 min`, `| Next: every 15 min`, `| Cost: 900 ◆`, `» CLICK to upgrade`; maxed = `✔ Maxed` muted, grey dye. Bottom row: action A (slot 19) `Pick Up` (hopper icon; `» CLICK to pick up`), Close 22. Slot 8 stays empty (no danger tile: pick-up loses nothing). Upgrade click: `executeGuarded(Transaction.withdraw(AccountRef.player(profile), cost currency, amount, TxReason.SHARD_BLOCK_UPGRADE).idempotencyKey("sbu:" + blockId + ":" + kind + ":" + nextLevel), guard)` with guard `UPDATE shard_blocks SET <col> = <col> + 1 WHERE block_id = ? AND <col> = ?` where `<col>` is `respawn_level` or `double_level` (must hit 1 row, so two players upgrading at once pay once); insufficient funds shows the missing amount (`You need 900 ◆, you have 612`). `tickInterval` 20 refreshes the regrow countdown. Pick-up: `store.archive(blockId, "pickup")` then `ItemBox.give(services, player, BoxSource.SHARD_BLOCK, item)` (remove first, give second, then `PlayerSaves.save`), block set to air, audit `shard_block_pickup`.
12. `ShardBlockCommand` (section 8) and `ShardBlockAdmin` helpers (give, info, remove, stats).
13. Docs and texts of 3.3, `docs/commands/shardblock.md`, `docs/COMMANDS.md` via `./gradlew commandsIndex`, `CHANGELOG.md`, `server/permissions/shardblock.txt`, a new `help.yml` topic `shards` (3 pages: what a shard is, the block, upgrades; `take-me-there` to nothing) and `help.yml`/`blackmarket.yml` text edits of 3.3.

### 4c. Crates (`modules/crates`)
14. Delete `RewardType.SHARDS` and every `case SHARDS`/currency loop entry of 3.1 row 2. `type: shards` in a file -> warn and skip the row (it must never pay).
15. `type: item` accepts `material: custom:<id>`: `CrateOpener` `validItem` (id registered in `core.items()`), the hand-out `case ITEM` (`core.items().create(id, amount, origin, null)`), `CratesModule.icon` uses `icon:`. No new type.
16. New pure `CatalogMigration` (next to `CatalogMerge`, called by `CratesModule` once when `crates.yml` has no `catalog-version: 2`): removes the rows `shards` (conqueror) and `spawner_t5` (inferno, phoenix, eclipse) **only if the row still equals the old default**, adds the rows below if absent, writes `crates.yml.before-v2` and a log `crates-migration-v2.log`, sets `catalog-version: 2`. New defaults in the bundled `crates.yml`:
    - blaze: `{id: shard_block, type: item, material: "custom:shard_block", icon: infested_stone, chance: 0.5, amount: 1}`
    - inferno: same, `chance: 1, top: true, rarity: rare`
    - phoenix: `chance: 2, top: true, rarity: rare`
    - eclipse: `chance: 3, top: true, rarity: epic`
    - conqueror: `chance: 2, top: true, rarity: rare`
    Messages (`messages/crates.yml`): `rewards.<crate>.shard_block: Shard Block` for the five crates; delete the `shards` generic (`shards: "Shards (<amount>)"`, two places) and the `spawner_t5` names.

### 4d. Rewards, Black Market, economy
17. `ClaimEngine.transactions`: remove `Currency.SHARDS` from the currency list.
18. Black Market: prices unchanged except premium spawners (ECON-SPAWNERS step 15). Review table in section 9 (notes only; the one config change is `phoenix_key ... limit=1/rotation` from 2, via `BlackMarketSettings.valueChanges`, because 2 per 12 h x 50 shards would exceed the daily cap).
19. `WatchConfig`: shard thresholds stay (`staffMinShards` etc.), plus one new rule `rules.shard-block.*` (alert when one profile breaks more than `shardblock.daily-cap-per-profile` / 2 shards in 10 min, i.e. auto-clicker pattern): reads `shard_block` ledger rows of the last 2 h like the other rules; alert only.

## 5. DDL (`modules/shardblock/src/main/resources/db/shardblock/V001__shardblock.sql`)
```
-- Placed shard blocks. The database is the truth; a block without a row is a plain block and pays nothing.
CREATE TABLE IF NOT EXISTS shard_blocks (
  block_id BIGINT NOT NULL AUTO_INCREMENT,
  world VARCHAR(64) NOT NULL,
  x INT NOT NULL,
  y INT NOT NULL,
  z INT NOT NULL,
  island_id BINARY(16) NOT NULL,
  placed_by BINARY(16) NOT NULL,
  respawn_level INT NOT NULL DEFAULT 0,
  double_level INT NOT NULL DEFAULT 0,
  state VARCHAR(12) NOT NULL,
  ready_at BIGINT NOT NULL DEFAULT 0,
  breaks BIGINT NOT NULL DEFAULT 0,
  shards_paid BIGINT NOT NULL DEFAULT 0,
  placed_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (block_id),
  CONSTRAINT uq_shard_blocks_pos UNIQUE (world, x, y, z)
);
CREATE INDEX IF NOT EXISTS idx_shard_blocks_island ON shard_blocks (island_id);
CREATE INDEX IF NOT EXISTS idx_shard_blocks_ready ON shard_blocks (state, ready_at);

-- Removed blocks (pick-up, island removed, staff), kept for staff restore; never deleted by the plugin.
CREATE TABLE IF NOT EXISTS shard_blocks_archive (
  archive_id BIGINT NOT NULL AUTO_INCREMENT,
  block_id BIGINT NOT NULL,
  world VARCHAR(64) NOT NULL,
  x INT NOT NULL,
  y INT NOT NULL,
  z INT NOT NULL,
  island_id BINARY(16) NOT NULL,
  placed_by BINARY(16) NOT NULL,
  respawn_level INT NOT NULL,
  double_level INT NOT NULL,
  breaks BIGINT NOT NULL,
  shards_paid BIGINT NOT NULL,
  reason VARCHAR(24) NOT NULL,
  archived_by VARCHAR(36) NULL,
  archived_at BIGINT NOT NULL,
  PRIMARY KEY (archive_id)
);
CREATE INDEX IF NOT EXISTS idx_shard_blocks_archive_island ON shard_blocks_archive (island_id);

-- Shards paid per profile and Oslo day (the daily cap, checked inside the payout transaction).
CREATE TABLE IF NOT EXISTS shard_block_daily (
  profile_key BINARY(16) NOT NULL,
  day INT NOT NULL,
  shards BIGINT NOT NULL,
  PRIMARY KEY (profile_key, day)
);
```
Portable H2 (MySQL mode) and MariaDB 11.8, idempotent, no data migration. Old day rows are purged by a weekly async delete (`DELETE ... WHERE day < today - 14`).

## 6. Config `shardblock.yml` (defaults)
| Key | Default |
|---|---|
| `enabled` | true |
| `worlds` | `[islands]` |
| `block.active-material` | `infested_stone` (item look too) |
| `block.depleted-material` | `bedrock` |
| `block.max-per-island` | 4 |
| `shards.per-break` | 1 |
| `shards.daily-cap-per-profile` | 100 (0 = off) |
| `shards.cap-zone` | `Europe/Oslo` |
| `levels.respawn-seconds` | `[1800, 1500, 1200, 900, 600, 420]` |
| `levels.double-percent` | `[0, 5, 10, 15, 20, 25]` |
| `upgrades.respawn` | `[crystals:100, crystals:300, crystals:900, crystals:2700, crystals:8100]` |
| `upgrades.double` | `[shards:10, shards:25, shards:50, shards:100, shards:200]` |
| `interaction.break-gap-ms` | 400 |
| `regrow.check-seconds` | 1 |
| `regrow.per-tick` | 200 |
| `sounds.break` / `sounds.upgrade` / `sounds.regrow` | `success` / `success` / (empty) |
`crates.yml`: step 16. `economy.yml` unchanged. `islands.yml permissions.role-defaults.shard-blocks`: `[admin, mod, member]`.

## 7. Messages (`messages/shardblock.yml`, English, FORMAT tokens)
- `item.name` `Shard Block`; `item.subtitle` `<c:text>Place it on your island.`; `item.description`: `Break it for a shard.` / `It regrows. Right-click to upgrade.`; actions `» CLICK to place`.
- `place.ok`: `<prefix><c:success><sym:check></c:success> <c:text>Shard block placed. Break it for <cur:shards>1 shard</cur:shards>.`; `place.limit`: `<prefix><c:error><sym:cross></c:error> <c:text>This island already has <c:value><max></c:value> shard blocks.`; `place.no-perm`: `... <c:text>Your role can't use shard blocks here.`; `place.world`: `... <c:text>Shard blocks only work on islands.`
- `break.bar`: `<c:success><sym:check></c:success> <cur:shards>+<amount> shard<s></cur:shards> <c:muted>· regrows in <time>`; `break.regrowing`: `<c:muted>Regrows in <c:value><time>`; `break.cap`: `<c:error><sym:cross></c:error> <c:text>Daily shard limit reached (<c:value><cap></c:value>). New day at 00:00 Oslo.`; `break.slow`: `<c:muted>Slow down.`
- Menu: `menu.title` `Shard Block` (plaque noun SHARD BLOCK); `menu.respawn.name` `<area:skills><bold>Faster Respawn`; `menu.double.name` `<area:skills><bold>Double Chance`; `menu.level`, `menu.now`, `menu.next`, `menu.cost`, `menu.maxed` (`✔ Maxed`), `menu.pickup.name` `Pick Up`, `menu.pickup.lore` `Returns the block with its levels.`; `upgrade.ok`, `upgrade.poor` (`You need <price>, you have <have>.`), `upgrade.changed` (`Someone upgraded it first.`).
- `pickup.ok`: `... <c:text>Shard block picked up. Levels kept.`; `pickup.box`: ` ... Inventory full: it is in /box.`; `pickup.not-yours`: `... Only the placer or an island manager can pick it up.`
- Staff: `admin.given`, `admin.info` (owner, island, levels, state, ready in, breaks, shards paid), `admin.removed`, `admin.stats`.

## 8. Permissions, commands, tab completion
`modules/shardblock/src/main/resources/permissions/shardblock.yml` (one owner per node; `PermissionFilesTest` checks):
`skyblock.shardblock.use` (default true; place, break, upgrade, pick-up; island roles still decide), `skyblock.staff.shardblock.give` (op), `skyblock.staff.shardblock.view` (op; info, stats), `skyblock.staff.shardblock.remove` (op). Grants in `server/permissions/shardblock.txt`: `lp group mod permission set skyblock.staff.shardblock.view true`; `lp group admin permission set skyblock.staff.shardblock.give true`, `...remove true`.
Command `/shardblock` (description "Staff tools for shard blocks", `category("Staff")`, `permission skyblock.staff.shardblock.view`): `give <player> [amount]` (arg `Args.onlinePlayer("player")`, amount `Args.integer("amount", 1, 64, "1", "4", "16")`; audited `shard_block_give`, delivery `ItemBox.give(..., BoxSource.STAFF)`), `info` (the block you look at, up to 6 blocks away), `remove` (the block you look at: archive reason `staff`, block to air, item to the placer's `/box`), `stats` (blocks total, per island top 5, shards paid today and total). Every argument tab-completes; no player command (players use the block itself and the help topic).

## 9. Black Market and shard price notes (values for `ECON-TABLES.md` C)
Black Market prices were written for 5 to 10 shards a day; a regular now gets about 10 a day and a whale at most 100. Rows unchanged: regalia 400 (1 per season), voidforged 250, mythic book 200, coinmaster 120, collector 100, autosell chest 120, eclipse key 75 (1 per month), legendary book 60, phoenix key 50 (**limit 2 -> 1 per rotation**), blueprint 40, elytra 30, bm tag 60, sell wand 25, wardrobe tome 20, disenchant scroll 8, island charters 150/300/450; new: 3 premium spawners 400 (limit 1 per day). Revisit after one week of real inflow (`/shardblock stats`, economy dashboard).

## 10. Tests (names; none needs a server)
`ShardRulesTest` (level tables, cost lookup, roll distribution over 100,000 rolls within 1% of the double chance, day key at Oslo midnight and DST, cap math), `ShardBlockConfigTest` (bundled file valid; list lengths; defaults of section 6), `ShardBlockStoreTest` (H2: insert, unique position, collect guard hits once, two parallel collects pay once, state `regrowing` refuses, archive moves row, daily cap conditional update refuses at cap and is exact under 50 parallel calls), `ShardPayoutAtomicityTest` (guard false = no ledger row, no state change; ledger failure rolls back the row update; idempotency key replay pays nothing), `ShardBlockItemsTest` (id, PDC levels round trip, stacking at level 0), `ShardBlockMenuTest` (slot layout, tile states, maxed), `ShardBlockMessagesTest` (every key used exists), `NoShardPayoutTest` (**source scan**: no `Transaction.deposit` or `deposit(...)` with `SHARDS`/`"shards"` outside `modules/shardblock`; no `type: shards` or `shards:` reward key in any bundled `crates.yml`, `rewards`, `pass`, `quests` resource; `ClaimEngine` loop has no shards), `CratesMigrationTest` (pure `CatalogMigration`: old default rows removed, edited rows kept, new rows added once, second run changes nothing, shards row never survives), `CrateCatalogTest` (extend: `type: shards` is skipped, `material: custom:shard_block` valid), `OpenPlanTest` (extend: custom item grant), `IslandPermTest` (extend: `shard-blocks` key, defaults), `TxReasonTest` (extend: valid, ≤ 32).

## 11. Manual checklist (Java + Bedrock)
1. `/shardblock give <me> 4`, place 4 on the island: 5th is refused; placing on spawn or in the Depths is refused; a visitor/coop cannot place or break.
2. Break one: +1 shard (action bar), block turns to bedrock, left-click shows `Regrows in ...`; after 30 min (test config 20 s) it is infested stone again; restart mid-regrow: timer continues from the database.
3. Right-click: menu opens (Bedrock too); upgrade respawn costs 100 crystals; double chance costs 10 shards; wrong balance shows the missing amount; levels survive a restart.
4. Pick up: item returns with levels; place it elsewhere: levels restored; inventory full -> `/box`.
5. Abuse: piston push/pull, creeper and TNT, water/lava, enderman, dispenser, fire: block stays and nothing is paid; creative break is refused; 20 clicks per second pay once.
6. Daily cap: with cap set to 3 the 4th break shows the limit message and the block stays ready; after midnight Oslo it pays again.
7. Island disband/reset removes the blocks (archive rows exist). Profile switch mid-click pays the profile that was active at the click or nothing.
8. Crates: Blaze to Eclipse can drop `Shard Block`; `crates.yml.before-v2` and the migration log exist after the first start; the conqueror `shards` row is gone; no crate pays shards.
9. `/bal` and `/baltop shards` still work; `/pay` shards works; nothing else raises the shard balance (watch the economy dashboard for an hour).

## 12. Credit shop contract (C1/I10; `CREDIT-SHOP.md` is the other lane's spec)
`CREDIT-SHOP.md` already lists the entry `shard_block=item|shard_block|0|16|items` in `creditshop.yml catalogue.items` (price 0 = hidden until the owner sets it, S11) and says "the ECON spec must register it as MATERIAL": done in step 6. **This spec does not add a price key**; the one source of the price is that catalogue line. Suggested placeholder when the owner agrees: `shard_block=item|shard_block|1000|4|items` (1,000 credits, at most 4 per order; a per-account season limit of 4 needs a `limit-per-season` field the credit shop lane does not have yet: recommendation only). Delivery uses the item registry (`core.items().create("shard_block", n, "credit_shop", null)` / `ShardBlockService.create`), bound or unclaimed by C1-R2-29. Mojang note: the item sells a path to tradeable shards (CREDIT-SHOP section 10 b); the owner accepted a mild competitive advantage (C1-R1-32) and the caps (4 blocks, 100 a day) bound it.

## 13. Risks and rollback
- **Balance:** shard value is set by the Black Market price list; whales are bounded by the daily cap and the per-account limits, but 4 islands (3 profiles) multiply the cap by 3 per account. Mitigation: economy-watch rule, `/shardblock stats`, caps are config.
- **Removal side effects:** hidden Conqueror crate loses its only shard row (replaced); any live `crates.yml` edited by hand keeps unknown rows until staff read the migration log. `RewardType.SHARDS` removal breaks no stored data (opens are logged by currency, not by type).
- **Look:** `infested_stone` and `bedrock` are placeholders; if Bedrock edition or Geyser maps them badly, change the two keys, nothing else.
- **Crash safety:** payout, state change and daily counter commit in one transaction; placing follows the spawner pattern (save player, then row); pick-up removes the row before giving the item.
- **Rollback:** set `shardblock.yml enabled: false` (blocks stay as stone, nothing pays); the migration keeps `crates.yml.before-v2`; tables are additive.

## 14. Audit and ledger reasons
Ledger: `shard_block` (deposit), `shardblk_upgrade` (withdraw crystals or shards); audit category `economy` rows from the ledger, category `island` actions `shard_block_place`, `shard_block_break` (batched per profile every 60 s like the satchel, one row with count and shards), `shard_block_pickup`, `shard_block_give`, `shard_block_remove`, `shard_block_archive`. Decision ids: `ECON-R1-02` to `ECON-R1-05` in `LEDGER.md`.

# Plan: money x100 (owner 6-7 Nov 2026, "bigger numbers, but balanced")

Status: **planned, not built yet** (deliberately after the gameplay batches: a half-done scale is worse than none).

## Rules
- One factor (100) for **money only**. Shards, crystals and credits stay.
- Done in one release with a database migration and a live-config step, recorded in `docs/decisions.yml` (idempotent: a marker row `economy_scale = 100` stops a second run).
- Ratios (percents, multipliers, minutes of income) never change.
- Block worth, island level `points.bank-base` and `points.blocks-unit` scale together with prices and balances, or island levels jump.

## What scales (inventory 6 Nov 2026)
**Knobs that already exist:** `sell.price-multiplier` (economy, max 1000), `buy.price-multiplier` (shop, max 10: raise to 1000), `forge.money-percent` (tools; module removed in batch 3).

**Flat values to x100** (bundled defaults and the live files):
- economy: `start.money`, `pay.min.money`, `pay.leave-flag.amount`, `pay.confirm-above.money`, `display.exact-below`, `notes.min/max.money`, `eco.approve-above.money`, `sell.hot-item.min-base`, `sell.market-tips.min-base`, `sell.big-sale-chat-from`, `pay-limit.tiers`; bank `deposit.min`, `withdraw.min`, `personal.tiers`, `island.levels`; watch `rules.*.min*` (money).
- shop: `feedback.chat-from`; spawner prices; `worth-only` block worth (via the knob).
- auction: `fee.min`, `price.min`, `price.max`. trade: `menu.money-steps`.
- progression: `upgrades.yml` costs (161), `masteries.yml` costs (67), `ascension.base-cost`, `tasks.yml` earn/deposit amounts (24), `points.bank-base`, `points.blocks-unit`, `spawner-tier-values`.
- rewards / crates / pass / quests: `pouch.ref-per-minute` (4 copies); afk `money.per-reward-by-tier`.
- guide-quests / tutorial money rewards; wardrobe money prices; shard block `upgrades.respawn/double` money entries; islands `lifecycle.profile-reset.join-money`; essentials `grind.farm-bonus` / `mine-bonus`; moderation `money-max`; achievement money goals.
- Java: `Format.money` exact-below 100,000, `Amounts.DEFAULT_EXACT_BELOW`, `SellFeature.unitPrice` thresholds, moderation picker steps, the GrindAreas fallback bonus lists, `ShopCatalogMigration.V6_PRICES`.
- Generators in `tools/` (`gen_upgrades_catalog.py`, `gen_masteries_catalog.py`, `gen_tasks_catalog.py`, `economy_model.py`) so a regeneration keeps the scale.

**Stored money to x100** (one SQL migration, `currency = 'money'` only): `eco_accounts.balance` (player, island bank, personal bank), `money_notes.amount`, `eco_bank_state.min_balance`, `eco_bank_withdrawn.amount`, `eco_send_unlocked.earned`, `ah_listings.price/fee/tax`, `ah_price_stats.median_price`, `island_bank_samples.balance`, `island_level.bank_low`, `island_block_worth.*` (or a rescan), `island_task_progress.amount` (earn/deposit keys), `island_kick_backups.money/bank_money/money_taken`, `profile_snapshots.money`, `island_stats_*` (`money_earned`), `achievement_progress` (money metrics), `quest_progress` (sell quests), `sanctions.amount` (money). The ledger and sale history stay as they were (history).

## Balance check after the scale
/farm and /mine bonus money must not out-earn the island (FULL_PLAN §0.3): the grind areas pay shards and XP, the island pays money long term.

## Build design (7 Nov 2026, batch 10 part C; checked against the code of today)
- **Marker per file**: a top-level `money-scale: 100`. A file that is created now (fresh install) gets it at once, so
  it is never scaled again. A live file without it has scale 1: every money key in it (owner values too) is multiplied
  once, a backup `<file>.pre-money-x100.bak` is written and every change is logged. Then the marker is set.
- **Typed configs**: `ModuleConfig.moneyKeys()` lists the paths and how to scale them (number, list of numbers, one
  field of a `a:b:c` line, `100k`-style tokens). `ConfigManager.load` runs the scale step after `valueChanges`, so old
  value changes still match old defaults. Bundled defaults are x100 in code.
- **Catalogs** (sell.yml, shop.yml, upgrades.yml, masteries.yml, tasks.yml, sets.yml, guide/tutorial money rewards):
  `CatalogScale.apply(file, rules)` with the same marker, called at the top of each loader. shop.yml: v8 in its chain.
- **Database**: one SQL migration per table (`V###__money_x100_<table>.sql`, the runner has no transaction per file, so
  one statement per file keeps it exactly-once). They run before the module's config load, in the same start. On a
  fresh database they change nothing. The ledger is scaled too, so balances and history stay consistent.
- **Not scaled**: shards, crystals, credits, "minutes of income" values (they multiply `ref-per-minute`, which scales),
  ratios such as the boss pay x60.
- Release: one update, DB and plugins backup first.

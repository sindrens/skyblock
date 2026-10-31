# Migration: gems → credits (card C1, wave 1a)

Gems are renamed **credits** and split into two ledger currencies (C1-R1-03, C1-R2-02):

| Key | What | Created by | Moves between players |
|---|---|---|---|
| `credits` | bought credits | store grant (`/storegrant`, later) and the owner's `/eco give\|set credits` (reason `admin_buy`) | yes, later with `/credit pay` (never `/pay`, never notes) |
| `credits_earned` | earned credits | votes, thanks, daily streak, pass, events, staff `/eco give credits_earned` (`admin_give`) | never (hard rule in code) |

Nothing was ever sold for gems, so **every old gem becomes an earned credit**. Players see one number (the total); only the owner of the balance sees the split (`/credit bal`). Spending takes earned credits first.

## Deploy rule
**Deploy the jar and the database together, never the jar alone or the SQL alone.** The SQL runs automatically: each module applies its own migration when it enables (tracked in `schema_version`), before it reads or writes anything. Do not run the files by hand.

Before the release:
1. Take a database backup (`mysqldump` of the skyblock database) and keep it until the release is verified.
2. Dry run on a copy of the live data (C1 card): restore the backup into a scratch database, point a local copy of the server at it, start it once, and compare the counts below before and after.
3. Deploy with `server/scripts/deploy.sh` (it ships the jar and the tracked `server/plugins/PhoenixSkyblock/*.yml`, which already say credits).

Row counts to compare (before = `gems`, after = `credits_earned`, totals must be equal):
```sql
SELECT currency, COUNT(*), SUM(balance) FROM eco_accounts WHERE currency IN ('gems','credits','credits_earned') GROUP BY currency;
SELECT currency, COUNT(*), SUM(amount)  FROM eco_ledger   WHERE currency IN ('gems','credits','credits_earned') GROUP BY currency;
SELECT source, COUNT(*) FROM profile_slots GROUP BY source;
SELECT origin, COUNT(*) FROM wardrobe_sets WHERE origin LIKE 'gems%' OR origin LIKE 'credits%' GROUP BY origin;
SELECT pay_currency, COUNT(*) FROM black_market_buys GROUP BY pay_currency;
```
After the release no row may say `gems` in any of these columns.

## What each module changes
| Module | File | Table.column | Change |
|---|---|---|---|
| economy | `db/economy/V009__gems_to_credits.sql` | `eco_accounts.currency`, `eco_ledger.currency`, `money_notes.currency`, `eco_watch_flows/accounts/supply/alerts.currency` | `gems` → `credits_earned` |
| auction | `db/auction/V002__gems_to_credits.sql` | `ah_listings.currency` | `gems` → `credits_earned` (money only today; expected 0 rows) |
| shop | `db/shop/V002__gems_to_credits.sql` | `shop_purchases.currency_key` | `gems` → `credits_earned` (money only today; expected 0 rows) |
| moderation | `db/moderation/V012__gems_to_credits.sql` | `sanctions.currency` | `gems` → `credits_earned` (a restore gives earned credits back) |
| blackmarket | `db/blackmarket/V002__gems_to_credits.sql` | `black_market_buys.pay_currency` | `gems` → `credits` ("paid with credits"; a refund looks up the ledger halves, so old buys come back as earned) |
| profiles | `db/profiles/V002__gems_to_credits.sql` | `profile_slots.source` | `gems` → `credits` (still kept at a season reset) |
| wardrobe | `db/wardrobe/V004__gems_to_credits.sql` | `wardrobe_sets.origin` | `gems` → `credits`, `gems_claim` → `credits_claim` |
| wardrobe | `db/wardrobe/V005__sigils_credits_column.{mariadb,h2}.sql` | `wardrobe_sigils.gems` | column renamed `credits` (values unchanged) |

Checked and **not** changed: `booster_thanks.gem_state` (a paid/unpaid flag, not a currency; the column keeps its name), rewards (no currency column; vote and daily payments are ledger rows, their idempotency keys now end in `:credits_earned` instead of `:gems`), trade (no currency column), hud (no table). Already applied migrations were not edited (`rewards V001` and `wardrobe V003` still say "store credits" in comments; changing them would change their checksums).

Every statement only touches rows that still have the old value, so a second run changes nothing. The economy rename can't merge two rows: if a `credits_earned` row already existed next to a `gems` row (impossible on a clean deploy), the `eco_accounts` update fails on the primary key and the economy module stops instead of losing or doubling a balance. Balances still equal their ledger deltas (only the key changed), so the balance-mismatch rule stays quiet.

## Config keys moved automatically on the first start
| File | Old | New |
|---|---|---|
| `economy.yml` (v4) | `start.gems`, `pay.min.gems`, `pay.confirm-above.gems`, `eco.approve-above.gems` | `start.credits_earned`, `pay.min.credits`, `pay.confirm-above.credits`, `eco.approve-above.credits_earned`; `tradeable.gems`, `notes.min/max.gems` removed (credits never become notes) |
| `economy-watch.yml` (v4) | `rules.earn-rate.min.gems`, `rules.big-transfer.min.gems`, `rules.staff-transfer.min.gems` | `…min.credits_earned`, `…min.credits`, `…min.credits` |
| `boosters.yml` (v2) | `thanks.gems`, `thanks.max-gems-per-booster` | `thanks.credits`, `thanks.max-credits-per-booster` |
| `daily.yml` (v2) | `streak.gem-every`, `streak.gems` | `streak.credits-every`, `streak.credits` |
| `votes.yml` (v2) | `rewards.gems` | `rewards.credits` |
| `hud.yml` (v3) | `values.gems: gems_formatted` | `values.credits: credits_formatted` |

New `economy.yml` keys: `legacy-gems-alias-until`, `credits.spend-attempts`, `baltop.currencies`, `baltop.check-ticks`, `display.exact-below`, `notes.purge-start-seconds`, `notes.purge-every-hours`, `database.retry.*`, `eco.approve-above.credits`, `tradeable.credits` (default off).

## Aliases (temporary)
- The word `gems` in commands and configs (`/eco give Steve gems 5`, a reward line `gems 3`, a set `type: gems`) means **earned credits** until `economy.yml → legacy-gems-alias-until` (default **2027-01-29**, 90 days after the rename). After that date the currency key `gems` is unknown. Reward lines and set sources keep accepting the word.
- Placeholders `gems`, `gems_formatted`, `gems_owned` and the style tags `<cur:gems>` / `<sym:gems>` show the credits total until the live files are updated (the tracked `server/plugins/PhoenixSkyblock` files are already updated and are shipped by `deploy.sh`).
- A `/gems` command never existed, so there is no `/gems` alias.

## Store (Tebex), later
Tebex packages will run the console-only `/storegrant <uuid> <package> <txid>` (C1-R1-17, not built yet). It creates **bought** credits only and never accepts the `gems` alias (`Currency.byCanonicalKey`). Rename any old Tebex package command that says gems at the same time.

## Permissions
`server/permissions/economy.txt`: `skyblock.economy.credits.grant` is **false** for admin and **true** for owner (the owner inherits admin; an inherited exact false would beat `*`). Run `server/scripts/apply-permissions.sh` after the deploy (deploy.sh does).

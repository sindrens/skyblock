# AFK pool (`modules/afk`)

A small area at spawn where standing still pays (owner O10, RW3). **No movement needed** (it is AFK); anti-abuse is one earning account per IP address instead. Settings: `afk.yml` (all editable in `/admin`); texts: `messages/afk.yml`; data: `db/afk`. Reload: `/skyblock reload afk`.

**No command** (`/afkpool` is not approved): status is on the **action bar** inside the pool (next reward countdown, today's total / 8h, reward size %) and on the **spawn scoreboard** (the blank line above `Island` becomes an `AFK` line while inside). Help: `/help afk` → page "AFK pool".

| Command | What it does | Permission | Status |
|---|---|---|---|
| (none) | Stand in the pool: rewards are automatic | `skyblock.afk` | ✅ |

Placeholders: `<ph:afk_line>` (the whole scoreboard line, `messages/afk.yml → scoreboard.line`; empty outside the pool), `<ph:afk_pool>` (countdown to the next reward, or `Limit reached` / `Paused` / `Off`; empty outside the pool) and `<ph:afk_today>` (credited AFK time today, e.g. `3h 15m`).

## Where the pool is
1. A region with id `pool.build-region` (default `afk_pool`) in the `regions:` list of an applied build's metadata (e.g. the spawn build; builders add `{ id: afk_pool, from: {...}, to: {...}, kind: afk }`), read through `BuildPoints`. No WorldGuard.
2. Otherwise the box in `afk.yml` (`pool.world`, `pool.min-*`, `pool.max-*`) when `pool.use-config-area: true`.
3. Neither: the pool is closed (logged at start/reload). The area is re-read every `time.save-seconds`, so applying a build opens it without a reload.

## Rules
- **Rewards every `time.reward-minutes` (15) of credited time:** money + island fuel. Time is credited every `time.check-seconds` (2) while the player is inside, may earn (`skyblock.afk`), is below the daily cap and holds their IP.
- **Money** = `money.per-reward-by-tier[tier]` × the session step. Tier = the player's highest level of `money.stage-skills` (mining/foraging/combat) mapped through `money.tier-levels` (the Depths unlock levels). Defaults are 2.5% of the reference income per hour per reward-hour, ×1.8 at the top step = 4.5% of REF ≤ ~4% of a regular player's real income (ECONOMY_BALANCE §7.5). Paid to the **active profile** (ledger reason `afk_pool`, a source on the `/eco` dashboard).
- **Session steps** (`money.session-steps`): 100% → 110% (30 min) → 125% (1 h) → 140% (2 h) → 160% (4 h) → 180% (6 h). **Leaving the pool resets the session** (not the daily total, not the progress toward the next reward). Quitting resets it too.
- **Island fuel** (`fuel.minutes-per-reward`, 15 min) to the island of the active profile; nothing without an island (the chat line says so). Kept as a pending grant and delivered through `IslandService#addIslandFuel` once Island Fuel exists.
- **Keys** at `keys.milestones` of the day: 1 h Ember, 3 h Ember, 6 h Cinder, 8 h Cinder (R3-15: AFK never gives Blaze keys; one each, once per account per day, database key). Kept as pending grants and delivered through `CrateKeys` (common) once crates exist; the player is told they are saved.
- **Personal sell booster** (`booster.*`): 2% per reward after 1 h in the session; +50% sell for 10 min, **banked** (max 60 min) and started automatically when the player is outside the pool. Since M10.2 the **boosters module** keeps it (`BoosterService.bank` inside the reward transaction; see [boosters](boosters.md)); the pool only holds it while the player is inside. It adds up with every other sell bonus under the +300% cap (O11). Per profile. Without the boosters module no booster is rolled.
- **Daily cap** `time.daily-cap-minutes` (480 = 8 h) per **account** (all profiles together) per day; the day resets at **midnight Europe/Oslo** (`time.timezone`, empty = core display timezone). Progress toward the next reward carries over midnight.
- **One account per IP** (`anti-abuse.one-per-ip`): the account that entered first earns; others on the same IP see "Paused: another account on your connection is earning". When it leaves, the next one takes over. **Per-IP daily cap** `anti-abuse.ip-daily-cap-minutes` (480) over all accounts of an IP, so alts can't take turns (kept in memory; a restart resets it).

## Storage, safety and audit
- Tables: `afk_state (player_uuid, reward_seq, progress_ms)`, `afk_daily (player_uuid, day_key, credited_ms, rewards_today)`, `afk_milestones (player_uuid, day_key, milestone_min)`, `afk_grants (pending keys/fuel)`, `afk_boosters` (old; its rows are moved to the boosters module's bank once at start-up).
- Every account's writes run in one ordered queue off the main thread. Credited time is saved every `time.save-seconds` (30) and on leaving, quitting and shutdown, so a crash loses at most 30 s.
- A reward is **one database transaction**: money (idempotency key `afk:<uuid>:<reward number>`) + reward number compare-and-set + today's total + fuel grant + banked booster. A replay after a crash can never pay twice; a stale copy is refused and reloaded.
- Grants are claimed before they are handed out (a crash can lose one but never give it twice); a failed hand-out is put back.
- Audit (category `economy`): `afk_pool` (ledger, money), `afk_reward` (seq, tier, step, money, fuel, island, booster), `afk_key`, `afk_grant_delivered` / `afk_grant_retry`. Booster banking and starts are logged by the boosters module (category `boosters`: `bank_add`, `bank_start`).

Permissions: `skyblock.afk` (earn in the pool; default true).

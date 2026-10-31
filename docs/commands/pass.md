# Battle pass (`modules/pass`)

The season battle pass (PHASE-10-REWARDS.md **M10.8**, P10-24, P10-25, P10-26 with REVIEW3 **R3-44**, P10-28; staff tools P10-41). Pass XP and the free track are per profile (nothing moves between your own profiles, P4); the premium track is per account. Settings: `pass.yml`; texts: `messages/pass.yml`. Reload: `/skyblock reload pass`.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/pass` | Opens the battle pass: 7 levels per page side by side (52/53 turn the page). Row 1 = the level line (reached / in progress with a bar / XP to go), row 2 = free rewards, row 3 = premium rewards (cosmetics only). Slot 4 = level, XP, season, days left, catch-up and premium state. Click a glowing level to claim it, or **Claim all** (slot 46). Item names start with the key fact (`Level 12 · Click to claim`, `· Claimed`, `· Locked`, `· Premium`) for Bedrock. Also: the `/menu` Rewards button claims ready pass tiers together with the daily reward, and `/help pass`. No arguments | `skyblock.pass.use` (everyone) | ✅ |
| `/pass admin view <player>` | Chat summary of an online player's pass on the active profile: level, XP, claimed tiers, premium, ready tiers | `skyblock.pass.admin` (admin) | ✅ |
| `/pass admin xp <player> <xp>` | Gives 1 to `max-xp-per-event` (10,000) pass XP to the active profile (no catch-up bonus; audited `xp`, actor = staff) | `skyblock.pass.admin` (admin) | ✅ |
| `/pass admin reset <player>` | Sets the active profile's pass XP to 0 for this season. Claimed tiers stay claimed, so nothing can be claimed twice (audited `staff_reset`) | `skyblock.pass.admin` (admin) | ✅ |
| `/pass admin premium <player> <on\|off>` | Turns the Premium Pass on or off for the player's account this season (a `pass_premium` row; the store writes the same row in Phase 11). The permission `skyblock.pass.premium` also counts as premium (audited `premium_granted` / `premium_removed`) | `skyblock.pass.admin` (admin) | ✅ |

Tab completion: online players, XP amounts, on/off.

**Levels** (P10-24): 100 levels x **800 pass XP** (`levels.*`), the same for every level. After level 100 every **2,000 XP** is a bonus step with the overflow reward (RX31: Crystal Pouch M + 1 **bound** Ember key, key source `pass_overflow`).

**Pass XP** comes from the common `PassXp` service, which quests (daily 100, weekly 400, island mission 150), votes (25), the daily calendar (50) and active playtime (20 per hour) already call. Each call carries an **idempotency key** (the caller's hand-out row id), so a retried or crash-recovered hand-out never adds XP twice. **Catch-up** (RX30): while a profile is 15+ levels behind the day line (season day x 1.1, `catch-up.*`, needs `season.start`), pass XP counts 1.5x. One event adds at most `max-xp-per-event`.

**Free track** (per profile, P10-25): a small reward every level in turn (`free.every-level`: 2 minutes of income at your tier, a crystal pouch), plus milestones (`free.milestones`): Ember x2 at 5, 15, 35, 45, 55, 65, 85, 95; Cinder at 10, 20, 30, 40, 60, 70, 80, 90; Blaze at 25 and 75; Inferno at 50; Phoenix at 100; a 25% Sell / Skill XP booster (30 min) at 12, 37, 62, 87; titles **Season Climber** (50) and **Season Champion** (100). Pass keys are **tradeable** (key source `pass`, P10-6).

**Premium track** (per account, P10-28): **cosmetics only** (R3-44, Mojang rules): a title every 5 levels (`premium.milestones`; the parser refuses money, crystals, keys and boosters there), **no extra daily reroll**. A premium cosmetic is given once per account, on whichever profile reaches the level first. Premium = permission `skyblock.pass.premium` or `/pass admin premium` (the store sets it in Phase 11; not part of any rank, RK26).

**Claims (crash-safe, never double):** one database transaction locks the profile's XP row, checks every tier is reached (and premium for premium tiers), writes the claim rows (primary key: once per tier, free per profile, premium per account), pays money and crystals through the ledger (reason `pass_reward`, idempotency key per claim) and writes keys, boosters and cosmetics as **pending** hand-out rows; all or nothing. After the commit: keys via `CrateKeys`, boosters as bound items (inventory, the rest to `/box`; offline: straight into the box), titles/tags via cosmetics. A missing module leaves the row pending (retried every 5 minutes and at start); a stop in the middle sends titles/tags again and marks keys/boosters **review** (`grant_review` in /logs). A tier whose reward can't be given yet (an unknown tag or crate) shows "Coming soon" and waits instead of being lost. `claim.auto` (off) would claim at every level-up instead (P10-29, open question).

**Season** (`season.id`, `season.start`, `season.days`): XP, claims and premium are stored per season id; a new id (Phase 12 seasons) starts everyone at level 0 on the next reload. A deleted profile loses its pass progress and free claims; premium stays with the account.

**Audit** (category `pass`, `/logs`): `xp` (every add with source), `claim` (tiers, money, crystals, keys, boosters, cosmetics), `grant_delivered`, `grant_review`, `grant_failed`, `premium_granted`, `premium_removed`, `staff_reset`, `profile_reset`, `season_changed`. The economy writes `pass_reward` ledger rows; crates and cosmetics log keys and titles.

**For other modules:** `PassXp` (common/pass: add XP with an event key) and `PassRewards` (common/pass: ready tiers and claim all, used by the `/menu` Rewards button). Placeholder `%pass_level%`.

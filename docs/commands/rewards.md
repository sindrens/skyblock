# Rewards (`modules/rewards`)

Daily calendar, streak and playtime rewards (PHASE-10-REWARDS.md **M10.6**, owner 128, R5, R6, P10-17; voting M10.5 joins this module later). Everything is **per profile**: each profile has its own calendar, streak and playtime (nothing moves between your own profiles, P4). Settings: `daily.yml`; texts: `messages/rewards.yml`. Reload: `/skyblock reload rewards`.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/daily` | Opens the daily rewards menu. **Calendar tab**: the 30 days (claimed = green glass ✔, today = the reward with *CLICK Claim*, later days = gray glass with the reward in the lore; the stack size is the day number), your streak (best, claims, next gems, streak titles) and the reset countdown. **Playtime tab** (no extra command, R6): active time of this profile, whether it counts right now, the hourly reward, the next key and the playtime titles. Also opened by the **Daily Rewards NPC** (role `daily`; sneak-click keeps the help text) and `/help daily`. No arguments, so there is nothing to tab-complete | `skyblock.rewards.daily` (everyone) | ✅ |

No staff commands (a `/daily admin` is not in the approved list; staff see everything in `/logs`, category rewards).

**Daily calendar**
- One claim per profile per day; the day resets at **00:00 Europe/Oslo** (`calendar.zone`), also on DST days.
- The calendar day moves **only when you claim**: missing a day never sends you back. After day 30 it starts at day 1 again.
- Rewards (`calendar.days`, one line per day, P10-17): money = minutes of reference income at your tier (`pouch.ref-per-minute`, the balance sheet's REF(t), same numbers as crates), crystal pouches = base x 1.3^(tier-1), bound keys through the crates module (days 3, 7, 10, 14, 17, 21, 24, 28 Inferno, 30 Eclipse), bound personal boosters through the boosters module, a random enchant book of a rarity and Disenchant Scrolls through the enchants module. **Island Fuel** and **Wardrobe Tomes** are not built yet: they pay their crystal `fallback` (shown in the menu as *pays ◆ N for now*) until they exist.
- **Streak** = days in a row with a claim. Every 7 streak days: **10 gems** (§9.2, `streak.gem-every`, `streak.gems`). Titles at streak **30** (*Devoted*) and **100** (*Unbroken*) through the cosmetics module (account titles). A missed day starts the streak over (the calendar day stays); the lost streak is stored for the **streak save** (100 store credits within 2 days, R5), whose button stays hidden until the store exists (Phase 11).
- A join reminder tells you when today's reward is still open (`calendar.join-reminder`; players can turn it off in `/settings` → Chat → *Daily reward reminder*).

**Playtime rewards** (R6)
- Only **active** time counts: you did something yourself in the last 5 minutes (`playtime.idle-minutes`: turning the camera, walking on the ground, chat, commands, clicks, breaking, placing, hitting; being pushed by water or riding does not count), you are not marked AFK (`/afk`) and you are **not in the AFK pool**. Frozen, staff mode and profile switches pause it.
- Every hour: Crystal Pouch S (`playtime.hourly-crystals` 20, tier-scaled) + 20 pass XP once the battle pass exists (`playtime.hourly-pass-xp`; the hook waits until then). No money.
- Every 10 hours: 1 bound **Cinder key** (`playtime.key-every-hours`, `playtime.key-crate`).
- Titles at **50, 100, 250, 500 hours** (*Regular, Veteran, Dedicated, Timeless*, account titles).
- Time is counted every 20 s and saved every 60 s; a crash loses at most the last minute of counted time.

**Rules (crash-safe, never double)**
- A claim is **one database transaction**: the calendar (or the paid playtime milestones) is checked against what was read and moved, a claim row is written (primary key profile + date: a second claim on the same day is impossible), money, crystals and gems are paid through the ledger (reasons `daily_reward` / `playtime_reward`, idempotency key per claim and currency) and the hand-outs are written as **pending** grant rows. All of it commits or nothing does.
- Hand-outs happen after the commit: a row is moved `pending` → `sending` in one atomic update (never handed out twice), then keys go through `CrateKeys` (reasons `daily` / `playtime`, bound), items to your inventory when you are online on that profile (the rest to `/box`, then the player file is saved) or straight into that profile's box, titles through cosmetics. Calendar items are **bound** to the profile (`calendar.items-bound`). When a module is missing for a moment (crates disabled ...) the row waits and is tried again every 5 minutes and at the next start.
- A stop or crash in the middle of a hand-out leaves the row `sending`: at the next start titles are granted again (idempotent) and everything else is marked **review** (`grant_review` in /logs) so staff can check `/box` and give it by hand. A reward can be lost, never doubled.
- Not while frozen, in staff mode or during a profile switch; one claim at a time per player; a `/box` at its hard cap refuses claims.

**Audit** (category `rewards`, `/logs`): `daily_claim` (day, streak, broken/lost streak, tier, money/crystals/gems, rewards), `playtime_reward` (hours paid, keys, titles, crystals, pass XP), `grant_delivered` (items, fuel, pass XP; keys and titles are logged by crates and cosmetics), `grant_failed`, `grant_review`, `profile_reset` (deleted profile). The economy writes `daily_reward` and `playtime_reward` ledger rows.

**For other modules:** `AfkPool` (common, published by the afk module) tells whether a player is in the AFK pool; `PassXp` (common) is the battle pass hook the pass module will publish (M10.8).

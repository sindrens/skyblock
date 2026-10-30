# REVIEW3: Developer brief

**For:** the main development session (Phoenix Skyblock).
**From:** the review session, 30 Oct 2026. Every item was checked against `main` at update95 and the live VPS configs, pulled 21:06 the same day.
**Owner answers:**
- REVIEW3 Answers page: https://claude.ai/artifact/MXk1MkDUJqSwhUKp8z9s66 (db `answers/review3v2`).
- Follow-ups page: https://claude.ai/artifact/7BwcM5JbABUeDuC6pXJxHt (db `answers/round2`). Its ids are F1–F15, D1–D4 and K1–K8.
- Read both with `ArtifactData get`.
- The fact-checked question list (v2) is `REVIEW3_SECOND_LOOK.md` (same folder). It replaces `docs/REVIEW3_SECOND_LOOK.md`.

**Status words used below:**
- ✅ **Build**: decided.
- 🔧 **Fix**: drift or bug, build now.
- ⏳ **Wait**: my proposal. Don't build until the follow-ups page answers it.
- 📝 **Plan**: needs a plan md first.
- ✔ **Settled**: already built; small follow-up only.

---

## 0. Short version
1. **Fix now (§3):** 9 items where the server doesn't match the owner's answers, or that are bugs. Examples: answer 641 was never applied, combat drops are lost on a full satchel, helpers can read every DM.
2. **Build list (§2):** about 30 decided changes, grouped by module.
3. **Owner ideas that need a plan (§4):** Depths zones v2, contribution on kick, spawner logic, tradeable gems, rank perk model, staff creative editing, special spawner drops.
4. **Wait for the follow-ups page (§5)** before building anything marked ⏳.
5. **Notes for the main session (§6):** problems seen in the process and ideas to keep quality up. These are ideas, not orders.

---

## 1. All 44 items at a glance
| Item | Owner answer | Status | Where |
|---|---|---|---|
| R3-1 Start money loop | **not answered** → F1 | ⏳ | economy, auction, trade, islands |
| R3-2 Bound keys → bound loot | Recommended | ✅ | crates (needs bound-item system) |
| R3-3 AFK one earner per connection | Recommended (keep as built + family whitelist) | ✅ | afk |
| R3-4 Spawner trading / price rise | Other: "find a better logic" → F2 | ⏳ | stacking, shop |
| R3-5a Gems | Other: gems tradeable via /pay and /trade; only crystals untradeable → F3–F5 | ⏳ (guardrails) | economy, trade |
| R3-5b Crystals untradeable, shard notes | Recommended | ✅ | economy |
| R3-6 /eco approval | Other: $1T limit + "Allow" in alert; staff creative editing → F15 | ✅ / ⏳ | economy, staff, islands |
| R3-7 Paid sell/XP bonus | Other: perks earnable by playing → F7 | ⏳ | perks, ranks plan |
| R3-8 Monthly cosmetic, not keys | Recommended | ✅ | ranks plan, perks |
| R3-9a DMs as /report evidence | Recommended | ✅ | staff, moderation |
| R3-9b Hide DMs from helpers | Recommended | 🔧 | logs permissions |
| R3-10 Mini-boss scaling | Recommended | ✅ | depths |
| R3-11 Island bank withdraw | Other: "it has to be a permission" → F8 | ⏳ | islands, economy |
| R3-12 Kick / leave | Other: island keeps 50% of a kicked member's contribution → F9–F11 | 📝 ⏳ | islands, Phase 5 |
| R3-13 Frozen spawners | Recommended (both follow-ups) | ✔ + 🔧 | stacking |
| R3-14 Paid items bound | Recommended (see F5) | ✅ ⏳ | tools, cosmetics, wardrobe |
| R3-15 No Blaze keys from AFK | Recommended | ✅ | afk |
| R3-16 Old 30-min rule | Recommended (cleanup) | ✔ + 🔧 | configs, docs |
| R3-17 Homes → P5-21 | Recommended | ✅ | islands (Phase 5 Homestead) |
| R3-18 3 fragments = Phoenix key | Recommended | ✅ | crates, depths drops |
| R3-19 Island size | Other: shards (expensive) **and** gems → F6 | ✅ ⏳ | progression, Black Market |
| R3-20 Gem sets looks only | Recommended | ✅ | wardrobe |
| R3-21a Spawner mobs vanilla loot | Other: mostly vanilla, a few special drops (§4.9) | ✅ 📝 | stacking, CUSTOM_MOB_DROPS |
| R3-21b Apply 641 | Recommended | 🔧 | stacking.yml |
| R3-22 Full satchel | Recommended | 🔧 | depths |
| R3-23 Forge from storage = take-out | Recommended | ✅ (with storage) | tools, islands storage |
| R3-24 Combat XP Depths only | Recommended | ✅ | skills, enchants |
| R3-25 AFK pool time vs interest | Recommended | ✅ | economy bank |
| R3-26 40 stacks per chunk total | Recommended | ✅ | stacking |
| R3-27 Hoppers | Other: "64 items per tick, fast" → F12, F13 | ⏳ | spigot.yml, paper config |
| R3-28 24 h trade block on dupes | Recommended | ✅ | moderation, items |
| R3-29 Quick-buy safety | Other: "we need better logic" → F14 | ⏳ | shop |
| R3-30 Two-step disband | Recommended | ✅ | islands |
| R3-31 Report reasons | Recommended | ✅ | staff (punishments.yml reasons) |
| R3-32 /msg filter + tutorial DMs | Recommended | ✅ | social, tutorial |
| R3-33 Top rated: 5 IPs, smoothed | Recommended | ✅ | islands visiting |
| R3-34 Full combat zones | Other: Depths zones redesign → D1–D4 | 📝 ⏳ | depths (big) |
| R3-35 Idle in AFK pool | Other: no kick; teleport non-earners to their island | ✅ | afk |
| R3-36 AFK pool own area | Recommended | ✅ | afk, builds |
| R3-37 Day-3 money | Recommended (keep curve, re-check) | ✅ | ECONOMY_BALANCE |
| R3-38 Trade value hints | Recommended | ✅ | trade |
| R3-39 Staff play accounts | Recommended | ✅ | rules, economy |
| R3-40 Notes 20/min + purge | Recommended | ✅ | economy notes |
| R3-41 Bank fee only | Recommended | ✅ | economy bank |
| R3-42 Warzone kill reward | Recommended | ✅ | warzone (Phase 9) |
| R3-43 Booster thanks | Recommended | ✅ | boosters (Phase 10) |
| R3-44 Drop premium reroll | Recommended | ✅ | pass (Phase 10) |
| FOUND (9 fixes) | Wants them in this brief | 🔧 | §3 |
| S1 What to build meanwhile | "I'm talking to the main session" | owner decides | — |
| S2 Releases | "every time it's something new, in a batch" | owner decides | — |
| S3 Fact-check every planning file | Recommended | ✅ process | §6 |
| S4 Clean up old bundles | Recommended (keep the last 5) | ✅ | §6 |

---

## 2. Decided changes (✅), by module

### Economy
- **R3-5b:** crystals can never be transferred. Make it a hard rule in code (`transferable=false`, not only `economy.yml tradeable`). Shard notes on: add `shards` to `notes.currencies`.
- **R3-6 /eco:** owner's words: "we will have it logged in discord soon so we need it to be flagged… make for now maximum 1 trillion and then click in alert to allow."
  - My reading: an /eco change up to $1T applies at once and is flagged. Above $1T it waits until an owner or admin clicks **Allow** in the alert.
  - The alert goes to staff and to Discord once the webhook exists.
  - Confirm this reading with the owner if unsure. The staff-creative part is F15.
- **R3-16:** delete the dead 30-minute keys (`auction.yml anti-abuse.new-account`, `economy.yml pay/notes new-account-minutes`) and the doc lines (`ECONOMY_BALANCE.md` ~1091, DECISIONS #40).
- **R3-25:** minutes in the AFK pool region don't count toward the interest activity rule (2 h in 7 days). Other idle time still counts.
- **R3-37:** keep the smooth curve. Re-check the owner's $5M day-3 target on the test server. The model today gives $12.9M for the 8 h/day grinder and $1.5M for the regular player.
- **R3-38 trade value hint:**
  - Use the server sell price and shop prices.
  - Items with no server price (spawners, books, sets, tools) show "value unknown".
  - Use an AH median only with 5+ different sellers and buyers, ignoring same-IP pairs.
- **R3-40 notes:** replace `withdraw-cooldown-seconds: 10` with a soft limit of 20 notes a minute. Purge cashed `money_notes` rows after 90 days.
- **R3-41 bank:** the only rule is the 2% fee (lower with Treasury). Remove `free-minutes` (set it to 0 and drop it from the menu text), and remove the "fee withdrawal forfeits the period's interest" rule. Same as BANK_UX Q1–Q2.
- **R3-28:** a system-detected dupe of a unique item gives the holders an automatic **24 h trade block** (/pay, notes, AH, /trade) that expires on its own. Staff decide after and can upgrade it to a freeze. Note: duped money notes already can't make money.
- **R3-39:** admins, builders and the owner play on a separate account; mods and helpers keep one. /eco can't target your own account or a same-IP account.

### Ranks, cosmetics, store (Phase 11 plans)
- **R3-8:** bought ranks give a monthly cosmetic, not crate keys. Won season ranks keep monthly keys.
- **R3-14:** skins, tags and sets bought with gems or credits stay account-bound. Only play-earned ones trade. Use `skins_owned.source`. Store tags today arrive as tradeable AH items, and that changes. See F5 (earned gems).
- **R3-20:** gem sets (Lorekeeper, Thornward, Stormkeel) become looks only, worn over an earned set (reuse `look_set_id`). The bonus versions move to crates and crystals.
- **R3-43 booster thanks:** keep `/thanks` and typed "thanks", fold them into one chat line, cap at 20 gems per booster, and give Bedrock a menu button.
- **R3-44:** remove the extra daily reroll from the premium pass.

### AFK pool
- **R3-3:** keep as built (`one-per-ip: true`, 480 min per connection). Add a staff family whitelist. **Check that Geyser/Floodgate passes the real Bedrock IP**, or every Bedrock player counts as one connection.
- **R3-15:** AFK never gives Blaze keys. The 8 h milestone gives a Cinder key (one line in `afk.yml keys.milestones`).
- **R3-35:** owner: "no need to, let them be, but just teleport them back to their island whenever they don't earn anything".
  - No idle kick.
  - A player in the pool who isn't earning (daily cap reached, or paused by the one-per-connection rule) is teleported to their island with a short message.
  - Suggested delay: 60 s after earning stops. Configurable.
- **R3-36:** the pool gets its own small area, not inside the Tavern, and players inside are hidden from each other. Use the shared visibility service (§6, D4). Expect about 90 players on average and 120–150 at peak.

### Islands
- **R3-13 follow-ups:**
  - (a) Spawners on islands that are disbanded or reset on purpose are cleared, not frozen for staff. Add an `IslandRemovedEvent` listener in stacking.
  - (b) Add a staff restore button for frozen spawners.
- **R3-17 → P5-21:** no personal homes. The Homestead mastery gives shared island homes (`/is home <name>`, +1 per mastery rank). The owner OK'd the new sub-command by answering Recommended.
- **R3-26:** a total cap of 40 mob stacks per chunk (all types together), on top of 8 per type.
- **R3-30:** two-step disband. The second screen shows the real amounts ("you lose $X, N items, bank $Y"), with the buttons in different slots.
- **R3-33 top rated:** needs ratings from at least 5 different IPs. Rank by a smoothed (Bayesian) average.
- **R3-19:** owner: "shards black market expensive and also possible with gems". Sizes above 9x9 are sold for shards (Black Market, expensive) **and** for gems. Guardrail in F6.

### Depths, tools, skills
- **R3-10 mini-boss scaling:**
  - Health +25% per extra eligible player, up to 10.
  - Eligible = 10% of the damage, hit in the last 90 s, not AFK, max 3 accounts per IP.
  - Rewards stay full for each eligible player.
- **R3-18:** 3 fragments make 1 Phoenix key, not 10 for any key. Fragments come from mine and forest tiers 11+ (later fishing, 1 in 4,000). Pick a drop chance so one key takes a set number of tier 11+ hours; there is no target yet, so ask the owner.
- **R3-22 full satchel:** drops go to the inventory, then `/box`. Breaking stops only at the `/box` hard cap (5,000). The warning comes every 3 s (`full-warning-ms: 3000`). Combat drops follow the same chain (today they are lost; see §3).
- **R3-23:** the Forge taking from island storage counts as a take-out (same permission, same daily limit). Build it with island storage. The limits must fit Forge amounts: tier 10 needs 2,824 items.
- **R3-24:** Combat XP only in Depths combat zones. Soul Reaper's "+Combat XP from stacked kills" needs a new effect.

### Staff, moderation, social
- **R3-9a:** `/report` attaches the last 10 DMs between the reporter and the reported player as evidence. That is the only way DMs become evidence. Evidence is kept 180 days; say so on the privacy page.
- **R3-31:** `/report` already works from minute 1. Add the reasons **Scamming, Threats, Griefing** (open question 542).
- **R3-32:**
  - Run `/msg` through the link/IP/phone filter (not the swear list, per answer 305).
  - Until an account finishes the tutorial, only staff and players it messaged first can DM it.

### Crates and keys (Phase 10)
- **R3-2:** loot from bound keys is bound too, and a key found inside a bound crate is bound as well. A spawner from a bound key goes straight onto the opener's island spawner stack.
  - This needs the **bound-item system** (SECURITY F-05). Build it once; R3-14 and F3/F5 use it too.

### Warzone (Phase 9)
- **R3-42:** the killer gets half of the victim's 2% money loss (max $500k), once per pair per hour, and nothing if both are on the same IP.

---

## 3. Fixes (🔧): the server doesn't match the owner, or a bug
1. **Answer 641 not applied:** live `stacking.yml mobs-per-spawner: 4.0`. Set it to **1** (R3-21b). Spawners make 4x the money the balance sheet expects.
2. **Combat drops lost on a full satchel:** `CombatZones.java` ~533. Use inventory → `/box` (R3-22). The Depths mining break is refused today (`DepthsDrops.java` 128–133); change that to the same chain.
3. **Helpers read every DM in /logs:** `server/permissions/logs.txt`. This breaks answer 318. Hide `chat/dm` from helpers; mods and up keep it (R3-9b).
4. **N2-1 (unlimited island joins) not applied:** live `islands.yml max-joins-per-season: 3`, `join-cooldown-hours: 24`. Confirm with the owner whether the 24 h cooldown stays (R3-12 relies on it), then apply.
5. **Frozen spawners on disbanded or reset islands:** clear them instead (R3-13a).
6. **Dead settings:**
   - the old 30-minute keys (`auction.yml`, `economy.yml`);
   - `islands.yml disband-prompt-length` and `homes.*`;
   - `shop-settings.yml buy.confirm-above`.
   - Add a test so dead keys can't come back (§6).
7. **Stale docs:**
   - `ECONOMY_BALANCE.md` and DECISIONS #40 (30-minute rule);
   - `docs/commands/islands.md` ("typed word" disband);
   - `RESOURCE_PACK_ART_BRIEF.md` §N, which still has the old rank names (Squire, Knight…) instead of Ashborn, Hatchling … Immortal.
8. **Economy model:** `tools/economy_model.py` has the paid sell bonus built in (`SELL_RANK_MAX=0.15`). Rerun after F7. Also rerun after F2 if accepted.
9. **Bedrock IP check:** confirm Floodgate gives each Bedrock player their real IP. AFK, ratings, warzone, gems and mini-boss eligibility all use per-IP rules.

---

## 4. Owner ideas that need a plan (📝)

### 4.1 Depths zones v2 (R3-34) → D1–D4
**Owner:** "the main idea is having 5 zones for each mine, each wood cutting, dungeon +1 on dungeon for boss. main boss area… where people can choose visibility and lets say ur in zone 1 in the mine mining coal, then unlocks iron, you will be in the same world but the coal turns into iron ore and if you turn on visibility for me which i am at coal, you will see iron and i see coal and we can see each other."

**What exists that supports it:**
- The M4.3 engine already sends per-player block states (`PlayerDepletion`, `DepthsEngine` `sendBlockChange`/`sendMultiBlockChange`, resent after `PlayerChunkLoadEvent`).
- `TierGates` already shows a different block per player at the same spot.
- Missing: a **tier palette**, meaning one "slot" block that shows a different ore or log per player.

**Changes needed:**
- Palette per resource (shown and depleted block per tier). The real block is the tier-1 block, so tier-1 players need no packets.
- `activeTier` per player and zone, refreshed on level-up, profile switch and the 20-tick check.
- Lazy per-chunk index of slot positions.
- `ResourceBreakEvent` gets `getShownType()` and `getTier()`. Today XP, drops, hooks and ambient mobs read the real block or the zone (`DepthsXpListener:26`, `DepthsDrops:116/163`, `DepthsHooks:50/53`, `AmbientMobs:120`); switch them to the new getters.
- **Hardness trap:** the client times the break from the shown block. Scale the anti-fast-break check by the shown block's hardness, and pick the softest block as the real one.
- Bedrock: Geyser turns a multi-block change into one packet per block. Throttle to about 300 blocks per tick per player and skip tier-1 players. A swap is about 4 KB on Java (441–1,267 slots per field).

**Proposed mapping (15 gates instead of 33):**

| Zone | Mine (12 tiers) | Forest (11) | Combat (10) |
|---|---|---|---|
| 1 | coal 0, copper 5, iron 10 | oak, birch, spruce | graveyard 0, crypt 6, spider den 12 |
| 2 | gold 15, redstone 20, lapis 25 | jungle, acacia, dark oak | slime 18, drowned 24 |
| 3 | diamond 30, emerald 35 | mangrove, cherry | witch 30, pillager 38 |
| 4 | amethyst 42, quartz 50 | pale oak, crimson | blaze 46, wither skeleton 55 |
| 5 | netherite 58, voidstone 66 | warped | ravager/evoker 66 |

Plus 1 dungeon boss hall after combat zone 5, and the main world-boss arena.

- **Lower tier (D2):** a player can pick a lower unlocked tier. The Forge and blueprints still need coal after iron unlocks (e.g. `sage_pickaxe` needs 256 depths coal).
- **Combat (D3):** mobs are shared entities.
  - Hiding them per tier is possible (the `setVisibleByDefault(false)` + `showEntity` pattern from ambient mobs), but hidden mobs still target, make sounds, shoot and tick.
  - Proposal: v1 = tier sub-arenas inside each combat zone with shared mobs. Layered mobs come only after a pilot.
  - Spawn rate must grow with players (today about 2 mobs/s per zone, enough for about 16 players).
- **Visibility (D4):** one shared visibility service. It computes `hidden = vanished || !allowed` and applies only the changes. Bukkit tracks hides per plugin, so a second feature calling `showPlayer` would unvanish staff.
  - Modes: Everyone (default), Friends+island, Nobody, plus a "pin player" option.
  - Hidden bodies are non-collidable.
- **Builds:**
  - Delivered builds: gate, layout, mine t01–t03, forest t01–t02, combat t01–t02.
  - Mine t02/t03 and forest t02 become zone-1 districts with one `//replace` (iron_ore → coal_ore, logs likewise).
  - The layout generator must output 15 zones + 2 boss areas.
  - New builder rule: each field uses one slot material.
- **Order:**
  1. Palette spike on the coal zone (2 Java + 1 Bedrock).
  2. Event and hook changes.
  3. Zone groups, gates and tier selector.
  4. Visibility service.
  5. Forest.
  6. Combat v1 + boss scaling.
  7. Builders start Z2+.
  8. 80-bot load test, then decide on layered mobs.
- **Cost:** palette engine M, zone schema/menu/migration M, selector S, visibility M, combat v1 S–M, layered mobs L, new builds L.
- **First step if D1 is yes:** write `docs/phases/PHASE-4-DEPTHS-V2.md`, fact-check it, and send it to the owner as the next planning file.

### 4.2 Contribution on kick (R3-12) → F9–F11
**Owner:** "if a player is kicked the island will lose the contribution that player has done to the island, only 50% of what that player contributed with will stay kept".

**What exists:**
- Only the M2.6 activity counters per member (`V010__island_stats.sql`, `ContributionShares`).
- Phase 5 (pillars, level, masteries) attributes nothing per member yet.
- A kick today removes only the membership (SECFIX1).

**Proposal (built with Phase 5):**
- Contribution = earned island points from the member's current stint:
  - pillar XP per member;
  - upgrade and mastery points, split by deposit share at the time of purchase;
  - bank deposits.
- Live sources (skills, Depths tier) already leave with the member.
- **On kick:**
  - The island keeps 50% of that member's contribution. The kept half becomes unattributed points (counts for level, never for a prize share). The other half is deleted.
  - Bought perks are never revoked; only later "island level X" gates can drop.
  - The kicked player keeps all personal progress and gets 50% of their unspent bank deposits back.
- **Leaving by choice (F10):** the island keeps 100%. Staff kicks, bans and members inactive 21+ days don't trigger the loss.
- **F11:** no leader kicks in the last 14 days of a season. Staff still can.
- **Preview before kick:** "Kicking Alex removes 21 points (Lv 38 → 36). Bought perks stay. Alex gets $120,000 back."
- Halving a 30% XP share costs about 1.4 levels per pillar, because levels grow ×1.12.

### 4.3 Spawners (R3-4) → F2
**Owner:** "we want spawners to always be worth having and then [stop] at a point where it's no longer worth".

**Proposal:**
- A fixed /shop price (tier 1 $117k, tier 2 $213k).
- Diminishing output per island and tier, counted across all blocks and outposts: spawners 1–16 at 100%, 17–32 at 60%, 33–64 at 35%, 65–128 at 20%, 129+ at 10%.
- Spawners stay tradeable (988). This drops 642, the +10% per owned spawner.

**Why:** the brake works wherever the spawner is placed, so alts and trading can't skip it. +10% is a wall: the 25th spawner costs $1.15M (49 days payback) and the 50th $12.5M.

**Model** (patched copy, shop tier 1–2 only, 900 players):

| Scenario | Sink ratio | Money supply day 90 |
|---|---|---|
| +10% plan, no trading | 84% | $1.8T |
| +10% plan, alts buy at base | 75% | $3.7T |
| Fixed price + curve | 84% | $1.8T |

**Where in code:**
- Multiply `E(n)/n` into `rateMultiplier` (`SpawnerManager.java:151`), which feeds `mobsPerMinute` and `spawnerOutput`.
- Keep per-island and per-tier totals in memory.
- Add `stacking.yml spawners.efficiency` and `shop.yml price-growth-per-owned: 1.0`.

**What the player sees:**
- Spawner menu: "Tier 2 here: 38 = 26.0 at full output. Next works at 35%."
- /shop tile: "Pays back in 171 h (about 14 days at your 12 h/day)". Green up to 100 h, yellow up to 300 h, red "Poor deal" above that.

### 4.4 Tradeable gems (R3-5a, R3-19, R3-14) → F3–F6
**Owner:** "i want gems to be tradeable, like in the /trade and pay command, crystals are for islands only and is NOT tradeable, everything beside crystals is tradeable".

- **Code:** two flags block it today: `economy.yml tradeable.gems` and `trade.yml money.currencies`. `/pay` and `/trade` already handle GEMS, and `/pay` confirms at 100 gems or more.
- **Effects if all gems trade:**
  - Real money becomes in-game money: €50 ≈ 5,000 gems, sold for $.
  - Alt vote funnels: 10 alts make about 150 gems a day.
  - Gems become the way to carry wealth across a season wipe.
  - Chargebacks can't be clawed back once gems have moved.
  - R3-14 binding becomes pointless.
- **Proposed guardrails (keep the decision):**
  - **F3:** store purchases arrive as a non-tradeable `credits` balance, spent first. Earned gems are tradeable.
  - **F4:**
    - To send gems: tutorial done, 5 h played, account 3+ days old.
    - Send max 300 a day, receive max 1,000 a day.
    - Only via /pay and /trade (no notes, AH or wagers).
    - Lower `economy-watch.yml` gem thresholds (big 300, staff 200, earn-rate 100/h).
    - Freeze transfers 48 h before and 24 h after a season reset.
  - **F5:** items bought with credits stay bound; items bought with earned gems can trade.
  - **F6:** island size via gems uses earned gems only.
- **Mojang:** the 2020 update to the Commercial Usage Guidelines says the test is whether a sold item or feature is designed to give a **competitive gameplay advantage** ([minecraft.net, 17 Apr 2020](https://www.minecraft.net/en-us/article/commercial-usage-guidelines-updated)). The full terms are at account.mojang.com/terms#commercial.
  - Paid island size, paid sell/XP bonus, and store gems that can be sold for in-game money are the risky ones.
  - Read the current full text before the store opens (M11.1). It's a hard blocker; nothing in the repo quotes it yet.

### 4.5 Rank perk model (R3-7) → F7
**Owner:** "all ranks have x perks, and of default the permissions for ranks are possible to be gained by playing the game and they are not immediately given, but some are".

**Proposal:** every perk gets one tag.

| Perk | Tag | Bought rank | Earned by playing |
|---|---|---|---|
| Prefix, colour, rank tag | Cosmetic | by tier | Ashborn after the tutorial; achievement titles |
| Borders, trails, join message, nick colours | Cosmetic | by tier | earned gems, crates, pass |
| Monthly cosmetic (R3-8) | Cosmetic | by tier | Eclipse crate tag |
| `/hat` | Convenience | Hatchling | 2 h active |
| `/craft` | Convenience | Fledgling | 10 h active |
| `/ec`, plain `/nick` | Convenience | Firewing | 25 h + skill total 50 |
| Island fly | Convenience | Flamecrest | Wings mastery (exists) |
| AH slots | Convenience | +1 to +4 | economy achievements, up to +2 |
| Profile slots, loadouts | Convenience | by tier | 500 / 1,500 earned gems |
| `/pv` vaults | Convenience | 1 to 4 | 500 earned gems each |
| Reserved slot / queue | Access | by tier | not earnable |
| Sell %, skill XP % | Gameplay | **0%** | upgrades, Market Contacts, sets, achievements |
| Fuel and keys in kits | Gameplay | none | calendar, AFK pool, quests |

**How it works:**
- A perk is unlocked if the player has the LuckPerms node **or** meets its `earn:` rule in `perks.yml`. Active hours exclude the AFK pool.
- Won season ranks keep their gameplay bonuses.
- One `/perks` page shows every perk's tag and how to earn it. It's a new command, so ask the owner first.
- Remove fuel from weekly kits.
- Rerun the model: income is about 4–8% lower without the paid bonus.

### 4.6 Island bank as a permission (R3-11) → F8
**Owner:** "it has to be a permission, the leader is the leader of the island and we try not to limit the players".

**Proposal:**
- Withdraw is a role permission the leader can give to any role, with a daily % limit per role that the leader sets.
- One safety rule: a member who joined less than 3 days ago can't withdraw.
- Every withdrawal appears in the bank history and pings the leader.

**Today:** it's built mostly this way (`islands.yml permissions.role-defaults.bank-withdraw: [admin]`, `bank.yml island.withdraw-limit-percent`). Admin has no cap.

### 4.7 Quick-buy safety (R3-29) → F14
**Owner:** "not necessary, we need better logic, think".

**Proposal:**
- No confirm screens.
- An "Undo last buy" button in the shop works for 15 s, with a full refund while the items are still in the inventory (a spawner until it's placed).
- A buy over 50% of the player's money needs a second click on the same button.
- This reuses the [Undo] pattern from P2 and works on Bedrock because it's a menu button, not clickable chat.

### 4.8 Staff creative island editing (R3-6) → F15
**Owner:** "make sure staff such as admin and owner can edit islands with creative mode somehow, maybe in /s staffmode and go creative and edit, and island bypass automatically when in /s, this is only for owner and admin".

**Proposal:**
- In `/s`, owner and admin may use creative on islands.
- `/is bypass` turns on by itself when entering `/s` and off when leaving.
- Every placed or broken block is logged.
- Items from creative are staff-tagged (`tag-staff-items: true`; they can't be sold, traded or listed).

**Today:**
- Creative is allowed only in `essentials.yml staff-powers.creative-worlds` (answer 331).
- The bypass is a manual toggle (`IslandProtection.java:238`).

### 4.9 Special spawner drops (R3-21a): write it down
**Owner:** "we will find a solution to that later, but mostly yes only vanilla, but for some we will have silverfish drop netherstars, villagers emerald etc write it down".

- Add to `docs/planning/CUSTOM_MOB_DROPS.md`: spawner mobs drop vanilla loot only, except a short list of special drops the owner will pick (examples: **silverfish → nether stars**, **villagers → emeralds**).
- Balance them in that file. Emeralds are `sellable: false` today, so decide their price there.

### 4.10 Hoppers (R3-27) → F12, F13
**Owner:** "make sure hoppers are 64 items per tick and yeah they're fast, this will reduce lag".

- **Live today:** `spigot.yml ticks-per.hopper-transfer: 8`, `hopper-check: 8`, `hopper-amount: 1`.
- **Proposal F12:** `hopper-amount: 64` with `hopper-transfer: 8`. That moves a full stack every 0.4 s, about 160 items/s per hopper (64× vanilla), with far fewer move operations.
  - Moving every tick would add work, not save it.
  - Vanilla item sorters (one item per move) stop working; collectors and storage do the sorting.
- **Also check** Paper `hopper.cooldown-when-full: true`.
- **Don't set `disable-move-event: true`:** `ItemStacker` and `MenuService` listen to `InventoryMoveItemEvent`.
- **F13:** count hoppers per chunk and alert staff above 64. No hard cap unless the load test shows lag.

---

## 5. Follow-ups (all answered 30 Oct; the final decisions are in §7 and override this table and §4)
| Id | Question (short) | My recommendation |
|---|---|---|
| F1 | R3-1: profile sends money only after earning $10k itself | Yes |
| F2 | Spawners: fixed price + diminishing output | Yes |
| F3 | Store gems arrive as non-tradeable credits; earned gems trade | Yes |
| F4 | Gem sending gate + daily limits | Yes |
| F5 | Credit-bought items bound, earned-gem items tradeable | Yes |
| F6 | Island size with earned gems only | Yes |
| F7 | Perk tags; bought ranks 0% gameplay; convenience earnable | Yes |
| F8 | Bank withdraw = leader-set permission + 3-day rule | Yes |
| F9 | Kick: island keeps 50% of contribution, preview, 50% deposits back | Yes |
| F10 | Leaving by choice: no loss for the island | Yes |
| F11 | No leader kicks in the last 14 days of a season | Yes |
| F12 | Hoppers: 64 items every 8 ticks | Yes (not every tick) |
| F13 | Hopper counter + staff alert above 64 | Yes |
| F14 | Undo last buy (15 s) + second click over 50% of money | Yes |
| F15 | Creative island editing in /s for owner/admin, auto-bypass | Yes |
| D1–D4 | Depths v2 zones, lower tier, combat v1, visibility | Yes; plan md first |
| K1–K8 | GUI sketches (separate job, done by the review session) | — |

---

## 6. Notes for the main session (ideas, not orders)
These are things the review session noticed. Use what helps, and ask the owner whenever something is unclear.

**Problems seen**
- **Answers don't always reach the server.** 641 and N2-1 were answered and never applied, and nothing caught it.
- **Review docs had wrong facts.** 13 of 44 REVIEW3 items were wrong or out of date, so the owner was deciding on bad information. The owner wants every planning file fact-checked against code and live configs before he sees it (S3 = yes). Add "Today:" lines with real config values.
- **Question load.** The owner has had more than 1,000 questions plus 25 planning files. Many answers were bulk "Suggested" taps in a few seconds, and that's where the contradictions came from. The owner now does **one planning md at a time**. Fewer, sharper questions with the current state shown get better answers.
- **Dead settings and stale docs** pile up after quick changes (the 30-minute keys, `homes.*`, old rank names in the art brief).
- **`STATUS.md` header** still says "Last updated: 2026-10-29 (after Phase 1 merge)", and the file mixes history with state. Keep it to the current state and let `CHANGELOG.md` hold the history.
- **Release tooling is fragile in a fresh session:**
  - `release2.sh` copies `$D/claude_deploy`, but `.skyborn-claude` only holds `deploy_key`.
  - It also assumes the mount `~/mnt/Development/...`. When folders are connected one by one, they mount as `~/mnt/skyblock` and `~/mnt/.skyborn-claude`.
  - In this session SSH worked with `deploy_key` as `minecraft@vps-24fa665d.vps.ovh.net`.
  - Make the key name and paths robust (look for either).
- **Cleanup (S4 = yes):** `.skyborn-claude` holds 614 MB (78 bundles, 52 deploy tarballs). Keep the last 5 of each. The Mac checkout has leftover untracked files from 01:55 (FreeRank*, MultiplierCap, SkillAdmin, `common/moderation/PlayerBannedEvent`) that are already in `main`. Deleting needs the owner's delete permission once.
- **GitHub access** isn't enabled for cloud sessions (`gh` returns 403), so bundles via the Mac stay the only path. Worth a line in STATUS.
- **Snapshots** made by the review session are in `.skyborn-claude/snapshots/` (repo tarball and a live-config tarball without secrets).
- **Releases (S2):** the owner wants new things released in batches.

**Ideas for efficiency and quality**
- **Build shared foundations once**, before the features that need them:
  - a bound-item system (R3-2, R3-14, F3, F5, SECURITY F-05);
  - one per-IP / alt-link service (R3-3, R3-10, R3-33, R3-42, F4, F11);
  - one visibility service (R3-36, D4, vanish).
- **Decision ledger:** `docs/decisions.yml` maps each answer id to a config path and value. A unit test checks the tracked configs against it, and a small VPS script checks the live ones. That turns "641 never applied" into a red build.
- **Dead-key test:** every key in a tracked yml must be read by a config class (or be on an allow list).
- **Fact-check step for planning files:** Sonnet agents read code and live configs, fill in "Today:" lines, and mark settled items. That is how REVIEW3 v2 was made.
- **Load test before big Depths work:** an 80-bot run on the test server, with the owner's OK as always.
- **Answer pages:** the owner prefers clickable pages with Recommended / No / Other buttons for many questions, and md files for the developer.

---

## 7. Follow-up answers (owner, 30 Oct, late): FINAL, these override §4 and §5
| Id | Owner's answer (his words in quotes) | What to build |
|---|---|---|
| F1 | "yes unless its to island members on the island they are on" | ✅ A profile can send money away (/pay, notes, /trade, AH buying) only after it has earned $10k itself. **Exception:** sending to members of the island the sender is on, including island bank deposits, is always allowed. |
| F2 | "oo i like, we are closing to something" | ✅ Spawners: fixed shop price plus diminishing output per island and tier (§4.3), with payback shown in /shop and the spawner menu. Fine-tune it through the feature card loop (see FEATURES.md). |
| F3 | "opposite, earned are bound, bought are tradeable" | ✅ **Gems bought in the store are tradeable. Gems earned in play (votes, streaks, prizes, thanks) are bound to the account.** Two balances. Crystals are never tradeable. ⚠ **Risk to log:** bought gems that can be traded for in-game money are a real-money → in-game-money path. Check Mojang's current commercial terms before the store opens (M11.1). Chargebacks: remove the rank and flag the account (RK15), and freeze the account's gem transfers. |
| F4 | "no, and look at f3" | ✅ No gem sending limits or gates. Still log every gem transfer and lower the economy-watch gem thresholds so big moves are seen. |
| F5 | "when they are right clicked, they are claimed then no tradeable, but if its unclaimed then yes it can be sold, through anywhere, /ah, /trade etc" | ✅ Items bought with gems arrive as an **unclaimed item**. Unclaimed, it can be sold or traded anywhere (AH, /trade, drop). Right-click claims it: it is added to the account and becomes bound. This is how store tags already work. Use it for skins, tags, sets and cosmetics. |
| F6 | "both" | ✅ Island sizes above 9x9 can be bought with shards (Black Market, expensive) or gems, bought or earned. |
| F7 | "NOT playtime, but if you imagine the whole grind from start game to end game as a battle pass, thats the princip, it has to be planned better" | 📝 Perks are unlocked along **one progression track from start game to end game, like a battle pass** (not by hours played). A bought rank unlocks its cosmetic and convenience perks at once. Gameplay perks are never sold. This needs its own plan, made through the feature card loop. Don't build until it's locked. |
| F8 | No | ✅ Island bank withdraw is fully the leader's choice per role, with no 3-day rule. Keep the history and the leader ping. |
| F9 | Recommended | ✅ (Phase 5) A kicked member's contribution: the island keeps 50%, with a preview before the kick. The kicked player keeps personal progress and gets 50% of their unspent bank deposits back. |
| F10 | "yes, and if player leaves or gets kicked, the island needs to wait 48 hours until they can have someone take over his slot… fix message for this and make sure ALL messages are saved somewhere" | ✅ Leaving by choice: the island keeps 100%. **New rule: a slot freed by a leave or kick stays locked for 48 h before the island can invite into it.** Example: limit 4, the 4th member leaves, so no invite for 48 h. Show it in the members menu and the invite error, e.g. "A slot opens in 31h 12m (Alex left)". Put every new text in `messages/islands.yml`. **All player-facing text must stay in `messages/<module>.yml`** (it does today: 33 files); no text in code. |
| F11 | "YES and there are also rewards for biggest contributors in general, fairplay" | ✅ No leader kicks in the last 14 days of a season (staff still can). 📝 Add season rewards for each island's biggest contributors (fair play). Plan it with Phase 5 and Phase 12. |
| F12 | "yes, if this is optimized and safe 64 items every 8 tick" | ✅ `spigot.yml hopper-amount: 64`, `hopper-transfer: 8`, `hopper-check: 8`, and Paper `hopper.cooldown-when-full: true`. Before release, check that it works with collectors, stacking (`ItemStacker` move events) and storage, with no dupes on full or partial stacks. Measure MSPT with a hopper-heavy test island. |
| F13 | "we need to make sure it wont lag, optimize or find a way to make it safe" | ✅ Hopper counter: count on block place and break only (a per-chunk int in memory, saved with chunk data or rebuilt lazily on chunk load). No world scanning and no per-tick work. Send at most one staff alert per chunk per hour. |
| F14 | "possible to toggle, and yes" | ✅ "Undo last buy" for 15 s, plus a second click for buys over 50% of the player's money. Both can be switched off per player in `/settings` (default on). |
| F15 | Recommended | ✅ In `/s` staff mode, owner and admin can use creative on islands. `/is bypass` turns on automatically in `/s` and off when leaving. Every placed or broken block is logged, and creative items are staff-tagged. |
| D1 | Recommended | 📝 Depths v2: 5 zones per line, a dungeon boss hall and the main boss arena, with tier palettes. **Write `PHASE-4-DEPTHS-V2.md` first** (the review session writes it as the next md). |
| D2 | "yes and the different tiers has different rewards and quantity for the shared ones etc" | ✅ A player can pick a lower unlocked tier. Each tier in a shared zone has its own drop table and quantities. |
| D3 | "layered mobs from the start, plan it an write and be critique if any questions ask its simple" | 📝 Combat zones use layered mobs from the start (each player sees and fights their own tier's mobs). It goes in the Depths v2 plan, with a critical review and questions for the owner. |
| D4 | Recommended | ✅ Visibility: Everyone (default) / Friends + island / Nobody, plus pinning a player. Built as one shared visibility service. |
| K1–K8 | Sketches: mockup page + exact-size PNG templates, 2 style directions on /menu first, smart-hub /menu, 9 screens in round 1, rank colours and badges, chat/tab/scoreboard mocks. **K7: "they ARE gonna be final art if they are good, i need the best".** K8: delivered in the chat for now. | The review session makes these; nothing for the developer yet. |

**New owner process (important, from F2):** every feature gets a short "feature card" md. The owner shows a card to an AI, says yes or no to its ideas, repeats until nothing useful comes back, and only then is it built. The index is `FEATURES.md`. Build a feature only after its card is marked **Locked**.

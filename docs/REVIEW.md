# Design Review #1: Problems, Gaps and Decisions

> Written 2026-10-29 as a critical pass over `DESIGN.md` before any gameplay code exists.
> Each item has a **problem**, a **proposed default**, and a status.
> **Rule:** a proposed default applies unless the owner changes it. When the owner decides, move the result into `DESIGN.md` and mark the item ✅.
>
> Severity: 🔴 must be settled before the related phase is coded · 🟡 important · 🟢 polish

---

## A. Contradictions and design errors

### A1 🔴 Money can buy island levels
**Problem:** Island Level = Σ pillars + Σ upgrade levels. Upgrades cost money, and money can come from gems/store. Upgrades are also far cheaper per level than late pillar levels, so the top would be decided by upgrade spending.
**Default:** Upgrade levels give **island level points weighted by tier** (upgrade level 1–3 = 1 point, 4–6 = 2, 7+ = 3), and every upgrade level *also* requires its tasks (which can't be bought). Gems can **never** buy upgrade levels or task progress.

### A2 🔴 Three overlapping "mining progressions"
**Problem:** Mining has a skill (player), a pillar (island), tool level (player), and Depths tier unlocks. Players won't understand which one does what, and the code will duplicate logic.
**Default (one clear job each):**
| System | Owner | Job |
|---|---|---|
| Skill | Player | Personal perks (speed, fortune), feeds pillar XP |
| Pillar | Island | Island level + island unlocks (outposts, biomes) |
| Tool tier + level | Player (per tool) | Tool power (tier from the Forge), perk slots (level) |
| Depths tier | **Player** | Which zone you may enter. Unlocked by **your skill level** in that aspect (not "blocks broken", which duplicates skill XP) |

So "blocks broken" from the owner's answer becomes skill XP, which comes *from* blocks broken. The behavior is the same, with one system instead of two.

### A3 ✅ Custom enchants on tools (resolved: Omnitool replaced by Forge tools, 2026-10-29)
**Resolution:** Tools are separate custom items (Forge + living tools + blueprints).
**Default:** Custom enchants go into the **perk slots of custom tools** and on **set armor pieces**. Vanilla tools/armor can exist for building but take vanilla enchants only.

### A4 🟡 Coop/Trust role vs. the solo→team rule
**Problem:** Does trusting someone (coop) turn a solo island into a team island? If yes, solo players can't let a friend help build. If no, coop is a loophole (a friend grinds without being a member).
**Default:** Coop **does not** convert, but coop players **give no pillar XP / task progress** to the island and can't use the bank. They can only build/break with permissions.

### A5 🔴 Island hopping
**Problem:** Nothing stops a strong player from joining island A for the week, then island B, carrying each.
**Default:** XP a member earned **stays with the island they earned it on**. Rejoin cooldown 24 h after leaving/kick (tunable). Max **join count per season** (e.g. 3). Team size cap (see B1).

### A6 ✅ Season-top store credits for teams (decided: split by contribution)
**Problem:** "400 credits for team #1": to whom?
**Default:** Paid to the **leader**, who distributes, *or* split evenly among members with ≥ X% contribution. Owner decides. Proposal: split by contribution share, minimum 5%.

### A7 🔴 "gg" gem reward abuse
**Default:** gg must be typed within 30 s of a store purchase announcement, once per purchase per player, max N per day, accounts < 1 h playtime excluded.

### A8 🟡 PvP + paid gems + custom enchants
**Problem:** Enchants work in PvP, and gems can buy power, so KOTH becomes pay-to-win. Combined with the EULA risk, this hurts trust.
**Default:** In PvP zones, custom enchant power is **normalized** (PvP-specific values in config), and gem items are never combat stats.

### A9 🟡 Store items wiped at season end
**Problem:** Collectors, sell wands, keys bought with real money get wiped. This drives chargebacks and angry players.
**Default:** The store clearly labels **"Season item: removed at season end"** vs **"Permanent"** (ranks, cosmetics, titles). Unspent gems and credits are **kept**. Gameplay items from the store are listed as seasonal.

### A10 🟢 Interest compounding check
0.25% / 3 h = 8 payouts/day ≈ +2.0%/day ≈ **×6 over 90 days** without caps. The per-payout cap is mandatory, not optional. The default cap grows with the Bank Capacity level.

---

## B. Missing core rules

### B1 🔴 Team size cap
Team islands compete on one leaderboard, so a 25-member island beats everyone. **Default:** max members = 4 at start, Member Slots upgrade up to **8**.

### B2 🔴 Vanilla survival basics: where do building materials come from?
Not defined anywhere: sand, gravel, clay, glass, dirt, ice, nether/end blocks, dyes, string, slime, etc.
**Default:** a **Builder Shop** (`/shop`) selling building blocks for money (a real money sink), plus a few drops from Depths tiers. See `WORLD.md` §6 for the full vanilla rules proposal.

### B3 ✅ Nether, End, villagers, vanilla enchanting, elytra, redstone, mob farms (decided, see WORLD §6)
All undefined. They hugely affect economy and performance. **Defaults are in `WORLD.md` §6** (Nether/End off, villagers no trading, elytra not obtainable, redstone allowed with limits, vanilla mob farms limited by entity caps).

### B4 🔴 Offline production must be simulated, not chunk-loaded
**Problem:** "Farms work while members are offline" cannot mean keeping chunks loaded. 200+ offline islands would kill the server.
**Default:** When the island unloads, the server **snapshots** its production (spawners, crops, collectors) and **calculates** output for the offline time (capped by Island Fuel) when the island loads again or a member checks. Output goes into collectors/storage. No chunks stay loaded.

### B5 🔴 Inventory full in The Depths
Mining at 1 block/s fills an inventory in ~1 minute. Nothing is defined.
**Default:** Drops go to a **per-player Depths Satchel** (virtual storage, size upgradable by skill/tool level), with one-click **Sell All** in the satchel GUI. An auto-sell toggle is an upgrade/rank perk. Satchel contents are visible in `/menu`.

### B6 🔴 Per-player blocks and shared mobs/players
**Problem:** Visibility "none" hides players, but mobs are shared. Others' mobs and others' kills look like ghosts.
**Default:** Depths mobs are **per-player instanced** (only you see/fight yours) in mine/forest zones (they're ambience). **Combat zones** and **world bosses** use shared mobs, where visibility = "everyone" is forced.

### B7 🔴 Death rules
**Default:**
| Where | Keep inventory | Penalty |
|---|---|---|
| Island / spawn / Depths | Yes | None (Depths: 5% satchel loss? No: keep it friendly) |
| PvP zones / KOTH / envoys | Yes for custom tools + sets (kept on death) | Lose carried **loot** items from the event; killer gets a % |
| Void fall on island | Teleport to island home, no damage |
Combat logging in PvP zones = death.

### B8 🟡 Who unlocks The Depths tiers in a team?
Per player (see A2). New members start at their own skill tiers. This prevents "carry" unlocks.

### B9 🟡 Island physical layout
Undefined: starting size, max size, spacing, world height, outpost location.
**Default (see `WORLD.md` §3):** islands aligned to **region files** (512×512 per island slot), so deleting and resetting is a file delete. Start 3×3 chunks, max 15×15. Outposts live in their own void world, same grid.

### B10 🟡 Leaving, disbanding, transferring
Undefined. **Default:** `/is leave` (confirm), `/is disband` (leader, double confirm, 24 h cooldown before a new island), `/is transfer <member>`. A disbanded island's level is removed from the top.

### B11 🟡 Server economy start values
Undefined: starting balance, tutorial rewards, hopper price, first upgrade costs. These are needed in Phase 2. The developer proposes a **balance sheet** (spreadsheet) before coding Phase 5. All values go in config.

---

## C. Missing player features (expected on any big server)

| Feature | Default |
|---|---|
| **Secure trade** `/trade` | Two-sided GUI, both confirm, 3 s countdown after the last change, logged |
| `/msg`, `/r`, `/ignore`, `/mail` (offline) | Yes. `/ignore` also blocks trade/tpa requests |
| **Settings menu** `/settings` | Toggles: chat notifications, island visit alerts, Depths visibility, scoreboard on/off, tips, PM, trade/tpa requests, sounds, particle effects |
| `/afk` + AFK detection | AFK players are kicked after X min from spawn only. Islands: allowed (fuel already handles offline) |
| `/stats [player]` | Player profile GUI: skills, playtime, kills, titles, collection % |
| `/rules`, `/discord`, `/store`, `/vote` | Clickable links, also in `/help` |
| **Titles** | Chosen in `/titles`, shown in chat prefix and tab. Earned from season tops, collection log, achievements, prestige |
| **Cosmetics** (kept across seasons) | Chat colors, tags, particle trails, kill effects, island border colors, tool skins, pet skins, join messages |
| `/fly` | On your own island only, rank perk or upgrade |
| **Trash** `/trash` | Yes |
| **Island chat** `/is chat` and team notifications | Yes |
| **Hall of Fame** | Spawn monument + `/halloffame` with past season winners (the season archive table) |

---

## D. Staff, safety and legal

### D1 ✅ Crates and paid keys (decided: keys are earn-only, never sold)
- **Show drop odds** in every crate preview (required by Tebex and many countries, and fair).
- Belgium/Netherlands have strict loot box rules. Decide whether keys are sold for money at all, or only earned.
- **Decided 2026-10-29:** keys are **earned only** (voting, quests, events). They are never sold for money or gems. Odds are still shown in every crate preview.

### D2 🔴 Privacy (GDPR, Norway/EU)
The store, Discord linking and stored IPs (punishments) are personal data. Needed: privacy policy, data retention, deletion requests. Players under 13 need care. **Default:** privacy policy + rules page before launch; IPs hashed where possible.

### D3 🟡 Chat safety for younger players
A chat filter (slurs, links, ads, personal info like phone numbers), anti-spam, anti-caps, and a mute-on-report threshold. All configurable.

### D4 🟡 Staff ops missing
Staff chat `/sc`, broadcast and auto-announcer, maintenance mode + whitelist for beta, restart scheduler with countdown, automated backups (DB + worlds, off-site), appeal process (Discord tickets), alt detection (same IP, flagged not banned).

### D5 🟡 Economy monitoring
Admin dashboard: money in circulation, top balances, money created/destroyed per day, sell volume per item. Inflation must be visible to catch problems early. Discord alerts on anomalies (e.g. a player gaining 10× normal per hour, which is a dupe signal).

---

## E. Technical risks

### E1 🔴 300 players on one Paper server
Possible, but only with discipline: high single-core CPU (e.g. Ryzen 9 7950X/9950X class), 16–24 GB RAM for the JVM, view-distance 6–8 / simulation-distance 4–5, entity/hopper limits, async everything, simulated offline islands (B4), per-player packet blocks instead of real blocks (cheap). **Default:** load test with bots at 350 before beta (Phase 13).

### E2 🔴 Bedrock limits
- Geyser shows **only the 16 legacy colors**. Hex colors get rounded. The palette in `UI_STYLE.md` defines a legacy fallback for every color.
- **No custom-font GUI textures** (negative-space tricks) without a Bedrock fallback. Default: plain chest GUIs with good icons, on both platforms.
- Custom item models need Geyser custom-item mappings. Plan the resource pack with this from day one.
- Unicode symbols: only those verified to render on Bedrock (list in `UI_STYLE.md`).
- Bedrock players' names have a prefix (`.Name`). Commands and tab completion must handle it.

### E3 🟡 Item identity
Every custom item carries a **PersistentDataContainer id + UUID** (for unique items like custom tools). Logic never reads display names or lore. Dupe detection: unique items with a duplicate UUID get flagged and removed.

### E4 🟡 Data and season archive
Each season gets a `season_id`. Wipes create a new season id instead of deleting rows, so history stays (Hall of Fame, stats, appeals).

---

### E5 🔴 Network-ready from the start
SkybornMC is a network. Defaults: Velocity proxy + hub before beta; Phoenix Skyblock code keeps all data in MariaDB and treats join/quit as possibly a server switch (see DESIGN §15). LuckPerms must use MariaDB on every server so ranks are shared.

## F. Owner answers that need a follow-up

| # | Topic | Question |
|---|---|---|
| F1 | Shards | How are shards earned? (Proposal: bosses, envoys, KOTH, high Depths tiers) |
| F2 | Sets | How are set pieces obtained? (Proposal: crafted from Depths drops + tokens, upgraded with tokens) |
| F3 | Mine drops | Proposal: ore items to the satchel (sell), tokens, rare key/shard chance, rare "geode" items |
| F4 | Team top 2/3 rewards | Proposal: 400 / 250 / 150 |
| F5 | Server name | Needed for branding, prefix, package |
| F6 | Leaderboard prize split | See A6 |
| F7 | Loot box decision | See D1 |

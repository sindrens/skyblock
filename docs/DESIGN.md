# Game Design Specification

> **Status:** Living document, v0.1 (2026-10-29). This is the single source of truth for *what* we build.
> Values marked **(tunable)** are starting points and must live in config, never in code.
> Anything not decided yet is listed in [`OPEN_QUESTIONS.md`](OPEN_QUESTIONS.md). Do not guess; ask the owner.
> Companion specs: [`UI_STYLE.md`](UI_STYLE.md) (colors, lore, GUIs, sounds, help), [`WORLD.md`](WORLD.md) (worlds, builds, tiers, vanilla rules), [`REVIEW.md`](REVIEW.md) (open problems with default decisions; defaults apply until the owner changes them).

---

## 0. Vision and pillars of the design

**Phoenix Skyblock** on the **SkybornMC** network (`play.skybornmc.net`): a brand-new, heavily custom **seasonal Skyblock** server for **Java + Bedrock (Geyser)** on **Paper 26.3**, built to hold **200–300 concurrent players** stably.

> **Version policy (decided 2026-10-29):** stay on the **latest stable Minecraft version** Paper and Geyser support (currently **26.3**, Java 25). Bedrock clients auto-update, so Geyser must stay current, and Geyser only supports the newest Java version. Update with each Minecraft release during development. **Client versions:** players on **1.21.5 up to the current version** can join (ViaVersion + ViaBackwards). 1.21.5 is the minimum because older protocols are exploitable and miss features we use (custom item models, pale oak).

Audience: everyone (casual players, grinders, PvPers, younger players, free-to-play and paying players).

Design rules that apply to *every* feature:

1. **Synergy.** Every system feeds others. Nobody wins by grinding one thing for 100 hours.
2. **Less is more.** Simple to understand, deep to master. If a feature needs a wall of text to explain, redesign it.
3. **Fast, felt progress.** Players should *feel* progress within their first minutes and every session after.
4. **Always something to do.** Mining, foraging, farming, combat, conquest, quests and events, all within 1–2 clicks.
5. **User-friendliness first.** Every feature is discoverable from `/menu` and `/help`, and every action is GUI-based. See [`STANDARDS.md`](STANDARDS.md).
6. **100% configurable in-game.** Every value, message, item, schematic, reward and timer is editable from an in-game admin GUI (permission-gated) and saved to config.
7. **Gameplay only, no lore.** Branding is the phoenix: mainly red, balanced with ember, gold and neutrals (see `UI_STYLE.md`).
8. **English only.**

---

## 1. Seasons

| Item | Value |
|---|---|
| Season length | 3 months (tunable) |
| Pre-season | 1 week of normal gameplay with faster progress (global boost, tunable), then a full wipe |
| Wiped at reset | Islands, outposts, levels, pillars, skills, tools, money, crystals, shards, items, loot |
| Kept at reset | Titles, cosmetics, ranks (gems: see open questions) |
| Final week | Double Conquest XP + extra KOTHs, so the island top is never decided early |

### Season leaderboard rewards (store credit, placeholders)

| Place | Solo islands | Team islands |
|---|---|---|
| 1 | 150 | 400 (team prizes split by contribution share, min. 5%) |
| 2 | 100 | TBD |
| 3 | 50 | TBD |

There are also **weekly mini-leaderboards** (island level gained, pillars, skills, boss damage) with smaller relevant rewards.

---

## 2. First join and tutorial

- The player spawns at a **medieval spawn**. An NPC guide offers the **optional, short tutorial** (never forced, skippable at any time, re-openable with `/tutorial`).
- Tutorial target: **under 3 minutes.** Steps: claim the **starter kit** from the Quartermaster → create island → break first blocks in the mine → chop a tree → sell → see `/is upgrade`. Done.
- The tutorial gives a completion reward.
- The whole start is a "soft tutorial": action bar hints, a clear next-step objective on the scoreboard, and clickable chat links.

---

## 3. Islands

### 3.1 Creation and grid
- A custom island system (no third-party island plugin).
- Islands are **aligned to chunks**. The island border expands in whole chunks.
- The starting island is a **configurable schematic**, swappable in-game.
- Every player gets a **solo island** (no solo/team prompt).

### 3.2 Solo → team (permanent)
- The moment a player **invites** someone, the island permanently becomes a **team island** (tracked by island UUID).
- It does **not** revert to solo when members are kicked or leave.
- A **confirmation warning** is shown before the first invite is sent, explaining that the change is permanent and that solo and team islands compete on separate leaderboards.
- Attempts to bypass (e.g. kicking everyone to re-enter the solo top) get a clear, friendly explanation.

### 3.3 Roles and permissions
- Preset roles: **Leader, Mod, Member, Coop/Trust**.
- The Leader edits each role's permissions in a **GUI** (build, break, containers, spawners, bank withdraw/deposit, invite, kick, warps, upgrades, biomes, outposts, settings).
- M2.3 (built): the GUI is a matrix of roles × the 14 build/use actions (break, place, interact, containers, redstone, animals, villagers, pickup, drop, armor stands, vehicles, fire, ender pearls, spawners); Mods edit Member/Coop/Visitor but never Mod/Leader. People management is fixed per role: Leader + Mod invite, kick (Mod only members), coop and set the home; only the Leader promotes, demotes and transfers. Bank, warps, upgrades, biomes, outposts and settings permissions join the matrix with their features.
- Coop (REVIEW A4): temporary trust that ends when no Leader/Mod of the island is online; no XP, task progress or bank; no member slot; never converts solo → team.

### 3.4 Visiting
- `/is visit <player>` and the warp GUI.
- 3 public **island warps** per island (tunable), with a warp browser ranked by visits/votes, as an **island showcase**.
- The Leader can **lock** the island. Members get notified when someone tries to visit, arrives or leaves.
- M2.4 (built): visit browser (most visited, top rated, most liked, newest), `/is lock|unlock|expel|ban|unban` (Leader + Mod), 3 warps per island with categories (shops, farms, parkour, builds, other), icon, public/private and `[warp]` sign warps (count toward the slots), warp browser by category, ratings 1-5 (one per player per island per season, changeable) and likes (one per player per island per day, separate from ratings). Rating and liking need a visit first. Member alerts for locked-visit attempts, arrivals and departures are `/settings` toggles. Details: `docs/commands/islands.md`.

### 3.5 Upgrades = masteries (`/is upgrade`)
`/is upgrade` and `/is masteries` are the **same GUI**. Every upgrade has levels. Each level requires:
- **Tasks** relevant to the upgrade (e.g. Island Size I: mine 500 coal ore + chop 300 oak logs), progressing **automatically while playing**, and
- A **money** cost (a sink).

Difficulty rises progressively. Early levels are easy.

Upgrades (all tunable, list can grow):

| Upgrade | Effect |
|---|---|
| Island Size | Border +1 chunk ring |
| Member Slots | Max members: 4 at start, up to 8 (REVIEW B1) |
| Generator | Better custom-generator ore tiers |
| Spawner Rate | Faster spawners |
| Spawner Stack Limit | More spawners per stack |
| Crop Growth | Faster crops |
| Harvest Yield | Chance for double crops |
| Lumberjack | Chance for double logs or rare drops |
| Mine Fortune | Island-wide bonus in the mine |
| Mob Loot | Extra spawner drops |
| Sell Multiplier | Island-wide sell bonus |
| Bank Capacity / Bank Interest | Island bank |
| Island Vault | Shared storage size |
| Warp Slots | Public warps |
| Outpost Slots | See §3.7 |
| Biome Tier / Biome Fusion | See §3.8 |
| Fuel Tank | Offline production storage (§5.4) |
| KOTH Banner | Small capture bonus |
| Envoy Luck | Better envoy crate odds |
| XP Boost | Island-wide skill XP bonus |

**No hopper limit.** Hoppers are priced so having many isn't worth it.

### 3.6 Island level: the 5 pillars

**Island Level = Σ pillar levels + Σ completed upgrade levels.**

> Upgrade levels are weighted by tier and can never be bought with gems (REVIEW A1).

| Pillar | Fed by |
|---|---|
| Mining | Blocks broken in the mine (higher ore tier = more XP) |
| Foraging | Logs in the forest (higher tree tier = more XP) |
| Farming | Crops, custom crops, fishing |
| Combat | Mobs, spawner kills, dungeon zones, bosses (damage share) |
| Conquest | KOTH, envoys, events, treasure hunts |

Rules:
- **No caps.** Synergy comes from the cost curve: XP for the next pillar level = `base × 1.12^level` (tunable). Your weakest pillar is always the cheapest place to gain island levels.
- **Balance bonus:** +2% XP to all pillars for each pillar within 10 levels of the highest pillar (max +10%).
- **Synergy milestones:** All pillars ≥ 10 / 25 / 40 / 60 unlock big island rewards (outposts, fusion, perks).
- **Selling gives no XP.** Money buys upgrades, and upgrades give levels, so the economy counts without allowing "dumping".
- **Offline output** gives 25% of manual Farming XP.
- Level never decreases within a season.
- Team islands get faster with more members but only compete with other team islands. No scaling.

XP per tier (tunable): each tier gives ~1.45× the previous. Example ore table:

| Tier | 1 Coal | 2 Copper | 3 Iron | 4 Gold | 5 Redstone | 6 Lapis | 7 Diamond | 8 Emerald | 9–12 Custom |
|---|---|---|---|---|---|---|---|---|---|
| XP | 1 | 1.5 | 2.5 | 4 | 6 | 9 | 13 | 19 | 28 / 40 / 58 / 85 |

Target pace (1 active player, ~1 block/s): pillar 30 ≈ 3–4 h, pillar 60 ≈ 35 h, pillar 80 ≈ 300 h+ (the natural ceiling for hardcore teams).

**`/is stats` GUI:** totals per category (blocks mined, logs, crops, farm output, items sold, money earned, KOTH time, bosses), per-member contribution, this week vs. last week, and progress to the next level. Island top tie-breaker: total XP.

### 3.7 Outposts
Small extra islands for farms, unlocked by progression. **Max 3.**

| Outpost | Unlock (tunable) | Biomes |
|---|---|---|
| 1: Greenhold (farming) | All pillars ≥ 10 | Plains (crop growth), Jungle (crop yield), Desert (crop sell value) |
| 2: Wildwood (combat/spawners) | All pillars ≥ 25 + first world boss defeated | Dark Forest (spawner speed), Swamp (mob drops), Snowy Taiga (XP drops) |
| 3: Emberdeep (late game) | All pillars ≥ 40 + Combat tier X | Nether Wastes (spawner stack), Crimson Forest (rare drops), Soul Sand Valley (boss key chance) |

### 3.8 Biomes
- The main island starts with normal biomes.
- Each outpost has **its own set of 3 biomes**. A biome belongs to exactly one outpost.
- Biomes **unlock** through the related pillar, and are **upgradeable** (Tier I–V).
- **Biome Fusion** (outpost upgrade): Fusion I merges 2 biomes, Fusion II merges all 3. Merged biomes give 75% of each effect (tunable).

---

## 4. Economy

### 4.0 Income meta (decided 2026-10-29)
The Depths ≈ 50% of income (active, main), island production ≈ 35% (steady, partly offline), quests/events/KOTH ≈ 15%. **Big numbers**: start $1,000, mid-game millions, late-game billions. Details: `CONTEXT.md` §3, `phases/PHASE-3-ECONOMY.md`.

### 4.1 Currencies

| Currency | Earned by | Used for |
|---|---|---|
| **Money** | Selling, quests, events, interest | Upgrades, shop, auction house, everything general |
| **Crystals** (island crystals) | Using custom tools (breaking, killing, chopping, farming) and island missions | Island shop, custom enchants (`/enchanter`), Forge extras. **Not tradeable** (no `/pay`, not on the AH) |
| **Gems** | Store credits, voting, saying "gg" when someone buys from the store | Premium items in-game |
| **Shards** | (see open questions) | The **Black Market** (rotating rare shop) |

- Island crystals replaced tokens (owner decision O8 / #39, CR1–CR4 in `DECISIONS_2026-10-30.md`); old `tokens` data, config keys and placeholders are migrated or aliased to crystals.
- `/pay <player> <amount> [currency]`. Tradeable currencies are configurable per currency (money and shards yes; gems and crystals no).
- Money can be withdrawn as **physical notes** (M3.2, built): `/withdraw <amount>` (money only, no fee, $100 to $1T, 10 s cooldown, not for accounts under 30 min played, owner #40) gives a unique Money Note; right-click cashes it. Every note is a database row cashed in one transaction, so a copy can never be cashed twice: it is confiscated, staff are alerted and it is audited. Staff: `/eco note`. Details: `docs/commands/economy.md`.

### 4.2 Selling and dynamic prices
- `/sell` GUI, sell wand, auto-sell chest, chunk collector.
- **Global dynamic prices.** Each item has a base price. Global sales push the price down, and it recovers toward base over time (e.g. 2% per 10 min). Floor 40% / ceiling 150% of base (tunable). Unsold items climb above base, which rewards selling a mix.
- The GUI shows price, trend arrow and % from base. A daily **Hot Item** gets a temporary boost.
- Prices reset at season start.
- Selling (M3.4, built): `/sell` sell box (tap items in your inventory to add them; they stay in the inventory until the sale), `/sell hand`, `/sell all`; prices in `sell.yml` (draft, owner approves before launch); dynamic prices with adaptive impact (settles at 85% of base at normal volume), 2%/10 min recovery, +10%/day drift for unsold items, daily Hot Item +25%; unique items (tools, enchant books, notes, set pieces) are never sellable; every sale is one ledger transaction paid to the active profile. Details: `docs/commands/economy.md`.
- **Max total sell multiplier** (upgrades + rank + boosters + sets) is capped at **+300% (×4.00)** (owner #46, tunable; reached only with the best of everything). See `ECONOMY_BALANCE.md` §8.

### 4.3 Auction house and player shops
- `/ah` auction house and player shops.
- Auction house (M3.6, built): money only; 1% listing fee + 5% sales tax (decided); 12/24/48 h listings; 5 listings by default, more by rank (`skyblock.auction.slots.<n>`); tag items, tools, blueprints and skins can be sold, starter-kit items and money notes can't; players with < 30 min playtime can't list or buy (owner #40); sold money reaches the seller even offline; expired/cancelled items wait in a collection bin. Details: `docs/commands/auction.md`.
- No extra money sinks beyond gameplay costs (owner decision). Inflation is controlled by price floors, interest caps and offline fuel.

### 4.4 Banks (`/bank` shows both)
| | Island bank | Personal bank |
|---|---|---|
| Access | Members, by role permission | Owner only |
| Capacity | Grows with Bank Capacity upgrade | Grows with personal tiers |
| Interest | 0.25% every 3 h (tunable) | 0.15% every 3 h (tunable) |
| Payout cap | Per payout, grows with upgrades | Per payout, grows with tier |
| Early withdrawal | Forfeits current cycle's interest + ~2% fee | Same |
| Special | Funds upgrades | Safe if you leave/are kicked; **10 balance tiers** unlock useful perks (lower AH tax, extra AH slots, sell bonus, etc.) |

- Interest is paid every 3 hours, **offline too**. The GUI shows the time until the next payout.

---

## 5. Tools, The Depths and gathering

### 5.1 Tools (decided 2026-10-29; replaces the old Omnitool idea)
Four connected systems. **All custom tools, blueprints and skins are tradeable** (trade, auction house) and **never wear out** (no durability). Every tool is a unique item (PDC id + UUID) for dupe protection.

| System | How it works |
|---|---|
| **Starter kit** (Quartermaster NPC at spawn) | Free basic custom pickaxe, axe, hoe, sword and rod. Re-claimable every 24 h if lost. The first step of the tutorial |
| **The Forge** (blacksmith NPC at spawn) | Forges tier tools (pickaxe, axe, hoe, sword, rod) from **Depths materials + money**. Each Depths tier unlocks the next tool tier (Coal Pickaxe → Copper → Iron → … → Voidstone). Grinding a tier gives the materials for the next tool |
| **Living tools** | Every custom tool gains XP when used and levels **1–50**. Levels give speed/fortune and unlock **perk slots** where custom enchants go (`/enchanter`, crystals). The tool remembers its stats (blocks mined, kills…) |
| **Blueprints** | Rare drops in the Depths and from bosses (e.g. *Blueprint: Magma Pickaxe*, Epic). Blueprint + materials at the Forge = a special tool with a unique ability. Valuable trade items |
| **Skins** | Cosmetic looks for tools (Phoenix Flame, Frostbite, Royal Gold …) from crates, store and events; applied in a skin menu; tradeable; kept across seasons |

Tool tiers, recipes, XP curve, perk slots and abilities live in config (`tools.yml`) and are balanced in the balance sheet.

### 5.2 The Depths (one big shared area)
- A single large area containing the **mine, the forest and the combat/dungeon zones**, each split into **tiers**.
- **`/dungeon`** opens a GUI to teleport to any unlocked tier in any aspect (mining, foraging, combat), including going back to earlier tiers.
- The next tier unlocks by the player's **skill level** in that aspect. Skill XP comes from blocks broken / logs chopped / mobs killed (see REVIEW A2). Tier tables: `WORLD.md` §4.
- **Per-player blocks:** each player sees their own ore/log blocks (packet-based), so nobody steals ore. A broken block **respawns after 10 s** (tunable).
- **Visibility setting:** see other players: everyone / island only / none.
- **No PvP** in The Depths. Ambient mobs exist. Danger scales with tier but is never too hard.
- Mine drops go to the per-player **Depths Satchel** (REVIEW B5). Drop mix: see open questions.
- Custom ores and trees after vanilla tiers. Names: proposed by the developer, approved by the owner.

### 5.3 Island generators, farms and spawners
- **Custom cobble generators** with upgradeable ore tiers.
- **Custom crops** + **harvester hoe** (custom hoe from the Forge).
- **Spawners:** the best ones are obtainable, stackable (max stack tunable). Types are **not** tier-gated by Combat pillar.
- **Mob stacking** for performance.
- **Minions/collectors:** yes (automation exists).
- **Auto-sell chests and chunk collectors:** Tier 1–5. **Merge 2 of the same tier into the next tier** (faster pickup, more storage). Obtained **only** from crates, the store and the monthly crate (not craftable).

### 5.4 Offline production: Island Fuel
- Every minute a member spends on the island adds 1 minute of **fuel**.
- When all members are offline, farms, collectors and spawners run on fuel.
- Starting max: 12 h, grows with the **Fuel Tank** upgrade.

---

## 6. Skills, sets and enchants

### 6.1 Skills (per player, reset each season)
Mining, Foraging, Farming, Combat, Fishing. **Max level 150** (owner #65); tiers unlock up to level 66, levels 67–150 cost 2% more each (`ECONOMY_BALANCE.md` §3.4). Staff: `/skills set|add` (admin + owner). Balanced levels with perks along the way. Skill leaderboards. Player skill XP also feeds the matching island pillar.

### 6.2 Sets (`/wardrobe`, replaces `/sets`) and `/loadout`
**Full plan: [`docs/plans/WARDROBE.md`](plans/WARDROBE.md)** (owner SE1 + L1–L4): 16 sets from money, crystals, gems and crates, set levels 0–50 (0× → 2×), merging two level-50 sets into one with both bonuses (level back to 0), a virtual wardrobe with locked real armor, and `/loadout` presets (armor, pet, hotbar tools). The table below is the original theme list the plan grew from:

| Set | Theme |
|---|---|
| Farmer | Crops near you grow faster, Haste, extra crops when harvesting |
| Miner | More crystals, faster block breaking in the mine |
| Merchant | More money when selling |
| Forager | Double logs chance, faster chopping |
| Warrior | Damage/defense vs. mobs and bosses |
| Angler | Better fishing loot and speed |
| Conqueror | Envoy luck, KOTH capture speed |

### 6.3 Custom enchants (`/enchanter`)
- A decent, balanced number across all tools and armor, in tiers.
- **Always guaranteed** (no fail/destroy chance).
- Applied in the `/enchanter` GUI into a tool's **perk slots** (from tool levels), paid with **crystals** earned by using tools and island missions.
- Can be levelled, removed and moved.
- Area enchants (explosive, vein miner, treefeller) are included, balanced.
- Enchants work in PvP.

---

## 7. PvE

- Custom mobs with abilities (difficulty by tier).
- **World bosses** + summonable bosses, damage leaderboard with top-3 rewards.
- Custom gear sets with set bonuses, and a stat system.
- **Pets:** the monthly crate gives a pet egg, which opens into a random pet egg of a rarity level. Pets level up through use and give buffs.
- Custom fishing system.

## 8. Conquest and PvP

- **KOTH:** the player captures; it also gives Conquest XP to their island. Normal rules, scheduled.
- **Envoys:** scheduled supply drops with loot tiers.
- PvP zones besides KOTH/envoys, `/duel` with wagers.
- Islands are always safe.
- Fairness for new players (e.g. low-level events).

## 9. Crates, rewards and quests

- **Crates:** Common, Uncommon, Rare, Epic, Legendary (+ Monthly). Physical crates at spawn **and** `/crates`. Animations.
- **Keys are earned only** (voting, quests, events). Never sold for money or gems (owner decision 2026-10-29). Keys are **virtual** but can be withdrawn as items in the `/crates` GUI. Tradeable.
- Daily rewards / login streak, voting with vote parties.
- Daily + weekly quests, battle pass (free + paid track), **achievements** (many, including fun/secret ones), **collection log**.
- **Titles and tags** (decided 2026-10-29), shown together in one **Tags & Titles** menu (`/tags`):
  - **Titles** are *earned*: achievements (many cool ones, not only winning), season tops, collection milestones, prestige. Kept across seasons.
  - **Tags** are *owned* cosmetics: won from crates or bought in the store. They come as a **tag item**: right-click to redeem it to your account. Unredeemed or extra tag items can be traded and **sold on the auction house**. Owned tags are kept across seasons.
  - The player picks one title and one tag to show in chat/tab.
- **Black Market:** rotating limited-stock shop paid in shards.
- **Tool prestige**: a level-50 tool can be prestiged (reset to level 1 for a permanent title/cosmetic + a small bonus on that tool).
- No rank-up system. Island level + skills are the progression.

## 10. Events (automatic)

Treasure hunt, money event, farming event, mining rush, double XP and more, all **automatic** on a configurable scheduler. **No** drop parties or build contests. Boss-bar countdowns and Discord announcements.

## 11. Ranks and store

- Players buy **credits** on the store (Tebex). Credits buy things in-game.
- **Free rank `Ember`** (owner RW4, `/freerank`, built in R2A): after 30 min playtime every player claims it once, with a cosmetic name color (`#E0A070`) and a choice of 1 of 3 tags (ember, spark, ash). Kept forever. Its small perks are not decided yet (OPEN_QUESTIONS).
- **5 donor ranks:** proposed names **Squire → Knight → Paladin → Warlord → Monarch**.
- Perks: worth it but not overpowered: more `/pv` vaults, cosmetics, convenience, and a **Progress Boost** of 2/4/6/8/10%.
- Progress Boost math: pillar level cost grows 12% per level, so +10% XP ≈ `ln(1.10)/ln(1.12)` ≈ **0.84 pillar levels ahead** at equal playtime, i.e. ~4 island levels across 5 pillars. Noticeable, not decisive. The boost counts toward the global multiplier cap.

> ⚠️ Owner is aware: selling gameplay advantages risks Mojang's commercial usage guidelines.

## 12. Community and integration
- Full **Discord** integration: account linking (with reward), event/KOTH/boss announcements, ticket bot, store announcements.
- Guilds/alliances between islands.

## 13. Endgame (months 2–3)
Tool prestige, blueprint hunting, collection log, high-tier dungeon zones with leaderboards, weekly leaderboards, Final Week, limited late-season cosmetics.

---

## 14. Plugins: custom vs. external (decided 2026-10-29)

All gameplay and server features are **custom**. External plugins only where building our own would be worse and cost years:

| Plugin | Status | Reason |
|---|---|---|
| Geyser + Floodgate | **Use** | Bedrock protocol translation |
| Grim (anticheat) | **Use** | Specialist work, Bedrock-aware |
| spark | **Built into Paper**, no separate plugin | Profiling (`/spark`) |
| FastAsyncWorldEdit | **Use** (builders/staff only) | Building spawn, Depths, warzone |
| LuckPerms | **Use** (confirmed) | Permissions/ranks backbone, invisible to players |
| CoreProtect | **Use** (confirmed), blocks only | Block logging/rollback at scale. Economy/item/staff logs are custom |
| ViaVersion + ViaBackwards | **Use** | Lets players on 1.21.5+ join the newest-version server |
| PacketEvents | **Library**, shaded into our jar | Per-player blocks, border, NPC packets |

Custom (not plugins): punishments, Discord bot, essentials (spawn/tpa/back/homes/vanish), chat, scoreboard, tab, holograms (TextDisplay), NPCs (mannequins), world management, economy (no Vault needed), Tebex integration via API, Votifier receiver, crates, everything gameplay.

---

## 15. SkybornMC network (decided 2026-10-29)

- **SkybornMC** (`play.skybornmc.net`) is a **network**. **Phoenix Skyblock** is its first gamemode, with the phoenix theme (mainly red). Other gamemodes may follow with their own themes.
- During development, Phoenix Skyblock runs as **one standalone Paper server**.
- Before beta, the network gets a **Velocity proxy** + **hub/lobby** server. Geyser and Floodgate then move to the proxy, and the MOTD/server list is set there.
- **Build for this from day one** (so no rewrite is needed later):
  - All persistent data in the **database** (MariaDB), keyed by player UUID. No gameplay data in local files.
  - No assumptions that the skyblock server is the only server (e.g. join/quit can be a server switch, not a network leave).
  - Network-wide things (ranks via LuckPerms, punishments, Discord linking, store credits, cosmetics) stored so other servers can read them.
  - Chat/announcements that may later go network-wide go through one service, so they can be swapped for cross-server messaging.

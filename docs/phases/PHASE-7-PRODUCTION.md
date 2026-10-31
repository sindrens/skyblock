# Phase 7: Island Production

**Goal:** the island earns while you play and, with fuel, while you are away. Players build cobble generators with better ores, grow 11 crop tiers with a harvester hoe, run stacked spawners, catch fish, collect drops in chunk collectors, sell them with auto-sell chests, keep it all running on **Island Fuel**, and late in the season unlock **outposts** with biomes and fusion. Island production is the steady ~35% of income. **It never beats The Depths for an active player.**

Depends on Phases 2–5 (islands, economy + dynamic sell, skills, tools, stacking, AFK pool, upgrades). Design: `DESIGN.md` §3.5–3.8, §5.3–5.4, `WORLD.md` §5, `REVIEW.md` B4, A9, `ECONOMY_BALANCE.md` §1.1, §4, §5.3, §7.5, §10 (R5, R10), `plans/ENCHANTS.md` §3.1, §6.4, §6.7 (E6), `plans/WARDROBE.md` (farming and fishing sets), `PHASE-5-PROGRESSION.md` (production upgrades, Fuel Tank, MA8/MA10).

**Every name, number and rule below is a default** the owner can change. Numbers go into typed configs, text into `messages/<module>.yml`. Unanswered decisions are in the QUESTIONS JSON at the end.

Owner decisions this plan follows:
- **DESIGN §5.3–5.4:** custom cobble generators with ore tiers; custom crops + harvester hoe; spawners stackable and not gated by Combat; collectors and auto-sell chests **tier 1–5, merge 2 of a tier into the next**, obtained **only** from crates, the store and the monthly crate (not craftable); Island Fuel: 1 minute on the island = 1 minute of fuel, start max 12 h, grows with Fuel Tank.
- **REVIEW B4:** offline production is **simulated**, never chunk-loaded.
- **O1/O2:** no block limits, no hopper limit, no filter hoppers; lag is handled by optimized systems and lag-machine detection. **WR2** no piston/observer limits.
- **O10 / RW3:** AFK pool gives fuel 1:1 (built, fuel saved as pending grants until this phase).
- **I8 / ST1–ST6:** no collector overflow into island storage; storage stays a plain vault.
- **V4** generator = vanilla ores + 2 custom. **V5** collectors sell at the dynamic price and run on fuel offline. **V6** harvester hoe 3×3 / 5×5 / 7×7, auto-replant, from the Forge.
- **S20–S28, SK1–SK10:** stacking and spawners as built in `modules/stacking` (this phase links to it and does not repeat it).
- **MA8 Fuel Efficiency, MA9 Beast Tamer, MA10 Angler:** yes (Phase 5 shows them "Coming soon" until this phase).
- **414:** islands **and outposts** are wiped at season reset. **A9:** store collectors are labelled "Season item".
- **Principles:** no waiting, nothing lost by accident, everything reachable from menus (Bedrock), English only, short lores, V2 look.

Not to be confused with the owner's separate AutoSell mod project: collectors here are a server feature.

---

## How the pieces fit (one job each)

| System | Belongs to | Job | Module |
|---|---|---|---|
| Island Fuel | Island | Keeps production running when no member is active | new `modules/production` + `common/production` |
| Generator | Island | Better ores from any cobble generator (mined by hand) | `modules/production` |
| Crops | Island | 11 crop tiers, growth engine, Farming skill | `modules/production` (+ skills, shop) |
| Harvester hoe, rods | Player tool | Farming and fishing tools in the Forge | `modules/tools` |
| Spawners + stacking | Island | Mobs and drops (**built**: `modules/stacking`) | `modules/stacking` (extended) |
| Chunk Collector | Island block | Picks up every drop in its chunk, online and offline | `modules/production` |
| Auto-sell Chest | Island block | Sells what it holds (and its chunk's collector) on a timer | `modules/production` |
| Offline simulation | Island | Calculates fueled output when the island loads again | `modules/production` (+ stacking API) |
| Fishing | Player | Fishing skill, loot by tier, custom fish | new `modules/fishing` |
| Outposts + biomes | Island | Up to 3 small extra islands with biome bonuses and fusion | new `modules/outposts` |

**Phase 7 can start before Phase 5 is built.** Every upgrade it reads (Generator, Crop Growth, Harvest Yield, Mob Loot, Fuel Tank, Outpost Slots, Biome tiers) goes through an `IslandService` hook with a default (level 0), the same way `spawnerStackLimit` and `spawnerRateMultiplier` work today. Phase 5 fills the hooks. Outposts (M7.9) need the pillars (M5.1) and are the only hard Phase 5 dependency.

---

## M7.1 Island Fuel (core)
The fuel tank decides whether the island works while nobody is active on it.

**States of an island** (checked every 60 s, `production.yml → fuel.check-seconds`):
| State | When | Production | Fuel |
|---|---|---|---|
| **Active** | A member is on the island (or one of its outposts) and was active in the last 15 min | Full speed | **+1 min per minute** for the island (not per member) |
| **Fueled** | No active member, tank > 0 | Full speed, simulated if unloaded (M7.6) | **−1 min per minute** × burn rate |
| **Asleep** | No active member, tank empty | Stops (crops pause, spawners stop, collectors idle) | 0 |

- **Active** means a real action in the last 15 min (`fuel.active-minutes`): break or place a block, hit a mob, harvest, fish, use a menu, chat, or walk 5+ blocks not in water, a vehicle or a bubble column. Standing AFK on the island burns fuel, it does not earn it (R5, rule 9).
- Only time on **your own active profile's island** earns fuel. The Depths, spawn and visiting earn none.
- **Coop players and visitors** never earn fuel and never make the island Active (REVIEW A4). Spawners still run near any player within 16 blocks (S24, built), but only member activity counts for fuel.
- **Tank size:** 12 h at start, **+2 h per Fuel Tank level** (12 levels, 36 h at level 12). `fuel.tank-hours-by-level`.
- **Burn rate:** 1.0, lowered by **Fuel Efficiency** (MA8: fuel lasts +10 / +20 / +30%) and island achievements (fuel efficiency, max +10%, `plans/RANKS_TITLES_ACHIEVEMENTS.md`). One bonus family `FUEL_EFFICIENCY`, cap **+40%**.
- **Other fuel sources** (all capped by the tank; a full tank refuses, so nothing is lost):
  - AFK pool: 15 min per reward, 1:1, max 8 h per account and IP per day (built; its pending grants are delivered on the first start of M7.1).
  - **Fuel Cells** (items: 1 h, 3 h, 6 h) from crates, the daily calendar, the battle pass, rank kits and the island shop (crystals). Right-click on your island adds the fuel. **Bound to the profile**, never tradeable, so money can't buy production hours.
- One tank runs the main island **and all its outposts**. Outposts burn no extra fuel.
- **Fuel shown:** island scoreboard line `Fuel 7h 20m` (or `Fuel Active`), a gauge in the island menu and `/is production`, and a join message if the island fell asleep ("Your island ran out of fuel 2h ago").
- **Hooks filled:** `IslandService#hasIslandFuel` (spawners already read it) and `IslandService#addIslandFuel`. The islands module delegates both to a `FuelProvider` registered by `modules/production` (same pattern as `bankFeeMasteryProvider`). The AFK module's fuel detection switches from the reflection check to "a `ProductionService` is registered".

**Data:** `island_fuel` (island_id, fuel_ms, tank_level, last_state, state_since, updated_at). Fuel changes in memory, saved every 30 s and on unload; grants (AFK, cells, staff) are one conditional `UPDATE … SET fuel_ms = LEAST(fuel_ms + ?, ?)` with an audit row `production/fuel_add` (island, minutes, source, actor, tank after). A Fuel Cell is claimed (item removed) before the fuel is added; a failed add gives the item back.
**Config:** `production.yml → fuel.*` (active minutes, check seconds, tank hours by level, burn rate, cell sizes).

## M7.2 Custom generators
- Any vanilla cobble generator on an island (lava + water forming cobblestone or stone, `BlockFormEvent`) forms a block rolled from the island's **Generator level** table instead. Basalt generators stay vanilla.
- Generator level 0–10 comes from the Generator production upgrade (Phase 5, `IslandService#generatorLevel`, default 0).
- **Generator ores are mined by hand.** There are no drills or auto-miners in Phase 7, so generator output is active play. Ore blocks drop vanilla items (coal, raw iron …) and the two custom ores drop custom items, all at the island price (`ECONOMY_BALANCE.md` §4.1: island items sell for half the Depths item, one tier lower).

| Level | Cobble | Coal | Copper | Iron | Gold | Redstone | Lapis | Diamond | Emerald | Ashstone | Emberite |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 0 | 100 | | | | | | | | | | |
| 1 | 90 | 10 | | | | | | | | | |
| 2 | 82 | 12 | 6 | | | | | | | | |
| 3 | 74 | 12 | 8 | 6 | | | | | | | |
| 4 | 66 | 12 | 9 | 8 | 5 | | | | | | |
| 5 | 60 | 11 | 9 | 8 | 7 | 5 | | | | | |
| 6 | 54 | 10 | 9 | 8 | 7 | 7 | 5 | | | | |
| 7 | 49 | 9 | 8 | 8 | 7 | 7 | 7 | 5 | | | |
| 8 | 45 | 8 | 8 | 7 | 7 | 7 | 7 | 6 | 5 | | |
| 9 | 42 | 7 | 7 | 7 | 7 | 7 | 6 | 6 | 6 | 5 | |
| 10 | 40 | 6 | 6 | 6 | 6 | 6 | 6 | 6 | 6 | 7 | 5 |
| Drop price | $1 | $10 | $14 | $18 | $25 | $33 | $45 | $61 | $82 | $180 | $396 |

(weights in %, `generators.yml → levels`; names Ashstone / Emberite are proposals)
- **XP:** no Mining skill XP (M4.5 rule: Mining XP only in The Depths). Generator ores give **Mining pillar XP at 25%** of the matching Depths ore XP. Only blocks the generator really made count (remembered per island, like the wardrobe's `BlockMemory`).
- **Books:** island generator ores roll Mining-pool books at 25% of the Depths chance (ENCHANTS §3.1). M7.2 adds the island gather hook the enchants module waits for (`IslandGatherEvent`: player, block, tier, generator-made). **Area enchants (Vein Rend …) don't break real island blocks.**
- **Lag guard:** at most `generators.max-rolls-per-second` (40) custom rolls per island; above it the generator forms plain cobblestone. Lag-machine detection (WR2) stays the main guard.
- Generator output already counts in `/is stats` (`GENERATOR_OUTPUT`, built).

## M7.3 Farming: crops, growth and the Farming skill
**Crop tiers** (`WORLD.md` §5.1, prices `ECONOMY_BALANCE.md` §4.1). Planting a crop needs its Farming level; a locked seed is refused with "Farming 15 needed: 9/15". Seeds in `/shop` get `requires: {farming: N}` (built support).

| Tier | Crop | Farming level | Crop item | Farming XP per ripe crop |
|---|---|---|---|---|
| 1 | Wheat | 0 | $10 | 1 |
| 2 | Carrot, Potato | 5 | $14 | 1.5 |
| 3 | Beetroot | 10 | $18 | 2.5 |
| 4 | Sugar cane | 15 | $25 | 4 |
| 5 | Melon, Pumpkin | 20 | $33 | 6 |
| 6 | Cocoa | 25 | $45 | 9 |
| 7 | Cactus | 30 | $61 | 13 |
| 8 | Sweet berries | 35 | $82 | 19 |
| 9 | Nether wart | 42 | $180 | 28 |
| 10 | Torchflower | 50 | $396 | 40 |
| 11 | Pitcher plant | 58 | $870 | 58 |

- **"Custom crops"** in Phase 7 = these 11 tiers with our own rules, plus the **Golden crop** from the Golden Seed enchant (E6). Fully custom crops with their own models wait for the resource pack.
- **Growth engine:** takes over `modules/stacking`'s dark-crop growth (S25) for island worlds. One budgeted task grows crops in loaded island chunks using the crop index that already exists (chunk data), light or not. Speed = vanilla average × Crop Growth upgrade (+12% per level) × the crop growth family (Cropwarden set, Plains biome, boosters; cap +100%, WARDROBE). `crops.per-tick` limits the work.
- **Harvest:** ripe crops broken by hand or with any hoe drop normally. **Harvest Yield** (+12% double-crop chance step per level) applies to every ripe harvest on the island.
- **Farming skill XP** only for ripe crops broken **with a hoe** (any hoe; the wardrobe already uses this rule). Feeds the Farming pillar (Phase 5). **Custom hoes** also give tool XP and roll Farming-pool books (ENCHANTS §3.2, 1 in 4,000–7,000).
- **Anti-farm:** crops grown with bone meal drop normally but give no skill XP, tool XP, set XP or books (positions remembered in memory, forgotten on restart). Placed-then-broken blocks already give no stats (M2.6). Coop players get drops, the island gets no XP.
- **Skills:** `skills.yml → available` adds `farming`; `/skills` drops "Coming soon" for Farming. Farming tier unlocks follow the same level curve as Mining (§3.4).
- **Wardrobe:** Tiller's Smock already works; Cropwarden Robes become buyable once crop tier 6 is reachable. Farming achievements (F3) switch on.
- **Event for E6:** `IslandCropHarvestEvent` (player, tool, crop tier, blocks, area harvest, replanted, bone-mealed) and a growth-stage API (Green Thumb, Golden crop).
- **Built (M7-3, 31 Oct):** `modules/production` farming behind `crops.yml → enabled: false` (crop tiers, planting gates, hoe-only Farming XP for members on their own island, grown/bone-meal memory, growth multiplier on every natural growth step, Harvest Yield copies, `FarmingService`, `IslandCropHarvestEvent`, `GameActionEvent.HARVEST_CROP`); Farming in `/skills`, Crop Growth / Harvest Yield live and harvest quests while it is on. Details: `docs/commands/production.md`; open points: `OPEN_QUESTIONS.md` M7-3. Area harvest / replant fields and the growth-stage API come with M7.4 / E6.

## M7.4 Harvester hoe and rod tiers (Forge)
- **Hoes:** 11 tiers, one per crop tier (basic stone hoe = tier 1, then copper, iron, gold, diamond, netherite and 5 custom names). Forged with money `F(t)` and `M(t)` crops of the tier before (`ECONOMY_BALANCE.md` §1.2), gated by Farming level. Upgrading keeps level, perks and stats (M4.2 rule).
- **Harvester area** by tool level: **3×3 from Lv 1, 5×5 at Lv 15, 7×7 at Lv 30**. Wide Reap adds rings up to 9×9 (E6). Right-click a ripe crop = harvest the area; every crop is **replanted** from its own drops (one seed each). Only ripe crops in the area are touched, only on islands and outposts where the player may break crops.
- Higher hoe tiers give **+5% crop drops per tier** (same rule as pickaxe drops) and more tool XP per crop.
- **Rods:** 12 tiers, one per Fishing tier (M7.8), forged with fish of the tier before, gated by Fishing level. Higher tiers: −3% wait per tier (speed family cap) and more tool XP per catch.
- Perk slots, prestige, skins and blueprints work like other tools. The Forge's Hoe and Rod tabs lose "Coming soon".
- `tools-defaults.yml → tiers.hoe / tiers.rod` get the full lists. No new command.

## M7.5 Chunk Collectors and Auto-sell Chests (tier 1–5)
Two placeable custom blocks. Both are unique items (PDC id + UUID, dupe detection), **from crates, the store and the monthly crate only** (Phase 10 tables already list them). Store copies say "Season item: removed at season end" (A9).

**Chunk Collector** (a retexturable block, e.g. a barrel):
- Picks up **every item dropped in its chunk**: mob loot, crop drops, piston farm drops, grinder kills. No entity scans: drops are caught when they spawn (`ItemSpawnEvent`, death and block drops) and counted straight into the collector.
- Unique items (tools, notes, books, anything with an item id) are never collected; they stay on the ground.
- **One collector per chunk.** It works for its own chunk only (on islands and outposts).
- **Full:** it stops picking up, the items stay on the ground as stacked items (STACK rules), and members get one warning per hour.
- Menu: contents by item with amount and value, **Sell all** (the member who clicks gets the money with the normal `/sell` rules and bonuses), take out (to inventory, overflow to `/box`), tier and next-merge info, offline share, linked Auto-sell Chest.

**Auto-sell Chest** (a retexturable chest):
- Sells its contents every interval at the **dynamic sell price** (V5). Hoppers and players can fill it.
- If a Chunk Collector is in the same chunk, the chest also sells from the collector (up to its per-sell cap). This is how a farm sells itself without hoppers.
- **Money goes to the island bank** (ledger reason `collector_sell`), with **island** sell bonuses only (Sell Multiplier upgrade, Market Contacts, Desert biome). Personal rank and booster bonuses need a player to sell. All bonuses stay under the +300% cap.
- One Auto-sell Chest per chunk. Items that can't be sold (no `sell.yml` price) stay inside.

| Tier | Collector storage | Collector offline share | Auto-sell interval | Auto-sell cap per sell | Rarity |
|---|---|---|---|---|---|
| 1 | 5,000 items | 60% | 10 min | 2,000 items | Common |
| 2 | 20,000 | 70% | 5 min | 8,000 | Uncommon |
| 3 | 80,000 | 80% | 2 min | 32,000 | Rare |
| 4 | 320,000 | 90% | 1 min | 128,000 | Epic |
| 5 | 1,280,000 | 100% | 30 s | 512,000 | Legendary |

- **Offline share** = how much of the simulated offline output (M7.6) the collector catches. It is the "faster pickup" of a higher tier and a lever against AFK production (R5).
- **Merge:** right-click a placed collector (or chest) with a second one of the **same kind and tier** → confirm screen → the placed block becomes the next tier and keeps its contents. Tier 5 can't merge. One database transaction: both item ids checked, the hand item consumed, the block's tier raised, audit `production/collector_merge`.
- **Starter collector:** every new island gets **one bound tier 1 Chunk Collector** from the tutorial finish (can't be traded, merged or sold). Without it, offline income would depend on crate luck. (Question PD20.)
- **Who may use them:** island role permission `containers` opens them; a new role key `collectors` (break, merge, take out, Sell all) defaults to Leader, Admin and Mod. Visitors never.
- **Breaking** one gives the item back with its contents kept inside the item's database row (nothing is lost); placing it again restores them.

**Data:** `production_collectors` (collector_id, item_uuid, island_id, world_name, chunk_x, chunk_z, block_x, block_y, block_z, collector_kind, collector_tier, bound, placed_by, placed_at), `production_collector_items` (collector_id, item_key, amount). Contents live in memory while loaded (counts per item key, not ItemStacks), saved every 30 s and on unload. Every place, break, merge, take-out and sell is audited (`island` category).

## M7.6 Offline simulation (REVIEW B4)
No production chunk is ever kept loaded. When an island's chunks unload, the engine keeps a **snapshot** per chunk: crops by tier and stage, spawner stacks (from `stacking_spawners`), collector and chest. When the island loads again (a member, coop or visitor arrives) or a member opens `/is production`, the engine calculates the **fueled minutes** since the snapshot (tank and burn rate), and applies:

| What | Without a collector in the chunk | With a Chunk Collector in the chunk |
|---|---|---|
| Crops | Grow by the fueled time; ripe crops wait for you | Full growth cycles are harvested virtually (drops × cycles × offline share, one seed back per cycle) into the collector; crops are left ripe |
| Spawners | Spawned mobs join the stacks next to the spawner, up to the cap (8 stacks × 1,024 per type per chunk); you kill them when you come back | Spawned mobs are killed virtually: their vanilla loot × offline share goes into the collector (no XP orbs) |
| Auto-sell Chest | Sells what it holds, one sell per interval of fueled time | Also sells from the collector |

- The math is pure (no Bukkit calls) and runs off the main thread; results are applied on the main thread in small batches. Loot uses the stacking module's `mobs.loot-samples` method (expected value, not one roll per mob).
- **Offline output gives no skill XP, tool XP, set XP or books.** Pillars: 25% of the manual Farming XP for crops (DESIGN §3.6); spawner kills give no Combat pillar XP (PG7).
- **Return report:** on join, members see one line ("While you were away: 6h 10m fueled, $412k sold, collectors 73% full"); `/is production` shows the full report.
- Online time near the island is real (vanilla growth, real mobs, real drops); the simulation only covers time with nobody near.

**Data:** `production_snapshots` (island_id, chunk_key, snapshot_kind, payload, taken_at). Snapshots are small (counts per type), written on unload in one batch.

## M7.7 Spawners: what this phase adds to `modules/stacking`
Stacking, stack limits, the spawner menu, kill rules and entity limits are **built** (`docs/commands/stacking.md`). This phase adds:
- **Offline output** (M7.6) and collector pickup (M7.5) for spawner drops.
- **Mob Loot** upgrade (+12% drops per level) and the **Spawner Rate** hook already read; the spawner menu's Upgrades button opens the island upgrade menu (no longer "Coming soon") and shows fuel state.
- **Sources (S26):** tier 2–3 spawner drops from The Depths combat zones (`depths.yml` drop tables), tier 1–2 in `/shop` with the growing price, tier 3–5 from crates and the Black Market (Phase 10). Per-island spawner counts by tier come from `StackingService#spawnerCount`.
- Spawners on outposts work the same (the Wildwood biomes boost them).
- Two numbers are **already waiting for the owner** and are not asked again here: `spawners.mobs-per-spawner` 4 → 1, and the shop price +10% per spawner owned (`OPEN_QUESTIONS.md`, BAL2). The balance pass (M7.11) uses the suggested values.

## M7.8 Fishing (skill, loot, custom fish)
Basic fishing comes in Phase 7 because the Fishing skill, rod enchants (E6) and fishing sets all wait for it. Sea creatures, lava fishing and fishing contests (FI 80) stay in Phase 8.
- Works in any water on **islands, outposts and marked spawn ponds** (a build region of kind `fishing`). Not in The Depths or the warzone.
- **Custom loot needs a custom rod** (the starter rod counts). Vanilla rods give vanilla loot and no XP.
- **12 fishing tiers** by Fishing level (0, 5, 10 … 58, 66, the Depths unlock levels). Each tier adds one **custom fish**: a named vanilla fish item (Bedrock-safe) with a rarity and a price. Names (proposals): Pond Cod, River Salmon, Reef Snapper, Spinefish, Ember Trout, Ashfin, Glowscale, Cinder Eel, Magma Carp, Starlight Koi, Voidfin, Phoenix Koi.
- **Loot mix** per catch: 70% fish (weighted toward your newest tiers), 15% junk, 10% treasure (small crystal pouch, vanilla enchanted book, name tag), 5% rare fish (5× value), and 1 in 4,000 a Phoenix key fragment (Phase 10). `fishing.yml → loot`.
- **Pace:** base wait 10–25 s, lowered by rod tier, Quick Bite, Netcaster/Stormkeel sets and Angler (speed family cap). Reference: ~120 catches per active hour.
- **Money:** a catch is worth `0.6 × (mine money per active hour at that tier) ÷ 120`, so fishing pays **about 60% of The Depths** per active hour. It is the relaxed option, with crystals, books (1 in 60–90) and Tide Chests (E6) on top.
- **XP:** Fishing skill XP per catch by fish tier; feeds the Farming pillar. Set XP 25 per catch (WARDROBE, built hook). Angler mastery (MA10) +5% speed, +4% loot.
- **Anti-AFK:** after **40 catches in the same 3-block spot** without moving 8+ blocks, catches give junk only and no XP until you move. Vanilla auto-fishing tricks only ever get vanilla loot.
- `skills.yml → available` adds `fishing`; `wardrobe.yml → features.available` adds `fishing` (Netcaster, Stormkeel buyable); fishing achievements and fishing quest types (Phase 10) switch on.
- **Event for E6:** `FishCatchEvent` (player, rod, tier band, loot roll with modifiers: weights, extra catch, bonus chest).

**Data:** `fishing_stats` (profile_key, catches, best_fish_tier, rare_catches, updated_at). The same-spot counter lives in memory.

## M7.9 Outposts
Small extra islands for farms (DESIGN §3.7). **Max 3 per island.** They live in the `outposts` world on the same grid as islands (region-aligned slots), **5×5 chunks**, upgradable to 7×7 and 9×9 (WORLD §3).

| Outpost | Unlock (all shown pillars ≥, plus) | Theme | Biomes |
|---|---|---|---|
| 1: Greenhold | 10 | Farming | Plains, Jungle, Desert |
| 2: Wildwood | 25 + Combat pillar 30 (stands in for "first world boss" until Phase 8) | Spawners | Dark Forest, Swamp, Snowy Taiga |
| 3: Emberdeep | 40 + Depths combat tier 10 (any member) | Late game | Nether Wastes, Crimson Forest, Soul Sand Valley |

- **Unlock** = buy the next **Outpost Slots** level (island upgrade menu, island bank) once its condition is met. Prices from `ECONOMY_BALANCE.md` §5.3: $1.5M / $7.3M / $60M.
- On unlock the outpost is pasted from its own builder schematic (FAWE, async) into a free slot. Builders get a brief per outpost (size, spawn pad, fishing water, farm space).
- **Same island:** roles, permissions, bank, fuel tank, settings, members and coop rules are shared with the main island. Block worth, pillars and stats count for the island.
- **Getting there:** the island menu's **Outposts** button and the Outposts portal at spawn (closed today) open a menu of your outposts (teleport, size, biome). Instant teleport (3 s only in combat).
- Outposts load only while a player is there. Visiting other islands' outposts follows the island's lock and ban.

**Data:** `island_outposts` (island_id, outpost_id, grid_x, grid_z, size_chunks, unlocked_at, unlocked_by), wiped with the island at season reset.

## M7.10 Biomes and fusion
- Each outpost has **its own 3 biomes** (a biome belongs to one outpost). The leader picks the **active biome** in the Biomes menu; the outpost's chunks are set to it (`setBiome` + chunk refresh, Bedrock fine).
- A biome **unlocks** at its outpost's gate on the related pillar (+0 / +5 / +10 levels): Greenhold on Farming, Wildwood on Combat, Emberdeep on Combat (Nether Wastes, Soul Sand Valley) and Farming (Crimson Forest).
- **Biome tiers I–V**, each **+5% per tier** (+25% at V), bought step by step. **Fusion I** merges 2 biomes, **Fusion II** all 3, each merged effect at **75%**. 18 steps per outpost, priced `1.22^j × REF(gate)` (§5.3).

| Biome | Effect (on that outpost only) | Bonus family |
|---|---|---|
| Plains | Crop growth | crop growth (cap +100%) |
| Jungle | Double-crop chance | extra drops |
| Desert | Crop sell value | sell (+300% cap) |
| Dark Forest | Spawner speed | spawner rate |
| Swamp | Mob drops | extra drops |
| Snowy Taiga | Vanilla XP from mobs | — |
| Nether Wastes | Spawner stack limit (+X% on the outpost, never above 512) | — |
| Crimson Forest | Rare drops (totems, shells, Golden Seeds) | rare finds |
| Soul Sand Valley | Boss key chance ("Coming soon" until Phase 8) | — |

- **Main island biome changer** (FI 15): cosmetic only, free, any biome from a list (`outposts.yml → cosmetic-biomes`), in the same Biomes menu. It needs the role key `biomes` (DESIGN §3.4).
- Effects feed existing bonus families wherever one exists, so every cap still holds.

**Data:** `outpost_biomes` (island_id, outpost_id, biome_id, biome_tier), `island_outposts.active_biome`, `island_outposts.fusion_step`.

## M7.11 Balance pass (BAL7)
Before any Phase 7 price goes live, `tools/economy_model.py` and `ECONOMY_BALANCE.md` get:
- Generator output as **active** play (it leaves the passive production level); the passive share moves to crops and spawners.
- Collector offline share by tier, the starter collector, crop growth cycles, fuel budget per archetype (table below), fishing at 60% parity, outpost and biome effects, Mob Loot and Harvest Yield.
- Checks to keep: season sink ratio ≥ 80%; island share **~35%** for the regular and **≤ 45%** for every archetype; +300% sell cap; AFK money ≤ 5% (§7.5).
- **New production rules** (the "never beats The Depths" checks):
  1. An island's whole output per production hour (crops + spawners + collectors) stays **below 40% of the same player's Depths income per active hour**, at every stage. Today's model: ~9% (regular, week 1) to ~30% (grinder, month 3).
  2. Mining your own generator pays **below 25%** of an active Depths hour at the same stage.
  3. Fishing pays **about 60%**; active harvesting is bounded by growth, so it can't run away.

## M7.12 Staff tools and dashboard
- `/isadmin fuel <player> add|set <minutes>` (needs approval, see questions): admins fix fuel after bugs. Audited with a reason.
- `/collector give <player> <kind> <tier>` (needs approval): like `/spawner give`, admin + owner, audited, overflow to `/box`.
- `/isadmin production <player>` view (mods): state, fuel, collectors, last offline report, output per hour, activity log (helps judge rule 9 reports).
- M3.8 dashboard rows: `collector_sell`, fuel added per source per day, offline output per day, island share per tier band, with an alert when island share passes 45%.
- Disband and reset backups include collectors (with contents), fuel and outposts (principle 3).

---

## Balance vs `ECONOMY_BALANCE.md`
- **Income meta:** The Depths ~50%, island ~35% (production 16% + spawners 18% for the regular, §2.3), events ~15%. Phase 7 must keep this.
- **Island items sell for half** the Depths item of the same tier, generator ores one tier lower (§4.1). Production upgrades follow `C_p` (§4.2). Late projects (outposts, biomes, fusion) follow §5.3.
- **Fuel budget per archetype** (per day; the model's production hours are an upper limit):

| Archetype | Active on island | Fuel earned there | AFK pool | Cells (crates, calendar, kits, pass) | Production h/day | Model (§1.1) |
|---|---|---|---|---|---|---|
| Casual (1 h) | ~0.3 h | ~0.3 h | ~4 h | ~1 h | ~5.6 h | 8 h |
| Regular (3 h) | ~0.9 h | ~0.9 h | ~6 h | ~1.5 h | ~9.3 h | 12 h |
| Grinder (8 h) | ~2.4 h | ~2.4 h | 8 h | ~2 h | ~14.8 h | 20 h |

Real production lands **below** the model, and collectors catch only 60–100% of offline output, so R5 has margin. BAL7 re-runs with these numbers.
- **Levers if the dashboard disagrees:** collector offline share, tank size, burn rate, `C_p` costs, crop growth base, auto-sell intervals, Fuel Cell chances in crates (§7.4).

## Anti-AFK-abuse rules (R5, rule 9)
1. Fuel is earned only by **active** members on their own island, **one minute per minute per island**, never per member.
2. AFK on the island **burns** fuel. The only AFK reward is the AFK pool (8 h per account and IP per day, built).
3. Offline output needs fuel and (for items) a collector, and a collector catches only its tier's share.
4. Offline and grinder output give **no** skill, tool or set XP and **no** books. Combat pillar only from player kills (PG7); offline crops give 25% Farming pillar XP.
5. Bone-mealed crops give no XP or books. Placed-then-broken blocks give no stats (built).
6. Fishing same-spot decay; custom loot only with a custom rod.
7. Fuel Cells are bound; nobody can buy production hours from other players.
8. Coop players and visitors add nothing (fuel, XP, tasks).
9. Lag machines are detected and disabled (WR2); generators cap custom rolls per second.
10. Staff see activity and output in `/isadmin production` for rule 9 reports.

## Limits and performance (300 players)
Target from `CONTEXT.md` §4: **20 TPS, MSPT < 40.** Budget for this phase: **≤ 5 ms per second** of main-thread time on average for production + fishing + outposts together (same warning as stacking, `perf.warn-ms-per-second`).
- **No chunk loading for production, ever.** Offline islands are snapshots plus math (M7.6). Outposts load only with a player there; teleports preload chunks async.
- **No per-tick scans.** Collectors catch drops by event with an O(1) chunk-key lookup. Generators work per form event. Crops grow in a budgeted task over the crop index (`crops.per-tick`), loaded chunks only. Auto-sell chests sit in a priority queue by next sell time. Spawners already use a queue (`spawners.due-per-tick`).
- **Simulation off the main thread:** pure math with a unit-tested benchmark (300 islands × 25 chunks in < 200 ms on one core). Results are applied in batches of `simulation.apply-per-tick`.
- **Database:** write-behind every 30 s and on unload; contents as counts per item key; one transaction per sell batch (ledger + bank + contents).
- **Entities:** spawner mobs and items stay stacked (STACK). Collectors remove item entities as they spawn, so a farm with a collector has almost no item entities.
- **Expected load:** ~150 islands loaded at peak (300 players, teams). Work grows with **loaded** islands only; 1,000+ offline islands cost nothing until they load.
- Phase 13 bot load test covers a "every island has 4 spawner blocks, 2 collectors and a 9×9 farm" scenario.

---

## UI mockups (V2 lore, vanilla items now, resource pack later)

### `/is production` (6 rows)
```
Row 1  red frame · [Fuel gauge: blaze powder] [Collectors: barrel] [Generator: cobblestone] [Crops: wheat] [Spawners: spawner] [Fishing: rod] · red frame
Row 2-5  page of the selected tab (collectors list with chunk + fill %, generator table, crop tiers with ✔/✘, spawner stacks)
Row 6  standard navigation · [Last offline report: clock] · [Outposts: map] · [Help]
```

### Fuel gauge lore
```
Island Fuel                          ← bold, gold
Production · Tank level 4

Keeps your island working when
no member is active.

| Status
| State: Fueled
| Fuel: 7h 20m / 20h
| Burn: 0.9x (Fuel Efficiency II)

| How to get fuel
| Play on your island: +1 min/min
| AFK pool, Fuel Cells

» CLICK see the last offline report
```

### Chunk Collector item lore
```
Chunk Collector                      ← bold, rare blue
Collector · Tier 3

Picks up every drop in its chunk.

| Stats
| Storage: 80,000 items
| Offline share: 80%

| Information
| Merge two Tier 3 into Tier 4

» PLACE on your island

RARE COLLECTOR
```

### Collector menu (5 rows)
```
Row 1  red frame · [Info: tier, fill 73%, chunk 12,-4, offline share] · red frame
Row 2-4  items: bone ×12,400 ($3.1k) · rotten flesh ×8,020 · arrow ×2,300 …  (click = take a stack, shift = all, overflow → /box)
Row 5  [Sell all: gold ingot] [Linked Auto-sell Chest: chest] [Merge help] [Back] [Help]
```

### Outposts menu
```
Row 2  [Greenhold: hay block ✔ Click to teleport] [Wildwood: dark oak sapling ✘ All pillars 25 · 18/25] [Emberdeep: netherrack ✘ Locked]
Row 4  [Biomes] [Outpost size] [Upgrades]
```

## Data (tables)
`island_fuel`, `production_collectors`, `production_collector_items`, `production_snapshots`, `fishing_stats`, `island_outposts`, `outpost_biomes`. All rows carry `season_id`. Column names avoid MariaDB reserved words (`collector_tier`, `biome_tier`, `grid_x`, `snapshot_kind`; never `key`, `range`, `order`, `rows`, `lines`). Crop positions stay in chunk data (built).

## Config files
`production.yml` (fuel, activity, simulation, perf), `generators.yml` (levels list), `crops.yml` (tiers, XP, growth), `collectors.yml` (tiers list), `fishing.yml` (tiers, fish, loot, anti-AFK), `outposts.yml` (outposts, biomes, cosmetic biomes). Numbers in typed configs; catalogs as lists with **no plain `@Setting` keys inside a list section**. Texts in `messages/production.yml`, `messages/fishing.yml`, `messages/outposts.yml`.

## Permissions
| Node | Default | Use |
|---|---|---|
| `skyblock.production.menu` | everyone | `/is production` (if approved) and the menu button |
| `skyblock.production.admin.view` | mod | `/isadmin production` |
| `skyblock.production.admin.fuel` | admin | `/isadmin fuel` |
| `skyblock.production.admin.give` | admin | `/collector give` |
| `skyblock.fishing.use` | everyone | custom fishing loot |
| `skyblock.outposts.use` | everyone | outposts menu and portal |

Island role keys (matrix): `collectors`, `outposts`, `biomes`. Grants in `server/permissions/production.txt`, `fishing.txt`, `outposts.txt`; command docs in `docs/commands/<module>.md`.

---

## Milestone order and dependencies

| # | Milestone | Depends on | Lane |
|---|---|---|---|
| M7.1 | Island Fuel core, Fuel Cells, AFK grant delivery | M2.x, AFK (built) | new modules/production, common/production, islands (delegate), afk (detection) |
| M7.2 | Custom generators + island gather hook | M7.1 | production (generator pkg) |
| M7.3 | Farming: crop tiers, growth engine, Farming skill, anti-farm | M7.1, STACK | production (crops pkg), skills, stacking (hand over crop growth), shop |
| M7.4 | Harvester hoe + rod tiers | M4.2 | modules/tools (can run in parallel with M7.1–M7.3) |
| M7.5 | Chunk Collectors + Auto-sell Chests, merge, starter collector | M7.1, M3.4 | production (collectors pkg), tutorial (finish reward hook) |
| M7.6 | Offline simulation + return report | M7.3, M7.5 | production, stacking (snapshot API) |
| M7.7 | Spawner additions (Mob Loot, sources, menu) | M7.5, M3.5 | stacking, shop, depths |
| M7.8 | Fishing | M7.4 (rods) | new modules/fishing, skills, wardrobe config (parallel lane to production) |
| M7.9 | Outposts | M7.1, M5.1, M5.4 | new modules/outposts, worlds, islands |
| M7.10 | Biomes + fusion | M7.9 | outposts |
| M7.11 | Balance pass BAL7 | M7.2, M7.3, M7.5, M7.8 configs | docs, tools |
| M7.12 | Staff tools + dashboard rows | M7.5, M7.6 | production, economy (admin pkg) |
| **E6** | Hoe + rod enchants, Farming and Fishing book pools live (ENCHANTS §6.4, §6.7) | E1 (built), **M7.3, M7.4, M7.8** | modules/enchants |

- **Unblocked by this phase:** E6 (QUEUE row, ⏸ today), Farming skill (M7.3), Fishing skill (M7.8), Farming/Fishing achievements, Cropwarden (M7.3), Netcaster and Stormkeel (M7.8), fishing quests (Phase 10), Phase 5's "Coming soon" upgrades (Generator, Fuel Tank) and masteries (Fuel Efficiency, Angler).
- **Parallel lanes:** production (M7.1 → M7.2 → M7.3 → M7.5 → M7.6), tools (M7.4), fishing (M7.8 after M7.4), outposts (M7.9 after Phase 5).

## Release / test checklists (owner)
| Milestone | You check (Java + Bedrock) |
|---|---|
| M7.1 | Play on your island → scoreboard says `Fuel Active` and fuel rises; go to The Depths → it says `Fueled` and drops; stand still 15 min on the island → fuel drops; a Fuel Cell adds fuel, a full tank refuses it; AFK pool fuel arrives |
| M7.2 | Build a cobble generator → coal appears at Generator level 1 (staff sets the level); ores drop island items; `/is stats` counts output |
| M7.3 | Plant carrots below Farming 5 → refused with progress; harvest ripe wheat with a hoe → Farming XP; with bone meal → no XP; crops grow in the dark |
| M7.4 | Forge a copper hoe from wheat + money; right-click ripe wheat → 3×3 harvested and replanted; Lv 15 → 5×5; rod tiers in the Forge |
| M7.5 | Place a collector → mob and crop drops in that chunk go into it; Sell all pays you; an Auto-sell Chest in the same chunk pays the island bank; merge two Tier 1 → Tier 2 keeps contents; full collector warns |
| M7.6 | Leave with fuel for 1 h (staff can fast-forward the snapshot time) → come back to the report line; collector filled at its share; without a collector the spawner stacks grew and crops are ripe |
| M7.7 | Spawner menu Upgrades button; Mob Loot raises drops; a tier 2 spawner can drop in Depths combat |
| M7.8 | Fish with the starter rod → custom fish and Fishing XP; vanilla rod → vanilla loot; 40 catches in one spot → junk until you move; Netcaster set buyable |
| M7.9 | Reach all pillars 10 → buy Outpost Slots I → Greenhold appears; teleport from the island menu and the spawn portal; a member builds there with the island's roles |
| M7.10 | Pick Plains → crops grow faster on the outpost only; upgrade to tier II; Fusion I merges two biomes at 75%; main island cosmetic biome |
| M7.12 | Staff fuel fix and collector give appear in `/logs`; production view |

## Decided
- Generators, custom crops + harvester hoe, stackable spawners, collectors and auto-sell chests tier 1–5 with merge, crate/store/monthly crate only (DESIGN §5.3).
- Island Fuel: 1 min on the island = 1 min fuel, 12 h start, Fuel Tank upgrade (DESIGN §5.4). Offline production simulated (B4).
- No block, hopper or piston limits; lag-machine detection (O1, O2, WR2). No collector overflow into storage (I8).
- Generator = vanilla ores + 2 custom (V4). Collectors at the dynamic price, fuel offline (V5). Harvester hoe 3×3 / 5×5 / 7×7 with auto-replant from the Forge (V6).
- Outposts Greenhold / Wildwood / Emberdeep, 3 biomes each, tiers I–V, Fusion I/II at 75% (DESIGN §3.7–3.8). Islands and outposts wiped at season reset (414).
- Fuel Efficiency, Beast Tamer, Angler masteries (MA8–MA10).

## QUESTIONS
Only decisions not answered yet. "s" is what this plan does if the owner doesn't answer. `p: 1` = important for launch.

```json
[
  {"q": "PD1: The island earns 1 fuel minute per minute while at least one member is active on it (not 1 per member)?", "s": "Yes", "why": "Otherwise a team of 5 makes 5x the fuel and AFK production runs away (risk R5).", "p": 1},
  {"q": "PD2: Only active time on your own island earns fuel (an action in the last 15 min); standing AFK on the island burns fuel, and The Depths earns none?", "s": "Yes", "why": "The AFK pool is the only AFK reward (your rule 9); this answers the old M3.0 question too.", "p": 1},
  {"q": "PD3: Fuel tank 12 h at start, +2 h per Fuel Tank level, 36 h at level 12?", "s": "Yes", "why": "Covers a weekend away late in the season without making offline the main income.", "p": 1},
  {"q": "PD4: Fuel Cells (1 h / 3 h / 6 h) from crates, calendar, battle pass, rank kits and the island shop are bound to the profile and can't be traded?", "s": "Yes", "why": "If fuel could be bought from other players, money would buy production hours.", "p": 1},
  {"q": "PD5: One fuel tank runs the main island and all its outposts (outposts burn no extra fuel)?", "s": "Yes", "why": "Simple to understand; outposts are already priced as late projects.", "p": 0},
  {"q": "PD6: Generator table: 11 levels from cobblestone to emerald plus two custom ores (Ashstone $180, Emberite $396), newest ore 5% per level?", "s": "Yes", "why": "Your answer V4 (vanilla ores + 2 custom), prices from the balance sheet.", "p": 1},
  {"q": "PD7: Generator ores must be mined by hand (no drills or auto-miners in Phase 7)?", "s": "Yes", "why": "Keeps generators active play; offline income comes from crops and spawners.", "p": 1},
  {"q": "PD8: Generator ores give Mining pillar XP at 25% of the Depths ore, but no Mining skill XP?", "s": "Yes", "why": "Skills stay Depths-driven (M4.5), but the island still sees its generator.", "p": 0},
  {"q": "PD9: Area enchants (Vein Rend and friends) don't break real island blocks; island generator ores still roll books at 25%?", "s": "Yes", "why": "Area breaking of real blocks is a grief and lag risk on islands.", "p": 0},
  {"q": "PD10: Planting unlocks by Farming level as in WORLD 5.1 (wheat 0, carrot/potato 5 ... pitcher plant 58), seeds in /shop locked the same way?", "s": "Yes", "why": "Same ladder as the Depths tiers, so farming has its own progression.", "p": 1},
  {"q": "PD11: Farming XP only for ripe crops broken with a hoe; bone-mealed crops give drops but no XP, books or set XP?", "s": "Yes", "why": "Stops bone-meal XP farming; the wardrobe already uses the hoe rule.", "p": 1},
  {"q": "PD12: Fully custom crops with their own models wait for the resource pack; Phase 7 has the 11 vanilla crop tiers plus Golden crops?", "s": "Yes", "why": "Custom models need the pack; the rules are custom already.", "p": 0},
  {"q": "PD13: Harvester hoe area by tool level: 3x3 from level 1, 5x5 at level 15, 7x7 at level 30 (Wide Reap up to 9x9)?", "s": "Yes", "why": "Matches your V6 answer with the tool level steps players already know.", "p": 0},
  {"q": "PD14: 11 hoe tiers (one per crop tier) and 12 rod tiers (one per fishing tier), forged with money plus crops/fish of the tier before?", "s": "Yes", "why": "Same Forge rules as pickaxes and axes.", "p": 0},
  {"q": "PD15: Chunk Collector storage 5k / 20k / 80k / 320k / 1.28M items by tier, and it catches 60 / 70 / 80 / 90 / 100% of offline output?", "s": "Yes", "why": "Merging gives more storage and faster pickup (DESIGN); the offline share also guards against AFK income.", "p": 1},
  {"q": "PD16: Auto-sell Chest sells every 10 / 5 / 2 / 1 / 0.5 min by tier at the dynamic price, and also sells from a Chunk Collector in the same chunk?", "s": "Yes", "why": "Lets a farm sell itself without hoppers (you said no filter hoppers).", "p": 1},
  {"q": "PD17: Auto-sell money goes to the island bank with island sell bonuses only (no personal rank or booster bonus)?", "s": "Yes", "why": "Production belongs to the island; personal bonuses need a player selling.", "p": 1},
  {"q": "PD18: One Chunk Collector and one Auto-sell Chest per chunk?", "s": "Yes", "why": "A collector covers its whole chunk; two would fight over the same drops.", "p": 0},
  {"q": "PD19: Merge by right-clicking a placed collector with another of the same kind and tier, then confirm; contents are kept?", "s": "Yes", "why": "One click, works on Bedrock, nothing is lost.", "p": 0},
  {"q": "PD20: Every new island gets one bound tier 1 Chunk Collector at the tutorial finish (can't be traded or merged)?", "s": "Yes", "why": "Without it, a casual player's offline income depends on crate luck.", "p": 1},
  {"q": "PD21: A full collector stops picking up; items stay on the ground as stacks (and despawn) and members get one warning per hour?", "s": "Yes", "why": "You said no collector overflow into storage; the warning tells players to sell.", "p": 0},
  {"q": "PD22: Offline without a collector: crops ripen and spawner mobs pile up in their stacks (max 8 stacks x 1,024 per type per chunk) for you to kill when you return?", "s": "Yes", "why": "Coming back has value and rewards playing; collectors turn it into items.", "p": 1},
  {"q": "PD23: Offline output gives no skill, tool or set XP and no books; offline crops give 25% Farming pillar XP, offline spawners no Combat pillar XP?", "s": "Yes", "why": "XP and books must come from playing, not from fuel.", "p": 1},
  {"q": "PD24: Basic fishing (skill, rods, loot, custom fish, rod enchants, fishing sets) comes in Phase 7; sea creatures, lava fishing and contests stay in Phase 8?", "s": "Yes", "why": "The Fishing skill, E6 and the fishing sets all wait for it.", "p": 1},
  {"q": "PD25: Fishing pays about 60% of The Depths per active hour (plus crystals, books and Tide Chests)?", "s": "Yes", "why": "A relaxed option that never replaces The Depths as main income.", "p": 1},
  {"q": "PD26: Fishing loot: 70% fish, 15% junk, 10% treasure, 5% rare fish (5x value), and 1 in 4,000 a Phoenix key fragment?", "s": "Yes", "why": "Keeps catches exciting; the key fragment chance is tiny.", "p": 0},
  {"q": "PD27: Fishing anti-AFK: after 40 catches in the same spot without moving 8 blocks, catches give only junk and no XP; custom loot only with a custom rod?", "s": "Yes", "why": "Stops AFK fishing machines without bothering real anglers.", "p": 1},
  {"q": "PD28: Fishing works on islands, outposts and marked spawn ponds, not in The Depths or the warzone?", "s": "Yes", "why": "Fishing belongs to the island side of the game.", "p": 0},
  {"q": "PD29: Outposts unlock with the Outpost Slots upgrade once all pillars reach 10 / 25 / 40; Wildwood uses Combat pillar 30 instead of 'first world boss' until bosses exist, Emberdeep needs a member at Depths combat tier 10?", "s": "Yes", "why": "Bosses come in Phase 8; outposts should not wait for them.", "p": 1},
  {"q": "PD30: Outposts share the island's roles, bank, fuel and settings, and are reached from the island menu and the Outposts portal at spawn (no new command)?", "s": "Yes", "why": "One island with extra land; menus work on Bedrock.", "p": 1},
  {"q": "PD31: Biomes: one active biome per outpost, tiers I-V give +5% each (+25% at V), Fusion I/II merge at 75% each, every effect inside the existing bonus caps?", "s": "Yes", "why": "Felt but small; caps keep the +300% sell rule and crop growth cap safe.", "p": 1},
  {"q": "PD32: Soul Sand Valley (boss key chance) shows 'Coming soon' until bosses exist?", "s": "Yes", "why": "Its effect has nothing to work on before Phase 8.", "p": 0},
  {"q": "PD33: The main island biome changer is cosmetic only and free (any biome from a list) in the Biomes menu?", "s": "Yes", "why": "Your FI 15 yes; effects belong to outposts (DESIGN 3.8).", "p": 0},
  {"q": "PD34: New sub-command /is production (fuel, collectors, generator, crops, spawners, last offline report)?", "s": "Yes", "why": "Players check fuel often; also a button in the island menu.", "p": 1},
  {"q": "PD35: Staff commands /isadmin fuel <player> add|set <minutes>, /isadmin production <player> and /collector give <player> <kind> <tier>, all audited?", "s": "Yes", "why": "Staff need to fix fuel after bugs and judge AFK reports; give works like /spawner give.", "p": 0},
  {"q": "PD36: Balance rule: an island's whole output per production hour stays below 40% of the same player's Depths income per active hour, generator mining below 25%?", "s": "Yes", "why": "Turns 'production never beats The Depths' into a number the model checks.", "p": 1},
  {"q": "PD37: New island role keys collectors (break, merge, take out, sell; Leader, Admin, Mod), outposts and biomes (Leader, Admin)?", "s": "Yes", "why": "Collectors hold the island's money; leaders decide who touches them.", "p": 0}
]
```

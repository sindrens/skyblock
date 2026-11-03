# Tools and Areas redesign: one toolset, three grind areas, a new start (TOOLS-AREAS)

**Status:** plan, written 3 Nov 2026 on branch `q/release-v4`. Read-only analysis; nothing built.
**Owner direction (3 Nov 2026):** 8 tools (4 island + 4 Depths) and a second inventory feel messy. Replace them: everyone starts with no tools
and a bossbar to the NPC **Jaden**; the Depths name goes away; areas are **Mine**, **Dungeon**, **Farm**; one tool per area (pickaxe, sword, hoe)
from an NPC in that area; tools level 1-50 then prestige; the Farm shows each player their own crop.
**Owner additions (3 Nov 18:48):** `/shop` becomes one hub; island seeds cost a lot (buy one, regrow it); island crops pay less, the Farm pays
best, island progress still needs island breaks; the hoe's Auto-Sell unlocks after about 2 minutes, with well formatted chat messages.

Every number, name and rule below is a **default the owner can change** and lives in config. Open points are in section 13 (**Questions for the owner**).
House rules (typed config, messages files, permissions files, tab completion, audit, dupe safety, tests, `/help`, Bedrock) are in
`docs/planning/FEEDBACK_3NOV.md` "House rules" and `docs/STANDARDS.md`; they apply to every phase and are not repeated.

Research note: the comparison with other servers (section 6.1) is from my knowledge of Hypixel SkyBlock farming and mining gear and of
prison/factions harvester hoes and prison pickaxes. I did not browse the web for this plan, so treat the numbers of other servers as
"shape", not as facts. Ask me to run a web pass if you want sources.

---

## 0. Summary

1. **What we have today is three overlapping tool systems:** Depths tools (`modules/tools`: pickaxe, axe, sword, hoe, rod, Forge, perk slots, prestige),
   island tools (`modules/islandtools`: wooden to netherite, shard upgrades, shared per island, hoe storage), and the harvester hoe inside `modules/tools`
   (`Harvester`). Plus a second inventory (`WorldTools`, tables `world_inventory` and `tool_stash`).
2. **New model:** one tool per area, named by what it does. `ToolKind` is reduced to `PICKAXE` (Mine), `SWORD` (Dungeon), `HOE` (Farm and island);
   `AXE` is retired (forest removed); `ROD` stays dormant for the later fishing area. One inventory. One level curve. One NPC menu per area:
   **Sell loot / Upgrade tool (tier, perks, prestige)**.
3. **The Farm needs no new engine.** The Depths per-player blocks engine already has everything: regions, per-player depletion, a timing wheel, chunk
   re-send (`PlayerChunkLoadEvent`), Bedrock throttling, and the DV2-SPIKE **tier palette** (every player sees the block of their own tier at the same
   spots). The Farm is a palette where the "tier" comes from a harvest counter instead of a skill level. Server block = real `wheat` age 7; players on
   carrot/potato/... get a packet override. Respawn 2 s is a config value. Section 8.
4. **Start flow:** no tutorial module. New small module `startflow` (starter chest, bossbar to Jaden, Jaden's menu, area intros, first-day funnel stats).
   `tutorial` is switched off (code kept one release for rollback). Section 4.
5. **Migration is the dangerous part and ships first**, behind a flag, with a crash-safe merge of the two inventories and a refund for island tool
   purchases. Section 3.
6. **Economy:** the Farm pays about 15% more per hour than the same-tier mine; island crops pay about 25% of the Farm item; island seeds cost
   $1,500 to $6,000 in `/shop`; `/shop` becomes the hub for buying, selling and area loot. Section 10.
7. **Phases:** P0 spikes, P1 names, P2 migration, P3 start flow, P4 tool system, P5 Farm, P6 island hoe, P7 `/shop` hub, P8 balance, P9 cleanup.
   About 6 to 8 weeks of agent time, three lanes. Section 11.

---

## 1. What exists today (findings from the code)

| Area | Where | Facts that matter for this plan |
|---|---|---|
| Depths tools | `modules/tools`: `ToolsModule`, `ToolItems`, `ToolCatalog`, `tools-defaults.yml`, `Forge`, `ForgeMenu`, `ToolMenu`, `ToolProgress`, `ToolCurve` | 5 kinds (`ToolKind`: PICKAXE, AXE, SWORD, HOE, ROD). Tiers per kind from `tools-defaults.yml` (pickaxe 12, axe 11, sword 10, hoe 11, rod 12). Tool = item with PDC data + row in `tools`. Curve `400 x 1.21^(L-1)`, max 50 (21.7M XP total). Perk slots at Lv 5/15/30/45, prestige once per tool per season (+slot 5). Kept on death. Forge = money + previous-tier drops. Blueprints, skins, 24 h starter kit. |
| Second inventory | `WorldTools` (+ `ToolTracker`), tables `world_inventory`, `tool_stash`, player PDC `inventory_side`, `inventory_pending(_token)` | Crossing into the `depths` world swaps the whole inventory (storage, armor, offhand). Live side lives in the player file, the other side in `world_inventory`; the live side's row is stale by design. Depths side starts with the kit `LEATHER 1 satchel` + starter tools. |
| Island tools | `modules/islandtools`: `IslandToolsModule`, `ToolListener`, `ToolStore`, `Upgrades`, `HoeFarming`, `HoeStorage`, `IslandGrowth`, tables `island_tools`, `island_tool_levels`, `island_tool_tasks`, `island_tool_storage` | Wood to netherite, tier paid with shards + money, upgrades paid with shards + an island task, shared per island. Hoe: row harvest into a storage, regrow clock (60 s to 15 s), Growth Burst, ripe cue. `item_id` in `island_tools` = the only valid copy of the item. Shard Finder gives shards (shards also come from shard blocks, 100/day). |
| Harvester hoe | `modules/tools/Harvester`, `HarvesterHook`, `HarvesterSwingEvent` | Right-click area harvest 3x3 to 9x9, replant, Farming XP via `IslandCropHarvestEvent`. Overlaps with islandtools `HoeFarming`. |
| Depths | `modules/depths`: engine, zones, tiers, drops, satchel, combat | Aspects `MINE`, `FOREST`, `COMBAT` (`zones.Aspect`). Mine 12 tiers, forest 11, combat 10. Hub "The Gate" with wing gates. `/dungeon` (tier menu), `/satchel`, `/depths` (staff). Drops -> satchel (`depths_satchel_items`), crystals batched (`CrystalBatcher`). |
| Per-player blocks | `depths/engine/DepthsEngine`, `PlayerDepletion`, `TimingWheel`, `DepletionMemory`, `SlotIndex`, `TierPalette`, `PaletteMath`, `TierPalettes` | Break handled on `BlockBreakEvent` (cancelled, real block never changes), `ResourceBreakEvent` fired, only the breaker gets the depleted block, timing wheel brings it back, depleted blocks re-applied on `PlayerChunkLoadEvent`, running timers survive relog. Palette: players see tier N block at slot positions, tier 1 = real block, Bedrock swap throttle (`palette.bedrock-blocks-per-tick` 300). Spike, off by default. |
| Tutorial | `modules/tutorial` (~12k lines): `TutorialEngine`, `Cameras`, `Pip`, `Guides`, `OptionalSteps`, `StarterKitContents`, `TutorialGate` service in `common` | 11 steps + 9 optional steps. Publishes `TutorialGate` (feature locks used by islands, economy, auction, trade, quests, worlds portals, depths `/dungeon`), `TutorialService`, `StarterContents` (islands call it to fill the starter chest). Kindle is the island guide from `modules/guide` (not the tutorial module); Pip is the tutorial's parrot. |
| NPCs | `modules/npcs` (`RoleRules`, `NpcsConfig`, `NpcDirectory` service) | Roles and click actions are config lines: `npcs.roles` (`depths_forge: forge`), `npcs.actions` (`forge: command forge`). NPC positions come from build yml `npcs:` entries. Depths gate has `depths_forge`, `depths_sell`, `depths_guide`; mine t01 has `mine_foreman`. |
| Builds | `server/builds/*.yml/.schem`, `depths_layout.yml`, `/skyblock builds apply` -> `zones.yml` merge | Built: spawn base and districts, island starter meadow, gate, mine t01-t03, forest t01-t02, combat t01-t02. The east wing of the Gate (`forest_t01` at x 80..159) is free to become the Farm. No Farm build exists yet. |
| Shop and sell | `modules/shop` (category menu, item grid, buy GUI, sell GUI, sell chest), `modules/economy/sell` (`SellService`, `sell.yml`, dynamic prices) | `/shop` = category menu (max 9 tabs incl. Farming, Mob Drops, Spawners) with a Sell chest button. Rule: shop price must exceed the max possible sell value (base x 7.5). Seeds today: wheat_seeds $10, carrot $120. |
| Currencies | `common/economy/Currency` | MONEY, CRYSTALS (not transferable), CREDITS, CREDITS_EARNED, SHARDS. Crystals come from mine/forest/combat drops via `CrystalBatcher`. |

---

## 2. Target model (one page)

```
 ISLAND  (starter chest: Pickaxe + Hoe)         SPAWN                   AREAS (own entrance NPC each)
 own farm, generator, trees                     Jaden (guide)           Mine    -> Pickaxe NPC  (sell Mine loot, upgrade Pickaxe)
 hoe + pickaxe work here, lower rewards         /shop hub               Dungeon -> Sword NPC    (sell Dungeon loot, upgrade Sword)
 island upgrades need island breaks             sell NPC, bank, AH...   Farm    -> Hoe NPC      (sell Farm loot, upgrade Hoe)
```

| Thing | Rule |
|---|---|
| Tools | `PICKAXE`, `SWORD`, `HOE`. One of each per profile. Level 1-50, then prestige. Server-side truth in the `tools` table; the item is a "valid copy" (a new copy invalidates older ones, like island tools did). |
| Inventory | One. No world swap. Staff keep `skyblock.tools.world-bypass` removed (nothing to bypass). |
| Areas | Mine (tiers 1-12), Dungeon (tiers 1-10), Farm (1 field set, 5 crops by progress). Same world `depths` internally; **internal ids stay** (`depths` module, world name, permission nodes, table names, config file names). Only text players see changes. |
| Currency | Money (sell everything). One **upgrade currency per area**, earned by using that area's tool: Mine = Crystals, Dungeon = Souls, Farm = Bushels (names are defaults, Q3). |
| NPC per area | Menu: **Sell loot** (area loot only, one click), **Upgrade tool** (tier-up, perk tree, prestige), **Replace lost tool**, **Go to tier / crop**. |
| Start | Island first. No tools except the starter chest. Bossbar -> Jaden at spawn -> menu that introduces Mine / Dungeon / Farm. |

---

## 3. Retire, migrate, rename (item 1)

### 3.1 What is retired

| Retired | How | When |
|---|---|---|
| `WorldTools` swap, `world_inventory`, `tool_stash`, `world-tools.*` and `world-inventory.*` settings | Switched off by a flag first (P2), tables kept 30 days, then dropped (P9). | P2 / P9 |
| Island tools (`islandtools`: wooden..netherite tiers, shard upgrades, shared levels, tasks-per-tool, Shard Finder, hoe storage) | Items removed on the player's next join, purchases refunded (3.4), tables archived. The **crop growth clock** (`IslandGrowth`), always-wet farmland, placed-block memory and the island regrow/Growth Burst stay (moved, 3.6). | P2 / P6 / P9 |
| Axe tools and the forest (aspect `FOREST`, 11 zones, forest gate, `ForestGuard`, `TreeTemplate(s)`, forest drops/tables, axe tiers, Foraging-only tier palette) | Wing gate closed at once (P1), payout of forest loot and axe money (3.5), code removed in P9. Forest builds `depths_forest_t01/t02` are archived, not pasted. | P1 / P2 / P9 |
| Forge as a spawn NPC (`/forge`, `depths_forge`, `blacksmith`, `quartermaster`) | Split: tier-up and prestige move into the three area NPC menus; blueprints and skins move to `/tool` (blueprint crafting needs the kind's NPC menu); starter kit claim replaced by the starter chest + "Replace lost tool". `/forge` stays as an alias that opens the menu of the area you stand in, or `/areas`. | P4 |
| Tutorial module (11 steps, cameras, Pip, optional steps, tutorial gates) | `modules.tutorial.enabled: false`, funnel replaced by `startflow` counters. Code deleted after one release (P9). | P3 / P9 |
| `Depths` as a player-visible name | See 3.7. | P1 |

### 3.2 Inventory merge (two inventories to one) - the core migration

Existing safety design to reuse: STANDARDS section 3 (crash may lose, never duplicate), `PlayerSaves`, `ItemBox`, the `WorldTools` pending-copy
design. The merge runs **once per profile**, lazily, on the first join/profile switch after the release (`WorldTools` keeps its cache logic until P9).

**Inputs per profile** (all already exist):
- the live inventory in the player file and its marker `inventory_side` (`<profile>:<side>`);
- the **other side's** row in `world_inventory` (never the live side's row: it is stale by design and would duplicate items);
- the pending copy (`inventory_pending`, `inventory_pending_token`) if a swap write was never confirmed (it wins over the row, as `WorldTools` already defines);
- `tool_stash` rows (Depths tools put away by the previous release; skip tool ids already present).

**Algorithm (main thread, for one online player, rows already loaded async):**
1. If the player PDC marker `inventory_merged=<profile>` is present: rows are only leftovers of a crash after the save. Delete them (idempotent) and stop.
2. Build the merge set: `live items` + `other-side items` (+ pending + stash). Remove from it: all island tool items (PDC of `islandtools`), the Depths kit items (`satchel` item, dungeon map; actions `satchel`/`dungeon` from `world-inventory.depths-items`), forest items (axes, forest drops are in the satchel, not items), and the old Forge starter-kit pieces of kinds AXE/ROD.
3. De-duplicate **tools** by tool id (`tools.tool_id` PDC): if two copies appear (should not), keep the one with the most XP, the rest go to the staff log (`tools_areas_merge_duplicate`), not to the player.
4. Place: live items stay in their slots; other-side items fill free slots in slot order (armor/offhand of the other side go to storage if the live armor/offhand slot is taken); what does not fit goes to `/box` (`ItemBox.give(..., BoxSource.MIGRATION, ...)`), never dropped, never destroyed.
5. Set marker `inventory_merged=<profile>` and clear `inventory_side`, `inventory_pending*` **in the same player file save** (`PlayerSaves.save`). Only after the save is confirmed: delete the `world_inventory` rows and `tool_stash` rows of this profile, then write `migration_marks (profile_key, name='inventory_merge', done_at)`.
6. Audit `item` / `tools_areas_merge` with counts (items, boxed, removed island tools, duplicates).

**Crash table:** crash before step 5 save -> nothing changed anywhere, merge repeats. Crash after save, before row delete -> marker present, step 1 deletes leftovers. Crash after delete, before `migration_marks` -> marker present, nothing to do. No state can create a second copy of any item.

**Open verification (P0 spike, no server needed):** how the `profiles` module stores each profile's inventory when a player switches profile (the player PDC marker belongs to the player file, profiles swap inventory snapshots). If the marker does not travel with the profile snapshot, store the "merged" flag in the profile snapshot instead (or in `migration_marks` written **before** the delete, with the delete done at the next join). The plan keeps the flag in both places so either design is safe.

**Offline profiles** (not the active one): their inventory snapshot is in the profile store; the merge runs on the snapshot when the profile is loaded (same algorithm, snapshot instead of live inventory). Players who never return keep the old rows until P9, then a staff command `/skyblock migrate areas --finish` merges remaining rows into their profile snapshots (or into `/box`).

**Staff tooling (P2):** `/skyblock migrate areas status|dry-run <player>|run <player>|report` (tab completed), dry-run prints what would move, what would be boxed and the refund amount; everything audited.

**Tests:** pure `InventoryMerge` class (lists in, lists out): live+other+stash, duplicates, overflow to box, armor conflict, tool de-dup, island tool removal, idempotent second run, pending-copy-wins. Crash simulation test with a fake store that throws after step 5. `MigrationMarks` store test on H2.

### 3.3 Tool item migration (Depths tools to the new three)

| Old | New |
|---|---|
| Depths pickaxe (any tier) | Becomes the profile's `PICKAXE`: same tool id, level, XP, prestige, perks, skin, stats kept. Tier index kept (coal..voidstone). |
| Depths sword | Becomes the `SWORD`, everything kept. |
| Depths hoe (Forge hoe tiers) | Becomes the `HOE`. Hoe tiers (basic..verdant) are replaced by the new hoe ladder (section 6.2); mapping by tier index, capped at the new maximum. |
| Several tools of one kind (e.g. two pickaxes from blueprints) | The one with the highest level/tier becomes the main tool; the others stay as **legacy tools** (still work, not upgradable, tradeable) so no value is lost; blueprint tools with abilities stay legacy until Q10 is answered. |
| Axe, rod | Axe: payout (3.5) and the item turns into a keepsake (non-functional "Retired Axe", tradeable, no stats) so nobody feels robbed. Rod: untouched (dormant kind). |
| Island tools | Removed, refunded (3.4). |

### 3.4 Refund policy for island tool purchases (proposal, Q8)

Island tool tier and upgrades were bought with **shards** (scarce: shard blocks only, 100/day) and money (tiers). Rows hold only `tier` and the `upgrades` string, not
what was paid, so the refund is **recomputed from the live formula**: shards = sum of `base x growth^n` for each bought upgrade level + tier shard price per tier; money = tier money prices.
- **Refund 100% of recomputed shards and money**, as ledger reason `island_tool_retire_refund`, one transaction per profile, idempotency key `island_tool_retire:<profile>`, written together with `migration_marks`.
  Recomputed values are capped at what the config could ever have cost, and a staff report lists the largest refunds (alert above a threshold).
- **Shared levels** (island rows, shared by members): each member's profile gets a refund for what the *island* had, split equally among members that exist on the island at migration time (so a bought tier is not refunded n times). Alternative: refund the island owner only (Q8).
- **Hoe storage** (`island_tool_storage`): items go to `/box` in full (never sold, never deleted).
- **Island tool XP**: there is none (island tools had no levels), so nothing to convert.
- **Veteran bonus** (optional, Q8): the new Hoe/Pickaxe start at level `1 + 4 x oldTierIndex` (wood 1, stone 5, iron 9, gold 13, diamond 17, netherite 21) if the profile has no Depths tool of that kind.

### 3.5 Forest removal payout

- Satchel items `depths_*_log` / `depths_*_stem` and any in inventory/box: sold automatically at the **base** `sell.yml` price (not the dynamic one), money credited, ledger reason `forest_retire_payout`, once per profile (idempotency key). Players see one chat summary.
- Depths axe tools: refund the **money** part of every tier they own (Forge price `F(t)` from the tier ladder; materials were consumed and are not refunded) + a keepsake. Reason `axe_retire_refund`.
- Foraging skill: stays (Q9) and is fed by island log breaks (island XP table already exists), so no one loses a skill. If the owner retires the skill, it becomes display-only.
- `depths_tier_unlocks` rows for `forest_*` zones and `zones.yml` forest zones are left in place until P9, then deleted.
- Mob/crafting/recipes that used forest items: `tools-defaults.yml` axe recipes disappear with the axe tiers; the sell table keeps the log prices for one release for stragglers, then drops them.

### 3.6 What happens to the island-side code

`islandtools` is split, not deleted at once:
- **Kept and moved** into a renamed module `islandfarm` (ID `islandfarm`; config `islandfarm.yml`): `IslandGrowth` (stage growth clock, label), always-wet farmland, placed-block memory (`Relevant`), regrow seconds and Growth Burst formulas (`ToolRules.regrowSeconds`, `burstPercent`), ripe cue.
- **Moved into the Hoe** (module `tools`): row/area harvest (`HoeFarming` row offsets, `Harvester` area logic merged into one class), hoe storage (optional, Q14), Auto-Sell (3.9 of section 10).
- **Deleted in P9:** `ToolStore`, `ToolItems`, `ToolMenu`, `ToolsMenu`, `StorageMenu` (island), `ToolListener`, shards payout, `Kind`, shared levels tables.
- The pure, unit-tested `Upgrades` parser (`kind:id:effect:max:base-cost:growth`) is **reused** by the new tool tree (moved to `tools`).
- `/is tools`, `/islandtools`, `/itools`: keep for two releases as aliases that open `/tool`.

### 3.7 Renaming "Depths" everywhere players see it

Rule: **internal ids stay**, text changes. No world rename (worlds.yml, build points, portals, DB rows, region names and 63+ files reference `depths`).

| Surface | Change |
|---|---|
| `messages/depths.yml` (423 lines), `messages/tools.yml`, `messages/tutorial.yml`, `messages/guide.yml`, `messages/npcs.yml`, `messages/hud.yml`, `help.yml`, `menu.yml` | Text pass: "The Depths" -> "the Mine / the Dungeon / the Farm" or "the Areas". Test: `NoDepthsWordTest` scans player-facing message files (allow-list for staff-only keys) and fails on "Depths". |
| Commands | New: `/areas` (menu: Mine, Dungeon, Farm, with level/tier and a Take me there button), `/mine`, `/farm`; `/dungeon` becomes the **Dungeon** tier menu only; `/satchel` stays (Q12). `/depths` stays as a staff command, hidden from tab complete for players. `/forge` -> alias. All with tab completion and `docs/commands/`. |
| `/menu` tiles | `depths` tile (command `dungeon`, icon `feature/depths`) -> `areas` tile (command `areas`). Icons: reuse the pack icon until art exists (`ICON_MAP`). Travel menu entry `depths-gate` -> three area entries. |
| Scoreboard | `hud.yml places.depths` and `messages/hud.yml scoreboard.depths*`: layout names stay, title/lines say Mine/Dungeon/Farm. Placeholder `depths_zone`/`depths_tier` keep their keys, values reworded ("Mine · Tier 3"). |
| Portals / builds | Spawn portal `to_depths` text -> "Areas" (message key `portal_to_depths` is in the build's `text:` and `worlds.yml portals` messages; keep ids). The Gate hub is renamed "The Crossroads" (zones.yml `hub.name`, one line). |
| Achievements, collection, quests, pass | Names containing Depths edited in their yml/messages; metric ids unchanged. |
| Resource pack | Icon `feature/depths` kept as the Areas icon; pack art can change later. |
| Docs | `WORLD.md` section 4, `DESIGN.md` 5.2, `docs/commands/depths.md`, `STANDARDS.md` 1.1 get a rename note. |

---

## 4. Start flow (item 2)

### 4.1 Join

1. First join of an account: create the profile + island immediately (reuse `IslandPortalFlow` / `IslandManager.createNow`, no confirm menu; the Gatehouse island portal stays for later and for new profiles), teleport to the island home, fill the starter chest (4.3).
2. Show the **bossbar** (yellow, same colour rule as the old tutorial bar in UI_STYLE 6.4): `Talk to Jaden at spawn` + muted `· /spawn`. After arrival at spawn it becomes `Talk to Jaden » 42m «` (distance via `NpcDirectory.npcByRole("jaden")`, existing `Trail`/compass logic can be copied into `startflow`; no camera, no parrot). The scoreboard "next objective" line shows the same text (UI rule: one clear objective).
3. Nothing is locked except what the old tutorial locked for abuse reasons (Q5).
4. Players who already played (existing accounts): no bossbar; they get the migration notice (3) and Jaden is available as a normal NPC.

### 4.2 Jaden

- **Where:** spawn Castle Courtyard (replaces the build NPC `guide`, `spawn_base_v1.yml` `guide` x2 y100 z-5; same spot, new name/skin; role `jaden`, `npcs.roles: guide: jaden`; the `help` NPC stays the help desk).
- **Click:** runs the Jaden menu; first-ever click prints the dialogue (chat, in the UI_STYLE format; no prefix on NPC lines, the NPC name in ember bold like Kindle did).
- **Menu (3 rows, Bedrock-safe, Back/Close in the standard slots):** tiles **Mine**, **Dungeon**, **Farm** (each: what you do, which tool you get and from whom, your level/progress, `» CLICK to go there` = standard teleport warmup), **Your island** (`/is home`), **Selling and shop** (`/shop`), **Help** (`/help`).
- **Dialogue (default text, `messages/startflow.yml`):**
  - first meeting: `Jaden » Welcome, <player>! Your island is your base. The real work happens out in the areas.`
  - `Jaden » The Mine pays in ore and crystals. The Dungeon pays in loot and souls. The Farm pays in crops and bushels.`
  - `Jaden » Each area has a craftsman who hands out your tool. Sell what you find, upgrade the tool, come back stronger.`
  - per area (when you pick a tile): one sentence about that area + `» Go there? [Take me there]`.
  - later clicks: short random tips, current objective, `[Replace lost tool]` shortcut.
- **Progress per account:** `welcome_progress(account, met_jaden_at, intro_mine, intro_dungeon, intro_farm)`. `met_jaden` removes the bossbar. The bossbar can continue with one line per unvisited area (`Visit the Mine`), then ends when all three were seen (Q4: guide beyond Jaden or stop after him).
- **First-visit rewards:** small and useful: the NPC of each area hands over the tool (4.4) and a one-time welcome gift (a few area currency, 1 tier of consumables). Audited, idempotent per profile.

### 4.3 Starter chest

`StarterContents` is a `common` interface published by the tutorial module and called by `IslandBuilder.fillChest`. The new `startflow` module publishes it instead; contents come from `startflow.yml starter-chest.items` (same `slot: item: amount` lines, `StarterSpec` parser is moved over unchanged).

Default contents (Q1): **Pickaxe and Hoe (level 1, bound to the profile)**, 8 wheat seeds, 8 bone meal, 2 oak saplings, 1 lava bucket, 16 bread, crafting table, chest, the field guide book (rewritten for Jaden and the areas). No sword (Dungeon NPC), no Axe. Tools come from `ToolService.grant(profile, kind)` (new), which creates the tools row + item once per profile (idempotent; a second call returns the existing tool as a new valid copy).

### 4.4 Area tool NPCs and re-issue

- Each area has one tool NPC at its entrance (roles `mine_tools`, `dungeon_tools`, `farm_tools`). Click = the area NPC menu (section 5.3).
- First visit: if the profile has no tool of that kind (sword always; pickaxe/hoe if the chest tool is lost), the NPC hands it out for free. Lost tool: **Replace lost tool** button creates a new valid copy (old copies stop working, same rule island tools had) for a small area-currency fee (default 0 for the first replacement per day, then 100 x tier) so replacing is not a free dupe path; the item is bound, so there is nothing to sell anyway.
- Existing `ToolService.starterKit` and the 24 h `starter_claims` table are retired with the Forge (table kept for audit).

### 4.5 What happens to the tutorial module and its NPCs

| Piece | Fate |
|---|---|
| Steps, camera flyovers, Pip, optional steps O1-O9 and their rewards, step menu, `/tutorial` | Retired. `/tutorial` becomes an alias of `/help`. Optional-step content that is still useful (settings, skills, team, upgrades) becomes Kindle challenges in `guide-quests.yml`. |
| Funnel stats (`FunnelStats`, `StatsMenu`) | Replaced by `startflow` counters: joined, island created, met Jaden, first area visited per area, first sale, first tool level 5, time to each (staff menu `/staff` -> Welcome funnel, Java vs Bedrock). |
| `TutorialGate` service + `Gates` ids | Kept as an interface in `common` (fail-open when no provider). Provider moves to `startflow` with only the gates Q5 decides to keep; all others return open. Callers (islands, economy, auction, trade, quests, worlds portals, depths) need no change. |
| `StarterContents`, starter chest | Moves to `startflow` (4.3). |
| Pip (the parrot) | Removed. |
| Kindle (island guide, `modules/guide`) | **Stays.** Only text changes ("the Depths" -> areas), and Kindle's rotation points at Jaden for area topics. Kindle is not part of the start flow. |
| NPCs `guide` (Castle Courtyard) | Becomes Jaden. `depths_guide` (dungeon board NPC at the Gate) -> hub guide, role `areas` (opens `/areas`). `depths_forge`, `blacksmith`, `quartermaster` -> removed from `npcs.roles` (Forge Quarter keeps the enchanter; the empty plots need a builder pass, Q16). `depths_sell` -> hub sell NPC (opens `/shop` hub). |
| Permissions | `skyblock.tutorial.*` nodes are kept declared but unused until P9 (PermissionFilesTest owns each node once). New nodes in `startflow.yml`. |

---

## 5. Areas (item 3)

### 5.1 Layout and builds

Internal: one world `depths`, hub "The Gate" -> renamed **The Crossroads**, wings: west **Mine** (12 zones, builds t01-t03 exist), north **Dungeon** (10 zones, builds t01-t02 exist), east **Farm** (was Forest). Source of truth for coordinates stays `zones.yml` (merged from `depths_layout.yml` by `/skyblock builds apply`).

| Area | Build status | Needed |
|---|---|---|
| Mine | t01-t03 built; t04-t12 not | Builder continues (unchanged). Add the tool NPC pad `mine_tools` (in t01 `arrival` area or on the Crossroads at the Mine gateway; Q15). |
| Dungeon | t01-t02 built | Builder continues. NPC pad `dungeon_tools` at the Crossroads gateway. |
| Farm | **none** | New builder brief (5.2). Slot: replaces `forest_t01` (x 80..159, z -48..31, 80x80). `zones.yml`: new zone `farm_01` (aspect `farm`), exit closed (single zone), later `farm_02` etc. for more fields if needed. |
| Crossroads (hub) | built (`depths_gate_v1`) | Rename wing sign `sign_forest` -> `sign_farm` (hologram text in the build yml), swap the forest gate palette entry, add NPC pads `mine_tools`, `dungeon_tools`, `farm_tools` + a sell NPC. A small builder fix, not a rebuild. |
| Spawn | built | Jaden replaces `guide`; the Forge Quarter loses the Forge/Blacksmith/Quartermaster (their plots become decoration or a second Enchanter hall - builder choice). |

### 5.2 Farm build brief (for `docs/BUILDER_BRIEF.md`, new section)

- 80x80 zone like the others, tier-gate and arrival pad like mine t01 (build yml format identical: `npcs`, `holograms`, `arrival`, `regions`, `ambient_spawns` (none), `particles`).
- One or more **crop fields**: rectangles of farmland (moisture 7) with `wheat` age 7 on every tile, 1-block paths between blocks of about 9x9 so players can walk; total 1,200 to 2,000 crop tiles. The `ore_field`-style region is called `crop_field`: cuboid contains **only** farmland (or `soul_sand` rows, see 8.7), wheat age 7 and air; nothing else, so the slot scan is exact. Water channels outside the cuboid for looks (no flowing water inside).
- No holograms on the field, no lanterns inside the cuboid (light blocks above at y+1 outside it), no fences inside.
- Decor: barn, windmill (stationary), scarecrows, hay bales (outside cuboid), the Hoe NPC pad `farm_tools` and a sell NPC pad `farm_sell` at the entrance.
- Safety: perimeter fence so nobody falls into void, no water that flows onto crops, a 2x2 safe area at the arrival.

### 5.3 Loot, currency, NPC menus

| | Mine | Dungeon | Farm |
|---|---|---|---|
| Loot (sell items) | `depths_coal`..`depths_voidstone` (12), unchanged ids, sell.yml unchanged | `depths_rotten_flesh`..`depths_wither_ash` (10), unchanged | New custom items `farm_wheat`, `farm_carrot`, `farm_potato`, `farm_beetroot`, `farm_nether_wart` (sell.yml section `farm`, 10.2). Not vanilla wheat, so island crops can pay less. |
| Upgrade currency | Crystals ◆ (exists) | Souls (new) | Bushels (new) |
| Earned | per block: `crystal-chance` tables (unchanged) | per kill: soul chance by mob tier | per crop: bushel chance by crop tier |
| Tool | Pickaxe | Sword | Hoe |
| Skill XP | Mining | Combat | Farming |
| Rare finds | Key fragments (tiers 11+), enchant books, blueprints | Books, blueprints, key fragments | Seed packs (a few free seeds), books at high crops, key fragments at the last crop (Q18) |
| Sell path | area loot goes to the **satchel** as today; NPC menu "Sell all <area> loot" | same | same |

**Currency implementation:** extend `Currency` with `SOULS` and `BUSHELS` (non-transferable like crystals, listed in `/bal`, per profile, wiped at season). `CrystalBatcher` is generalised to `AreaCurrencyBatcher` (same batching, same ledger idempotency). A mapping `ToolKind -> Currency` in `tools.yml`. Enchants (`/enchanter`) cost the currency of the tool they go on (Q3b): a dungeon player no longer has to mine for sword enchants. All keys migrate: `tokens` and crystal data stay valid.
Cheaper alternative (Q3): keep Crystals as the only upgrade currency for all three areas (no enum change) and make "area" a *source tag*. Simpler, but loses "different currency per area".

**Area NPC menu (4 rows; same builder `AreaToolMenu` for the three NPCs):**
```
 Row 0:  4 tool tile (kind, tier, level + XP bar, prestige stars, currency balance)
 Row 1:  [Sell all <area> loot (value)]  [Sell one item type...]  [Upgrade perks]  [Tier up]  [Prestige]
 Row 2:  [Go to tier/crop picker]  [Replace lost tool]  [Blueprints]  [Skins]
 Row 3:  Back  .  Close   (standard slots)
```
`Sell all` uses `SellService` (one ledger transaction, items from satchel + inventory of that area only), never its own pricing; shows the sale line from UI_STYLE (action bar `✔ Sold 412 Coal for $8,240`, chat line with hover breakdown above `sell.big-sale-chat-from`). The `/shop` hub (section 9) reaches the same sell logic.

### 5.4 Island vs areas

| On the island | Behaviour |
|---|---|
| Pickaxe | Works on island ores and generator output: vanilla drops, tool XP x `island-factor` (default 40%), crystals 0 (areas only), counts for island tasks. |
| Hoe | Works on island crops (Harvester + row upgrade + replant), tool XP x 40%, Bushels 0, counts for island tasks. |
| Sword | Works on island spawner mobs: kills count for island tasks, XP x 25% (as today), souls 0. |
| Rewards | Island yields vanilla items; they sell for less (10.3). |
| Island progression | Island upgrades keep their tasks, now phrased as island activities (`harvest N crops`, `mine N ore`, `kill N spawner mobs`), counted from island breaks only. Farm/areas do not count. See 10.4. |

---

## 6. Tool system (item 4)

### 6.1 What other servers do (from memory, shape only)

- **Hypixel SkyBlock farming:** a hoe per crop family, stats (Farming Fortune = % chance for extra crop rolls), enchants such as Replenish (replant), Turbo-crop, Dedication, Cultivating (counter), crop collections gate recipes. Lesson: **a single stat (fortune) carries the economy; other perks are convenience.**
- **Hypixel SkyBlock mining and swords:** Mining Speed and Mining Fortune stats, a right-click ability on a cooldown, level via collections/XP. Lesson: **one active ability per tool with a cooldown; passive stats everywhere else.**
- **Prison pickaxes:** enchants bought with a token currency earned by mining (Efficiency, Fortune, Haste, Jackhammer, Explosive), Key Finder, Token Merchant, prestige resets ranks for a multiplier. Lesson: **earn-by-use currency + enchant list + prestige loop is the proven retention loop.**
- **Factions/skyblock harvester hoes:** Replant, Auto-Sell, harvest radius upgrades, XP/haste boosts. Lesson: **Auto-Sell and replant are convenience, radius is the progression, and sell multipliers must be capped** (we cap at +300%).
- **Takeaway for balance:** three power sources only (level, upgrades, prestige), a hard family cap, one ability per tool, and money parity between areas.

### 6.2 Tiers, levels, upgrades, prestige - how they fit together

Four layers, each with one job (so "everything makes sense"):

| Layer | Source | What it does | Cap |
|---|---|---|---|
| **Tier** (material, 12 / 10 / 5 steps) | NPC "Tier up": area materials + money (existing Forge economy) | Base speed/damage/yield and the look (stone..netherite). Sink for area loot and money. | Pickaxe 12 (coal..voidstone), Sword 10, Hoe 5 (wooden, stone, iron, gold, diamond... names Q17) |
| **Level 1-50** (XP) | Using the tool (anywhere; areas full XP, island reduced) | Small passive power (+1% speed, +0.4% drops per level), unlocks perk slots (5/15/30/45) and **gates the maximum level of each upgrade** | 50 |
| **Upgrades** (tree of 8 per tool) | Area currency, NPC menu | Chosen power: speed, yield, convenience, one ability | each upgrade has a max level; the level gate rises with tool level and prestige |
| **Prestige** (stars) | Level 50 + area currency + money | Resets level/XP to 1; keeps tier, upgrades, perks, stats; +1 star = +10% tool XP gain, +2 to all upgrade level gates, +2% sell value of that area's loot (counted in the sell bonus family, under the +300% cap). Max 10 stars per season (Q11). | 10 |

Existing **enchants and perk slots** (`modules/enchants`, `/enchanter`, books, 48 enchants) stay: slots at Lv 5/15/30/45 (+prestige slot). Enchants are the *rare, random-source* layer; upgrades are the *deterministic, bought* layer. To avoid double-counting, upgrades `efficiency`/`fortune` and enchants of the same name share the existing **family caps** (`BonusService`: EXTRA_DROPS, break speed). `ToolService.maxEnchantLevel` gate is unchanged.

### 6.3 Level curve (formula, table)

Today: step(L) = 400 x 1.21^(L-1), total to Lv 50 = 21.7M XP, ~200 played hours for a regular player (ECONOMY_BALANCE 3.5). The owner wants it **easy and progressively faster** (Q6). Interpretation used here: (a) the first levels come in minutes, (b) levels never feel like a wall, because XP gain rises with tool level and area tier, (c) prestige restarts the curve with a speed bonus.

**Proposed:** `step(L) = base x growth^(L-1)` with **base 200, growth 1.15**, plus **+2% XP per tool level** (Lv 50 = x2.0) and +10% per prestige star. Total to Lv 50 = **1.26M XP** (was 21.7M). XP per action is unchanged (tier XP: mine blocks 1..85, Dungeon kills tier XP x 5, Farm crops 1..10 by crop, island x 40%).

| Level | Step to next | Total XP | Regular player reaches it (mine, hours played) |
|---|---|---|---|
| 2 | 200 | 200 | 5 min |
| 5 | 350 | 1.0k | 25 min |
| 10 | 704 | 3.4k | 1.7 h |
| 15 | 1.4k | 8.1k | 3.0 h |
| 20 | 2.8k | 17.6k | 5.2 h |
| 30 | 11.5k | 75k | 10.4 h |
| 40 | 46.6k | 309k | 19 h |
| 45 | 93.7k | 623k | 26 h |
| 50 | (max) | 1.26M | 35 h |

(Simulation: a quick script using the BAL2 mine pacing - tier reached by hour, blocks per hour and tier XP from ECONOMY_BALANCE 3.1/3.4/3.3; casual players take about 2.5x, grinders about 0.5x. Exact totals per level are generated by `tools/economy_model.py` in P8 and replace this table; the order of magnitude is the point: **Lv 50 in about 35 regular hours, i.e. around day 12, then prestige keeps going.**) Slot timings become: slot 1 (Lv 5) 25 min, slot 2 (Lv 15) 3 h, slot 3 (Lv 30) 10 h, slot 4 (Lv 45) 27 h, slot 5 (prestige) 35 h. Compared with today (1.1 h, 6.8 h, 30 h, 121 h, 200 h) this is **about 4x faster**; `curve.base`, `curve.growth`, `xp.level-bonus`, `xp.prestige-bonus` are settings, so the owner can slow it down.
Prestige n costs the curve x `1 + 0.25 n` XP (config), so late prestiges stay long.

### 6.4 Pickaxe (Mine, currency Crystals)

| Upgrade | Max | Effect per level | Level gate | Cost (crystals, level n+1) |
|---|---|---|---|---|
| Efficiency | 10 | +8% break speed (attribute) | 1 + 4n | 60 x 1.7^n |
| Fortune | 10 | +10% expected drops (EXTRA_DROPS family) | 3 + 4n | 80 x 1.8^n |
| Haste Hands | 3 | haste I-III while mining | 10 + 10n | 200 x 3^n |
| Smelter | 5 | +20% chance ores drop smelted | 5 + 6n | 100 x 1.9^n |
| Crystal Finder | 5 | +10% crystal chance | 8 + 6n | 120 x 2.0^n |
| Prospector | 5 | +10% tool XP | 1 + 8n | 60 x 1.7^n |
| Magnet | 1 | drops straight to the satchel (already default; becomes the free baseline, removed from tree) | | |
| Auto-Sell | 1 | sells mined loot in batches (Q13) | 15 | 2,000 |
| **Ability: Overdrive** | 5 | right-click: 6 s of 3x3 breaks (levels extend duration and shorten cooldown 60 s -> 30 s) | 20 | 500 x 2.5^n |

Prices above are shapes: costs are fitted so the whole tree takes about 30 to 40 mine hours at the crystals-per-hour table (ECONOMY_BALANCE 9.1: 118/h first hour, 412/h day 3, 2,066/h month 1 for a regular player) and a single early upgrade takes 3 to 10 minutes. P8 fits exact numbers in `economy_model.py` (new table `area_tools`).

### 6.5 Sword (Dungeon, currency Souls)

| Upgrade | Max | Effect per level | Gate |
|---|---|---|---|
| Sharpness | 10 | +6% damage | 1 + 4n |
| Looting | 10 | +10% drops | 3 + 4n |
| Life Leech | 5 | heals 1% of damage dealt (existing `lifesteal-max-heal` cap) | 8 + 8n |
| Cleave | 3 | hits one extra mob within 2 blocks for 40/60/80% | 10 + 10n |
| Executioner | 5 | +8% damage to mobs under 25% HP | 12 + 6n |
| Soul Finder | 5 | +10% soul chance | 8 + 6n |
| Battle Study | 5 | +10% tool XP | 1 + 8n |
| **Ability: Whirlwind** | 5 | right-click: damage + knockback to mobs within 4 blocks, cooldown 45 s -> 25 s | 20 |

Dungeon mobs, tiers and drops stay as built (`mobs.yml`). Kill XP = tier XP x 5.

### 6.6 Hoe (Farm and island, currency Bushels)

| Upgrade | Max | Effect per level | Gate |
|---|---|---|---|
| Reach (harvest pattern) | 5 | 1x1 -> 3x3 -> 5x5 -> 7x7 -> 9x9 (one click harvests all ripe crops in the square; the same rule island and Farm) | 1 + 8n |
| Fortune | 10 | +10% extra crops | 3 + 4n |
| Green Thumb | 5 | +10% tool XP | 1 + 8n |
| Quick Regrow | 5 | Farm respawn 2.0 s -> 1.0 s floor (step 0.25 s); island regrow -8% per level (floor 10 s) | 10 + 8n |
| Seed Saver | 5 | island: +0.1 seeds returned per harvest (the "buy one seed" loop, 10.5) | 5 + 8n |
| Growth Burst | 3 | island: chance the harvested row is ripe at once (existing formula) | 15 + 10n |
| Replant | 1 | free baseline on island (consumes a seed from the drop); not sold | |
| **Auto-Sell** | 1 | **free, unlocks at hoe Lv 3** (about 2 minutes): crops sell in batches, see 10.6 | 3 |
| Magnet | 1 | free baseline | |
| **Ability: Scythe** | 5 | right-click on the Farm/island: harvest radius x2 for 5 s | 20 |

Hoe tiers (Q17): Wooden, Stone, Iron, Golden, Diamond (5 tiers, tier-up materials = the previous crop's farm item + money), unlocking by farm crop progress rather than skill level; names are placeholders.

### 6.7 Prestige rules

- Requirement: level 50, all `required-for-prestige` upgrades at their gate max (default: none), cost in area currency and money (default 50,000 currency x (1 + 0.5 n) and $1M x 2^n).
- Confirm dialog (standard 3-row), audited (`tool_prestige`), the star shows in the item name/lore and on the leaderboard.
- Effects as the table in 6.2; level/XP reset; **tier, upgrades, perks and stats kept**; max stars 10 (config). Per season (tools are season-wiped like everything).

### 6.8 Balance principles (enforced by tests and the model)

1. Money per hour at equal tier: Farm = Mine x 1.15, Dungeon = Mine x 1.0 (existing parity), island = 25-40% of the area.
2. Power sources: level (passive, max ~+50% speed/+20% drops), upgrades (about +80% speed, +100% drops at max), prestige (+2% sell/star); all inside BonusService families, under the +300% sell cap and the existing family caps. `CapSimTest` pins the worst case (everything maxed, 10 stars, best set) under the cap.
3. Convenience (Auto-Sell, Magnet, Replant) is cheap and early; power is what costs.
4. Anything new that makes money is added to `tools/economy_model.py` first (project rule: balance sheet before prices).

---

## 7. Where the XP and rewards come from (hooks)

All area rewards flow through the existing event chain, so synergy (quests, collection, achievements, enchants, sets, stacking) keeps working:

`ResourceBreakEvent` (Mine, Farm) -> `DepthsDrops` (drops, currency, rare finds) -> `DepthsDropEvent`. Dungeon: `DepthsMobKillEvent`.
New: `AreaTool` bridge in the `tools` module listens to those events (today `DepthsHooks`), adds tool XP, upgrade effects and area currency, and calls `AreaCurrencyBatcher`.
`IslandCropHarvestEvent` (production) stays the island crop hook.

---

## 8. Farm technical design (item 5)

### 8.1 Reuse the Depths engine

The engine already handles: resource regions, per-player depletion, timing wheel, chunk re-send, relog memory, Bedrock throttling, protection of everything else in the region, fast-break check. The Farm adds:
- `Aspect.FARM` (replaces `FOREST`), mapped to `SkillType.FARMING`.
- A **crop palette** per field: the DV2-SPIKE palette (`TierPalette`, `PaletteMath`, `TierPalettes`, `SlotIndex`) promoted from "one pilot zone behind `palette.enabled`" to a per-zone `palette:` block in `zones.yml` (multiple palettes at once) and an abstraction `TierSource` with two implementations: `SkillLevelSource` (current, mine/forest) and `CounterSource` (Farm: harvest counters).
- Resource definition for the field (zones.yml `fields.crop`):
  ```yaml
  fields:
    crop:
      corner-1: "..."; corner-2: "..."
      resources:
        - { block: wheat, depleted: "air", respawn-seconds: 2 }
      palette:
        source: counter
        tiers:
          - { crop: wheat,      shown: "minecraft:wheat[age=7]",          unlock: 0 }
          - { crop: carrot,     shown: "minecraft:carrots[age=7]",        unlock: 500 }     # wheat harvested
          - { crop: potato,     shown: "minecraft:potatoes[age=7]",       unlock: 2500 }    # carrots harvested
          - { crop: beetroot,   shown: "minecraft:beetroots[age=3]",      unlock: 8000 }
          - { crop: nether_wart, shown: "minecraft:nether_wart[age=3]",   unlock: 20000, below: "minecraft:soul_sand" }
  ```
  (Thresholds, order and crops are defaults, Q19/Q20.) The mine and dungeon zones are untouched.

### 8.2 Server block versus what the client sees (the key decision)

Options:

| Option | Server block | Pros | Cons |
|---|---|---|---|
| **A (recommended)** | Real `wheat[age=7]` on farmland | Wheat players need **no packets** (like palette tier 1). The server block is a real breakable block, so `BlockDamageEvent` and `BlockBreakEvent` fire. Any player who is out of sync still sees a sane crop. | Needs physics guards (below). A fake-carrot player gets one wheat flash on break (8.4). |
| B | A neutral block (e.g. `short_grass`) | No physics concerns | Every player gets packets for every tile, 300 players x 1,600 tiles on every chunk send; more Bedrock load; a wrong state shows grass, not a crop. |
| C | Air + packets | None | **Does not work**: the server ignores dig packets on air, no break events at all. |

So: the real block **must** be a breakable block (C is impossible), and wheat is the cheapest and most natural. Guards for real wheat on real farmland in the field region (`FarmGuard` listener, all `ignoreCancelled`, only inside crop regions):
- cancel `BlockPhysicsEvent`/`BlockGrowEvent`/`BlockFadeEvent`/`MoistureChangeEvent` (farmland stays moist 7, wheat stays age 7);
- cancel `PlayerInteractEvent` with `Action.PHYSICAL` and `EntityChangeBlockEvent` (no trampling); cancel bone meal, planting, bucket use (already covered by `engine.protect-regions` + world protection flags);
- world `random_tick_speed` already 0 in `depths`; water and pistons cannot occur (protected region);
- builders may only paste farmland/wheat/soul sand inside the cuboid (build validator rule for kind `crop_field`).

### 8.3 Per-player crop state (O(1) per player)

The visible crop is **one number per player** (`activeCrop`, an index into the palette), not a per-block map. Per-block per-player state exists only for blocks that are **currently depleted** (the 2 s window, ~20 to 200 entries per active player, already how `PlayerDepletion` works).
- Progress: `farm_progress(profile_key, crop, harvested BIGINT, unlocked_at)`; counters live in memory per online profile and are written behind (batch every few seconds and on quit, like `GainBuffer` for skills; flush on profile switch/disable).
- `activeCrop` = highest unlocked crop, or the one the player picked in the Hoe NPC menu (palette "forced tier", D2). Unlock check on each counted harvest: one int compare.

### 8.4 Break handling, step by step

1. Client sees `carrots[age=7]` (override) or the real wheat. Both are instant-break crops (hardness 0). The client predicts the break (block disappears locally) and sends the dig packet.
2. Server: `BlockDamageEvent` (instabreak) then `BlockBreakEvent` on the **real wheat**. `DepthsEngine.onBreak` cancels it (real block never changes), looks up the region, checks the player's own state:
   - already depleted for this player -> ignore, re-send depleted (prevents double rewards);
   - else mark depleted for this player (key = packed block position), schedule 2 s respawn on the timing wheel, fire `ResourceBreakEvent` with the **player's active crop** (`getShownType()`), which drives drops, currency, XP, counters.
3. **Revert flash.** Cancelling a `BlockBreakEvent` makes the server send the real block back; the engine therefore re-sends the depleted block one tick later (`queueResend`), which is what the spike already does. For wheat players the client briefly sees the real wheat reappear (about one tick) before air; for carrot players the same flash shows wheat instead of carrots. This is the one visible artefact. Mitigations: (a) keep it to one tick (same as mine ores today); (b) hoe `Reach` batches the squares into one multi-block change; (c) optional "fresh sprout" respawn stage. P0 spike measures it on the test server with the owner's permission; if unacceptable the fallback is making depleted look = the real block for that tick (use `farmland` shown under the crop, which is what the revert already shows).
4. Respawn: timing wheel fires at +2 s (40 ticks): `sendBlockChange` with the player's **current** active crop (so a crop unlock mid-wave never shows a stale crop); if the block is in an unsent chunk the next chunk send handles it.
5. Optional sprout animation (age 0 at break, age 4 at 1 s, ripe at 2 s): `farm.sprout-stages` (default off; 3 packets instead of 2 per crop).
6. Rewards: `DepthsDrops` -> `farm_<crop>` items to the satchel, Farming XP, tool XP, bushels, counters, quest/achievement hooks, via the normal event, so the Farm uses the same code as the Mine.

### 8.5 Chunk resend, join, teleport, unlock swap

- `PlayerChunkLoadEvent` (fires right after the chunk packet) re-applies depleted blocks and the palette override for the chunk, exactly as the engine does now. Players with `activeCrop = wheat` and nothing depleted receive nothing.
- Join/teleport/world change: depleted timers are parked in `DepletionMemory` (existing); on arrival `isChunkSent` guards avoid sending into unsent chunks.
- **Unlock swap** (e.g. carrots unlocked): re-send every slot of the chunks the client has, nearest first, with the existing wave particle and title ("Carrots unlocked!"). Java: one multi-block change per chunk section; Bedrock: throttled `palette.bedrock-blocks-per-tick` (300/tick, ~6 ticks for 1,600 tiles).
- Reload/rebuild: `/skyblock reload depths` re-indexes slots; a builder edit invalidates the chunk's slot cache (existing).

### 8.6 Hoe on the Farm (Reach / Scythe)

Right-click or left-click a crop with the Hoe: positions inside the Reach square that are (a) inside the same `crop_field` region and chunk-sent, (b) ripe for the player (not depleted), (c) within `farm.max-reach` of the clicked block, are treated exactly like individual breaks (same depletion + `ResourceBreakEvent` per tile, so counters/XP/drops are per tile) but **sent as one multi-block change**. A per-player token bucket `farm.max-rewarded-breaks-per-second` (default 6, same as the 6 blocks/s model cap) limits rewarded tiles; extra tiles are not depleted (so nothing is lost silently, the click just harvests fewer). Cooldown `farm.swing-cooldown-ms` (100).

### 8.7 Which crops

Only instant-break crops with a simple block state: wheat, carrots, potatoes, beetroots (farmland), nether wart (soul sand under; the override also sends `soul_sand` under the tile). Stack crops (sugar cane, cactus) and fruit crops (melon, pumpkin, hardness 1) are **not** Farm crops (different break time and shape); they stay island crops. Torchflower/pitcher are possible later.

### 8.8 Bedrock (Geyser) caveats

- Geyser translates `ClientboundBlockUpdate`/multi-block updates to individual Bedrock packets; large swaps must be throttled (already built and logged by `palette.log-swaps`).
- Java and Bedrock both show crop models from block state; `carrots[age=7]` and `potatoes[age=7]` render as vanilla crop stages. Nether wart `age=3` fine.
- Bedrock breaks crops with tap/hold: instabreak crops still produce dig packets; the 6/s token bucket keeps touch players and PC players on the same pace.
- A held break on Bedrock can send repeated dig starts on the same tile: depletion check swallows them.
- Test list for the owner (Bedrock): unlock swap with 1,600 tiles, break flash, relog mid-respawn, teleport away and back.

### 8.9 Performance (300 players)

- Break: 1 hash lookup (`RegionIndex`), 1 map insert, 1 wheel add, 1 event; engine statistics already tracked (`/depths debug`: avg/max break nanos).
- 300 players x 4 breaks/s = 1,200 breaks/s -> about 2,400 small block-change packets/s (break resend + respawn). With Reach batching it is less. No entities, no holograms, no ticking per crop.
- Memory: depleted map per active player capped (`remembered-per-player` 4,096), `activeCrop` int per player, slot index per chunk lazily built.
- Swaps are the only burst (unlock, join to a changed crop): throttled, logged, measured.
- Anti-exploit: server validates each tile against the player's own state; `max-rewarded-breaks-per-second`; Grim handles reach/speed; the 2 s respawn is a floor-bounded config (`farm.min-respawn-seconds` 1).

---

## 9. `/shop` as the one hub (owner addition)

**Today** (`modules/shop`): `/shop` -> category menu (up to 9 tabs: Building Blocks, Nature, Farming, Mob Drops, Colors, Decoration, Redstone, Essentials, Spawners) with item grids, buy GUI, sell GUI, and a "Sell chest" button; `/sell` is separate (sell chest, hand, all). Area loot is sold from the satchel menu. Spawner tiers 1-2 are a shop tab (`kind: spawners`). So buying drops (Mob Drops tab), crops/seeds (Farming tab), spawners and selling already exist; they are spread over `/shop`, `/sell`, `/satchel` and the NPC `depths_sell`.

**Change:** one landing screen, `/shop`, with three rows:
```
 Row 0 (buy):   [Blocks] [Nature] [Farming (seeds, crops)] [Mob Drops] [Spawners] [Colors] [Decoration] [Redstone] [Essentials]
 Row 1 (sell):  [Sell inventory] [Sell chest] [Sell area loot -> Mine / Dungeon / Farm / all] [Market tips / Hot item]
 Row 2:         wallet tile, Undo, Back, Close
```
- The 9-tab cap in `ShopSettings` becomes 12 (config); Row 0 stays the existing `shop.yml categories`, so no catalogue change.
- **Sell area loot** opens a tiny menu with the three areas (value of what you hold per area) and "Sell all" per area, using the same `SellService` path as the NPC menus; no pricing code is duplicated.
- Area NPCs open the same menus (NPC role `*_sell` -> `shop sell <area>`), `/sell` stays as the quick command.
- Sub-commands (tab completed): `/shop sell` (sell row), `/shop sell mine|dungeon|farm|all`, `/shop <category>` (exists).
- Help topic, `/menu` tile and `docs/commands/shop.md` updated. Bedrock: all plain chest clicks (no right/middle click needed).

---

## 10. Economy and balance

### 10.1 Targets

| Stage | Money per played hour at the equal tier |
|---|---|
| Mine tier 1 | $32.5k (BAL2) |
| Dungeon tier 1 | ~= Mine (existing parity) |
| Farm, wheat | **$37k** (x1.15) |
| Island crops, wheat | 25% of the Farm item (**~$9k/h** for a medium farm, then limited by island size, regrow clock and seeds) |

### 10.2 Farm item prices (sell.yml section `farm`, defaults)

Derivation: rewarded crops per played hour at the cap = 6 per second x 3,600 x 75% uptime would be 16k, but real play (walking, aiming, Reach squares) is about 2.5 to 3 crops/s effective -> **~6,700 crops/h** (the model value; the cap only stops macros). Wheat: 37,000 / 6,700 = **$5.5**. Each next crop x1.35 like the mine tiers (and x2.2 at the top crops like the mine's tier 9+).

| Farm item | Crop unlock | Price (default) | Farming XP | Tool XP | Bushel chance |
|---|---|---|---|---|---|
| farm_wheat | 0 | $5.5 | 1 | 1 | 0.05 |
| farm_carrot | 500 wheat | $7.5 | 1.5 | 1.5 | 0.065 |
| farm_potato | 2,500 carrots | $10 | 2.5 | 2.5 | 0.085 |
| farm_beetroot | 8,000 potatoes | $13.5 | 4 | 4 | 0.11 |
| farm_nether_wart | 20,000 beetroot | $18 | 6 | 6 | 0.143 |

Conflict to resolve first: today's `sell.yml` has **vanilla wheat at $10 and carrot at $14**, i.e. above these Farm prices. Island crops must be *lower* than the Farm item (owner), so the plan is: **lower the vanilla crop sell values to 25% of the Farm item** (wheat $1.4, carrot $1.9, potato $2.5, beetroot $3.4, nether wart $4.5; sugar cane, cactus, melon, pumpkin, cocoa, berries scaled by the same rule from their island production tier) - Q21. Option B (keep $10 and make the Farm much richer) is rejected because it breaks the "Farm = same $/h as the mine" parity and would make the Farm the only place to play.

Prices flow through the existing dynamic-price system (floor 40%, ceiling 150%, Hot Item); because Farm items are separate ids, selling Farm wheat does not depress island wheat and vice versa.

### 10.3 Island crop prices and seeds

- Island crops sell at 25% of the Farm item (10.2). A 200-tile island farm at the 15 to 60 s regrow clock makes about 5k to 13k crops/h, i.e. **$7k to $18k/h** for wheat - between 20% and 55% of the mine at the same time, and it needs a hoe click each cycle (Reach squares make that cheap; Auto-Sell makes it passive-ish).
- Growth is bounded by **farm size** (island size, seeds), which is why seeds are the lever:
- `/shop` seed prices (default; "THE ONE RULE" is a lower bound only, these are far above it): `wheat_seeds` **$1,500**, `beetroot_seeds` $4,000, `carrot` $6,000, `potato` $6,000, sweet berries $8,000, nether wart not sold. Start money is $1,000, so nobody buys wheat seeds on minute one; the starter chest has 8 seeds and the **Farm area drops Seed Packs** (rare, 1 to 3 seeds of the current crop, Q18) as the free way in. Optional `shop.seed-escalation` (price x1.5 per seed already bought that day, resets at midnight Oslo; default off, Q22) if spamming still happens.
- The expansion loop is **harvest and replant**: island harvest returns seeds (vanilla drop rules, tunable by `Seed Saver`, expected return about 1.3 to 1.6 per ripe crop), the Hoe's `Replant` consumes one, the rest become new tiles by hand. One bought seed grows the farm geometrically, which is what the owner asked for. The model caps it at the island's farmland count (island size upgrade) and the regrow clock.
- Fortune extra crops are never seeds (`harvester.no-bonus-items` exists).

### 10.4 Island vs Farm: both must matter

| | Farm (spawn area) | Island |
|---|---|---|
| Money | best (x1) | 25% value per crop but passive and scalable with farm size |
| Tool XP | 100% | 40% (`xp.island-factor`) |
| Area currency | yes | no |
| Skill XP | full | full (Farming skill is not area-bound) |
| **Island progression** | no | **yes**: island upgrades need island activity (tasks) |
| Seeds | no | yes |

Island upgrades keep their `ProgressionService` tasks; task types are rewritten to island activities (`ISLAND_CROPS`, `ISLAND_ORES`, `ISLAND_KILLS`, `ISLAND_LOGS`), counted from `IslandCropHarvestEvent` and island break/kill events by any member, **not** from the areas. A player who farms only the Farm is rich but their island stalls; a player who only farms the island has a strong island and a slower wallet. Targets (P8, model): to finish the five production upgrades a regular player needs about **40% of their farming time on the island**; Kindle's rotation nudges this with challenges ("harvest 300 crops on your island").

### 10.5 Hoe Auto-Sell

- Unlock: **hoe level 3** (default `autosell.unlock-level: 3`). Level 3 = 430 XP total; at 1 to 2 XP per crop and 2 to 3 crops/s that is **about 2 to 3 minutes** on the Farm (island: ~5 min at 40% XP; Q13 can also start a timer).
- Free, no currency. Off by default after unlock, one click in the Hoe menu or `[Turn on]` in the unlock message; per tool setting `autosell` (persisted in the tool row).
- Works on the Farm and the island. Sells the **crops of that harvest** (not the whole inventory) through `SellService` in one batch every `autosell.batch-seconds` (default 5), one ledger transaction per batch (reason `auto_sell`, idempotency key per batch), the sell bonus and cap apply as for manual sales. Failure path: items stay in the satchel/inventory (no loss). Cap: `autosell.max-per-batch`.
- Pickaxe and Sword get the same toggle at their level (default Lv 10 / Lv 10, Q13), paid as an upgrade (2,000 area currency) so players keep a reason to open the NPC menu.

**Messages (UI_STYLE 3.1: plain words, ✔ in green, values white, money in currency colour, `[Action]` yellow with hover, no prefix on batch lines):**
```
 Unlock (once, with prefix, title-free):
   PHOENIX » ✔ Auto-Sell unlocked on your Hoe. Crops sell as you harvest. [Turn on]
   (hover: "Click to turn Auto-Sell on. You can switch it in the Hoe menu.")

 Turn on / off (reply to a click):
   PHOENIX » ✔ Auto-Sell on.        PHOENIX » ✔ Auto-Sell off.

 Each batch (action bar, merged, at most one per 1.5 s):
   ✔ Sold 48 Wheat · $264

 Summary (chat, every 30 s of selling or when you stop; no prefix; one line):
   ✔ Auto-sold 312 crops for $1,716 (last 30 s)  [Details]
   (hover: Wheat x 240 $1,320 / Carrot x 72 $396 · sell bonus +12% included)

 Problems (error format, with the fix):
   ✘ Auto-Sell paused: your satchel is full. Sell at the farm NPC or turn it off.   [Open satchel]
   ✘ Auto-Sell paused: the economy is busy. It retries on its own.
```
Keys in `messages/tools.yml` under `autosell.*`; a `ToolsMessagesTest` extension checks every key exists and every message follows the format rules (prefix only on direct replies).

### 10.6 Other balance changes (P8)

- `tools/economy_model.py`: new tables `area_tools` (levels, upgrades, currency flows), `farm` (crop pacing, prices, island vs farm), changed `tool_levels`; `--check` must stay green.
- `sell.yml`: add `farm` section; lower vanilla crop values; remove forest items (kept one release).
- `shop.yml`: seed/crop prices (10.3); a build test (`BuySellInvariantTest`) still enforces price > max sell value.
- Island income share stays near 35%: a monitoring row (M3.8 dashboard) for island crops per hour.

---

## 11. Order of work (item 6)

Sizes: S under half a day, M about a day, L several days. Lanes: A = data/start flow, B = tools/NPC/economy, C = Farm/engine. Max 3 agents, each its own branch `q/<item>`.

| # | Phase | Size | Lane | Depends | What ships | Tests | Manual checklist (Java + Bedrock) |
|---|---|---|---|---|---|---|---|
| P0 | Decisions and spikes | S | A | - | Owner answers (section 13); `OPEN_QUESTIONS`; spike notes: profile snapshot vs PDC marker (3.2), revert-flash plan (8.4), `Currency` extension cost | - | - |
| P1 | Names and closed forest | M | A | P0 | Text pass "Depths" -> areas, `/areas` `/mine` `/farm`, `/dungeon` = Dungeon, wing sign text, forest gate closed (no teleport), hidden tabs | `NoDepthsWordTest`, `DungeonMenuTest` (no forest tab), command tab completion tests, messages tests | `/areas` shows 3 areas; no "Depths" in menus, help, scoreboard; forest gone from `/dungeon`; Bedrock menus fit |
| P2 | Migration | L | A | P1 | `InventoryMerge`, one inventory, island tool retire + refund, forest payout, `/skyblock migrate areas`, `migration_marks`, flag `tools-areas.migration.enabled` | Merge tests (dupes, overflow, crash sim, idempotent), refund math test, payout test, store tests (H2) | Old account with items on both sides logs in: one inventory, nothing missing; boxed overflow; refunds in `/bal` and `/transactions`; relog twice = no change; switch profile |
| P3 | Start flow | M | A | P1 | `startflow` module: starter chest, bossbar, Jaden NPC + menu, `welcome_progress`, funnel counters, tutorial off, gates provider | `StarterSpec` (moved), `WelcomeProgress`, bossbar text, gate rules tests; messages tests | New account: island at once, chest (Pickaxe + Hoe), bossbar, walk to Jaden, menu, each Take me there works, bossbar gone; Bedrock chest + menu |
| P4 | Tool system v2 | L | B | P2 | `ToolKind` reduced, curve v2, upgrade tree (`Upgrades` reused), area currencies (`SOULS`, `BUSHELS`) + batcher, `AreaToolMenu`, NPC roles, replace-lost-tool, prestige v2, `/tool` update, `tools.yml` v2 + valueChanges | Curve, upgrades parse/cost/gates, prestige, currency batcher, menu state tests, cap sim, catalog defaults | Get pickaxe/sword, level, spend currency, tier-up, prestige on a staff-boosted tool, replace lost tool (old copy dead), sell loot at NPC |
| P5 | Farm | L | C | P1, P4 (tool XP hooks) | `Aspect.FARM`, palette promotion + `CounterSource`, `farm_progress`, FarmGuard, crop swap, 2 s respawn, farm drops (`farm_*`), Reach, builder brief + `/skyblock builds` for `farm_01` | `CounterSource` thresholds, unlock moment, token bucket, slot index with crops, zones.yml parse, drop table, guard cancel tests | Wheat everywhere, 2 s respawn, no holograms, unlock carrots after 500 (title + swap), another player still sees wheat, relog mid-respawn, rejoin keeps crop, chunk border re-send, Bedrock swap speed |
| P6 | Island hoe | M | B | P4 | `islandfarm` module (growth clock moved), merged Harvester/HoeFarming, island tasks rewritten, island XP factors, Auto-Sell, Seed Saver, messages | Task counting, Reach geometry, auto-sell batch/failure, message format tests | Island: hoe harvest and replant, seeds back, island upgrade tasks count, auto-sell at Lv 3 with correct messages, nothing sold twice |
| P7 | `/shop` hub | M | B | P4 (sell menus) | Hub screen, sell row, area-loot menu, `/shop sell ...`, tab cap 12, seed prices, shop tests | Shop menu layout tests, `BuySellInvariantTest`, tab completion tests | `/shop` shows buy + sell rows; sell area loot; Bedrock without right-click; seed price |
| P8 | Balance | M | B | P4-P7 | `economy_model.py` tables, sell.yml/shop.yml values, valueChanges, documentation | `--check`, price tests | Spot-check earnings per hour in each area on the test server |
| P9 | Cleanup | M | A | one release after P8 | Delete `tutorial`, `islandtools` (rest), forest code, `world_inventory`/`tool_stash`/island tables (after 30 days), docs, COMMANDS index | full `./gradlew build` | Regression pass |

Total: about 6 to 8 weeks of agent time with three lanes; P2 is the gate for everything player-visible, and P5 needs the builder (Farm build) in parallel from P1 on.
**Release order:** P1+P2 together (names + migration), then P3, P4, P5/P6/P7 in parallel branches, P8 with the last one, P9 later.
Rollback: every phase is behind `tools-areas.*` flags; P2 keeps old tables 30 days.

---

## 12. Risks and edge cases

| Risk | Handling |
|---|---|
| Merge dupes/loss | One-way flags, same-file marker, never read the live side's row, boxed overflow, dry-run, crash tests (3.2). |
| Refund abuse (shards are scarce) | Recompute from config, caps, alert on large refunds, once per profile, idempotent. |
| Players with several tools of one kind | Main tool + legacy tools; nothing deleted. |
| Revert flash on Farm break | 8.4: one tick, spike to measure, fallback documented. |
| Island crop scaling | Price 25%, farmland count bounded by island size, seed price, dashboard row. |
| Auto-Sell + Fortune dupe-ish loops | Sells only what the harvest produced, batch idempotency, ledger rows, `max-per-batch`. |
| Tutorial gates removed = alt/boost holes | Q5; money-moving gates stay (tied to Jaden), AH playtime rule untouched. |
| Many modules reference "Depths" | Text only; internal ids frozen; `NoDepthsWordTest`. |
| Currency enum change | Additive (two new values), `listed()`/`transferable()` set, economy.yml defaults; DB uses string keys. |
| Season wipe | New tables (`farm_progress`, tool upgrades) get `season_id` or are wiped by the season job like `tools`. |
| Builders | Farm and Crossroads builds block P5's final release; placeholders (`/depths generate` style) let P5 be tested without a build. |

---

## 13. Questions for the owner (item 7)

Each has the default the plan uses.

**Start and tools**
1. **Starter chest tools:** which tools? Default: Pickaxe and Hoe in the chest, Sword from the Dungeon NPC. Alternatives: only a Hoe; all three.
2. **Bound tools?** Default: tools are bound to the profile (not tradeable, replaced at the NPC), because tradeable levelled tools allow selling levelling. DESIGN 5.1 said tradeable. Keep tradeable?
3. **Area currencies:** (a) three currencies Crystals / Souls / Bushels (default) or one (Crystals) for all? (b) Names OK? (c) Should enchants cost the currency of the tool they go on (default yes)?
4. **Jaden:** stops after the first conversation (default) or keeps the bossbar going until you visited the Mine, Dungeon and Farm? Skin/name OK ("Jaden")? Where: replaces the Castle Courtyard guide NPC?
5. **Locks without a tutorial:** keep `pay`, `withdraw`, `trade`, `auction` closed until Jaden's first conversation (default), keep nothing locked, or add a 30 minute playtime gate?
6. **XP curve:** is "easy and progressively faster" = about 35 regular hours to Lv 50, with +2% XP per level (default)? Slower (2x) or faster?

**Migration**
7. Merging **both inventories into one** keeps everything and boxes overflow. OK? Anything else that should be removed from inventories (maps, torches from the Depths kit)?
8. **Island tool refund:** 100% of recomputed shards and money (default); split across island members or owner only (default: split)? Add the Veteran start level for Hoe/Pickaxe (default no)?
9. **Foraging skill:** keep, fed by island logs (default), or retire?
10. **Legacy tools** (second pickaxe, blueprint tools with abilities): keep working as legacy (default), or convert to blueprint-ability upgrades on the main tool?
11. **Prestige:** max 10 stars per season, reset level/XP only (default). OK? Repeatable more than once per season (the old rule was once per tool per season)?
12. **Satchel:** keep name "Satchel" and `/satchel` (default), rename to "Loot Bag"? Remove the satchel item from the hotbar (default yes)?
13. **Auto-Sell:** Hoe Lv 3 free (your request); Pickaxe and Sword Lv 10 as a 2,000 currency upgrade (default)? Or give all three Lv 3 free?

**Areas**
14. **Hoe storage** (island hoe had a 256+ item storage): drop it (default; Auto-Sell and satchel replace it), or keep?
15. **NPC placement:** tool NPCs at the Crossroads gateways (default) or inside each area's entrance zone?
16. **Forge Quarter** at spawn loses the Forge, Blacksmith and Quartermaster. Keep the district as the Enchanter / Wardrobe / Blueprint hall (default) or ask the builders to redo it?
17. **Hoe tiers and names:** 5 tiers wooden to diamond (default)? Pickaxe/sword tier ladders unchanged?
18. **Farm extras:** seed packs, key fragments and books on the Farm as listed in 5.3? Any other rare finds?
19. **Farm unlock thresholds:** wheat -> carrot after 500, carrot -> potato 2,500, potato -> beetroot 8,000, beetroot -> nether wart 20,000 (each counts the previous crop, default). Other numbers? Count total crops instead of the previous crop?
20. **Crop order after carrot:** potato, beetroot, nether wart (default). Add sweet berries, torchflower or pitcher? Can players pick a lower unlocked crop at the NPC (default yes)?
21. **Vanilla crop sell prices:** lower to 25% of the Farm item (default, wheat $10 -> $1.4) or raise the Farm instead (breaks parity)? This changes island income.
22. **Seed prices:** wheat seeds $1,500, beetroot seeds $4,000, carrot/potato $6,000 (default) with a daily escalation if players still spam them (default off)? Or a daily purchase limit?
23. **Farm vs island split:** Farm crops pay 4x per item; island crops give island task progress (default). Should island crops also give Bushels at a small rate (default no)?
24. **Island tasks** are rewritten to island activities (crops, ores, kills, logs). Keep upgrade tasks as they are today, or simplify?
25. **Farm respawn:** 2 s (yours). OK to let Quick Regrow lower it to 1 s, and keep the optional sprout animation off?
26. **Dungeon and Mine tiers** stay zone-based (default). Do you want the Mine to also become one field with per-player ore (the palette) later?

**Shop and economy**
27. **`/shop` hub layout** (buy row, sell row, area-loot sell): OK? Raise the tab cap to 12?
28. **Shop spawner tab** and Mob Drops tab stay as they are inside the hub (default).
29. **Auto-Sell messages:** the action bar line plus a 30 s chat summary (default), or only the action bar?
30. **Fishing rod:** leave dormant (default) until the fishing area is planned.

**Naming**
31. "The Gate" -> **The Crossroads**; areas called **Mine, Dungeon, Farm**; hub command `/areas`. OK?
32. Keep the world, module and permission ids as `depths` internally (default) to avoid a risky rename?

### Top questions (the ones that block building)
1. Which tools go in the starter chest (Q1) and are tools bound (Q2).
2. One upgrade currency or three (Q3).
3. XP speed for level 50 (Q6).
4. Refund policy for island tools and the legacy-tool rule (Q8, Q10).
5. Vanilla crop prices vs Farm prices (Q21) and the seed prices (Q22).
6. Farm unlock numbers and crop order (Q19, Q20).
7. Which gates stay without the tutorial (Q5).

---

## Owner decisions (3 Nov 2026, 23:05-23:17) - these override the defaults above

1. **Personal tools only.** The island tools (`modules/islandtools`, one record per profile) are *the* tools. Their perks get
   better on an island: island upgrades boost the tool perks of every member (the shared island levels already do this).
   There are no Depths tools any more (`modules/tools` Forge/Depths tools are retired; code may be reused).
2. **The Depths and the dungeon are removed.** Players get `/farm` and `/mine` (one world `grind`, essentials `GrindAreas`).
   Depths code is reused where it fits (per-player blocks, drops), the rest is deleted later; every removal is noted here.
   Done in this step: menu Play button and Travel place point to the Farm and the Mine; build portals with target `depths`
   lead to the `grind` world (`worlds.yml portals.world-targets`); `/dungeon` removed; the Depths tier map is `/tiers` until
   the module is deleted.
3. **Tools work the same at /farm and /mine** as on the island (level up, perks, shards): `islandtools.yml tool-worlds`.
4. **Per-player crops at /farm**: the real blocks are wheat; each player sees and harvests their own unlocked crop
   (wheat -> carrots at 500 harvested -> potatoes at 2,500 -> beetroot at 8,000, `GrindAreas.CROPS`, counter in the player
   data). The same for ores at /mine is next.
5. **Joining an island**: inventory and ender chest emptied (backup), money topped up to $1,000
   (`islands.yml lifecycle.profile-reset.on-join`, `join-money`).
6. **GUIs stay vanilla** (no glass panes, no pack frame), simple and clean; icons revamped; lore in the PNG format
   (`| Header` sections, `» CLICK to ...` last).

Done since: per-player ores at /mine (`GrindAreas.ORE_TIERS`: coal and copper, iron at 300 mined, gold and redstone at
1,500, lapis and diamond at 5,000, emerald at 15,000; real blocks stone, view and drop per player); hoe rework (Auto-Sell
20 shards, extra drops / shard finder / storage 10 levels with small steps, islandtools config v5); plain vanilla icons
in /menu and Travel (Travel: Farm instead of Depths Gate, Mine instead of Forge Quarter). The shop grid already fills
full rows of 7 (no zigzag) since the v5 batch; the shop was invisible on the live server because the module failed to
start (fixed: argument name).

Still to do, in order:
higher but balanced economy numbers; Depths module deletion (after the inventory merge ran for everyone); GUI revamp
(/menu icons, shop layout incl. the zigzag, island upgrades and /is masteries in the lore format).

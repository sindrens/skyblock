# Stacking (`modules/stacking`)

Stacked mobs, dropped items and spawner blocks in the worlds of `stacking.yml → worlds` (default `islands`; outposts later; never The Depths, warzone, spawn or boss worlds), island entity limits, and crops that grow without light. Owner decisions 2026-10-30: Stacking S1–S28 (round 2) and SK1–SK10 (round 3). Stacking can't be turned off (S11). Settings: `stacking.yml`; texts: `messages/stacking.yml`; spawner data: table `stacking_spawners`.

| Command | What it does | Permission | Status |
|---|---|---|---|
| `/spawner give <player> <type> [amount]` | Staff: give an online player spawner items of a type from the tier lists (1–2,304, default 1; stacks of 64, the rest drops at their feet). Audited (`staff`, `spawner_give`, with type and tier); the player is told | `skyblock.spawner.give` (admin + owner) | ✅ |

Staff button (no command): `/inspect <player>` → **Frozen spawners** restores frozen stacked spawners to the placer's /box (R3-13b, `skyblock.spawner.restore`, admin + owner; see Spawners below).

No player commands: everything happens in the world and in the spawner menu (sneak-right-click a spawner, or right-click it with an empty hand). Help topic: `/help spawners`.

## Mobs (S1–S13, SK1)
- Spawner mobs and natural mobs (spawn reasons in `mobs.spawn-reasons`; plugin spawns never) of the same kind within 5 blocks join one stack of up to 1,024. Kind = type, spawner or natural, baby or adult, sheep color and sheared, slime size, mooshroom variant, charged creeper.
- Name above the stack: `<white>Zombie <gold>x45` (`mob-name`). Single mobs keep no name.
- Max 8 stacks of the same type per chunk; more spawns join a stack in the chunk or are cancelled.
- A lethal hit kills one mob; a `StackKillProvider` (common, for the enchant module: e.g. Slaughter) can kill more per hit. Fall, lava, void and `/kill` damage (`mobs.whole-stack-damage`) kill the whole stack. Drops and XP are multiplied by the number killed: the loot table is rolled `mobs.loot-samples` (8) times and scaled, and the drops become one stacked item per kind. Equipment is never multiplied.
- Spawner mobs have no AI, vanilla loot only (SK9), and never despawn; stacks of 2+ never despawn and can't pick up items. Evoker totems and shulker shells from spawner mobs only with `spawners.rare-drops` (2% / 5% per mob, SK10).
- Never stack: named (name tag), tamed, leashed, saddled, riding or ridden mobs, villagers, wandering traders, zombie villagers, allays, bosses (wither, dragon, warden, elder guardian), mobs with the scoreboard tags `skyblock_no_stack` or `skyblock_boss`, and the types in `mobs.never-stack`.
- On a stack: name tags, leads, taming, saddles and riding are refused with a short message. Shearing a sheep stack shears all of them (wool × stack).
- Breeding (S10): feeding a stack with its breeding food breeds one pair per food item (15 cows + 5 wheat = 5 babies); the babies join the stack, the parents used wait `mobs.breed-cooldown-seconds` (300), XP 1–7 per baby.
- A stack that transforms (lightning, drowning, curing) stays a stack of the same size.

## Items (S14–S19)
- Dropped items of the same kind within 3 blocks merge into one entity whose count is a long (no max), shown as `Diamond x1,234` (from 2 items, `items.name-min-count`).
- Players pick up as much as fits, the rest stays; hoppers take up to 64 at once; mobs never pick up a stack.
- Items disappear about 10 minutes after the last item joined (`items.despawn-minutes`).
- Unique custom items (with a unique id from `core.items`, e.g. money notes, tools) never stack; display items of other plugins are never touched.

## Spawners (S20–S28, SK3–SK8)
- Spawner items are custom items `spawner_1`..`spawner_5` (one per tier, footer shows the tier rarity) with the mob type in their data. Tiers from `spawners.tier-1..5`: 1 pig, cow, chicken, sheep, rabbit; 2 zombie, skeleton, spider, cave spider, slime, squid; 3 creeper, witch, drowned, husk, stray, magma cube; 4 blaze, enderman, guardian, piglin, zombified piglin, hoglin, glow squid; 5 iron golem, wither skeleton, shulker, evoker, ravager, breeze. Villager, warden, wither, ender dragon, elder guardian and allay are refused even if listed (SK8).
- Placing works only on an island. One block holds 8 spawners (`spawners.stack-start`), up to 512 with the Spawner Stack Limit mastery (`IslandService#spawnerStackLimit`).
- Add: right-click the block with a spawner of the same type, or the menu (Add 1 / Add all). Take: break = one spawner, sneak-break = all (no silk touch); or the menu (Take 1 / Take all). The block disappears at 0. Spawn eggs can't change a spawner.
- Output: every 10–40 s each spawner adds `spawners.mobs-per-spawner` (1, owner answer 641) mobs × the stack × the Spawner Rate mastery (`IslandService#spawnerRateMultiplier`), straight into stacks next to the spawner. Light, space and blocks are ignored (S25). Runs while a player is within 16 blocks, or while the island has fuel (`IslandService#hasIslandFuel`, false until Island Fuel exists) and no member is online. Vanilla spawning is off for these blocks.
- Menu: type, tier, stack and limit, mobs per minute, next spawn, status, Add 1/Add all/Take 1/Take all, Upgrades (masteries, coming soon).
- Who may use spawners: the island role permission `spawners` (islands module) decides; explosions never break stacked spawners.
- Data: the database row is the truth (conditional updates, items handed out only after the row changed); every change is audited (`island`: `spawner_place`, `spawner_add`, `spawner_remove`, `spawner_frozen`).
- Crash safety (SECFIX1 F-01b): adding spawners saves the player file (without the items) before the stack grows in the database; a placement writes its row one tick later, after the server used up the item and the player file was saved (a placement another plugin cancelled writes nothing). A crash can lose a spawner at worst, never duplicate one.
- Lost blocks (SECFIX1 F-07, R3-13): a stacked spawner whose block is gone (crash before the chunk was saved, island reset, world edit) is **frozen, not deleted**: the row moves in one transaction to `stacking_spawners_frozen` (V002, state `frozen`, with type, amount, island, who placed it, reason). It never spawns and can't be taken (no dupe); the console warns and the audit row `spawner_frozen` has the frozen id. Staff give them back with the **Frozen spawners** button in `/inspect <player>` (R3-13b, `skyblock.spawner.restore`, admin + owner): it lists the frozen spawners that player placed (oldest first, up to `spawners.frozen-menu-limit`, 200); click one and confirm, and the row is marked `restored` exactly once (conditional update, a double click can't give twice) and the spawners go to the placer's `/box` (source `spawner-restore`, offline-safe). Audited as `staff/spawner_restore`. No new command.
- Disband and reset (R3-13a): when an island is disbanded or reset on purpose (`IslandRemovedEvent` reasons `disband`/`reset`), its spawners are **cleared, not frozen**: they leave memory at once and one transaction deletes the island's `stacking_spawners` rows and marks its waiting frozen rows `cleared`. Audited as `island/spawners_cleared` (blocks, spawners per type). A staff delete (`delete`) keeps the old behaviour (frozen when the block is gone), like the island bank.
- Taking spawners out is refused while the player's /box is at its hard cap (SECFIX1 F-09, `box.yml → cap.hard-max-entries`).

## Island entity limits (SK2)
Armor stands 20, item frames 60 (glow frames too), minecarts 20, boats 10 (rafts, chest boats), villagers 20, display entities 30, each +`IslandService#entityLimitBonusPercent` (the owner's upgrade: +50%). Checked when one is placed (message) or spawned (dispensers, breeding, plugins: cancelled quietly). Counts cover the island's loaded chunks.

## Crops (S25)
Crops on islands grow without light: planted crops (`crops.types`) are remembered per chunk (chunk data); every 30 s each dark crop has a 15% chance to grow a stage (a `BlockGrowEvent` is fired first); ripe melon/pumpkin stems grow their fruit. Lit crops grow the vanilla way.

## For other modules
- `core.services().optional(StackingService.class)` (`common/stacking`): `mobCount`, `itemCount`, `spawnerCount`, `fromStackedSpawner`; tag `StackingService.NO_STACK_TAG`.
- `StackKillProvider` (`common/stacking`): register one to kill more mobs per hit.
- `IslandService` hooks (defaults until masteries/fuel exist): `spawnerStackLimit`, `spawnerRateMultiplier`, `hasIslandFuel`, `entityLimitBonusPercent`.
- Skills: mobs from stacked spawners count as spawner mobs (`skills.yml → combat.spawner-mobs-give-xp`).
- Stacked spawners spawn their mobs themselves with spawn reason `CUSTOM` (no vanilla `SPAWNER` event). Island flags such as "mob spawning off" must only block `NATURAL` spawns, never these (check with `StackingService#fromStackedSpawner` if needed).

## Performance
No per-tick entity scans: mobs merge on spawn using a chunk-indexed cache (entity add/remove events), walking mobs are re-checked by a budgeted task (`mobs.natural-merge-*`), items merge on spawn and through vanilla's own merge event, spawners sit in a priority queue by next spawn tick (`spawners.due-per-tick`), crops are spread over ticks (`crops.per-tick`). Main-thread time is measured; a warning is logged when it averages above `perf.warn-ms-per-second` (5 ms/s) over a minute.

Permission definitions: `modules/stacking/src/main/resources/permissions/stacking.yml`. Rank grants: `server/permissions/stacking.txt`.

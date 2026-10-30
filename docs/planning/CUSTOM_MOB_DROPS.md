# Custom mob drops

**Status:** needs owner input

## What you already decided
- **693:** "villagers are not gonna be a thing used". Custom drops for mobs: **every mob killed drops 1** custom drop normally, **more with looting**. Emeralds become sellable, but trading for them won't be worth it.
- **695:** vanilla mob drops sell from any farm. Custom materials have their own sell value and other uses; some may sell for other currencies.
- **641 / 642:** spawners spawn 1 mob per cycle; each extra spawner costs 10% more.
- **772 / 776:** Depths combat kills drop custom items used in Forge recipes; mine and forest recipes use Depths drops.
- **409:** mob griefing on in island worlds so villager farms work (made before 693).
- **SK (round 3):** spawners never for villagers, warden, wither, dragon, elder guardian, allay.
- **REVIEW3 R3-21:** no custom drops from spawner mobs (spawners are already the biggest money risk).

## What exists in the game now
- **Depths combat mobs** (`mobs.yml`): custom drops per mob, crystals, XP. Phase 8 M8.1 adds shared drop tables, elites and a drop budget test.
- **Island and spawner mobs:** vanilla loot, sold with `/sell` (`sell.yml → mob-loot`, prices by spawner tier).
- **Emeralds:** `sellable: false` (because villagers turn free items into emeralds).
- Villager trading works as in vanilla.

## The problem / what to decide
1. Which mobs get custom drops: Depths, natural island mobs, spawner mobs, bosses?
2. What are the drops for, so they're not just more money?
3. What happens to villagers and emeralds?
4. Spawner mobs with an extra sellable drop would blow up island income (R3-21).

## Our proposal
**Where custom drops come from:**
| Source | Custom drop |
|---|---|
| Depths combat mobs | yes, 1 per kill (+1 per Looting/Plunder level), as today |
| Natural mobs on islands (night, dark spots) | yes, 1 per kill (+ Looting) |
| Spawner mobs | **no** custom drop; vanilla loot only (R3-21). The Mob Loot upgrade raises vanilla loot |
| Bosses and mini-bosses | own tables (Phase 8) |

**One custom drop per mob type**, for example: Zombie → Rotten Sinew, Skeleton → Bone Shard, Spider → Silk Gland, Creeper → Blast Powder.
- **Uses:** Forge recipes, enchant upgrades, summon sigils, island fuel cells, quests and collections.
- **Sell value:** low money value, below their crafting value. A few rare ones (elite drops) sell for shards.
- All in `mobs.yml` / `drops.yml`, so you can change every drop.

**Villagers and emeralds:**
- Turn villager trading **off** (villagers can exist as decoration).
- Emeralds become sellable from island generators.
- Mob griefing can stay on for other farms.

## Questions for you
1. Custom drops from Depths mobs and natural island mobs, 1 per kill plus 1 per Looting level? **Suggested: Yes.**
2. No custom drops from spawner mobs (R3-21)? **Suggested: Yes.**
3. One custom drop type per mob type? **Suggested: Yes.**
4. Use custom drops in Forge, enchants, sigils, fuel, quests and collections? **Suggested: Yes.**
5. Sell value below crafting value, a few rare ones sell for shards? **Suggested: Yes.**
6. Turn villager trading off? **Suggested: Yes.**
7. Make emeralds sellable once trading is off? **Suggested: Yes.**
8. Does the custom Plunder enchant count like Looting for extra drops? **Suggested: Yes.**
9. We write the first list of drop names and uses for you to approve? **Suggested: Yes.**

## Your notes (write here)


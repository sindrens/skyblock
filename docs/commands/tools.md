# Tools module (`modules/tools`): M4.2

Starter kit (Quartermaster), the Forge (Blacksmith), living tools (levels 1-50, perk slots, prestige), blueprints and skins (DESIGN §5.1, PHASE-4 M4.2). Settings and catalog (tiers, abilities, blueprints, skins, island XP, smelt results): `tools.yml`; texts: `messages/tools.yml`; data: tables `tools`, `skins_owned`, `blueprints_found`, `starter_claims`.

| Command | Description | Permission | Status |
|---|---|---|---|
| `/forge [type]` | The Forge (Blacksmith placeholder until NPCs exist): tabs per tool type (Hoes only while farming is on, `crops.yml → enabled`; Rods only once the Fishing skill opens, M7.8), every tier with owned ✔ / craftable / locked ✘ and progress (tool, skill level, materials, money). Forging the next tier keeps level, perks and stats. Also opens **Starter kit** (Quartermaster: free pickaxe, axe, hoe, sword, rod; only missing pieces; every 24 h) and **Blueprints** (craft special tools) | `skyblock.tools.forge` (everyone) | ✅ |
| `/tool` | Tool menu of the custom tool in your hand (same as shift + right-click, for Bedrock): level and XP, stats, perk slots (empty / locked with level / prestige slot), history, skin (Skins menu), prestige, open Forge | `skyblock.tools.menu` (everyone) | ✅ |
| `/tool give <player> <tool> [tier]` | Staff: give an online player a new custom tool (`tool` = pickaxe, axe, sword, hoe, rod; `tier` = tier id like `iron` or number, default 1). Audited (`staff`, `tool_give`) | `skyblock.tools.admin.give` (admin + owner) | ✅ |
| `/blueprint give <player> <blueprint>` | Staff: give an online player a blueprint item. Audited (`staff`, `blueprint_give`); recorded in `blueprints_found` | `skyblock.tools.admin.blueprint` (admin + owner) | ✅ |
| `/skin give <player> <skin>` | Staff: add a skin to a player's collection (offline players too; kept across seasons). Audited (`staff`, `skin_give`) | `skyblock.tools.admin.skin` (admin + owner) | ✅ |

Rules (defaults, all in `tools.yml`):
- No durability; tradeable (trade, auction house). Starter kit pieces (`tool_starter_*`, data key `phoenixskyblock:starter_kit`) are refused by the auction house; forging one turns it into a normal tool.
- XP per block/log in The Depths = the tier XP of the zone (`xp.tier-xp`, same as skill XP); kills = tier XP × `xp.kill-multiplier` (5); outside The Depths `xp.island-factor` (25%). Level curve: step `400 × 1.21^(L-1)` (ECONOMY_BALANCE §3.5), max level 50.
- Perk slots at Lv 5 / 15 / 30 / 45; prestige at Lv 50 (once per tool per season): Lv 1, keeps perks and stats, +1 slot (slot 5), a star.
- Break speed: real item attributes (`mining_efficiency` per tier + `block_break_speed` +1% per level), works on Bedrock. Swords: attack damage per tier.
- Custom tools are kept on death; they can't go into anvils, smithing tables, grindstones, crafting grids or enchanting tables.
- **Harvester hoe (PHASE-7 M7.4, only while farming is on):** right-click a crop with a custom hoe on your own island (stored member, survival, not sneaking) → the ripe crops in a square around it are harvested: 3×3 from Lv 1, 5×5 at Lv 15, 7×7 at Lv 30 (`harvester.size-levels`, max `harvester.max-size` 9 with enchant rings). Wheat, carrots, potatoes, beetroots, nether wart and cocoa are replanted at age 0 from one of their own seeds (drops first, then the inventory) and only if your Farming level may plant them; sugar cane and cactus are cut above the bottom block; melons and pumpkins only when an attached stem points at them (`harvester.replant / columns / fruits`). Each block is broken as if by hand (`Player#breakBlock`): protection, island roles, Farming XP, bone-meal rule, Harvest Yield, quests and stats run once per crop. Cooldown `harvester.cooldown-ms` (0.4 s). Action bar `Reaped N crops (5x5)`.
- **Hoe progress:** tool XP only from the production module's `IslandCropHarvestEvent` (ripe, with a hoe, no bone meal, members on their own island): crop tier XP (`xp.tier-xp`) × crops × `xp.crop-factor` (1); history counter `crops`. Extra crop drops +`fortune.per-tier-percent` (5%) per hoe tier on those harvests and on ripe sweet berries picked with the hoe (seeds excluded: `harvester.no-bonus-items`; max `harvester.max-drop-multiplier` ×3). Hoe tiers: 11 (Farmer's Hoe … Verdant Crown), forged with money F(t) and M(t) crops of the tier before, gated by the Farming level of the crop tier. Rod tiers: 12, fish of the tier before (item ids come with M7.8), gated by Fishing level, hidden until fishing exists.
- For the hoe enchants (E6): service `HarvesterHook` (`extraRings`, `replantStages`) and event `HarvesterSwingEvent` (player, hoe, centre, size, crops broken) in `common/tools`.
- Abilities (blueprint tools): `auto_smelt`, `swift`, `scholar`, `lifesteal`.
- NPC spots for later: `npc.quartermaster`, `npc.blacksmith`.

For other modules: `core.services().optional(ToolService.class)` (`common/tools`): `read(item)` → `ToolInfo` (kind, tier, level, prestige, unlocked slots, perks, skin, ability, stats), `slotLevels()`, `maxEnchantLevel(tool, max)` (ENCHANTS §2.4 gate), `setPerks(holder, toolId, perks, actor, reason)`, `savePerks(connection, toolId, perks)` for the enchants module's own transaction, `perkNames(namer)`. Event `ToolLevelUpEvent`. Depths XP and blueprint drops come from the depths module's `ResourceBreakEvent` (only `DepthsHooks` touches depths; `compileOnly`).

Audit: `item` category: `tool_level_up`, `tool_forge`, `tool_forge_conflict`, `tool_prestige`, `tool_skin`, `tool_perks`, `starter_kit`, `blueprint_get`, `blueprint_craft`, `blueprint_reused`; economy ledger reasons `forge`, `forge_blueprint`, `forge_refund`.

Permission definitions: `modules/tools/src/main/resources/permissions/tools.yml`. Rank grants: `server/permissions/tools.txt`.

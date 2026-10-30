package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of {@code block-values.yml}: what one placed block is worth for the island level (PHASE-5 M5.2, owner
 * P5-33 default). Order: {@code values} (exact worth) → {@code zero} → {@code storage-blocks} (sell price based) →
 * shop price x {@code shop-percent}. Blocks the shop does not sell are worth 0 (staff items, bedrock, barriers).
 * Spawners: per spawner in a stack, by tier. Catalog lists only; plain keys stay outside the lists.
 */
public final class BlockValuesConfig extends ModuleConfig {

    @Override
    public String fileName() {
        return "block-values.yml";
    }

    @Override
    public String header() {
        return """
                Block worth of the island level (Phase 5, M5.2). Only placed blocks inside the island border count; items in
                chests never count. Block worth gives 0.5 x square root(worth / $1,000) points, at most 20% of the level.
                Worth of one block, first match wins:
                  values          exact worth, as 'block: money' (e.g. 'beacon: 150000')
                  zero            cheap utility blocks worth 0 (hoppers, cobble, dirt, glass ...)
                  storage-blocks  'block: item amount' = amount x the item's /sell base price x storage-percent
                  otherwise       the /shop price x shop-percent (blocks the shop does not sell are worth 0)
                Spawners count per spawner in a stack: spawner-tier-values (tier from stacking.yml spawners.tier-N).
                After changes: /skyblock reload progression (new values apply to new blocks; the nightly rescan and the
                Recalculate button in /is level count everything again).""";
    }

    @Setting(path = "shop-percent", desc = "Worth of a block the shop sells = this percent of its /shop price.", min = 0, max = 1000, level = Setting.Level.OWNER)
    public double shopPercent = 50;

    @Setting(path = "storage-percent", desc = "Worth of a storage block = amount x the item's /sell base price x this percent.", min = 0, max = 1000, level = Setting.Level.OWNER)
    public double storagePercent = 50;

    @Setting(path = "values", desc = "Exact worth of one block, as 'block: money'. Wins over everything else.", level = Setting.Level.OWNER)
    public List<String> values = List.of();

    @Setting(path = "zero", desc = "Blocks worth 0 (cheap utility and filler blocks).", level = Setting.Level.OWNER)
    public List<String> zero = List.of("hopper", "chest", "trapped_chest", "barrel", "cobblestone", "cobbled_deepslate", "stone",
            "dirt", "coarse_dirt", "grass_block", "sand", "red_sand", "gravel", "netherrack", "glass", "glass_pane", "torch",
            "wall_torch", "ladder", "scaffolding", "crafting_table", "furnace", "oak_planks", "spruce_planks", "birch_planks");

    @Setting(path = "storage-blocks", desc = "Storage blocks worth their content, as 'block: item amount' (uses the item's /sell base price).", level = Setting.Level.OWNER)
    public List<String> storageBlocks = List.of("coal_block: coal 9", "iron_block: iron_ingot 9", "gold_block: gold_ingot 9",
            "copper_block: copper_ingot 9", "lapis_block: lapis_lazuli 9", "redstone_block: redstone 9",
            "diamond_block: diamond 9", "emerald_block: emerald 9", "netherite_block: netherite_ingot 9",
            "raw_iron_block: raw_iron 9", "raw_gold_block: raw_gold 9", "raw_copper_block: raw_copper 9",
            "amethyst_block: amethyst_shard 4", "quartz_block: quartz 4");

    @Setting(path = "spawner-tier-values", desc = "Worth of ONE spawner by tier, as 'tier: money' (default 50% of the first shop price of the tier, balance sheet 4.3).", level = Setting.Level.OWNER)
    public List<String> spawnerTierValues = List.of("1: 58500", "2: 106500", "3: 193500", "4: 353000", "5: 1050000");
}

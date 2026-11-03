package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/** Settings of the island tools ({@code islandtools.yml}); texts in messages/islandtools.yml. */
public final class IslandToolsConfig extends ModuleConfig {

    @Setting(path = "enabled", desc = "Island tools on (owner D2/D12: every profile gets the wooden island set on first join).")
    public boolean enabled = true;

    @Setting(path = "tier-costs", desc = "Shards for each tier-up, wood→stone, stone→iron, iron→gold, gold→diamond, diamond→netherite (owner 3 Nov 2026: island tools are paid with shards; plus tier-money).")
    public List<Integer> tierCosts = List.of(5, 150, 500, 1500, 4000);

    @Setting(path = "tier-money", desc = "Money for each tier-up (same order as tier-costs). The first one is cheap so stone comes fast.")
    public List<Integer> tierMoney = List.of(100, 5000, 25000, 100000, 400000);

    /** The v1 default (crystals). */
    static final List<Integer> TIER_COSTS_V1 = List.of(500, 2500, 8000, 25000, 75000);

    @Setting(path = "tasks.per-level", desc = "Owner 3 Nov 2026: island actions with that tool needed for each upgrade level (level 1, 2, 3 ...; the last one repeats). Pickaxe: ores and stone mined, axe: logs chopped, hoe: ripe crops harvested, sword: mobs killed. Any member counts; used up by the upgrade.")
    public List<Integer> taskPerLevel = List.of(50, 200, 600, 1500, 4000);

    @Setting(path = "shards.chance-pickaxe", desc = "Percent chance of 1 shard per relevant block mined with the island pickaxe.", min = 0, max = 100)
    public double shardPickaxe = 2;

    @Setting(path = "shards.chance-axe", desc = "Percent chance of 1 shard per log chopped with the island axe.", min = 0, max = 100)
    public double shardAxe = 2;

    @Setting(path = "shards.chance-hoe", desc = "Percent chance of 1 shard per ripe crop harvested with the island hoe.", min = 0, max = 100)
    public double shardHoe = 1;

    @Setting(path = "shards.chance-sword", desc = "Percent chance of 1 shard per mob killed with the island sword.", min = 0, max = 100)
    public double shardSword = 4;

    @Setting(path = "shards.finder-per-level", desc = "Extra shard chance (percent points) per level of the Shard Finder upgrade.", min = 0, max = 20)
    public double shardFinderPerLevel = 0.5;

    @Setting(path = "auto-sell.price-percent", desc = "Owner 3 Nov 2026: Auto-Sell sells drops as you break them at this percent of the sell price (the other selling bonuses apply too).", min = 1, max = 100)
    public double autoSellPercent = 90;

    @Setting(path = "auto-sell.batch-seconds", desc = "Auto-sold drops are paid in one sale this often.", min = 1, max = 60)
    public int autoSellSeconds = 3;

    @Setting(path = "shards.payout-seconds", desc = "Found shards are paid in one batch this often (and shown on the action bar).", min = 1, max = 60)
    public int shardPayoutSeconds = 3;

    @Setting(path = "confirm-above", desc = "T8: a confirm screen for an upgrade or tier-up costing at least this many crystals (0 = never).", min = 0, max = 100_000_000)
    public long confirmAbove = 5000;

    @Setting(path = "relevant.pickaxe", desc = "Owner 3 Nov 2026: blocks where the island pickaxe's extra drops, Smelter and shards work (block ids, * wildcard at the start or end). Blocks a player placed never count.")
    public List<String> relevantPickaxe = List.of("*_ore", "stone", "cobblestone", "deepslate", "cobbled_deepslate", "andesite",
            "diorite", "granite", "tuff", "calcite", "netherrack", "basalt", "blackstone", "end_stone", "obsidian", "ancient_debris",
            "amethyst_cluster", "raw_*_block");

    @Setting(path = "relevant.axe", desc = "Blocks where the island axe's extra drops and shards work.")
    public List<String> relevantAxe = List.of("*_log", "*_wood", "*_stem", "*_hyphae", "mangrove_roots", "bamboo_block", "*_mushroom_block");

    @Setting(path = "relevant.hoe", desc = "Blocks where the island hoe's extra drops and shards work (ripe crops only, always).")
    public List<String> relevantHoe = List.of("wheat", "carrots", "potatoes", "beetroots", "nether_wart", "cocoa", "melon", "pumpkin",
            "sweet_berry_bush", "sugar_cane", "cactus", "bamboo");

    @Setting(path = "placed.memory-hours", desc = "How long a block a player placed is remembered (so breaking it gives no bonus).", min = 1, max = 168)
    public int placedHours = 12;

    @Setting(path = "growth.enabled", desc = "Owner 3 Nov 2026: every crop on an island grows on the island's clock, one stage every few seconds (the regrow time below split over the crop's stages, faster with the crop-growth upgrade), with a puff and a soft sound.")
    public boolean growthEnabled = true;

    @Setting(path = "growth.step-floor-ms", desc = "Shortest time between two stages of one crop (milliseconds).", min = 100, max = 60000)
    public long growthStepFloorMillis = 500;

    @Setting(path = "growth.budget", desc = "Most crops grown per half second (the rest wait for the next run; keeps the tick time flat).", min = 10, max = 10000)
    public int growthBudget = 600;

    @Setting(path = "growth.sound", desc = "A soft sound when a crop near you grows (at most every 2 s per player).")
    public boolean growthSound = true;

    @Setting(path = "growth.label", desc = "Looking at a crop on an island shows its stage and the time to the next one (only you see it).")
    public boolean growthLabel = true;

    @Setting(path = "growth.label-range", desc = "How far you can look at a crop for its label (blocks).", min = 1, max = 10)
    public int growthLabelRange = 5;

    @Setting(path = "farmland.always-wet", desc = "Owner 3 Nov 2026: tilled dirt on islands is moist farmland at once and never dries, turns back to dirt or gets trampled (no water needed).")
    public boolean farmlandAlwaysWet = true;

    @Setting(path = "only-on-island", desc = "Owner 3 Nov 2026: island tools exist only in the island world; in every other world (spawn, the Depths, events) they are put away and come back in the same slot on the island. Off = only the put-away-worlds below.")
    public boolean onlyOnIsland = true;

    @Setting(path = "own-inventory-worlds", desc = "Owner 3 Nov 2026: worlds with their own inventory (tools.yml world-tools: the Depths). Island tools are never in that inventory, so nothing is put away or given back there.")
    public List<String> ownInventoryWorlds = List.of("depths");

    @Setting(path = "put-away-worlds", desc = "T1/D1: worlds where island tools are put away (and given back on the island in the same slot).")
    public List<String> putAwayWorlds = List.of("depths");

    @Setting(path = "upgrades", desc = "Upgrades per tool, 'kind:id:effect:max:base-cost:growth' (owner 3 Nov 2026: costs are shards). Effects: enchant=<id>, haste, magnet, "
            + "smelt, drops (EXTRA_DROPS bonus), timber, experience, lifesteal, row (hoe), storage (hoe), shards (Shard Finder), autosell (Auto-Sell). Cost of level n+1 = base x growth^n.")
    public List<String> upgrades = List.of(
            "pickaxe:efficiency:enchant=efficiency:5:10:2", "pickaxe:fortune:enchant=fortune:3:25:2.5", "pickaxe:haste:haste:2:40:3",
            "pickaxe:magnet:magnet:1:60:1", "pickaxe:smelter:smelt:5:15:2", "pickaxe:extra_drops:drops:5:20:2.2",
            "pickaxe:experience:experience:5:10:2", "pickaxe:shard_finder:shards:5:25:2.2", "pickaxe:auto_sell:autosell:1:300:1",
            "axe:efficiency:enchant=efficiency:5:10:2", "axe:extra_drops:drops:5:20:2.2", "axe:timber:timber:5:25:2.2",
            "axe:haste:haste:2:40:3", "axe:magnet:magnet:1:60:1", "axe:sharpness:enchant=sharpness:3:15:2.4",
            "axe:experience:experience:5:10:2", "axe:shard_finder:shards:5:25:2.2", "axe:auto_sell:autosell:1:300:1",
            "hoe:row:row:4:20:3", "hoe:storage:storage:5:15:2.5", "hoe:fortune:enchant=fortune:3:25:2.5",
            "hoe:extra_drops:drops:5:20:2.2", "hoe:magnet:magnet:1:60:1", "hoe:efficiency:enchant=efficiency:3:10:2",
            "hoe:haste:haste:2:40:3", "hoe:shard_finder:shards:5:25:2.2", "hoe:auto_sell:autosell:1:300:1",
            "sword:sharpness:enchant=sharpness:5:15:2.2", "sword:looting:enchant=looting:3:25:2.5",
            "sword:sweeping:enchant=sweeping_edge:3:20:2.4", "sword:fire_aspect:enchant=fire_aspect:2:30:2.8",
            "sword:knockback:enchant=knockback:2:10:2.5", "sword:extra_drops:drops:5:20:2.2", "sword:lifesteal:lifesteal:3:40:2.5",
            "sword:shard_finder:shards:5:25:2.2");

    /** The v1 default (crystal prices, no Shard Finder). */
    static final List<String> UPGRADES_V1 = List.of("pickaxe:efficiency:enchant=efficiency:5:150:2.2", "pickaxe:fortune:enchant=fortune:3:400:2.8", "pickaxe:haste:haste:2:600:3",
            "pickaxe:magnet:magnet:1:900:1", "pickaxe:smelter:smelt:5:250:2", "pickaxe:extra_drops:drops:5:300:2.2",
            "pickaxe:experience:experience:5:200:2",
            "axe:efficiency:enchant=efficiency:5:150:2.2", "axe:extra_drops:drops:5:300:2.2", "axe:timber:timber:5:400:2.3",
            "axe:haste:haste:2:600:3", "axe:magnet:magnet:1:900:1", "axe:sharpness:enchant=sharpness:3:250:2.4",
            "axe:experience:experience:5:200:2",
            "hoe:row:row:4:300:3", "hoe:storage:storage:5:250:2.5", "hoe:fortune:enchant=fortune:3:400:2.8",
            "hoe:extra_drops:drops:5:300:2.2", "hoe:magnet:magnet:1:900:1", "hoe:efficiency:enchant=efficiency:3:150:2.2",
            "hoe:haste:haste:2:600:3",
            "sword:sharpness:enchant=sharpness:5:200:2.2", "sword:looting:enchant=looting:3:400:2.8",
            "sword:sweeping:enchant=sweeping_edge:3:300:2.4", "sword:fire_aspect:enchant=fire_aspect:2:500:2.8",
            "sword:knockback:enchant=knockback:2:200:2.5", "sword:extra_drops:drops:5:300:2.2", "sword:lifesteal:lifesteal:3:600:2.5");

    @Setting(path = "effects.drops-per-level", desc = "EXTRA_DROPS bonus percent per level of a 'drops' upgrade (the family cap applies on top).", min = 0, max = 100)
    public double dropsPerLevel = 8;

    @Setting(path = "effects.smelt-per-level", desc = "Auto-smelt chance percent per level of 'smelt'.", min = 0, max = 100)
    public double smeltPerLevel = 20;

    @Setting(path = "effects.timber-per-level", desc = "Logs above the broken one that fall too, per level of 'timber'.", min = 0, max = 10)
    public int timberPerLevel = 2;

    @Setting(path = "effects.experience-per-level", desc = "Extra block XP percent per level of 'experience'.", min = 0, max = 500)
    public double experiencePerLevel = 25;

    @Setting(path = "effects.lifesteal-per-level", desc = "Percent of damage dealt healed per level of 'lifesteal'.", min = 0, max = 50)
    public double lifestealPerLevel = 3;

    @Setting(path = "hoe.row-lengths", desc = "T9: hoe row length for Row level 0, 1, 2 ... (default 1, 3, 5, 7, 9).")
    public List<Integer> rowLengths = List.of(1, 3, 5, 7, 9);

    @Setting(path = "hoe.storage-base", desc = "T9: hoe storage at Storage level 0 (items).", min = 1, max = 1_000_000)
    public long storageBase = 256;

    @Setting(path = "hoe.storage-factor", desc = "T9: storage multiplier per Storage level (about x3).", min = 1, max = 10)
    public double storageFactor = 3;

    @Setting(path = "regrow.enabled", desc = "T11: crops harvested with the island hoe replant themselves and are ripe again after the regrow time.")
    public boolean regrowEnabled = true;

    @Setting(path = "regrow.base-seconds", desc = "T11: regrow time without upgrades.", min = 5, max = 3600)
    public double regrowBase = 60;

    @Setting(path = "regrow.top-seconds", desc = "T11: regrow time at the top island upgrade.", min = 1, max = 3600)
    public double regrowTop = 15;

    @Setting(path = "regrow.full-at-percent", desc = "Island upgrade value (percent) that gives the top regrow time.", min = 1, max = 10_000)
    public double regrowFullAt = 120;

    @Setting(path = "regrow.min-seconds", desc = "G11: floor after the CROP_GROWTH perks.", min = 1, max = 3600)
    public double regrowFloor = 10;

    @Setting(path = "regrow.upgrade-effect", desc = "Island upgrade effect (upgrades.yml) that speeds up regrowing.")
    public String regrowEffect = "crop-growth";

    @Setting(path = "regrow.max-tracked", desc = "Most regrowing crops remembered at once (oldest dropped: they grow normally).", min = 100, max = 1_000_000)
    public int regrowMax = 50_000;

    @Setting(path = "burst.percent-per-growth", desc = "T12: Growth Burst chance (percent) per percent of the island's crop-growth upgrade.", min = 0, max = 10)
    public double burstPerGrowth = 0.05;

    @Setting(path = "burst.max-percent", desc = "T12: highest Growth Burst chance.", min = 0, max = 100)
    public double burstMax = 10;

    @Setting(path = "cue.near-blocks", desc = "T13: players this close see a strong ripe cue.", min = 1, max = 64)
    public double cueNear = 6;

    @Setting(path = "cue.far-blocks", desc = "T13: players this close see a subtle cue (one particle).", min = 1, max = 128)
    public double cueFar = 16;

    @Override
    public int version() {
        return 2;
    }

    /** v2 (owner 3 Nov 2026): shards instead of crystals, Shard Finder on every tool, cheaper first tier. */
    @Override
    public java.util.Map<Integer, java.util.Map<String, ValueChange>> valueChanges() {
        IslandToolsConfig d = new IslandToolsConfig();
        return java.util.Map.of(2, java.util.Map.of("tier-costs", ValueChange.of(TIER_COSTS_V1, d.tierCosts),
                "upgrades", ValueChange.of(UPGRADES_V1, d.upgrades)));
    }

    @Override
    public String fileName() {
        return "islandtools.yml";
    }

    @Override
    public String header() {
        return "Island tools (Complete Developer Spec D1-D12, T1-T13). Texts: messages/islandtools.yml. Reload: /skyblock reload islandtools";
    }
}

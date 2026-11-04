package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;
import java.util.Map;

/**
 * Settings of the island tools ({@code islandtools.yml}); texts in messages/islandtools.yml. Owner 4 Nov 2026: two tools
 * (harvester hoe and pickaxe), diamond from the start, owned by the island (level, XP, perks and prestige per island),
 * perks unlocking at tool levels, Farming and Mining Shards.
 */
public final class IslandToolsConfig extends ModuleConfig {

    @Setting(path = "enabled", desc = "Island tools on (every island member gets the island's hoe and pickaxe).")
    public boolean enabled = true;

    // ------------------------------------------------------------------ levels and prestige

    @Setting(path = "levels.max", desc = "Owner 4 Nov 2026: highest tool level; at this level the island owner/co-owner can prestige the tool.", min = 2, max = 1000)
    public int maxLevel = 50;

    @Setting(path = "levels.xp-base", desc = "XP for level L to L+1 = xp-base x L^exponent x (1 + prestige-factor x prestige).", min = 0.01, max = 1_000_000)
    public double xpBase = 12;

    @Setting(path = "levels.exponent", desc = "See levels.xp-base: how much slower every next level is (1.6 = the owner's sawtooth curve).", min = 0, max = 5)
    public double xpExponent = 1.6;

    @Setting(path = "levels.prestige-factor", desc = "See levels.xp-base: every prestige makes the levels this much slower (0.35 = +35%).", min = 0, max = 10)
    public double xpPrestigeFactor = 0.35;

    @Setting(path = "levels.xp-per-block", desc = "Tool XP per relevant block (a ripe crop for the hoe, a natural ore or stone for the pickaxe), before the bonuses.", min = 0, max = 1000)
    public double xpPerBlock = 1;

    @Setting(path = "levels.title", desc = "A title on screen for the player whose block levelled the tool up (the other members get an action bar line).")
    public boolean levelTitle = true;

    @Setting(path = "prestige.bonus-percent", desc = "Owner 4 Nov 2026: the island's permanent bonus per prestige on that tool: this much more tool XP and shards of its type.", min = 0, max = 100)
    public double prestigeBonus = 5;

    @Setting(path = "prestige.discount-percent", desc = "Perks of a prestiged tool are this much cheaper per prestige.", min = 0, max = 100)
    public double prestigeDiscount = 5;

    @Setting(path = "prestige.price-floor-percent", desc = "Perk prices never drop under this share of the full price, however many prestiges.", min = 0, max = 100)
    public double priceFloor = 50;

    @Setting(path = "prestige.max-stars", desc = "Stars in the tool name up to this prestige (★★★★★); after it '★6', '★7' ...", min = 1, max = 10)
    public int maxStars = 5;

    @Setting(path = "prestige.roles", desc = "Island roles that may prestige a tool (owner: owner and co-owner = LEADER, ADMIN).")
    public List<String> prestigeRoles = List.of("LEADER", "ADMIN");

    // ------------------------------------------------------------------ shards

    @Setting(path = "shards.chance-pickaxe", desc = "Percent chance of 1 Mining Shard per relevant block mined with the island pickaxe (over 100 = more than one).", min = 0, max = 1000)
    public double shardPickaxe = 6;

    @Setting(path = "shards.chance-hoe", desc = "Percent chance of 1 Farming Shard per ripe crop harvested with the island hoe (over 100 = more than one).", min = 0, max = 1000)
    public double shardHoe = 8;

    @Setting(path = "shards.finder-per-level", desc = "Shard Finder: percent more shards per level (owner 4 Nov 2026: tiny steps, 0.1% each).", min = 0, max = 20)
    public double shardFinderPerLevel = 0.1;

    @Setting(path = "shards.payout-seconds", desc = "Found shards are paid in one batch this often (and shown on the action bar).", min = 1, max = 60)
    public int shardPayoutSeconds = 3;

    @Setting(path = "auto-sell.price-percent", desc = "Auto-Sell sells drops as you break them at this percent of the sell price (the other selling bonuses apply too).", min = 1, max = 100)
    public double autoSellPercent = 90;

    @Setting(path = "auto-sell.batch-seconds", desc = "Auto-sold drops are paid in one sale this often.", min = 1, max = 60)
    public int autoSellSeconds = 3;

    // ------------------------------------------------------------------ perks

    @Setting(path = "relevant.pickaxe", desc = "Blocks where the island pickaxe earns XP, shards and its perks (block ids, * wildcard at the start or end). Blocks a player placed never count.")
    public List<String> relevantPickaxe = List.of("*_ore", "stone", "cobblestone", "deepslate", "cobbled_deepslate", "andesite",
            "diorite", "granite", "tuff", "calcite", "netherrack", "basalt", "blackstone", "end_stone", "obsidian", "ancient_debris",
            "amethyst_cluster", "raw_*_block");

    @Setting(path = "relevant.hoe", desc = "Blocks where the island hoe earns XP, shards and its perks (ripe crops only, always).")
    public List<String> relevantHoe = List.of("wheat", "carrots", "potatoes", "beetroots", "nether_wart", "cocoa", "melon", "pumpkin",
            "sweet_berry_bush", "sugar_cane", "cactus", "bamboo");

    @Setting(path = "placed.memory-hours", desc = "How long a block a player placed is remembered (so breaking it gives no bonus).", min = 1, max = 168)
    public int placedHours = 12;

    @Setting(path = "perks", desc = "Owner 4 Nov 2026: perks per tool, 'kind:id:effect:max:unlock-level:costs[:prestige]'. costs 'base+step' = a long "
            + "track (price of level L = base + step x L^prices.exponent, nice-rounded) or 'a/b/c' = the price of each level. Effects: "
            + "enchant=<vanilla id>, haste, smelt, drops (Extra Drops), storage (hoe), shards (Shard Finder), autosell (Auto-Sell), growth "
            + "(island crop growth, hoe), sweep (hoe Sweep / pickaxe Vein), wisdom (more tool XP), sell (Sell Bonus), lucky (Shard Pouch finds). "
            + "Magnet is built in (drops go straight to the inventory). Prices are paid in the tool's own shards.")
    public List<String> perks = List.of(
            "hoe:shard_finder:shards:1000:1:300+10", "hoe:efficiency:enchant=efficiency:5:1:1500/4000/10000/25000/60000",
            "hoe:extra_drops:drops:500:5:400+12", "hoe:fortune:enchant=fortune:3:5:5000/25000/100000",
            "hoe:haste:haste:2:10:15000/75000", "hoe:wisdom:wisdom:100:10:500+25", "hoe:growth:growth:250:15:600+20",
            "hoe:storage:storage:250:15:300+10", "hoe:auto_sell:autosell:1:25:50000", "hoe:sell_bonus:sell:500:30:800+20",
            "hoe:lucky:lucky:500:35:1000+25", "hoe:sweep:sweep:100:45:5000+150:1",
            "pickaxe:shard_finder:shards:1000:1:300+10", "pickaxe:efficiency:enchant=efficiency:5:1:1500/4000/10000/25000/60000",
            "pickaxe:extra_drops:drops:500:5:400+12", "pickaxe:fortune:enchant=fortune:3:5:5000/25000/100000",
            "pickaxe:haste:haste:2:10:15000/75000", "pickaxe:wisdom:wisdom:100:10:500+25", "pickaxe:smelter:smelt:100:15:600+20",
            "pickaxe:auto_sell:autosell:1:25:50000", "pickaxe:sell_bonus:sell:500:30:800+20", "pickaxe:lucky:lucky:500:35:1000+25",
            "pickaxe:vein:sweep:100:45:5000+150:1");

    @Setting(path = "prices.exponent", desc = "Exponent of the long-track price curve (owner 4 Nov 2026: 1.35).", min = 0, max = 4)
    public double priceExponent = 1.35;

    @Setting(path = "effects.drops-per-level", desc = "Extra Drops: percent more drops per level. Owner 4 Nov 2026: the island's own farm/mine boosts "
            + "(island-boost.*) are much stronger than this.", min = 0, max = 100)
    public double dropsPerLevel = 0.05;

    @Setting(path = "effects.smelt-per-level", desc = "Smelter: auto-smelt chance percent per level.", min = 0, max = 100)
    public double smeltPerLevel = 0.5;

    @Setting(path = "effects.growth-per-level", desc = "Growth (hoe): percent faster growth of the crops on the island per level.", min = 0, max = 100)
    public double growthPerLevel = 0.2;

    @Setting(path = "effects.wisdom-per-level", desc = "Wisdom: percent more tool XP per level.", min = 0, max = 100)
    public double wisdomPerLevel = 0.5;

    @Setting(path = "effects.sell-per-level", desc = "Sell Bonus: percent more money per level when Auto-Sell or the hoe storage sells.", min = 0, max = 10)
    public double sellPerLevel = 0.02;

    @Setting(path = "effects.lucky-chance-per-level", desc = "Lucky: percent chance per relevant block and level of a Shard Pouch.", min = 0, max = 1)
    public double luckyPerLevel = 0.002;

    @Setting(path = "effects.lucky-min", desc = "Lucky: fewest shards in a Shard Pouch (the prestige bonus applies on top).", min = 1, max = 1_000_000)
    public int luckyMin = 25;

    @Setting(path = "effects.lucky-max", desc = "Lucky: most shards in a Shard Pouch.", min = 1, max = 1_000_000)
    public int luckyMax = 250;

    @Setting(path = "sweep.bands", desc = "Owner 4 Nov 2026: Sweep (hoe) and Vein (pickaxe) per prestige band, 'prestige:extra-blocks:chance-at-max-level'. "
            + "Locked below the first band; the 100 levels raise the chance inside the current band.")
    public List<String> sweepBands = List.of("1:2:10", "2:3:15", "3:4:20", "5:6:30");

    @Setting(path = "sweep.radius", desc = "Sweep/Vein look this many blocks around the broken one for ripe crops / the same ore.", min = 1, max = 4)
    public int sweepRadius = 2;

    @Setting(path = "sweep.shards", desc = "Swept crops and veined ores also roll for shards and Shard Pouches (off: only normal drops and tool XP).")
    public boolean sweepShards = false;

    @Setting(path = "hoe.speed-level", desc = "Owner 4 Nov 2026: Speed while holding the island hoe at /farm and on islands (0 = off).", min = 0, max = 5)
    public int speedLevel = 1;

    @Setting(path = "hoe.swiftness-prestige", desc = "'Swiftness': from this hoe prestige on the Speed is one level higher (0 = never).", min = 0, max = 100)
    public int swiftnessPrestige = 2;

    @Setting(path = "hoe.storage-base", desc = "Hoe storage at Storage level 0 (items).", min = 1, max = 1_000_000)
    public long storageBase = 2000;

    @Setting(path = "hoe.storage-per-level", desc = "More hoe storage per Storage level (items).", min = 0, max = 100_000)
    public long storagePerLevel = 200;

    @Setting(path = "island-boost.hoe-effect", desc = "Owner 4 Nov 2026: the island upgrade (upgrades.yml effect) whose value is percent more crops for "
            + "everything the island hoe harvests (on islands and at /farm), from the island the player is a member of.")
    public String hoeBoostEffect = "harvest-yield";

    @Setting(path = "island-boost.pickaxe-effect", desc = "The island upgrade whose value is percent more ores for the island pickaxe (on islands and at /mine).")
    public String pickaxeBoostEffect = "mine-fortune";

    @Setting(path = "tool-worlds", desc = "Worlds besides the islands where the tools work exactly like on an island (level up, perks, shards): the /farm and /mine world.")
    public List<String> toolWorlds = List.of("grind");

    // ------------------------------------------------------------------ island crops

    @Setting(path = "growth.enabled", desc = "Owner 3 Nov 2026: every crop on an island grows on the island's clock, one stage every few seconds (the regrow time below split over the crop's stages, faster with the crop-growth upgrade), with a puff and a soft sound.")
    public boolean growthEnabled = true;

    @Setting(path = "growth.step-floor-ms", desc = "Shortest time between two stages of one crop (milliseconds).", min = 100, max = 60000)
    public long growthStepFloorMillis = 500;

    @Setting(path = "growth.stage-seconds", desc = "Feedback 3 Nov 2026: seconds per growth stage of an island crop without upgrades "
            + "(the crop-growth upgrade makes it faster, down to step-floor-ms).", min = 0.5, max = 600)
    public double growthStageSeconds = 3;

    @Setting(path = "growth.bonemeal-stages", desc = "Stages one bone meal adds to an island crop (1 to this, random).", min = 1, max = 7)
    public int growthBonemealStages = 3;

    @Setting(path = "growth.budget", desc = "Most crops grown per half second on all islands together (the rest wait for the next "
            + "run; keeps the tick time flat). Feedback 4 Nov 2026: 2000 (600 was exactly one 60x60 field, so two big fields "
            + "grew at half speed).", min = 10, max = 10000)
    public int growthBudget = 2000;

    @Setting(path = "growth.max-ms-per-run", desc = "Most milliseconds one half-second growth run may take (the rest waits for the "
            + "next run). Feedback 4 Nov 2026: 4 (was a fixed 2, too little for big fields).", min = 1, max = 20)
    public int growthMaxMillis = 4;

    @Setting(path = "growth.sound", desc = "A soft sound when a crop near you grows (at most every 2 s per player).")
    public boolean growthSound = true;

    @Setting(path = "growth.label", desc = "Looking at a crop on an island shows its stage and the time to the next one (only you see it).")
    public boolean growthLabel = false; // feedback 3 Nov 2026: no hologram over crops

    @Setting(path = "growth.label-range", desc = "How far you can look at a crop for its label (blocks).", min = 1, max = 10)
    public int growthLabelRange = 5;

    @Setting(path = "farmland.always-wet", desc = "Owner 3 Nov 2026: tilled dirt on islands is moist farmland at once and never dries, turns back to dirt or gets trampled (no water needed).")
    public boolean farmlandAlwaysWet = true;

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
        return 7;
    }

    /** Changed defaults (a value the owner chose is kept). */
    @Override
    public Map<Integer, Map<String, ValueChange>> valueChanges() {
        IslandToolsConfig d = new IslandToolsConfig();
        return Map.of(4, Map.of("growth.label", ValueChange.of(true, false)),
                6, Map.of("growth.budget", ValueChange.of(600, d.growthBudget)),
                // v7 (owner 4 Nov 2026, island tools rework): more shards, tiny perk steps, linear hoe storage
                7, Map.of("shards.chance-hoe", ValueChange.of(3.0, d.shardHoe), "shards.chance-pickaxe", ValueChange.of(2.0, d.shardPickaxe),
                        "shards.finder-per-level", ValueChange.of(0.5, d.shardFinderPerLevel),
                        "effects.drops-per-level", ValueChange.of(8.0, d.dropsPerLevel),
                        "effects.smelt-per-level", ValueChange.of(20.0, d.smeltPerLevel),
                        "hoe.storage-base", ValueChange.of(256, d.storageBase)));
    }

    /** Owner 4 Nov 2026: the old tier, task, axe/sword, row and put-away settings are gone. */
    @Override
    public Map<String, String> removedKeys() {
        String gone = "island tools rework 4 Nov 2026";
        return Map.ofEntries(Map.entry("tier-costs", gone + ": the tools start at diamond"), Map.entry("tier-money", gone + ": no tiers"),
                Map.entry("tasks", gone + ": tool levels replace the island tasks"),
                Map.entry("shards.chance-axe", gone + ": no island axe"), Map.entry("shards.chance-sword", gone + ": no island sword"),
                Map.entry("relevant.axe", gone + ": no island axe"), Map.entry("confirm-above", gone + ": only prestige has a confirm screen"),
                Map.entry("only-on-island", gone + ": the Depths are gone"), Map.entry("own-inventory-worlds", gone + ": the Depths are gone"),
                Map.entry("put-away-worlds", gone + ": the Depths are gone"), Map.entry("upgrades", gone + ": replaced by 'perks'"),
                Map.entry("effects.timber-per-level", gone + ": no island axe"), Map.entry("effects.experience-per-level", gone + ": Wisdom gives tool XP"),
                Map.entry("effects.lifesteal-per-level", gone + ": no island sword"), Map.entry("hoe.row-lengths", gone + ": Row became Sweep"),
                Map.entry("hoe.storage-factor", gone + ": the storage grows by hoe.storage-per-level"));
    }

    @Override
    public String fileName() {
        return "islandtools.yml";
    }

    @Override
    public String header() {
        return "Island tools: the island's harvester hoe and pickaxe (owner 4 Nov 2026). Texts: messages/islandtools.yml. Reload: /skyblock reload islandtools";
    }
}

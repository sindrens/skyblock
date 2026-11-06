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

    // ------------------------------------------------------------------ owner 6 Nov 2026: personal grind enchants

    @Setting(path = "enchants.enabled", desc = "Owner 6 Nov 2026: personal grind enchants (bought with your own shards, work only at /farm and /mine).")
    public boolean enchantsEnabled = true;

    @Setting(path = "enchants.list", desc = "Grind enchants (17 per tool) as 'kind:id:max:cost:chance1:chanceMax:value1:valueMax:cooldownSeconds"
            + ":unlockLevel:unlockPrestige'. Chance (percent per block) and value grow linearly from level 1 to max; a level costs "
            + "cost x level^1.2 shards; the unlock is the tool level and prestige needed. Area ids (value = blocks, drops only, one per "
            + "break): blast, lightning, meteor, nuke / explode, row_reaper, tornado, meteor_shower. rich_vein/bountiful (extra items), "
            + "shard_burst/shard_jackpot (shards), shard_echo (percent more shards after a Burst), compound_interest (streak: up to "
            + "value percent more shards), combo (streak: up to value percent more tool XP), cash_vein/cash_crop/golden_crop (money = "
            + "sell price x value), quick_study/motherlode (extra unlock progress), scholar (percent more skill XP), geode/"
            + "harvest_basket (one of every unlocked ore/crop), lucky_streak (after value blocks without a rare find the rare chances "
            + "double), keyfinder (a crate key).", level = Setting.Level.OWNER)
    public List<String> enchantList = List.of(
            "pickaxe:blast:1000:2:0.5:1.0:4:18:0:1:0",
            "pickaxe:shard_burst:1000:2:0.4:1.0:3:6:0:1:0",
            "pickaxe:scholar:1000:2:0:0:5:25:0:1:0",
            "pickaxe:rich_vein:1000:2:1:5:1:2:0:3:0",
            "pickaxe:quick_study:1000:2:1:5:1:1:0:5:0",
            "pickaxe:cash_vein:1000:3:0.5:2.0:5:5:0:8:0",
            "pickaxe:combo:1000:2:0:0:5:20:0:10:0",
            "pickaxe:compound_interest:1000:3:0:0:5:15:0:13:0",
            "pickaxe:shard_echo:1000:3:0:0:10:25:0:16:0",
            "pickaxe:lightning:1000:3:0.05:0.15:9:18:20:20:0",
            "pickaxe:motherlode:1000:3:0.2:0.6:10:10:0:24:0",
            "pickaxe:lucky_streak:1000:3:0:0:100:50:0:28:0",
            "pickaxe:geode:1000:4:0.05:0.2:1:1:0:32:0",
            "pickaxe:keyfinder:1000:5:0.001:0.02:1:1:0:36:0",
            "pickaxe:meteor:1000:5:0.03:0.08:15:25:20:1:1",
            "pickaxe:nuke:1000:8:0.003:0.01:40:100:300:1:5",
            "pickaxe:shard_jackpot:1000:6:0.01:0.04:40:75:0:1:10",
            "hoe:explode:1000:2:0.5:1.0:4:18:0:1:0",
            "hoe:shard_burst:1000:2:0.4:1.0:3:6:0:1:0",
            "hoe:scholar:1000:2:0:0:5:25:0:1:0",
            "hoe:bountiful:1000:2:1:5:1:2:0:3:0",
            "hoe:quick_study:1000:2:1:5:1:1:0:5:0",
            "hoe:cash_crop:1000:3:0.5:2.0:5:5:0:8:0",
            "hoe:combo:1000:2:0:0:5:20:0:10:0",
            "hoe:compound_interest:1000:3:0:0:5:15:0:13:0",
            "hoe:shard_echo:1000:3:0:0:10:25:0:16:0",
            "hoe:row_reaper:1000:3:0.3:0.7:4:7:0:20:0",
            "hoe:golden_crop:1000:3:0.1:0.4:5:5:0:24:0",
            "hoe:lucky_streak:1000:3:0:0:100:50:0:28:0",
            "hoe:harvest_basket:1000:4:0.05:0.2:1:1:0:32:0",
            "hoe:keyfinder:1000:5:0.001:0.02:1:1:0:36:0",
            "hoe:tornado:1000:5:0.03:0.08:20:28:20:1:1",
            "hoe:meteor_shower:1000:8:0.01:0.03:12:36:60:1:5",
            "hoe:shard_jackpot:1000:6:0.01:0.04:40:100:0:1:10");

    @Setting(path = "enchants.cap-base", desc = "Highest grind enchant level at prestige 0 (plans/TOOLS_V3.md).", min = 1, max = 100_000)
    public int enchantCapBase = 20;

    @Setting(path = "enchants.cap-per-prestige", desc = "The grind enchant level cap rises by this much every prestige (20: 1000 at prestige 49-50).", min = 0, max = 100_000)
    public int enchantCapPerPrestige = 20;

    @Setting(path = "enchants.streak-step-percent", desc = "Compound Interest and Combo: percent bonus per block in a streak (up to the enchant's value).", min = 0, max = 100)
    public double enchantStreakStep = 0.1;

    @Setting(path = "enchants.streak-gap-shards", desc = "Compound Interest: seconds without a block that end the streak.", min = 1, max = 60)
    public int enchantStreakGapShards = 3;

    @Setting(path = "enchants.streak-gap-xp", desc = "Combo: seconds without a block that end the streak.", min = 1, max = 60)
    public int enchantStreakGapXp = 2;

    @Setting(path = "enchants.echo-seconds", desc = "Shard Echo: seconds of more shards after a Shard Burst.", min = 1, max = 120)
    public int enchantEchoSeconds = 10;

    @Setting(path = "enchants.shard-bonus-cap", desc = "Shard Echo and Compound Interest together give at most this many percent more shards (events come on top).", min = 0, max = 1000)
    public double enchantShardCap = 65;

    @Setting(path = "enchants.area-per-tick", desc = "Area enchants break at most this many blocks per tick (the rest follow next ticks).", min = 1, max = 500)
    public int enchantAreaPerTick = 30;

    @Setting(path = "enchants.key-crate", desc = "Crate whose key Keyfinder finds.", level = Setting.Level.OWNER)
    public String enchantKeyCrate = "ember";

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
    public double shardPickaxe = 30; // feedback 5 Nov 2026: about 5x faster

    @Setting(path = "shards.chance-hoe", desc = "Percent chance of 1 Farming Shard per ripe crop harvested with the island hoe (over 100 = more than one).", min = 0, max = 1000)
    public double shardHoe = 40; // feedback 5 Nov 2026: about 5x faster

    @Setting(path = "shards.finder-per-level", desc = "Shard Finder: percent more shards per level (feedback 5 Nov 2026: 1% a level, 100 levels = double shards).", min = 0, max = 20)
    public double shardFinderPerLevel = 1.0;

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
    public List<String> perks = List.of( // feedback 5 Nov 2026: fewer, bigger steps; the first upgrade within a minute
            "hoe:shard_finder:shards:100:1:40+6", "hoe:efficiency:enchant=efficiency:5:1:150/600/2000/6000/15000",
            "hoe:extra_drops:drops:100:3:60+8", "hoe:fortune:enchant=fortune:3:5:1500/8000/30000",
            "hoe:wisdom:wisdom:50:5:80+10", "hoe:growth:growth:100:10:100+10", "hoe:storage:storage:100:10:60+6",
            "hoe:haste:haste:2:15:5000/30000", "hoe:auto_sell:autosell:1:20:20000", "hoe:sell_bonus:sell:100:25:200+20",
            "hoe:lucky:lucky:100:30:300+25", "hoe:sweep:sweep:100:45:3000+100:1",
            "pickaxe:shard_finder:shards:100:1:40+6", "pickaxe:efficiency:enchant=efficiency:5:1:150/600/2000/6000/15000",
            "pickaxe:extra_drops:drops:100:3:60+8", "pickaxe:fortune:enchant=fortune:3:5:1500/8000/30000",
            "pickaxe:wisdom:wisdom:50:5:80+10", "pickaxe:smelter:smelt:100:10:100+10", "pickaxe:storage:storage:100:10:60+6",
            "pickaxe:haste:haste:2:15:5000/30000", "pickaxe:auto_sell:autosell:1:20:20000", "pickaxe:sell_bonus:sell:100:25:200+20",
            "pickaxe:lucky:lucky:100:30:300+25", "pickaxe:vein:sweep:100:45:3000+100:1");

    /** {@code perks} v8 (owner 4 Nov 2026 19:39): 500-1000 tiny levels; replaced on 5 Nov 2026 (feedback: too slow, +0.01% steps). */
    static final List<String> PERKS_V8 = List.of(
            "hoe:shard_finder:shards:1000:1:300+10", "hoe:efficiency:enchant=efficiency:5:1:1500/4000/10000/25000/60000",
            "hoe:extra_drops:drops:500:5:400+12", "hoe:fortune:enchant=fortune:3:5:5000/25000/100000",
            "hoe:haste:haste:2:10:15000/75000", "hoe:wisdom:wisdom:100:10:500+25", "hoe:growth:growth:250:15:600+20",
            "hoe:storage:storage:250:15:300+10", "hoe:auto_sell:autosell:1:25:50000", "hoe:sell_bonus:sell:500:30:800+20",
            "hoe:lucky:lucky:500:35:1000+25", "hoe:sweep:sweep:100:45:5000+150:1",
            "pickaxe:shard_finder:shards:1000:1:300+10", "pickaxe:efficiency:enchant=efficiency:5:1:1500/4000/10000/25000/60000",
            "pickaxe:extra_drops:drops:500:5:400+12", "pickaxe:fortune:enchant=fortune:3:5:5000/25000/100000",
            "pickaxe:haste:haste:2:10:15000/75000", "pickaxe:wisdom:wisdom:100:10:500+25", "pickaxe:smelter:smelt:100:15:600+20",
            "pickaxe:storage:storage:250:15:300+10", "pickaxe:auto_sell:autosell:1:25:50000", "pickaxe:sell_bonus:sell:500:30:800+20", "pickaxe:lucky:lucky:500:35:1000+25",
            "pickaxe:vein:sweep:100:45:5000+150:1");

    /** The v7 default (before the pickaxe storage, 4 Nov 2026 19:39). */
    static final List<String> PERKS_V7 = List.of(
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
    public double dropsPerLevel = 0.5;

    @Setting(path = "effects.smelt-per-level", desc = "Smelter: auto-smelt chance percent per level.", min = 0, max = 100)
    public double smeltPerLevel = 1.0;

    @Setting(path = "effects.growth-per-level", desc = "Growth (hoe): percent faster growth of the crops on the island per level.", min = 0, max = 100)
    public double growthPerLevel = 0.5;

    @Setting(path = "effects.wisdom-per-level", desc = "Wisdom: percent more tool XP per level.", min = 0, max = 100)
    public double wisdomPerLevel = 2.0;

    @Setting(path = "effects.sell-per-level", desc = "Sell Bonus: percent more money per level when Auto-Sell or the hoe storage sells.", min = 0, max = 10)
    public double sellPerLevel = 0.1;

    @Setting(path = "effects.lucky-chance-per-level", desc = "Lucky: percent chance per relevant block and level of a Shard Pouch.", min = 0, max = 1)
    public double luckyPerLevel = 0.01;

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

    @Setting(path = "hoe.storage-base", desc = "Hoe and pickaxe storage at Storage level 0 (items; loot lands there when the bag is full).", min = 1, max = 1_000_000)
    public long storageBase = 2000;

    @Setting(path = "hoe.storage-per-level", desc = "More storage per Storage level (items), hoe and pickaxe.", min = 0, max = 100_000)
    public long storagePerLevel = 500;

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
        return 9;
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
                        "hoe.storage-base", ValueChange.of(256, d.storageBase)),
                // v8 (owner 4 Nov 2026 19:39): the pickaxe got a storage too
                8, Map.of("perks", ValueChange.of(PERKS_V7, PERKS_V8)),
                // v9 (feedback 5 Nov 2026): shards about 5x faster, Shard Finder 1% a level, 50-100 bigger levels per perk
                9, Map.ofEntries(Map.entry("perks", ValueChange.of(PERKS_V8, d.perks)),
                        Map.entry("shards.chance-hoe", ValueChange.of(8.0, d.shardHoe)), Map.entry("shards.chance-pickaxe", ValueChange.of(6.0, d.shardPickaxe)),
                        Map.entry("shards.finder-per-level", ValueChange.of(0.1, d.shardFinderPerLevel)),
                        Map.entry("effects.drops-per-level", ValueChange.of(0.05, d.dropsPerLevel)),
                        Map.entry("effects.smelt-per-level", ValueChange.of(0.5, d.smeltPerLevel)),
                        Map.entry("effects.growth-per-level", ValueChange.of(0.2, d.growthPerLevel)),
                        Map.entry("effects.wisdom-per-level", ValueChange.of(0.5, d.wisdomPerLevel)),
                        Map.entry("effects.sell-per-level", ValueChange.of(0.02, d.sellPerLevel)),
                        Map.entry("effects.lucky-chance-per-level", ValueChange.of(0.002, d.luckyPerLevel)),
                        Map.entry("hoe.storage-per-level", ValueChange.of(200L, d.storagePerLevel))));
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

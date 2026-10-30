package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Wardrobe settings ({@code wardrobe.yml}, WARDROBE §8.3). Every number is a default the owner can change. Set definitions
 * live in {@code sets.yml}, texts in {@code messages/wardrobe.yml}.
 */
final class WardrobeConfig extends ModuleConfig {

    @Setting(path = "season-id", desc = "Current season (gem-set claims are once per profile per season). Stays 0 until the season system exists.",
            min = 0, max = 100_000, level = Setting.Level.OWNER)
    public int seasonId = 0;

    // ------------------------------------------------------------------ levels (W2)

    @Setting(path = "levels.enabled", desc = "Set levels (W2): sets start at level 0 (0x bonus) and level up by playing. Off = every owned set gives levels.fixed-multiplier.",
            level = Setting.Level.OWNER)
    public boolean levelsEnabled = true;

    @Setting(path = "levels.fixed-multiplier", desc = "Bonus multiplier of every set while set levels are off (1.0 = the values in sets.yml).",
            min = 0, max = 10, level = Setting.Level.OWNER)
    public double fixedMultiplier = 1.0;

    @Setting(path = "levels.max", desc = "Highest set level.", min = 1, max = 1000, level = Setting.Level.OWNER)
    public int maxLevel = 50;

    @Setting(path = "levels.max-multiplier", desc = "Bonus multiplier at the highest set level (linear from 0x at level 0: Lv 25 = 1x, Lv 50 = 2x).",
            min = 0, max = 100, level = Setting.Level.OWNER)
    public double maxMultiplier = 2.0;

    @Setting(path = "levels.base-xp", desc = "Set XP for level 0 -> 1. Level L -> L+1 costs base-xp x growth^L (200 x 1.10^L: 232,782 XP to level 50).",
            min = 1, max = 1_000_000_000, level = Setting.Level.OWNER)
    public double baseXp = 200;

    @Setting(path = "levels.growth", desc = "XP growth per level (1.10 = every level costs 10% more than the one before).",
            min = 1, max = 10, level = Setting.Level.OWNER)
    public double growth = 1.10;

    @Setting(path = "levels.merged-xp-factor", desc = "Merged sets need this many times the XP for every level (WD15).",
            min = 0.01, max = 100, level = Setting.Level.OWNER)
    public double mergedXpFactor = 1.5;

    @Setting(path = "levels.title-levels", desc = "Levels that show a title on screen when reached (milestones); other level-ups use the action bar.",
            level = Setting.Level.OWNER)
    public List<String> titleLevels = List.of("10", "25", "40", "50");

    @Setting(path = "levels.trim-upgrade-level", desc = "From this set level the pieces use the set's upgraded trim material (sets.yml upgraded-trim-material).",
            min = 0, max = 1000, level = Setting.Level.OWNER)
    public int trimUpgradeLevel = 10;

    @Setting(path = "levels.glint-level", desc = "From this set level every piece has the enchant glint.", min = 0, max = 1000, level = Setting.Level.OWNER)
    public int glintLevel = 50;

    @Setting(path = "levels.trail-level", desc = "From this set level (all 4 pieces worn) the player gets a particle trail (toggle in /settings or the set screen).",
            min = 0, max = 1000, level = Setting.Level.OWNER)
    public int trailLevel = 50;

    @Setting(path = "enchants.second-slot-level", desc = "Set level that opens a 2nd armor enchant slot on every piece (EN12, used by E5).",
            min = 0, max = 1000, level = Setting.Level.OWNER)
    public int secondSlotLevel = 25;

    // ------------------------------------------------------------------ set XP (W2, WARDROBE §3.1)

    @Setting(path = "xp.min-pieces", desc = "Pieces of a set that must be worn before it earns set XP.", min = 1, max = 4, level = Setting.Level.OWNER)
    public int xpMinPieces = 2;

    @Setting(path = "xp.partial-share", desc = "Share of the XP a set earns with 2-3 pieces worn (0.5 = 50%).", min = 0, max = 10, level = Setting.Level.OWNER)
    public double xpPartialShare = 0.5;

    @Setting(path = "xp.full-share", desc = "Share of the XP a set earns with all 4 pieces worn (1.0 = 100%).", min = 0, max = 10, level = Setting.Level.OWNER)
    public double xpFullShare = 1.0;

    @Setting(path = "xp.actions.depths-ore", desc = "Set XP per ore block of your own in The Depths (mining).", min = 0, max = 1_000_000, level = Setting.Level.OWNER)
    public double xpDepthsOre = 1;
    @Setting(path = "xp.actions.island-ore", desc = "Set XP per block from an island generator (mining). Only blocks the generator made count.", min = 0, max = 1_000_000, level = Setting.Level.OWNER)
    public double xpIslandOre = 0.25;
    @Setting(path = "xp.actions.depths-log", desc = "Set XP per log in The Depths (foraging).", min = 0, max = 1_000_000, level = Setting.Level.OWNER)
    public double xpDepthsLog = 1;
    @Setting(path = "xp.actions.island-log", desc = "Set XP per log of a tree that grew on an island (foraging). Placed logs never count.", min = 0, max = 1_000_000, level = Setting.Level.OWNER)
    public double xpIslandLog = 0.25;
    @Setting(path = "xp.actions.crop", desc = "Set XP per fully grown crop harvested (farming).", min = 0, max = 1_000_000, level = Setting.Level.OWNER)
    public double xpCrop = 0.5;
    @Setting(path = "xp.actions.depths-kill", desc = "Set XP per mob killed in xp.combat-worlds (combat). Stacked island mobs give none (WD12).", min = 0, max = 1_000_000, level = Setting.Level.OWNER)
    public double xpDepthsKill = 8;
    @Setting(path = "xp.actions.mini-boss", desc = "Set XP per mini-boss with 5%+ damage (boss; sent by the boss module later).", min = 0, max = 1_000_000, level = Setting.Level.OWNER)
    public double xpMiniBoss = 300;
    @Setting(path = "xp.actions.world-boss", desc = "Set XP per world boss with 5%+ damage (boss; sent by the boss module later).", min = 0, max = 1_000_000, level = Setting.Level.OWNER)
    public double xpWorldBoss = 1500;
    @Setting(path = "xp.actions.fish", desc = "Set XP per fish caught (fishing; only once 'fishing' is in features.available).", min = 0, max = 1_000_000, level = Setting.Level.OWNER)
    public double xpFish = 25;
    @Setting(path = "xp.actions.sell-item", desc = "Set XP per item sold (sell; sent by the sell code).", min = 0, max = 1_000_000, level = Setting.Level.OWNER)
    public double xpSellItem = 1;
    @Setting(path = "xp.actions.sell-island-item", desc = "Set XP per island item sold (sell).", min = 0, max = 1_000_000, level = Setting.Level.OWNER)
    public double xpSellIslandItem = 0.5;
    @Setting(path = "xp.actions.koth-tick", desc = "Set XP per second on a KOTH capture point (conquest; sent by the conquest module later).", min = 0, max = 1_000_000, level = Setting.Level.OWNER)
    public double xpKothTick = 2;
    @Setting(path = "xp.actions.koth-capture", desc = "Set XP per KOTH capture (conquest).", min = 0, max = 1_000_000, level = Setting.Level.OWNER)
    public double xpKothCapture = 500;
    @Setting(path = "xp.actions.envoy", desc = "Set XP per envoy crate claimed (conquest).", min = 0, max = 1_000_000, level = Setting.Level.OWNER)
    public double xpEnvoy = 40;
    @Setting(path = "xp.actions.warzone-kill", desc = "Set XP per warzone player kill (pvp; the conquest module applies the victim rules).", min = 0, max = 1_000_000, level = Setting.Level.OWNER)
    public double xpWarzoneKill = 50;

    @Setting(path = "xp.sources.mining", desc = "What sets with activity 'mining' earn from: XP activity[:rate] (mining, foraging, farming, combat, boss, fishing, sell, conquest, pvp).", level = Setting.Level.OWNER)
    public List<String> sourcesMining = List.of("mining");
    @Setting(path = "xp.sources.foraging", desc = "What sets with activity 'foraging' earn from.", level = Setting.Level.OWNER)
    public List<String> sourcesForaging = List.of("foraging");
    @Setting(path = "xp.sources.farming", desc = "What sets with activity 'farming' earn from.", level = Setting.Level.OWNER)
    public List<String> sourcesFarming = List.of("farming");
    @Setting(path = "xp.sources.combat", desc = "What sets with activity 'combat' earn from.", level = Setting.Level.OWNER)
    public List<String> sourcesCombat = List.of("combat", "boss");
    @Setting(path = "xp.sources.fishing", desc = "What sets with activity 'fishing' earn from.", level = Setting.Level.OWNER)
    public List<String> sourcesFishing = List.of("fishing");
    @Setting(path = "xp.sources.conquest", desc = "What sets with activity 'conquest' earn from.", level = Setting.Level.OWNER)
    public List<String> sourcesConquest = List.of("conquest", "pvp");
    @Setting(path = "xp.sources.selling", desc = "What sets with activity 'selling' earn from.", level = Setting.Level.OWNER)
    public List<String> sourcesSelling = List.of("sell");
    @Setting(path = "xp.sources.skills", desc = "What sets with activity 'skills' (Lorekeeper) earn from.", level = Setting.Level.OWNER)
    public List<String> sourcesSkills = List.of("mining:0.75", "foraging:0.75", "farming:0.75", "combat:0.75", "fishing:0.75");
    @Setting(path = "xp.sources.gathering", desc = "What sets with activity 'gathering' (Voidforged) earn from.", level = Setting.Level.OWNER)
    public List<String> sourcesGathering = List.of("mining", "foraging", "farming");
    @Setting(path = "xp.sources.all", desc = "What sets with activity 'all' (Phoenix) earn from.", level = Setting.Level.OWNER)
    public List<String> sourcesAll = List.of("mining", "foraging", "farming", "combat", "boss", "fishing", "conquest");

    @Setting(path = "xp.hourly-caps", desc = "Most set XP per player and hour per XP activity (activity:amount), before the piece share. WD12: sell 8,000.",
            level = Setting.Level.OWNER)
    public List<String> hourlyCaps = List.of("sell:8000", "farming:10000");

    @Setting(path = "xp.combat-worlds", desc = "Worlds where mob kills give set XP (The Depths). Island mobs and stacked mobs never do (WD12).")
    public List<String> combatWorlds = List.of("depths");

    @Setting(path = "xp.island-worlds", desc = "Worlds where island generator blocks, grown tree logs and crops give set XP.")
    public List<String> islandWorlds = List.of("islands");

    @Setting(path = "xp.crops-need-hoe", desc = "Crops only give set XP when harvested with a hoe.")
    public boolean cropsNeedHoe = true;

    @Setting(path = "xp.crops", desc = "Crops that give set XP when fully grown (block ids).")
    public List<String> crops = List.of("WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "COCOA");

    @Setting(path = "xp.generator-blocks", desc = "Blocks an island generator (lava + water, or the island's generator upgrade) makes that give set XP when mined.")
    public List<String> generatorBlocks = List.of("COBBLESTONE", "STONE", "BASALT", "COBBLED_DEEPSLATE", "DEEPSLATE", "ANDESITE", "DIORITE",
            "GRANITE", "TUFF", "CALCITE", "COAL_ORE", "IRON_ORE", "COPPER_ORE", "GOLD_ORE", "REDSTONE_ORE", "LAPIS_ORE", "DIAMOND_ORE",
            "EMERALD_ORE", "DEEPSLATE_COAL_ORE", "DEEPSLATE_IRON_ORE", "DEEPSLATE_COPPER_ORE", "DEEPSLATE_GOLD_ORE",
            "DEEPSLATE_REDSTONE_ORE", "DEEPSLATE_LAPIS_ORE", "DEEPSLATE_DIAMOND_ORE", "DEEPSLATE_EMERALD_ORE", "NETHER_QUARTZ_ORE",
            "NETHER_GOLD_ORE");

    @Setting(path = "xp.tracked-blocks", desc = "How many generator-made blocks and grown logs are remembered (oldest are forgotten first; restart forgets all).",
            min = 0, max = 2_000_000)
    public int trackedBlocks = 100_000;

    @Setting(path = "xp.flush-seconds", desc = "Seconds between saves of earned set XP (also saved on quit, profile switch, merge and shutdown).", min = 5, max = 600)
    public int flushSeconds = 30;

    @Setting(path = "xp.bar-seconds", desc = "At most one set XP line in the action bar per this many seconds (0 = never; level-ups always show).", min = 0, max = 600)
    public int barSeconds = 10;

    // ------------------------------------------------------------------ Wardrobe Tomes (WD13)

    @Setting(path = "tomes.normal-xp", desc = "Set XP of a Wardrobe Tome.", min = 0, max = 1_000_000_000, level = Setting.Level.OWNER)
    public long tomeXp = 2500;

    @Setting(path = "tomes.greater-xp", desc = "Set XP of a Greater Wardrobe Tome (Inferno/Phoenix crates).", min = 0, max = 1_000_000_000, level = Setting.Level.OWNER)
    public long greaterTomeXp = 10_000;

    @Setting(path = "tomes.max-level", desc = "A tome never raises a set past this level (the last levels are always played).", min = 0, max = 1000, level = Setting.Level.OWNER)
    public int tomeMaxLevel = 40;

    @Setting(path = "tomes.material", desc = "Vanilla item a Wardrobe Tome looks like (never placeable or craftable).")
    public String tomeMaterial = "BOOK";

    // ------------------------------------------------------------------ merging (WD22/WD23)

    @Setting(path = "merge.enabled", desc = "Merging two max-level sets into one (both bonuses, back to level 0).", level = Setting.Level.OWNER)
    public boolean mergeEnabled = true;

    @Setting(path = "merge.fee-per-step", desc = "Crystal fee per tier step: fee = this x (tier A + tier B - 1); I+I 5,000 ... III+III 25,000. 0 = free.",
            min = 0, max = 1_000_000_000_000L, level = Setting.Level.OWNER)
    public long mergeFeePerStep = 5000;

    // ------------------------------------------------------------------ warzone normalization (WD18)

    @Setting(path = "warzone.worlds", desc = "Worlds that count as warzone/duels: every set at warzone.multiplier, only warzone.families work, armor normalized, no trails.")
    public List<String> warzoneWorlds = List.of("warzone");

    @Setting(path = "warzone.multiplier", desc = "Bonus multiplier of every set in the warzone, whatever its level (1.0 = level 25).", min = 0, max = 10, level = Setting.Level.OWNER)
    public double warzoneMultiplier = 1.0;

    @Setting(path = "warzone.families", desc = "Bonus families that work in the warzone (family:factor); all others give nothing there.", level = Setting.Level.OWNER)
    public List<String> warzoneFamilies = List.of("damage_reduction:0.5", "conquest:1");

    @Setting(path = "warzone.armor", desc = "Armor points per piece (helm, cuirass, greaves, boots) of every set piece in the warzone (diamond-like).", level = Setting.Level.OWNER)
    public List<String> warzoneArmor = List.of("3", "8", "6", "3");

    @Setting(path = "warzone.toughness", desc = "Armor toughness per piece in the warzone.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double warzoneToughness = 2;

    @Setting(path = "warzone.knockback-resistance", desc = "Knockback resistance per piece in the warzone.", min = 0, max = 1, level = Setting.Level.OWNER)
    public double warzoneKnockback = 0;

    // ------------------------------------------------------------------ trail (level 50)

    @Setting(path = "trail.particle", desc = "Particle of the level-50 trail (DUST uses the set's dye or trail.color).")
    public String trailParticle = "DUST";

    @Setting(path = "trail.color", desc = "Trail color (#RRGGBB) for sets without a leather dye.")
    public String trailColor = "#FF6A00";

    @Setting(path = "trail.interval-ticks", desc = "Ticks between two trail puffs while the player moves.", min = 2, max = 200)
    public int trailInterval = 6;

    @Setting(path = "trail.count", desc = "Particles per puff.", min = 1, max = 20)
    public int trailCount = 3;

    // ------------------------------------------------------------------ equipping

    @Setting(path = "equip.cooldown-seconds", desc = "Seconds between two wear/take-off actions (a loadout swap counts as one too).", min = 0, max = 60)
    public int equipCooldownSeconds = 2;

    @Setting(path = "equip.blocked-worlds", desc = "Worlds where sets can't be put on or taken off (the warzone).")
    public List<String> blockedWorlds = List.of("warzone");

    @Setting(path = "equip.hint-cooldown-seconds", desc = "Seconds between two \"Use /wardrobe to take this off\" hints.", min = 0, max = 300)
    public int hintCooldownSeconds = 3;

    @Setting(path = "equip.combat-seconds", desc = "No wearing, taking off or Wardrobe Chest moves for this many seconds after combat (PvP or a hostile mob; LD6). 0 = off.",
            min = 0, max = 600)
    public int equipCombatSeconds = 10;

    // ------------------------------------------------------------------ loadouts (W3, WARDROBE §7)

    @Setting(path = "loadout.enabled", desc = "Loadouts (/loadout): saved presets that swap set pieces, the pet and hotbar tools in one click.",
            level = Setting.Level.OWNER)
    public boolean loadoutEnabled = true;

    @Setting(path = "loadout.free-slots", desc = "Presets every player has (L2: 2). Ranks give more with skyblock.loadout.slots.<n> (the highest counts).",
            min = 0, max = 8, level = Setting.Level.OWNER)
    public int loadoutFreeSlots = 2;

    @Setting(path = "loadout.max-slots", desc = "Most presets anyone can have (free or rank slots + bought slots).", min = 1, max = 8,
            level = Setting.Level.OWNER)
    public int loadoutMaxSlots = 8;

    @Setting(path = "loadout.bought-max", desc = "Most extra presets that can be bought with store credits (account-wide, kept across seasons).",
            min = 0, max = 8, level = Setting.Level.OWNER)
    public int loadoutBoughtMax = 3;

    @Setting(path = "loadout.cooldown-seconds", desc = "Seconds between two swaps (shared with wearing and taking off sets).", min = 0, max = 60)
    public int loadoutCooldownSeconds = 2;

    @Setting(path = "loadout.combat-seconds", desc = "No swaps for this many seconds after combat (PvP or a hostile mob; L4). 0 = off.", min = 0, max = 600)
    public int loadoutCombatSeconds = 10;

    @Setting(path = "loadout.blocked-worlds", desc = "Worlds where loadouts can't be swapped (L4: the warzone). Saving and editing presets works everywhere.")
    public List<String> loadoutBlockedWorlds = List.of("warzone");

    @Setting(path = "loadout.partial", desc = "A swap still happens when a part can't (tool gone, set gone, no room): that part is skipped and listed. "
            + "Off = refuse the whole swap instead.", level = Setting.Level.OWNER)
    public boolean loadoutPartial = true;

    @Setting(path = "loadout.name-max-length", desc = "Longest preset name (letters, digits, _ and -; one word, so /loadout <name> works).",
            min = 1, max = 16)
    public int loadoutNameMax = 16;

    @Setting(path = "loadout.default-icon", desc = "Menu icon of a new preset without tools (item id).")
    public String loadoutDefaultIcon = "armor_stand";

    @Setting(path = "loadout.icons", desc = "Icons every player can pick for a preset (item ids), besides the preset's own tools and sets.")
    public List<String> loadoutIcons = List.of("iron_pickaxe", "iron_axe", "iron_sword", "iron_hoe", "fishing_rod", "bow", "shield",
            "emerald", "wheat", "oak_log", "diamond", "armor_stand");

    @Setting(path = "features.available", desc = "Features that exist on this server; sets with enabled-from a feature not listed show \"Arrives with ...\" (conquest, fishing).",
            level = Setting.Level.OWNER)
    public List<String> availableFeatures = List.of();

    // ------------------------------------------------------------------ armor points per tier (attribute modifiers)

    @Setting(path = "armor.tier-1.armor", desc = "Armor points per piece (helm, cuirass, greaves, boots) for tier I sets (iron-like).", level = Setting.Level.OWNER)
    public List<String> armorTier1 = List.of("2", "6", "5", "2");
    @Setting(path = "armor.tier-1.toughness", desc = "Armor toughness per piece for tier I sets.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double toughnessTier1 = 0;
    @Setting(path = "armor.tier-1.knockback-resistance", desc = "Knockback resistance per piece (0.025 = 2.5%) for tier I sets.", min = 0, max = 1, level = Setting.Level.OWNER)
    public double knockbackTier1 = 0;

    @Setting(path = "armor.tier-2.armor", desc = "Armor points per piece for tier II sets (diamond-like).", level = Setting.Level.OWNER)
    public List<String> armorTier2 = List.of("3", "8", "6", "3");
    @Setting(path = "armor.tier-2.toughness", desc = "Armor toughness per piece for tier II sets.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double toughnessTier2 = 2;
    @Setting(path = "armor.tier-2.knockback-resistance", desc = "Knockback resistance per piece for tier II sets.", min = 0, max = 1, level = Setting.Level.OWNER)
    public double knockbackTier2 = 0;

    @Setting(path = "armor.tier-3.armor", desc = "Armor points per piece for tier III sets (diamond-like).", level = Setting.Level.OWNER)
    public List<String> armorTier3 = List.of("3", "8", "6", "3");
    @Setting(path = "armor.tier-3.toughness", desc = "Armor toughness per piece for tier III sets.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double toughnessTier3 = 2;
    @Setting(path = "armor.tier-3.knockback-resistance", desc = "Knockback resistance per piece for tier III sets.", min = 0, max = 1, level = Setting.Level.OWNER)
    public double knockbackTier3 = 0;

    @Setting(path = "armor.tier-4.armor", desc = "Armor points per piece for tier IV sets (netherite-like).", level = Setting.Level.OWNER)
    public List<String> armorTier4 = List.of("3", "8", "6", "3");
    @Setting(path = "armor.tier-4.toughness", desc = "Armor toughness per piece for tier IV sets.", min = 0, max = 100, level = Setting.Level.OWNER)
    public double toughnessTier4 = 3;
    @Setting(path = "armor.tier-4.knockback-resistance", desc = "Knockback resistance per piece for tier IV sets (4 x 0.025 = 10%).", min = 0, max = 1, level = Setting.Level.OWNER)
    public double knockbackTier4 = 0.025;

    // ------------------------------------------------------------------ Wardrobe Chest (storage)

    @Setting(path = "storage.size", desc = "Items the Wardrobe Chest holds per profile.", min = 0, max = 270)
    public int storageSize = 27;

    @Setting(path = "storage.per-grant", desc = "Extra items per storage rank grant (skyblock.wardrobe.storage.1-3; the highest counts).", min = 0, max = 270)
    public int storagePerGrant = 9;

    @Setting(path = "storage.item-types", desc = "Item types that may go in the Wardrobe Chest (unique, tracked items only).")
    public List<String> storageTypes = List.of("tool", "enchant_book", "scroll");

    @Setting(path = "storage.item-ids", desc = "Extra custom item ids that may go in the Wardrobe Chest (unique items only).")
    public List<String> storageIds = List.of("set_token", "wardrobe_tome");

    // ------------------------------------------------------------------ tokens

    @Setting(path = "tokens.material", desc = "Vanilla item a Set Token looks like (right-click to redeem; never placeable or craftable).")
    public String tokenMaterial = "ARMOR_STAND";

    // ------------------------------------------------------------------ gates (Depths tiers from skill levels)

    @Setting(path = "tiers.mining", desc = "Mining skill level that opens each Mine tier (tier 1 first); keep in line with The Depths.")
    public List<String> tiersMining = List.of("0", "5", "10", "15", "20", "25", "30", "35", "42", "50", "58", "66");
    @Setting(path = "tiers.foraging", desc = "Foraging skill level that opens each Forest tier.")
    public List<String> tiersForaging = List.of("0", "5", "10", "15", "20", "25", "30", "35", "42", "50", "58");
    @Setting(path = "tiers.combat", desc = "Combat skill level that opens each Combat tier.")
    public List<String> tiersCombat = List.of("0", "6", "12", "18", "24", "30", "38", "46", "55", "66");
    @Setting(path = "tiers.farming", desc = "Farming skill level per crop tier (Phase 7).")
    public List<String> tiersFarming = List.of("0");
    @Setting(path = "tiers.fishing", desc = "Fishing skill level per fishing band.")
    public List<String> tiersFishing = List.of("0", "20", "40", "60");

    // ------------------------------------------------------------------ safety

    @Setting(path = "reconcile.alert-staff", desc = "Tell staff (skyblock.staff.notify) when a copied set piece is found outside its owner's armor slots.")
    public boolean alertStaff = true;

    /** Version 2 (W2): W1 shipped levels.enabled: false as a placeholder; it moves aside so set levels start on. */
    @Override
    public int version() {
        return 2;
    }

    @Override
    public java.util.Map<Integer, java.util.Map<String, String>> migrations() {
        return java.util.Map.of(2, java.util.Map.of("levels.enabled", "legacy.w1-levels-enabled"));
    }

    @Override
    public String fileName() {
        return "wardrobe.yml";
    }

    @Override
    public String header() {
        return """
                The wardrobe (/wardrobe): set levels and set XP, Wardrobe Tomes, merging, warzone normalization, equip
                rules, loadouts (/loadout), armor points per tier, the Wardrobe Chest, Set Tokens and tier gates. The sets themselves are in sets.yml, texts in messages/wardrobe.yml.
                Reload: /skyblock reload wardrobe""";
    }

    // ------------------------------------------------------------------ typed views

    /** Armor points per piece for a tier (head, chest, legs, feet). */
    List<Double> armor(int tier) {
        return numbers(switch (tier) {
            case 1 -> armorTier1;
            case 2 -> armorTier2;
            case 3 -> armorTier3;
            default -> armorTier4;
        }, 4);
    }

    double toughness(int tier) {
        return switch (tier) {
            case 1 -> toughnessTier1;
            case 2 -> toughnessTier2;
            case 3 -> toughnessTier3;
            default -> toughnessTier4;
        };
    }

    double knockback(int tier) {
        return switch (tier) {
            case 1 -> knockbackTier1;
            case 2 -> knockbackTier2;
            case 3 -> knockbackTier3;
            default -> knockbackTier4;
        };
    }

    /** Skill levels that open tiers 1, 2, 3 … in a wing (mining, foraging, combat, farming, fishing). */
    List<Integer> tiers(String wing) {
        List<String> raw = switch (wing) {
            case "mining" -> tiersMining;
            case "foraging" -> tiersForaging;
            case "combat" -> tiersCombat;
            case "farming" -> tiersFarming;
            case "fishing" -> tiersFishing;
            default -> List.of();
        };
        List<Integer> out = new ArrayList<>();
        for (String s : raw) {
            try {
                out.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {
                // reported by validate()
            }
        }
        return out;
    }

    boolean featureAvailable(String feature) {
        return feature == null || feature.isEmpty()
                || availableFeatures.stream().anyMatch(f -> f.trim().equalsIgnoreCase(feature));
    }

    boolean blockedWorld(String world) {
        return blockedWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(world));
    }

    boolean loadoutBlockedWorld(String world) {
        return loadoutBlockedWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(world));
    }

    boolean storable(String itemType, String itemId) {
        String type = itemType == null ? "" : itemType.toLowerCase(Locale.ROOT).replace('-', '_');
        return storageTypes.stream().anyMatch(t -> t.trim().toLowerCase(Locale.ROOT).replace('-', '_').equals(type))
                || (itemId != null && storageIds.stream().anyMatch(i -> i.trim().equalsIgnoreCase(itemId)));
    }

    // ------------------------------------------------------------------ W2 typed views

    /** The level curve. */
    SetLevels levels() {
        return new SetLevels(baseXp, growth, maxLevel, mergedXpFactor);
    }

    /** Piece shares for set XP. */
    SetXp.Shares shares() {
        return new SetXp.Shares(xpMinPieces, xpPartialShare, xpFullShare);
    }

    /** Set XP per unit of an action. */
    double actionXp(SetXp.Action action) {
        return switch (action) {
            case DEPTHS_ORE -> xpDepthsOre;
            case ISLAND_ORE -> xpIslandOre;
            case DEPTHS_LOG -> xpDepthsLog;
            case ISLAND_LOG -> xpIslandLog;
            case CROP -> xpCrop;
            case DEPTHS_KILL -> xpDepthsKill;
            case MINI_BOSS -> xpMiniBoss;
            case WORLD_BOSS -> xpWorldBoss;
            case FISH -> xpFish;
            case SELL_ITEM -> xpSellItem;
            case SELL_ISLAND_ITEM -> xpSellIslandItem;
            case KOTH_TICK -> xpKothTick;
            case KOTH_CAPTURE -> xpKothCapture;
            case ENVOY -> xpEnvoy;
            case WARZONE_KILL -> xpWarzoneKill;
        };
    }

    /** Raw {@code xp.sources.<activity>} list of a set activity (empty for unknown activities). */
    List<String> rawSources(String setActivity) {
        return switch (setActivity == null ? "" : setActivity) {
            case "mining" -> sourcesMining;
            case "foraging" -> sourcesForaging;
            case "farming" -> sourcesFarming;
            case "combat" -> sourcesCombat;
            case "fishing" -> sourcesFishing;
            case "conquest" -> sourcesConquest;
            case "selling" -> sourcesSelling;
            case "skills" -> sourcesSkills;
            case "gathering" -> sourcesGathering;
            case "all" -> sourcesAll;
            default -> List.of();
        };
    }

    /** XP activity → rate for sets of a set activity. */
    java.util.Map<String, Double> sources(String setActivity) {
        return SetXp.parseSources(rawSources(setActivity), p -> { }, "xp.sources." + setActivity);
    }

    /** Hourly cap per XP activity (0 = none). */
    java.util.Map<String, Double> hourlyCapMap() {
        return pairs(hourlyCaps, null);
    }

    /** The warzone rules. */
    SetBonuses.Normalization normalization() {
        java.util.Map<net.skybornmc.skyblock.common.bonus.BonusFamily, Double> factors = new java.util.EnumMap<>(net.skybornmc.skyblock.common.bonus.BonusFamily.class);
        pairs(warzoneFamilies, null).forEach((family, factor) ->
                net.skybornmc.skyblock.common.bonus.BonusFamily.byId(family).ifPresent(f -> factors.put(f, factor)));
        return new SetBonuses.Normalization(warzoneMultiplier, factors);
    }

    List<Double> warzoneArmorPoints() {
        return numbers(warzoneArmor, 4);
    }

    boolean warzoneWorld(String world) {
        return warzoneWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(world));
    }

    boolean combatWorld(String world) {
        return combatWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(world));
    }

    boolean islandWorld(String world) {
        return islandWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(world));
    }

    boolean titleLevel(int level) {
        return titleLevels.stream().anyMatch(t -> t.trim().equals(String.valueOf(level)));
    }

    /** {@code key:number} pairs; bad entries are reported (when {@code problems} is given) and skipped. */
    private static java.util.Map<String, Double> pairs(List<String> raw, List<String> problems) {
        java.util.Map<String, Double> out = new java.util.LinkedHashMap<>();
        for (String s : raw) {
            String[] parts = s == null ? new String[0] : s.trim().toLowerCase(Locale.ROOT).split(":", 2);
            if (parts.length != 2) {
                if (problems != null) {
                    problems.add("'" + s + "' is not key:number");
                }
                continue;
            }
            try {
                double v = Double.parseDouble(parts[1].trim());
                if (Double.isFinite(v) && v >= 0) {
                    out.put(parts[0].trim(), v);
                    continue;
                }
            } catch (NumberFormatException ignored) {
                // reported below
            }
            if (problems != null) {
                problems.add("'" + s + "' has no valid number");
            }
        }
        return out;
    }

    /** Problems with list values (numbers that are not numbers). */
    List<String> validate() {
        List<String> out = new ArrayList<>();
        for (int tier = 1; tier <= 4; tier++) {
            if (armor(tier).size() != 4) {
                out.add("wardrobe.yml → armor.tier-" + tier + ".armor needs 4 numbers (helm, cuirass, greaves, boots); using 0 for the rest");
            }
        }
        for (String wing : List.of("mining", "foraging", "combat", "farming", "fishing")) {
            if (tiers(wing).isEmpty()) {
                out.add("wardrobe.yml → tiers." + wing + " has no numbers; nobody passes a " + wing + " tier gate");
            }
        }
        List<String> pairProblems = new ArrayList<>();
        pairs(hourlyCaps, pairProblems);
        pairs(warzoneFamilies, pairProblems);
        pairProblems.forEach(p -> out.add("wardrobe.yml → xp.hourly-caps / warzone.families: " + p));
        for (String family : pairs(warzoneFamilies, null).keySet()) {
            if (net.skybornmc.skyblock.common.bonus.BonusFamily.byId(family).isEmpty()) {
                out.add("wardrobe.yml → warzone.families: unknown bonus family '" + family + "'");
            }
        }
        for (String cap : pairs(hourlyCaps, null).keySet()) {
            if (!SetXp.ACTIVITIES.contains(cap)) {
                out.add("wardrobe.yml → xp.hourly-caps: unknown XP activity '" + cap + "'");
            }
        }
        for (String activity : SetRegistry.ACTIVITIES) {
            SetXp.parseSources(rawSources(activity), out::add, "wardrobe.yml → xp.sources." + activity);
        }
        if (warzoneArmorPoints().size() != 4) {
            out.add("wardrobe.yml → warzone.armor needs 4 numbers (helm, cuirass, greaves, boots); using 0 for the rest");
        }
        return out;
    }

    private static List<Double> numbers(List<String> raw, int size) {
        List<Double> out = new ArrayList<>();
        for (String s : raw) {
            try {
                double v = Double.parseDouble(s.trim());
                out.add(Double.isFinite(v) && v >= 0 ? v : 0);
            } catch (NumberFormatException ignored) {
                // skipped
            }
        }
        return out.size() > size ? out.subList(0, size) : out;
    }
}

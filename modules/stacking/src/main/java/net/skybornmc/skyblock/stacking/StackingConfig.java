package net.skybornmc.skyblock.stacking;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/** Typed settings of {@code stacking.yml} (owner decisions 2026-10-30: Stacking S1–S28, SK1–SK10). */
public final class StackingConfig extends ModuleConfig {

    // ------------------------------------------------------------------ general

    @Setting(path = "worlds", desc = "Worlds where mobs, items and spawners stack and the island limits apply (owner: islands; outposts later). Never add depths, warzone, spawn or boss worlds.")
    public List<String> worlds = List.of("islands");

    @Setting(path = "perf.warn-ms-per-second", desc = "Log a warning when stacking uses more main-thread time than this, averaged over a minute (milliseconds per second).", min = 0.1, max = 1000)
    public double perfWarnMsPerSecond = 5.0;

    // ------------------------------------------------------------------ mobs

    @Setting(path = "mobs.max-stack", desc = "Most mobs in one stack (owner S2: 1,024).", min = 2, max = 100_000, level = Setting.Level.OWNER)
    public int mobMaxStack = 1024;

    @Setting(path = "mobs.merge-radius", desc = "Blocks within which a new mob joins a stack of the same kind (owner S3: 5).", min = 0.5, max = 16)
    public double mobMergeRadius = 5.0;

    @Setting(path = "mobs.max-stacks-per-chunk", desc = "Most stacks of the same mob type in one chunk (owner SK1: 8). More spawns join a stack in the chunk or are cancelled.", min = 1, max = 64, level = Setting.Level.OWNER)
    public int mobMaxStacksPerChunk = 8;

    @Setting(path = "mobs.max-stacks-per-chunk-total", desc = "Most mob stacks of all types together in one chunk (owner R3-26: 40), on top of the per-type limit above. More spawns join a stack in the chunk or are cancelled.", min = 1, max = 1024, level = Setting.Level.OWNER)
    public int mobMaxStacksPerChunkTotal = 40;

    @Setting(path = "mobs.spawn-reasons", desc = "Spawn reasons that stack (Paper SpawnReason names). Plugin spawns (CUSTOM, COMMAND) never stack.")
    public List<String> mobSpawnReasons = List.of("NATURAL", "SPAWNER", "SPAWNER_EGG", "DISPENSE_EGG", "EGG", "BREEDING",
            "VILLAGE_DEFENSE", "BUILD_IRONGOLEM", "BUILD_SNOWMAN", "SLIME_SPLIT", "DEFAULT");

    @Setting(path = "mobs.never-stack", desc = "Extra mob types that never stack (entity ids, e.g. fox). Villagers, bosses, allays, tamed, named, leashed and saddled mobs never stack anyway.")
    public List<String> mobNeverStack = List.of();

    @Setting(path = "mobs.whole-stack-damage", desc = "Damage types that kill the whole stack at once (owner S5: fall, lava and void, so mob grinders work). Damage type ids.")
    public List<String> wholeStackDamage = List.of("fall", "lava", "out_of_world", "generic_kill");

    @Setting(path = "mobs.loot-samples", desc = "When many mobs die at once, the loot table is rolled this many times and scaled to the number killed (accurate averages without rolling 1,024 times).", min = 1, max = 64)
    public int lootSamples = 8;

    @Setting(path = "mobs.breed-cooldown-seconds", desc = "After breeding, the parents used wait this long before they breed again (vanilla: 300).", min = 0, max = 3600)
    public int breedCooldownSeconds = 300;

    @Setting(path = "mobs.breed-xp-min", desc = "Least XP per baby bred (vanilla 1).", min = 0, max = 100)
    public int breedXpMin = 1;

    @Setting(path = "mobs.breed-xp-max", desc = "Most XP per baby bred (vanilla 7).", min = 0, max = 100)
    public int breedXpMax = 7;

    @Setting(path = "mobs.shear-whole-stack", desc = "Shearing a stack of sheep shears every sheep (wool times the stack). Off: stacks can't be sheared.")
    public boolean shearWholeStack = true;

    @Setting(path = "mobs.natural-merge-interval-ticks", desc = "How often moving (natural) mobs are checked for stacks that walked close to each other.", min = 10, max = 1200)
    public int naturalMergeIntervalTicks = 40;

    @Setting(path = "mobs.natural-merge-budget", desc = "Most moving mobs checked per run (spreads the work over several runs).", min = 10, max = 10_000)
    public int naturalMergeBudget = 200;

    @Setting(path = "mobs.spawner-mobs-hurt-players", desc = "Spawner mobs can hurt players (contact, poison, explosions, arrows). "
            + "Off (feedback 1 Nov 2026): they stand still and never do damage; players still kill them.")
    public boolean spawnerMobsHurtPlayers = false;

    @Setting(path = "mobs.spawner-mobs-burn-in-day", desc = "Zombies and skeletons from spawners burn in daylight (owner S25: spawn conditions ignored, so off).")
    public boolean spawnerMobsBurnInDay = false;

    // ------------------------------------------------------------------ items

    @Setting(path = "items.merge-radius", desc = "Blocks within which dropped items of the same kind merge into one (owner S14: 3).", min = 0.5, max = 16)
    public double itemMergeRadius = 3.0;

    @Setting(path = "items.despawn-minutes", desc = "Dropped items on islands disappear after about this long (owner S18: 10).", min = 1, max = 120)
    public int itemDespawnMinutes = 10;

    @Setting(path = "items.hopper-per-transfer", desc = "Most items a hopper takes from a stacked item at once (owner S17: 64).", min = 1, max = 3456)
    public int hopperPerTransfer = 64;

    @Setting(path = "items.name-min-count", desc = "Show 'Diamond x1,234' above dropped items from this many items.", min = 1, max = 1_000_000)
    public int itemNameMinCount = 2;

    // ------------------------------------------------------------------ spawners

    @Setting(path = "spawners.stack-start", desc = "Spawners one block holds without upgrades (owner S20: 8).", min = 1, max = 100_000, level = Setting.Level.OWNER)
    public int spawnerStackStart = 8;

    @Setting(path = "spawners.stack-max", desc = "Spawners one block can ever hold, with the Spawner Stack Limit upgrade (ECON-SPAWNERS S4: 32; was 512). Blocks above it keep their spawners but can't grow.", min = 1, max = 100_000, level = Setting.Level.OWNER)
    public int spawnerStackMax = 32;

    @Setting(path = "spawners.interval-jitter-percent", desc = "Random spread of each spawn interval in percent (0 = exact; a little desyncs many spawners).", min = 0, max = 50)
    public int spawnerIntervalJitterPercent = 0;

    @Setting(path = "spawners.interval-floor-seconds", desc = "No spawner ever spawns faster than this, whatever the steps say (protects the main thread).", min = 1, max = 60, level = Setting.Level.OWNER)
    public int spawnerIntervalFloorSeconds = 4;

    @Setting(path = "spawners.speed-steps-seconds", desc = "Seconds between spawns per speed upgrade level (level 0 first; /is upgrade). A level above the list uses the last step.", level = Setting.Level.OWNER)
    public List<String> speedSteps = List.of("10", "9", "8", "7", "6", "5");

    @Setting(path = "spawners.mob-steps", desc = "Mobs each spawner adds per spawn, per mobs upgrade level (level 0 first).", level = Setting.Level.OWNER)
    public List<String> mobSteps = List.of("1.0", "1.25", "1.5", "1.75", "2.0");

    @Setting(path = "spawners.loot-steps", desc = "Custom drop multiplier per Mob Loot upgrade level (level 0 first).", level = Setting.Level.OWNER)
    public List<String> lootSteps = List.of("1.0", "1.1", "1.2", "1.3", "1.4", "1.5");

    @Setting(path = "spawners.max-per-tier", desc = "Spawners of one tier an island can hold (all blocks; ECON-SPAWNERS S3: 32). 0 = no cap. Islands above it keep theirs (grandfathered) but can't add more.", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public int spawnerMaxPerTier = 32;

    @Setting(path = "spawners.cap-perk-max", desc = "Most extra spawners per tier a credit perk can add to the cap.", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public int spawnerCapPerkMax = 64;

    @Setting(path = "spawners.legacy", desc = "Spawner types that left the roster (mob:tier): they keep working with vanilla loot, count in that tier, but are never sold.", level = Setting.Level.OWNER)
    public List<String> legacy = List.of("squid:2", "magma_cube:3", "glow_squid:4", "piglin:4", "shulker:5", "ravager:5", "breeze:5");

    @Setting(path = "spawners.unlock.tier-2.kills", desc = "Kills of tier 1 mobs on an island before its shop sells tier 2 spawners (0 = open).", min = 0, max = 100_000_000, level = Setting.Level.OWNER)
    public long unlockTier2Kills = 1000;

    @Setting(path = "spawners.unlock.tier-3.kills", desc = "Kills of tier 2 mobs before tier 3 spawners are sold (0 = open).", min = 0, max = 100_000_000, level = Setting.Level.OWNER)
    public long unlockTier3Kills = 5000;

    @Setting(path = "spawners.unlock.tier-4.kills", desc = "Kills of tier 3 mobs before tier 4 spawners are sold (0 = open).", min = 0, max = 100_000_000, level = Setting.Level.OWNER)
    public long unlockTier4Kills = 20000;

    @Setting(path = "spawners.unlock.tier-5.kills", desc = "Kills of tier 4 mobs before tier 5 spawners are sold (premium: 0 = no gate).", min = 0, max = 100_000_000, level = Setting.Level.OWNER)
    public long unlockTier5Kills = 0;

    @Setting(path = "spawners.unlock.tier-2.mobs", desc = "Only these tier 1 mobs count for the tier 2 unlock (empty = every tier 1 mob).")
    public List<String> unlockTier2Mobs = List.of();

    @Setting(path = "spawners.unlock.tier-3.mobs", desc = "Only these tier 2 mobs count for the tier 3 unlock (empty = every tier 2 mob).")
    public List<String> unlockTier3Mobs = List.of();

    @Setting(path = "spawners.unlock.tier-4.mobs", desc = "Only these tier 3 mobs count for the tier 4 unlock (empty = every tier 3 mob).")
    public List<String> unlockTier4Mobs = List.of();

    @Setting(path = "spawners.unlock.tier-5.mobs", desc = "Only these tier 4 mobs count for the tier 5 unlock (empty = every tier 4 mob).")
    public List<String> unlockTier5Mobs = List.of();

    @Setting(path = "spawners.unlock.save-seconds", desc = "Kill counts are written in one batch this often.", min = 5, max = 600, restart = true)
    public int unlockSaveSeconds = 30;

    @Setting(path = "spawners.drops", desc = "Custom drop per spawner mob: mob:item id:amount per mob (ECON-SPAWNERS S6). Spawner mobs with a line drop only this item; others keep vanilla loot.", level = Setting.Level.OWNER)
    public List<String> spawnerDrops = List.of(
            // Owner 3 Nov 2026: each mob drops its own vanilla item; the amount per kill keeps the income of the balance
            // model (old custom drop price / sell price of the item). Premium mobs keep their custom drop.
            "pig:porkchop:0.5", "cow:beef:0.5", "chicken:chicken:0.5", "sheep:mutton:0.5", "rabbit:rabbit:0.5",
            "zombie:rotten_flesh:0.55", "skeleton:bone:0.55", "spider:string:0.55", "cave_spider:spider_eye:0.55",
            "slime:slime_ball:0.55", "creeper:gunpowder:0.48", "witch:glowstone_dust:0.48", "drowned:rotten_flesh:0.89",
            "husk:rotten_flesh:0.89", "stray:bone:0.89", "blaze:blaze_rod:0.49", "enderman:ender_pearl:0.49",
            "guardian:prismarine_shard:0.49", "hoglin:porkchop:3", "zombified_piglin:rotten_flesh:1.67",
            "iron_golem:iron_ingot:4.4", "wither_skeleton:mobdrop_wither_skeleton:1", "evoker:mobdrop_evoker:1");

    /** The custom-item drops before 3 Nov 2026 (version 3 moves untouched lists to the vanilla items). */
    static final List<String> SPAWNER_DROPS_V2 = List.of("pig:mobdrop_pig:1", "cow:mobdrop_cow:1", "chicken:mobdrop_chicken:1", "sheep:mobdrop_sheep:1",
            "rabbit:mobdrop_rabbit:1", "zombie:mobdrop_zombie:1", "skeleton:mobdrop_skeleton:1", "spider:mobdrop_spider:1",
            "cave_spider:mobdrop_cave_spider:1", "slime:mobdrop_slime:1", "creeper:mobdrop_creeper:1", "witch:mobdrop_witch:1",
            "drowned:mobdrop_drowned:1", "husk:mobdrop_husk:1", "stray:mobdrop_stray:1", "blaze:mobdrop_blaze:1",
            "enderman:mobdrop_enderman:1", "guardian:mobdrop_guardian:1", "hoglin:mobdrop_hoglin:1",
            "zombified_piglin:mobdrop_zombified_piglin:1", "iron_golem:mobdrop_iron_golem:1",
            "wither_skeleton:mobdrop_wither_skeleton:1", "evoker:mobdrop_evoker:1");

    @Setting(path = "spawners.drop-items", desc = "Custom drop items: item id=material:rarity (names in messages/stacking.yml drops.<id>, sell prices in sell.yml custom-items.spawner-drops). Pick inert materials (no food, block, potion or tool).", level = Setting.Level.OWNER)
    public List<String> spawnerDropItems = List.of("mobdrop_pig=clay_ball:common", "mobdrop_cow=clay_ball:common",
            "mobdrop_chicken=clay_ball:common", "mobdrop_sheep=clay_ball:common", "mobdrop_rabbit=clay_ball:common",
            "mobdrop_zombie=flint:uncommon", "mobdrop_skeleton=flint:uncommon", "mobdrop_spider=flint:uncommon",
            "mobdrop_cave_spider=flint:uncommon", "mobdrop_slime=flint:uncommon", "mobdrop_creeper=iron_nugget:uncommon",
            "mobdrop_witch=iron_nugget:uncommon", "mobdrop_drowned=iron_nugget:uncommon", "mobdrop_husk=iron_nugget:uncommon",
            "mobdrop_stray=iron_nugget:uncommon", "mobdrop_blaze=gold_nugget:rare", "mobdrop_enderman=gold_nugget:rare",
            "mobdrop_guardian=gold_nugget:rare", "mobdrop_hoglin=gold_nugget:rare", "mobdrop_zombified_piglin=gold_nugget:rare",
            "mobdrop_iron_golem=echo_shard:epic", "mobdrop_wither_skeleton=echo_shard:epic", "mobdrop_evoker=echo_shard:epic");

    @Setting(path = "spawners.mobs-per-spawner", desc = "Mobs each stacked spawner adds per spawn (owner answer 641: 1; vanilla up to 4). Output = this x mob step x stack x F2 efficiency.", min = 0.01, max = 100, level = Setting.Level.OWNER)
    public double mobsPerSpawner = 1.0;

    @Setting(path = "spawners.activation-range", desc = "A spawner runs while a player is this close (owner S24: 16 blocks), or while the island has fuel and nobody is online.", min = 1, max = 128)
    public int activationRange = 16;

    @Setting(path = "spawners.spawn-range", desc = "Mobs appear up to this many blocks from the spawner (vanilla 4). Light, space and blocks are ignored (owner S25).", min = 0, max = 8)
    public int spawnRange = 4;

    @Setting(path = "spawners.due-per-tick", desc = "Most spawner blocks handled in one tick (the rest wait a tick; keeps lag spikes away).", min = 1, max = 10_000)
    public int spawnerDuePerTick = 100;

    @Setting(path = "spawners.frozen-menu-limit", desc = "R3-13b: most frozen spawners shown in a player's staff restore menu (/inspect → Frozen spawners), oldest first.", min = 10, max = 2_000)
    public int frozenMenuLimit = 200;

    @Setting(path = "spawners.tier-1", desc = "Tier 1 spawner types (owner SK3, shop).", level = Setting.Level.OWNER)
    public List<String> tier1 = List.of("pig", "cow", "chicken", "sheep", "rabbit");

    @Setting(path = "spawners.tier-2", desc = "Tier 2 spawner types (Cave; ECON-SPAWNERS: 5 mobs, unlock by tier 1 kills).", level = Setting.Level.OWNER)
    public List<String> tier2 = List.of("zombie", "skeleton", "spider", "cave_spider", "slime");

    @Setting(path = "spawners.tier-3", desc = "Tier 3 spawner types (Wild; unlock by tier 2 kills).", level = Setting.Level.OWNER)
    public List<String> tier3 = List.of("creeper", "witch", "drowned", "husk", "stray");

    @Setting(path = "spawners.tier-4", desc = "Tier 4 spawner types (Nether; unlock by tier 3 kills).", level = Setting.Level.OWNER)
    public List<String> tier4 = List.of("blaze", "enderman", "guardian", "hoglin", "zombified_piglin");

    @Setting(path = "spawners.tier-5", desc = "Tier 5 = premium spawner types (Black Market for shards, credit shop later; never in /shop). Villager, warden, wither, ender dragon, elder guardian and allay are never allowed (SK8).", level = Setting.Level.OWNER)
    public List<String> tier5 = List.of("iron_golem", "wither_skeleton", "evoker");

    @Setting(path = "spawners.efficiency", desc = "Owner F2: diminishing output per island and tier. All spawners of one tier on an island are counted (every block, outposts later); the k-th one works at the percent of its band. Format from-to:percent, the last band open (129+:10). Bands start at 1 without gaps. Empty list = every spawner at 100%. A broken list falls back to these defaults.", level = Setting.Level.OWNER)
    public List<String> spawnerEfficiency = List.of("1-16:100", "17-32:60", "33-64:35", "65-128:20", "129+:10");

    @Setting(path = "spawners.rare-drops", desc = "Strong drops from spawner mobs get this chance per mob instead of the vanilla one (owner SK10: very rare). Format mob:item:chance, chance 0-1.", level = Setting.Level.OWNER)
    public List<String> rareDrops = List.of("evoker:totem_of_undying:0.02");

    // ------------------------------------------------------------------ island entity limits (owner SK2)

    @Setting(path = "limits.armor-stands", desc = "Armor stands per island (owner SK2: 20, +50% with the upgrade).", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public int limitArmorStands = 20;

    @Setting(path = "limits.item-frames", desc = "Item frames (also glow item frames) per island (owner SK2: 60).", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public int limitItemFrames = 60;

    @Setting(path = "limits.minecarts", desc = "Minecarts (all kinds) per island (owner SK2: 20).", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public int limitMinecarts = 20;

    @Setting(path = "limits.boats", desc = "Boats and rafts (also with chests) per island (owner SK2: 10).", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public int limitBoats = 10;

    @Setting(path = "limits.villagers", desc = "Villagers per island (owner SK2: 20).", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public int limitVillagers = 20;

    @Setting(path = "limits.displays", desc = "Display/hologram entities per island (owner SK2: 30).", min = 0, max = 100_000, level = Setting.Level.OWNER)
    public int limitDisplays = 30;

    // ------------------------------------------------------------------ hopper counter (owner F13)

    @Setting(path = "hoppers.counter-enabled", desc = "Count hoppers per loaded chunk in the stacking worlds (owner F13). Counted once when a chunk loads, then kept up to date by place/break/explode events; never scanned per tick.")
    public boolean hopperCounterEnabled = true;

    @Setting(path = "hoppers.alert-above", desc = "Staff get an alert when one chunk has more hoppers than this (owner F13: 64). There is no hard cap.", min = 1, max = 100_000, level = Setting.Level.OWNER)
    public int hopperAlertAbove = 64;

    @Setting(path = "hoppers.alert-step", desc = "Alert again for every this many hoppers more in the same chunk (64 above 64: at 65, 129, 193...). 0 = only the first time.", min = 0, max = 100_000)
    public int hopperAlertStep = 64;

    @Setting(path = "hoppers.alert-cooldown-minutes", desc = "The same chunk does not alert again at the same level within this time (placing and breaking one hopper does not spam).", min = 1, max = 10_080)
    public int hopperAlertCooldownMinutes = 30;

    @Setting(path = "hoppers.alerts-per-minute", desc = "Most hopper alerts for all chunks together per minute.", min = 1, max = 600)
    public int hopperAlertsPerMinute = 5;

    // ------------------------------------------------------------------ crops (owner S25)

    @Setting(path = "crops.enabled", desc = "Crops on islands also grow in the dark (owner S25).")
    public boolean cropsEnabled = true;

    @Setting(path = "crops.types", desc = "Blocks that grow without light (block ids). Melon and pumpkin stems also grow their fruit.")
    public List<String> cropTypes = List.of("wheat", "carrots", "potatoes", "beetroots", "melon_stem", "pumpkin_stem", "sweet_berry_bush",
            "torchflower_crop");

    @Setting(path = "crops.check-interval-seconds", desc = "How often dark crops get a growth chance.", min = 5, max = 3600)
    public int cropCheckIntervalSeconds = 30;

    @Setting(path = "crops.grow-chance", desc = "Chance per check that a dark crop grows one stage (0.15 every 30 s is about vanilla speed on watered farmland).", min = 0, max = 1)
    public double cropGrowChance = 0.15;

    @Setting(path = "crops.light-level", desc = "Crops with at least this much light grow the vanilla way and are skipped (vanilla 9).", min = 0, max = 15)
    public int cropLightLevel = 9;

    @Setting(path = "crops.per-tick", desc = "Most crops checked in one tick (a check round is spread over several ticks).", min = 10, max = 100_000)
    public int cropsPerTick = 2000;

    @Override
    public int version() {
        return 3;
    }

    /** ECON-SPAWNERS step 3: the new roster and defaults, applied only while the live value is still the old default. */
    /** ECON-SPAWNERS: the spawn time comes from spawners.speed-steps-seconds; the old interval keys are deleted on load. */
    @Override
    public java.util.Map<String, String> removedKeys() {
        return java.util.Map.of(
                "spawners.min-interval-seconds", "ECON-SPAWNERS: every spawner spawns on its island's speed step (spawners.speed-steps-seconds)",
                "spawners.max-interval-seconds", "ECON-SPAWNERS: every spawner spawns on its island's speed step (spawners.speed-steps-seconds)");
    }

    @Override
    public java.util.Map<Integer, java.util.Map<String, ValueChange>> valueChanges() {
        return java.util.Map.of(3, java.util.Map.of("spawners.drops", ValueChange.of(SPAWNER_DROPS_V2, new StackingConfig().spawnerDrops)),
                2, java.util.Map.of(
                "spawners.stack-max", ValueChange.of(512, 32),
                "spawners.tier-2", ValueChange.of(List.of("zombie", "skeleton", "spider", "cave_spider", "slime", "squid"),
                        List.of("zombie", "skeleton", "spider", "cave_spider", "slime")),
                "spawners.tier-3", ValueChange.of(List.of("creeper", "witch", "drowned", "husk", "stray", "magma_cube"),
                        List.of("creeper", "witch", "drowned", "husk", "stray")),
                "spawners.tier-4", ValueChange.of(List.of("blaze", "enderman", "guardian", "piglin", "zombified_piglin", "hoglin", "glow_squid"),
                        List.of("blaze", "enderman", "guardian", "hoglin", "zombified_piglin")),
                "spawners.tier-5", ValueChange.of(List.of("iron_golem", "wither_skeleton", "shulker", "evoker", "ravager", "breeze"),
                        List.of("iron_golem", "wither_skeleton", "evoker")),
                "spawners.rare-drops", ValueChange.of(List.of("evoker:totem_of_undying:0.02", "shulker:shulker_shell:0.05"),
                        List.of("evoker:totem_of_undying:0.02"))));
    }

    @Override
    public String fileName() {
        return "stacking.yml";
    }

    @Override
    public String header() {
        return """
                Stacking: mobs, dropped items and spawners stack on islands; island entity limits; crops grow in the dark.
                Texts: messages/stacking.yml. Reload: /skyblock reload stacking""";
    }
}

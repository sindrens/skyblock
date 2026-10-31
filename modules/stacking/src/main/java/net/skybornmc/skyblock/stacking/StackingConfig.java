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

    @Setting(path = "spawners.stack-max", desc = "Spawners one block can ever hold, with the Spawner Stack Limit mastery (owner S20: 512).", min = 1, max = 100_000, level = Setting.Level.OWNER)
    public int spawnerStackMax = 512;

    @Setting(path = "spawners.min-interval-seconds", desc = "Shortest time between two spawns of one spawner block (vanilla 10).", min = 1, max = 3600, level = Setting.Level.OWNER)
    public int spawnerMinIntervalSeconds = 10;

    @Setting(path = "spawners.max-interval-seconds", desc = "Longest time between two spawns of one spawner block (vanilla 40).", min = 1, max = 3600, level = Setting.Level.OWNER)
    public int spawnerMaxIntervalSeconds = 40;

    @Setting(path = "spawners.mobs-per-spawner", desc = "Mobs each stacked spawner adds per spawn (owner answer 641: 1; vanilla up to 4). Output = this x stack x Spawner Rate mastery (owner S23).", min = 0.01, max = 100, level = Setting.Level.OWNER)
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

    @Setting(path = "spawners.tier-2", desc = "Tier 2 spawner types (owner SK4, shop + The Depths).", level = Setting.Level.OWNER)
    public List<String> tier2 = List.of("zombie", "skeleton", "spider", "cave_spider", "slime", "squid");

    @Setting(path = "spawners.tier-3", desc = "Tier 3 spawner types (owner SK5, The Depths + crates).", level = Setting.Level.OWNER)
    public List<String> tier3 = List.of("creeper", "witch", "drowned", "husk", "stray", "magma_cube");

    @Setting(path = "spawners.tier-4", desc = "Tier 4 spawner types (owner SK6, crates).", level = Setting.Level.OWNER)
    public List<String> tier4 = List.of("blaze", "enderman", "guardian", "piglin", "zombified_piglin", "hoglin", "glow_squid");

    @Setting(path = "spawners.tier-5", desc = "Tier 5 spawner types (owner SK7, crates + Black Market). Villager, warden, wither, ender dragon, elder guardian and allay are never allowed (SK8).", level = Setting.Level.OWNER)
    public List<String> tier5 = List.of("iron_golem", "wither_skeleton", "shulker", "evoker", "ravager", "breeze");

    @Setting(path = "spawners.efficiency", desc = "Owner F2: diminishing output per island and tier. All spawners of one tier on an island are counted (every block, outposts later); the k-th one works at the percent of its band. Format from-to:percent, the last band open (129+:10). Bands start at 1 without gaps. Empty list = every spawner at 100%. A broken list falls back to these defaults.", level = Setting.Level.OWNER)
    public List<String> spawnerEfficiency = List.of("1-16:100", "17-32:60", "33-64:35", "65-128:20", "129+:10");

    @Setting(path = "spawners.rare-drops", desc = "Strong drops from spawner mobs get this chance per mob instead of the vanilla one (owner SK10: very rare). Format mob:item:chance, chance 0-1.", level = Setting.Level.OWNER)
    public List<String> rareDrops = List.of("evoker:totem_of_undying:0.02", "shulker:shulker_shell:0.05");

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

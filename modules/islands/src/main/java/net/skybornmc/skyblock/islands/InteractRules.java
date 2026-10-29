package net.skybornmc.skyblock.islands;

import java.util.Set;

/**
 * Which {@link IslandPerm} a block or item interaction needs, decided by material name (pure, no Bukkit, so it is unit
 * tested and works for blocks added by future versions with the usual names). {@link IslandProtection} turns these into
 * {@code EnumMap}s once at enable, so the lookup per event is one array read.
 */
public final class InteractRules {

    private static final Set<String> WORKSTATIONS = Set.of("CRAFTING_TABLE", "ANVIL", "CHIPPED_ANVIL", "DAMAGED_ANVIL",
            "ENCHANTING_TABLE", "GRINDSTONE", "STONECUTTER", "LOOM", "CARTOGRAPHY_TABLE", "SMITHING_TABLE", "FLETCHING_TABLE");
    private static final Set<String> CONTAINERS = Set.of("CHEST", "TRAPPED_CHEST", "BARREL", "FURNACE", "BLAST_FURNACE", "SMOKER",
            "HOPPER", "DROPPER", "DISPENSER", "BREWING_STAND", "BEACON", "CHISELED_BOOKSHELF", "DECORATED_POT", "LECTERN", "JUKEBOX",
            "CRAFTER", "COMPOSTER", "CAULDRON", "WATER_CAULDRON", "LAVA_CAULDRON", "POWDER_SNOW_CAULDRON", "VAULT", "CAMPFIRE",
            "SOUL_CAMPFIRE");
    private static final Set<String> REDSTONE = Set.of("REPEATER", "COMPARATOR", "DAYLIGHT_DETECTOR", "NOTE_BLOCK", "REDSTONE_WIRE");
    private static final Set<String> INTERACT = Set.of("LEVER", "BELL", "RESPAWN_ANCHOR", "CAKE", "CANDLE_CAKE");
    private static final Set<String> HARVEST = Set.of("SWEET_BERRY_BUSH", "CAVE_VINES", "CAVE_VINES_PLANT", "BEEHIVE", "BEE_NEST",
            "DRAGON_EGG");
    private static final Set<String> SPAWNERS = Set.of("SPAWNER", "TRIAL_SPAWNER");
    private static final Set<String> MODIFY = Set.of("FLOWER_POT", "END_PORTAL_FRAME");
    private static final Set<String> MODIFYING_ITEMS = Set.of("BONE_MEAL", "HONEYCOMB", "ENDER_EYE");
    private static final Set<String> TRAMPLE = Set.of("FARMLAND", "TURTLE_EGG", "SNIFFER_EGG");

    private InteractRules() {
    }

    /** Permission needed to right-click a block of this material, or null (no protection: e.g. stone, ender chest). */
    public static IslandPerm block(String material) {
        if (material == null || material.equals("ENDER_CHEST")) {
            return null;
        }
        if (SPAWNERS.contains(material)) {
            return IslandPerm.SPAWNERS;
        }
        if (CONTAINERS.contains(material) || material.endsWith("SHULKER_BOX") || material.endsWith("_CHEST")
                || material.endsWith("_SHELF")) {
            return IslandPerm.CONTAINERS;
        }
        if (REDSTONE.contains(material)) {
            return IslandPerm.REDSTONE;
        }
        if (INTERACT.contains(material) || WORKSTATIONS.contains(material) || material.endsWith("_DOOR")
                || material.endsWith("_TRAPDOOR") || material.endsWith("_FENCE_GATE") || material.endsWith("_BUTTON")
                || material.endsWith("_BED") || material.endsWith("_CANDLE_CAKE") || material.endsWith("_PRESSURE_PLATE")) {
            return IslandPerm.INTERACT;
        }
        if (HARVEST.contains(material)) {
            return IslandPerm.BREAK;
        }
        if (MODIFY.contains(material) || material.startsWith("POTTED_") || material.endsWith("_SIGN")
                || material.equals("CANDLE") || material.endsWith("_CANDLE")) {
            return IslandPerm.PLACE;
        }
        return null;
    }

    /** Permission needed to use this item on a block (right click), or null. Checked in addition to {@link #block}. */
    public static IslandPerm item(String material) {
        if (material == null) {
            return null;
        }
        if (material.equals("FLINT_AND_STEEL") || material.equals("FIRE_CHARGE")) {
            return IslandPerm.FIRE;
        }
        if (material.equals("BRUSH")) {
            return IslandPerm.BREAK;
        }
        if (MODIFYING_ITEMS.contains(material) || material.endsWith("_SPAWN_EGG") || material.endsWith("_AXE")
                || material.endsWith("_SHOVEL") || material.endsWith("_HOE")) {
            return IslandPerm.PLACE;
        }
        return null;
    }

    /** Permission needed to step on this block (physical interaction), or null. */
    public static IslandPerm physical(String material) {
        if (material == null) {
            return null;
        }
        if (TRAMPLE.contains(material)) {
            return IslandPerm.BREAK;
        }
        if (material.endsWith("_PRESSURE_PLATE") || material.equals("TRIPWIRE")) {
            return IslandPerm.INTERACT;
        }
        return null;
    }
}

package net.skybornmc.skyblock.islands;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InteractRulesTest {

    @Test
    void blocksNeedTheRightPermission() {
        assertEquals(IslandPerm.INTERACT, InteractRules.block("OAK_DOOR"));
        assertEquals(IslandPerm.INTERACT, InteractRules.block("IRON_TRAPDOOR"));
        assertEquals(IslandPerm.INTERACT, InteractRules.block("SPRUCE_FENCE_GATE"));
        assertEquals(IslandPerm.INTERACT, InteractRules.block("STONE_BUTTON"));
        assertEquals(IslandPerm.INTERACT, InteractRules.block("LEVER"));
        assertEquals(IslandPerm.INTERACT, InteractRules.block("LIGHT_WEIGHTED_PRESSURE_PLATE"));
        assertEquals(IslandPerm.INTERACT, InteractRules.block("RED_BED"));
        assertEquals(IslandPerm.INTERACT, InteractRules.block("RESPAWN_ANCHOR"));
        assertEquals(IslandPerm.INTERACT, InteractRules.block("ANVIL"));
        assertEquals(IslandPerm.INTERACT, InteractRules.block("BLUE_CANDLE_CAKE"));
        assertEquals(IslandPerm.CONTAINERS, InteractRules.block("CHEST"));
        assertEquals(IslandPerm.CONTAINERS, InteractRules.block("TRAPPED_CHEST"));
        assertEquals(IslandPerm.CONTAINERS, InteractRules.block("COPPER_CHEST"));
        assertEquals(IslandPerm.CONTAINERS, InteractRules.block("SHULKER_BOX"));
        assertEquals(IslandPerm.CONTAINERS, InteractRules.block("LIME_SHULKER_BOX"));
        assertEquals(IslandPerm.CONTAINERS, InteractRules.block("BARREL"));
        assertEquals(IslandPerm.CONTAINERS, InteractRules.block("HOPPER"));
        assertEquals(IslandPerm.CONTAINERS, InteractRules.block("LECTERN"));
        assertEquals(IslandPerm.CONTAINERS, InteractRules.block("OAK_SHELF"));
        assertEquals(IslandPerm.REDSTONE, InteractRules.block("REPEATER"));
        assertEquals(IslandPerm.REDSTONE, InteractRules.block("COMPARATOR"));
        assertEquals(IslandPerm.REDSTONE, InteractRules.block("NOTE_BLOCK"));
        assertEquals(IslandPerm.SPAWNERS, InteractRules.block("SPAWNER"));
        assertEquals(IslandPerm.SPAWNERS, InteractRules.block("TRIAL_SPAWNER"));
        assertEquals(IslandPerm.BREAK, InteractRules.block("SWEET_BERRY_BUSH"));
        assertEquals(IslandPerm.PLACE, InteractRules.block("POTTED_CACTUS"));
        assertEquals(IslandPerm.PLACE, InteractRules.block("OAK_HANGING_SIGN"));
        assertEquals(IslandPerm.PLACE, InteractRules.block("BIRCH_WALL_SIGN"));
        assertNull(InteractRules.block("ENDER_CHEST"), "personal storage");
        assertNull(InteractRules.block("STONE"));
        assertNull(InteractRules.block("BOOKSHELF"));
        assertNull(InteractRules.block(null));
    }

    @Test
    void itemsUsedOnBlocks() {
        assertEquals(IslandPerm.FIRE, InteractRules.item("FLINT_AND_STEEL"));
        assertEquals(IslandPerm.FIRE, InteractRules.item("FIRE_CHARGE"));
        assertEquals(IslandPerm.PLACE, InteractRules.item("BONE_MEAL"));
        assertEquals(IslandPerm.PLACE, InteractRules.item("DIAMOND_AXE"));
        assertEquals(IslandPerm.PLACE, InteractRules.item("WOODEN_HOE"));
        assertEquals(IslandPerm.PLACE, InteractRules.item("IRON_SHOVEL"));
        assertEquals(IslandPerm.PLACE, InteractRules.item("HONEYCOMB"));
        assertEquals(IslandPerm.PLACE, InteractRules.item("ZOMBIE_SPAWN_EGG"));
        assertEquals(IslandPerm.BREAK, InteractRules.item("BRUSH"));
        assertNull(InteractRules.item("DIAMOND_PICKAXE"), "pickaxes don't change blocks by right click");
        assertNull(InteractRules.item("BREAD"));
    }

    @Test
    void steppingOnBlocks() {
        assertEquals(IslandPerm.BREAK, InteractRules.physical("FARMLAND"));
        assertEquals(IslandPerm.BREAK, InteractRules.physical("TURTLE_EGG"));
        assertEquals(IslandPerm.INTERACT, InteractRules.physical("OAK_PRESSURE_PLATE"));
        assertEquals(IslandPerm.INTERACT, InteractRules.physical("TRIPWIRE"));
        assertNull(InteractRules.physical("GRASS_BLOCK"));
    }

    @Test
    void realMaterialNamesAreClassified() {
        // guards against renamed materials: these must exist in the Paper version we build against
        for (Material m : new Material[]{Material.CHEST, Material.OAK_DOOR, Material.LEVER, Material.REPEATER, Material.SPAWNER,
                Material.FARMLAND, Material.FLINT_AND_STEEL}) {
            boolean classified = InteractRules.block(m.name()) != null || InteractRules.item(m.name()) != null
                    || InteractRules.physical(m.name()) != null;
            assertEquals(true, classified, m.name());
        }
    }
}

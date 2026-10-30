package net.skybornmc.skyblock.worlds.protection;

import net.skybornmc.skyblock.worlds.WorldsConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockUseTest {

    private final WorldsConfig c = new WorldsConfig();
    private final BlockUse use = new BlockUse(c.protectionInteractAllowed, c.protectionContainers, c.protectionWorkstations);

    @Test
    void doorsAndFriendsStayFree() {
        for (String key : List.of("oak_door", "iron_door", "stone_button", "lever", "heavy_weighted_pressure_plate", "spruce_fence_gate",
                "ender_chest", "crafting_table", "oak_sign", "minecraft:cherry_wall_hanging_sign")) {
            assertEquals(BlockUse.Use.ALLOWED, use.classify(key), key);
        }
    }

    @Test
    void containersAndWorkstations() {
        for (String key : List.of("chest", "barrel", "shulker_box", "red_shulker_box", "blast_furnace", "hopper", "lectern", "campfire",
                "potted_fern", "flower_pot", "water_cauldron", "cauldron", "decorated_pot")) {
            assertEquals(BlockUse.Use.CONTAINER, use.classify(key), key);
        }
        for (String key : List.of("anvil", "chipped_anvil", "damaged_anvil", "enchanting_table", "grindstone", "smithing_table", "stonecutter")) {
            assertEquals(BlockUse.Use.WORKSTATION, use.classify(key), key);
        }
    }

    @Test
    void everythingElseIsOther() {
        for (String key : List.of("oak_trapdoor", "repeater", "cake", "note_block", "stone", "bell")) {
            assertEquals(BlockUse.Use.OTHER, use.classify(key), key);
        }
    }

    @Test
    void freeListWinsOverOtherLists() {
        BlockUse custom = new BlockUse(List.of("chest", " Anvil "), List.of("chest"), List.of("anvil"));
        assertEquals(BlockUse.Use.ALLOWED, custom.classify("chest"));
        assertEquals(BlockUse.Use.ALLOWED, custom.classify("ANVIL"));
    }
}

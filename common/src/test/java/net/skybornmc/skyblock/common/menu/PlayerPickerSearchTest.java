package net.skybornmc.skyblock.common.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GUI-4b: the player picker's search by part of a name. */
class PlayerPickerSearchTest {

    @Test
    void partOfTheNameInAnyCase() {
        assertTrue(PlayerPickerMenu.matches("SlickyMatt", "matt"));
        assertTrue(PlayerPickerMenu.matches("SlickyMatt", "SLICK"));
        assertTrue(PlayerPickerMenu.matches(".BedrockAlex", "alex"), "Bedrock names with the Floodgate prefix");
        assertFalse(PlayerPickerMenu.matches("Steve", "alex"));
        assertFalse(PlayerPickerMenu.matches(null, "a"));
    }

    @Test
    void noSearchMatchesEveryone() {
        assertTrue(PlayerPickerMenu.matches("Steve", null));
        assertTrue(PlayerPickerMenu.matches("Steve", ""));
    }
}

package net.skybornmc.skyblock.common.settings;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingToggleChoiceTest {

    private static SettingToggle choice(String def) {
        return SettingToggle.choice("core", "show-players", SettingCategory.DISPLAY, Material.SPYGLASS,
                List.of("everyone", "friends", "nobody"), def, null, "navigation", "settings.toggles.show-players");
    }

    @Test
    void onOffSwitchHasNoChoices() {
        SettingToggle t = new SettingToggle("m", "tips", SettingCategory.DISPLAY, Material.LANTERN, true, null, "n", "k", false);
        assertFalse(t.isChoice());
        assertTrue(t.choices().isEmpty());
        assertNull(t.defaultChoice());
    }

    @Test
    void choiceSettingCyclesThroughChoices() {
        SettingToggle t = choice("everyone");
        assertTrue(t.isChoice());
        assertEquals("friends", t.nextChoice("everyone"));
        assertEquals("nobody", t.nextChoice("friends"));
        assertEquals("everyone", t.nextChoice("nobody"));
        assertEquals("everyone", t.nextChoice("unknown"));
    }

    @Test
    void defaultMustBeAChoice() {
        assertThrows(IllegalArgumentException.class, () -> choice("pinned"));
    }

    @Test
    void choiceIdsAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> SettingToggle.choice("core", "x", SettingCategory.DISPLAY, Material.STONE,
                List.of("Bad Value"), "Bad Value", null, "n", "k"));
    }
}

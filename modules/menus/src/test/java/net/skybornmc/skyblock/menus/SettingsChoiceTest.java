package net.skybornmc.skyblock.menus;

import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** VIS: stored choices of multiple-choice settings fall back to the default when missing or no longer valid. */
class SettingsChoiceTest {

    private static final SettingToggle SHOW = SettingToggle.choice("core", "show-players", SettingCategory.DISPLAY, Material.SPYGLASS,
            List.of("everyone", "friends", "nobody"), "everyone", null, "navigation", "settings.toggles.show-players");

    @Test
    void storedChoiceIsUsed() {
        assertEquals("nobody", SettingsRegistry.parseChoice("nobody", SHOW));
    }

    @Test
    void missingOrUnknownFallsBackToDefault() {
        assertEquals("everyone", SettingsRegistry.parseChoice(null, SHOW));
        assertEquals("everyone", SettingsRegistry.parseChoice("pinned", SHOW));
        assertEquals("everyone", SettingsRegistry.parseChoice("true", SHOW));
    }
}

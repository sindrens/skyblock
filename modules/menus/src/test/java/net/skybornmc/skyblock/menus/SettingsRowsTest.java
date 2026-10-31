package net.skybornmc.skyblock.menus;

import net.skybornmc.skyblock.common.settings.SettingCategory;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GUI-4c: /settings shows one category at a time (tabs in row 0) instead of 8 rows over 2 pages. */
class SettingsRowsTest {

    @Test
    void onlyCategoriesWithSwitchesGetATab() {
        Map<SettingCategory, Integer> counts = new EnumMap<>(SettingCategory.class);
        counts.put(SettingCategory.CHAT, 7);
        counts.put(SettingCategory.SOCIAL, 13);
        counts.put(SettingCategory.DISPLAY, 6);
        counts.put(SettingCategory.SOUNDS, 0);
        counts.put(SettingCategory.GAMEPLAY, 3);
        assertEquals(List.of(SettingCategory.CHAT, SettingCategory.SOCIAL, SettingCategory.DISPLAY, SettingCategory.GAMEPLAY),
                SettingsMenu.tabs(counts));
        assertTrue(SettingsMenu.tabs(Map.of()).isEmpty());
    }

    @Test
    void theSelectedTabStaysWhileItHasSwitches() {
        List<SettingCategory> tabs = List.of(SettingCategory.CHAT, SettingCategory.DISPLAY);
        assertEquals(SettingCategory.CHAT, SettingsMenu.pick(tabs, null), "the first tab on open");
        assertEquals(SettingCategory.DISPLAY, SettingsMenu.pick(tabs, SettingCategory.DISPLAY));
        assertEquals(SettingCategory.CHAT, SettingsMenu.pick(tabs, SettingCategory.SOUNDS), "a tab that lost its switches falls back");
    }

    @Test
    void fiveTabsFitTheTopRow() {
        int[] slots = TopLayout.tabSlots(SettingCategory.values().length);
        assertEquals(SettingCategory.values().length, slots.length);
        for (int slot : slots) {
            assertTrue(slot >= 1 && slot <= 7, "tabs stay inside the frame corners");
        }
    }
}

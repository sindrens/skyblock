package net.skybornmc.skyblock.menus;

import net.skybornmc.skyblock.common.settings.SettingCategory;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** QUICK1: /settings never hides a switch (overflow continues on the next row, rows continue on the next page). */
class SettingsRowsTest {

    @Test
    void everyCategoryGetsARowAndOverflowContinues() {
        Map<SettingCategory, Integer> counts = new EnumMap<>(SettingCategory.class);
        counts.put(SettingCategory.CHAT, 7);
        counts.put(SettingCategory.SOCIAL, 13);
        counts.put(SettingCategory.DISPLAY, 6);
        counts.put(SettingCategory.SOUNDS, 0);
        counts.put(SettingCategory.GAMEPLAY, 3);
        List<SettingsMenu.Row> rows = SettingsMenu.rows(counts, 6);
        assertEquals(List.of(
                new SettingsMenu.Row(SettingCategory.CHAT, 0, 6),
                new SettingsMenu.Row(SettingCategory.CHAT, 6, 7),
                new SettingsMenu.Row(SettingCategory.SOCIAL, 0, 6),
                new SettingsMenu.Row(SettingCategory.SOCIAL, 6, 12),
                new SettingsMenu.Row(SettingCategory.SOCIAL, 12, 13),
                new SettingsMenu.Row(SettingCategory.DISPLAY, 0, 6),
                new SettingsMenu.Row(SettingCategory.SOUNDS, 0, 0),
                new SettingsMenu.Row(SettingCategory.GAMEPLAY, 0, 3)), rows);
        int shown = rows.stream().mapToInt(r -> r.to() - r.from()).sum();
        assertEquals(7 + 13 + 6 + 3, shown, "every switch has a slot");
    }

    @Test
    void missingCategoriesStillShowAnEmptyRow() {
        List<SettingsMenu.Row> rows = SettingsMenu.rows(Map.of(), 6);
        assertEquals(SettingCategory.values().length, rows.size());
    }
}

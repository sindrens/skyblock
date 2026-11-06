package net.skybornmc.skyblock.common.rank;

import net.kyori.adventure.text.format.TextColor;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BadgeSettingsTest {

    @Test
    void onlyTheRankColourPickerIsRegistered() {
        List<SettingToggle> t = BadgeSettings.toggles("chat", List.of("phoenix", "ember"));
        assertEquals(List.of(BadgeSettings.RANK_COLOR), t.stream().map(SettingToggle::id).toList());
        for (String retired : new String[]{"reduced-motion", "simple-badges", "large-badges"}) {
            assertTrue(t.stream().noneMatch(x -> x.id().equals(retired)), retired);
        }
    }

    @Test
    void rankColourIsAPermissionGatedChoiceDefaultingToTheFirstGradient() {
        SettingToggle c = BadgeSettings.toggles("chat", List.of("phoenix", "ember", "frost")).stream()
                .filter(x -> x.id().equals(BadgeSettings.RANK_COLOR)).findFirst().orElseThrow();
        assertTrue(c.isChoice());
        assertEquals("phoenix", c.defaultChoice());
        assertEquals(List.of("phoenix", "ember", "frost"), c.choices());
        assertEquals(BadgeSettings.PERM_RANK_COLOR, c.permission());
        assertEquals(SettingCategory.DISPLAY, c.category());
    }

    @Test
    void noGradientsMeansNoPicker() {
        assertTrue(BadgeSettings.toggles("chat", List.of()).isEmpty());
    }

    @Test
    void gradientConfigParsingSkipsBadLines() {
        Map<String, List<TextColor>> g = BadgeGradients.parse(List.of(
                "phoenix=#FFE27A,#FF8C1A,#FF2D3F", "ember=#FFB347,#C2300F", "bad", "x=#FFFFFF", "Up=#111111,#222222",
                "nope=#GGGGGG,#222222", "phoenix=#000000,#111111"));
        assertEquals(List.of("phoenix", "ember"), List.copyOf(g.keySet()));
        assertEquals(3, g.get("phoenix").size());
        assertEquals(TextColor.color(0xFFE27A), g.get("phoenix").getFirst());
    }
}

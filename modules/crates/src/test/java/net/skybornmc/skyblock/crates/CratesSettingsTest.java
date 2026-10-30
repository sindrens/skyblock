package net.skybornmc.skyblock.crates;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CratesSettingsTest {

    @Test
    void farmableSourcesAreBoundAndUnknownIsBoundToo() {
        CratesSettings s = new CratesSettings();
        assertTrue(s.bound("afk_pool"), "the AFK pool's reason");
        assertTrue(s.bound("vote"));
        assertTrue(s.bound("daily"));
        assertTrue(s.bound("playtime"));
        assertTrue(s.bound("rank"));
        assertFalse(s.bound("quest"));
        assertFalse(s.bound("PASS"));
        assertFalse(s.bound("weekly_top"));
        assertTrue(s.bound("something_new"), "unknown sources are bound (safest)");
        assertTrue(s.bound(null));
    }

    @Test
    void physicalPointMapping() {
        Map<String, String> map = PhysicalCrates.mapping(new CratesSettings().physicalPoints);
        assertEquals("ember", map.get("common"));
        assertEquals("eclipse", map.get("monthly"));
        assertEquals(6, map.size(), "6 pedestals, no Conqueror yet");
        assertEquals(Map.of("a", "b"), PhysicalCrates.mapping(List.of("a:b", "broken", ":x", "y:")));
        assertEquals(List.of(0, 1), PhysicalCrates.offsets(new CratesSettings().physicalOffsets));
        assertEquals(List.of(2), PhysicalCrates.offsets(List.of("x", "2")));
        for (String crate : map.values()) {
            assertTrue(Fixtures.bundled().crate(crate).isPresent(), crate);
        }
    }

    @Test
    void durationText() {
        assertEquals("1h", CratesModule.durationText(60));
        assertEquals("3h", CratesModule.durationText(180));
        assertEquals("45m", CratesModule.durationText(45));
        assertEquals("1h 30m", CratesModule.durationText(90));
    }
}

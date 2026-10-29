package net.skybornmc.skyblock.hud;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidebarLinesTest {

    @Test
    void entriesAreUniqueAndInvisible() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 32; i++) {
            String e = SidebarLines.entry(i);
            assertTrue(seen.add(e), "duplicate entry for line " + i);
            assertTrue(e.replaceAll("§.", "").isEmpty(), "entry must only contain color codes");
        }
        assertThrows(IllegalArgumentException.class, () -> SidebarLines.entry(32));
    }

    @Test
    void onlyChangedLinesAreReported() {
        assertEquals(List.of(1), SidebarLines.changed(List.of("a", "b", "c"), List.of("a", "x", "c")));
        assertEquals(List.of(), SidebarLines.changed(List.of("a"), List.of("a", "b")));
    }

    @Test
    void playersAreSpreadOverTheInterval() {
        int due = 0;
        for (long tick = 0; tick < 20; tick++) {
            if (SidebarLines.due(12345, tick, 20)) {
                due++;
            }
        }
        assertEquals(1, due);
        assertTrue(SidebarLines.due(-7, 13, 20), "negative hashes map with floorMod");
    }
}

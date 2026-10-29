package net.skybornmc.skyblock.depths;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegionLoaderTest {

    @Test
    void parsesCornerFormats() {
        assertArrayEquals(new int[]{0, 60, -5}, RegionLoader.corner("0, 60, -5", "corner-1"));
        assertArrayEquals(new int[]{10, 70, 20}, RegionLoader.corner("10 70 20", "corner-1"));
        assertArrayEquals(new int[]{-1, 64, 3}, RegionLoader.corner(List.of(-1, 64, 3), "corner-1"));
        assertArrayEquals(new int[]{-1, 64, 3}, RegionLoader.corner("-0.5, 64.9, 3.2", "corner-1"));
    }

    @Test
    void rejectsBadCorners() {
        assertThrows(IllegalArgumentException.class, () -> RegionLoader.corner(null, "corner-1"));
        assertThrows(IllegalArgumentException.class, () -> RegionLoader.corner("1, 2", "corner-1"));
        assertThrows(IllegalArgumentException.class, () -> RegionLoader.corner("a, b, c", "corner-1"));
        assertThrows(IllegalArgumentException.class, () -> RegionLoader.corner("0, 5000, 0", "corner-1"));
    }

    @Test
    void respawnSecondsRange() {
        assertEquals(10, RegionLoader.respawnSeconds(10));
        assertEquals(15, RegionLoader.respawnSeconds("15"));
        assertThrows(IllegalArgumentException.class, () -> RegionLoader.respawnSeconds(0));
        assertThrows(IllegalArgumentException.class, () -> RegionLoader.respawnSeconds("ten"));
    }
}

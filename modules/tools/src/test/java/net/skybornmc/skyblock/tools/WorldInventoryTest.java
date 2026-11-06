package net.skybornmc.skyblock.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 3 Nov 2026: the Depths has its own inventory; which set is live comes from a per-profile marker. */
class WorldInventoryTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void noMarkerMeansSurface() {
        assertEquals(WorldTools.SURFACE, WorldTools.liveSide(null, A));
        assertEquals(WorldTools.SURFACE, WorldTools.liveSide("", A));
        assertEquals(WorldTools.SURFACE, WorldTools.liveSide("garbage", A));
    }

    @Test
    void markerOfThisProfile() {
        assertEquals(WorldTools.DEPTHS, WorldTools.liveSide(A + ":depths", A));
        assertEquals(WorldTools.SURFACE, WorldTools.liveSide(A + ":surface", A));
    }

    @Test
    void markerOfAnotherProfileMeansSurface() {
        assertEquals(WorldTools.SURFACE, WorldTools.liveSide(B + ":depths", A));
    }

    @Test
    void depthsWorldIgnoresCase() {
        assertTrue(WorldTools.depthsWorld(List.of("depths"), "Depths"));
        assertFalse(WorldTools.depthsWorld(List.of("depths"), "islands"));
        assertFalse(WorldTools.depthsWorld(List.of("depths"), null));
    }
}

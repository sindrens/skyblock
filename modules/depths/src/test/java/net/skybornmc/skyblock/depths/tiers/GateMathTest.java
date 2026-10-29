package net.skybornmc.skyblock.depths.tiers;

import net.skybornmc.skyblock.depths.engine.BlockKeys;
import net.skybornmc.skyblock.depths.zones.Box;
import net.skybornmc.skyblock.depths.zones.Side;
import net.skybornmc.skyblock.depths.zones.Zone;
import net.skybornmc.skyblock.depths.zones.ZoneLayout;
import net.skybornmc.skyblock.depths.zones.ZoneSet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateMathTest {

    /** mine_t01 of zones.yml: x -160..-81, gate on the east edge x = -81. */
    private static final Box ZONE = Box.of(-160, 94, -48, -81, 110, 31);

    @Test
    void walkingThroughTheGateLineEnters() {
        // x -79.9 is block -80 (causeway, outside); x -80.2 is block -81 (the gate line, inside)
        assertTrue(GateMath.enters(ZONE, -79.9, 100, 0.5, -80.2, 100, 0.5), "crossing x = -80 (east face) enters");
        assertFalse(GateMath.enters(ZONE, -80.2, 100, 0.5, -82.0, 100, 0.5), "moving inside is not entering");
        assertFalse(GateMath.enters(ZONE, -79.9, 100, 0.5, -79.1, 100, 0.5), "moving outside is not entering");
        assertFalse(GateMath.enters(ZONE, -81.5, 100, 0.5, -79.5, 100, 0.5), "leaving is not entering");
        // the exact face: x = -80.0 floors to -80, which is outside the box (max -81)
        assertFalse(ZONE.contains(-80.0, 100, 0.5));
        assertTrue(ZONE.contains(-80.0001, 100, 0.5));
    }

    @Test
    void teleportFarAwayIntoTheZoneEnters() {
        assertTrue(GateMath.enters(ZONE, 0.5, 100, 38.5, -120.5, 100, -8.5), "pearl/teleport from The Gate");
        assertFalse(GateMath.enters(ZONE, 0.5, 100, 38.5, -120.5, 120, -8.5), "above the zone box is not inside");
    }

    @Test
    void pushGoesBackTheWayThePlayerCame() {
        assertEquals(Side.EAST, GateMath.outside(ZONE, -79.9, 0.5));
        assertEquals(Side.WEST, GateMath.outside(ZONE, -161.5, 0.5));
        assertEquals(Side.NORTH, GateMath.outside(ZONE, -120, -49.5));
        assertEquals(Side.SOUTH, GateMath.outside(ZONE, -120, 32.5));
        // diagonal outside: the larger distance wins
        assertEquals(Side.SOUTH, GateMath.outside(ZONE, -78, 40));
        // inside: the nearest face is the shortest way out
        assertEquals(Side.EAST, GateMath.outside(ZONE, -82.5, 0.5));
        assertEquals(Side.NORTH, GateMath.outside(ZONE, -120, -47.5));
        assertEquals(1, GateMath.pushX(Side.EAST));
        assertEquals(0, GateMath.pushZ(Side.EAST));
        assertEquals(-1, GateMath.pushX(Side.WEST));
        assertEquals(-1, GateMath.pushZ(Side.NORTH));
        assertEquals(1, GateMath.pushZ(Side.SOUTH));
        assertEquals(0, GateMath.pushX(Side.SOUTH));
    }

    @Test
    void everyGateOfTheLayoutSitsOnItsZoneEdgeAndPushesOutward() {
        ZoneSet set = new ZoneLayout(ZoneLayout.Params.defaults()).build("depths");
        for (Zone zone : set.zones()) {
            Box gate = zone.gate();
            assertTrue(gate.onEdgeOf(zone.region()), zone.id() + " gate on the zone edge");
            assertTrue(gate.sizeX() == 1 || gate.sizeZ() == 1, zone.id() + " gate is one block thick");
            // a player just outside the gate's middle, moving one block in, enters and is pushed back out
            double cx = (gate.minX() + gate.maxX() + 1) / 2.0;
            double cz = (gate.minZ() + gate.maxZ() + 1) / 2.0;
            Side face = GateMath.outside(zone.region(), cx, cz); // gate is inside: nearest face = its wall
            double ox = cx + GateMath.pushX(face) * 1.0;
            double oz = cz + GateMath.pushZ(face) * 1.0;
            double y = gate.minY() + 0.5;
            assertFalse(zone.region().contains(ox, y, oz), zone.id() + " one block out of the gate is outside");
            assertTrue(GateMath.enters(zone.region(), ox, y, oz, cx, y, cz), zone.id() + " stepping into the gate enters");
            assertEquals(face, GateMath.outside(zone.region(), ox, oz), zone.id() + " pushed back through its gate");
            assertEquals(face.alongX(), GateMath.spansZ(gate), zone.id() + " bars connect along the wall");
        }
    }

    @Test
    void gateOrientationAndBlocks() {
        Box alongZ = Box.of(-81, 100, -2, -81, 104, 2);
        Box alongX = Box.of(-2, 100, -81, 2, 104, -81);
        assertTrue(GateMath.spansZ(alongZ));
        assertFalse(GateMath.spansZ(alongX));
        long[] blocks = GateMath.blocks(alongZ);
        assertEquals(25, blocks.length);
        assertTrue(Arrays.stream(blocks).allMatch(k -> BlockKeys.x(k) == -81 && alongZ.contains(BlockKeys.x(k), BlockKeys.y(k), BlockKeys.z(k))));
        assertEquals(25, Arrays.stream(blocks).distinct().count());
    }

    @Test
    void chunksOfABox() {
        assertArrayEquals(new long[]{BlockKeys.chunk(-6, -1), BlockKeys.chunk(-6, 0)}, GateMath.chunks(Box.of(-81, 100, -2, -81, 104, 2)));
        assertArrayEquals(new long[]{BlockKeys.chunk(0, 0)}, GateMath.chunks(Box.of(1, 0, 1, 15, 5, 15)));
        assertEquals(4, GateMath.chunks(Box.of(15, 0, 15, 16, 0, 16)).length);
    }
}

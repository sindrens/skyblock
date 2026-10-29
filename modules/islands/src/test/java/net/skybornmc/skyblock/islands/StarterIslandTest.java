package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.islands.StarterIsland.Piece;
import net.skybornmc.skyblock.islands.StarterIsland.Pos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The placeholder islet keeps every required element of BUILDER_BRIEF §6.3. */
class StarterIslandTest {

    private final Map<Pos, Piece> blocks = StarterIsland.blocks();

    private long count(Piece piece) {
        return blocks.values().stream().filter(p -> p == piece).count();
    }

    @Test
    void exactlyOneChestAndOneSignAtTheirSpots() {
        assertEquals(1, count(Piece.CHEST));
        assertEquals(1, count(Piece.OAK_SIGN));
        assertEquals(Piece.CHEST, blocks.get(StarterIsland.CHEST));
        assertEquals(Piece.OAK_SIGN, blocks.get(StarterIsland.SIGN));
        assertTrue(blocks.get(StarterIsland.CHEST.offset(0, -1, 0)).solid(), "chest stands on ground");
        assertTrue(blocks.get(StarterIsland.SIGN.offset(0, -1, 0)).solid(), "sign stands on ground");
    }

    @Test
    void homeSpotIsSolidWithTwoAirAbove() {
        Pos home = StarterIsland.HOME;
        assertTrue(blocks.get(home.offset(0, -1, 0)).solid());
        assertNull(blocks.get(home));
        assertNull(blocks.get(home.offset(0, 1, 0)));
    }

    @Test
    void infiniteWaterAndNoLava() {
        assertTrue(count(Piece.WATER) >= 2);
        boolean adjacent = blocks.entrySet().stream().filter(e -> e.getValue() == Piece.WATER)
                .anyMatch(e -> blocks.get(e.getKey().offset(1, 0, 0)) == Piece.WATER || blocks.get(e.getKey().offset(0, 0, 1)) == Piece.WATER);
        assertTrue(adjacent, "two water sources side by side");
        assertFalse(blocks.values().stream().anyMatch(p -> p.name().contains("LAVA")));
        // water never touches air sideways or below (it must not flow off the island)
        blocks.forEach((pos, piece) -> {
            if (piece == Piece.WATER) {
                for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}}) {
                    Piece next = blocks.get(pos.offset(d[0], d[1], d[2]));
                    assertTrue(next != null && (next.solid() || next == Piece.WATER), "water leaks at " + pos);
                }
            }
        });
    }

    @Test
    void enoughFarmlandNearWater() {
        long nearWater = blocks.entrySet().stream()
                .filter(e -> e.getValue() == Piece.FARMLAND || e.getValue() == Piece.DIRT && e.getKey().y() == 0)
                .filter(e -> blocks.entrySet().stream().anyMatch(w -> w.getValue() == Piece.WATER
                        && Math.abs(w.getKey().x() - e.getKey().x()) <= 4 && Math.abs(w.getKey().z() - e.getKey().z()) <= 4))
                .count();
        assertTrue(nearWater >= 8, "farmland near water: " + nearWater);
    }

    @Test
    void oakTreeWithLeavesThatWontDecay() {
        assertTrue(count(Piece.OAK_LOG) >= 5);
        Map<Pos, Integer> distances = StarterIsland.leafDistances(blocks);
        assertEquals(count(Piece.OAK_LEAVES), distances.size());
        distances.forEach((pos, d) -> assertTrue(d >= 1 && d <= 4, "leaf at " + pos + " has distance " + d));
    }

    @Test
    void flatStonePadForTheFirstCobbleGenerator() {
        int found = 0;
        for (int x = 1; x <= 5; x++) {
            for (int z = -4; z <= -2; z++) {
                assertEquals(Piece.STONE, blocks.get(new Pos(x, 0, z)));
                assertNull(blocks.get(new Pos(x, 1, z)), "pad must be clear above");
                found++;
            }
        }
        assertEquals(15, found);
    }

    @Test
    void smallFootprintAndOreSpecksUnderneath() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Pos p : blocks.keySet()) {
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minZ = Math.min(minZ, p.z());
            maxZ = Math.max(maxZ, p.z());
        }
        assertTrue(maxX - minX + 1 <= 21 && maxZ - minZ + 1 <= 21, "footprint must stay small (<= 21x21)");
        long ores = count(Piece.COAL_ORE) + count(Piece.IRON_ORE);
        assertTrue(ores >= 2 && ores <= 4);
        blocks.forEach((pos, piece) -> {
            if (piece == Piece.COAL_ORE || piece == Piece.IRON_ORE) {
                assertTrue(pos.y() < 0, "ores only in the underside");
            }
        });
    }

    @Test
    void taperedUnderside() {
        int previous = Integer.MAX_VALUE;
        for (int y = 0; y >= -6; y--) {
            final int layer = y;
            int size = (int) blocks.keySet().stream().filter(p -> p.y() == layer).count();
            assertTrue(size > 0, "layer " + y + " exists");
            assertTrue(size <= previous, "layer " + y + " is not smaller than the one above");
            previous = size;
        }
    }
}

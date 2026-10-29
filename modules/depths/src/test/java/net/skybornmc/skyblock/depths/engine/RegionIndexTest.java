package net.skybornmc.skyblock.depths.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionIndexTest {

    private record Box(String id, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) implements Bounds {
    }

    @Test
    void findsTheRegionContainingABlock() {
        Box mine = new Box("mine", "depths", 0, 60, 0, 40, 90, 40);
        Box forest = new Box("forest", "depths", 100, 60, -50, 150, 120, -10);
        RegionIndex<Box> index = RegionIndex.of(List.of(mine, forest));
        assertSame(mine, index.at("depths", 0, 60, 0));
        assertSame(mine, index.at("depths", 40, 90, 40));
        assertSame(forest, index.at("depths", 120, 100, -30));
        assertNull(index.at("depths", 41, 70, 20));     // just outside x
        assertNull(index.at("depths", 20, 59, 20));     // below
        assertNull(index.at("depths", 20, 91, 20));     // above
        assertNull(index.at("islands", 20, 70, 20));    // other world
    }

    @Test
    void handlesNegativeCoordinatesAndChunkEdges() {
        Box box = new Box("neg", "depths", -33, 0, -17, -16, 10, -1);
        RegionIndex<Box> index = RegionIndex.of(List.of(box));
        assertSame(box, index.at("depths", -33, 5, -17));
        assertSame(box, index.at("depths", -16, 5, -1));
        assertNull(index.at("depths", -15, 5, -1));
        assertNull(index.at("depths", -16, 5, 0));
        assertTrue(index.touchesChunk("depths", -3, -2));
        assertTrue(index.touchesChunk("depths", -1, -1));
        assertFalse(index.touchesChunk("depths", 0, 0));
    }

    @Test
    void firstRegionWinsOnOverlap() {
        Box a = new Box("a", "w", 0, 0, 0, 20, 20, 20);
        Box b = new Box("b", "w", 10, 0, 10, 30, 20, 30);
        assertSame(a, RegionIndex.of(List.of(a, b)).at("w", 15, 5, 15));
        assertSame(b, RegionIndex.of(List.of(b, a)).at("w", 15, 5, 15));
        assertSame(b, RegionIndex.of(List.of(a, b)).at("w", 25, 5, 25));
    }

    @Test
    void emptyIndexFindsNothing() {
        RegionIndex<Box> index = RegionIndex.empty();
        assertTrue(index.isEmpty());
        assertNull(index.at("depths", 0, 0, 0));
        assertFalse(index.hasWorld("depths"));
    }

    @Test
    void rejectsHugeRegions() {
        Box huge = new Box("huge", "w", -3_000_000, 0, -3_000_000, 3_000_000, 10, 3_000_000);
        assertThrows(IllegalArgumentException.class, () -> RegionIndex.of(List.of(huge)));
    }

    @Test
    void matchesBruteForce() {
        java.util.Random random = new java.util.Random(4);
        List<Box> boxes = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            int x = random.nextInt(400) - 200;
            int z = random.nextInt(400) - 200;
            int y = random.nextInt(100);
            boxes.add(new Box("r" + i, "w", x, y, z, x + random.nextInt(60), y + random.nextInt(40), z + random.nextInt(60)));
        }
        RegionIndex<Box> index = RegionIndex.of(boxes);
        for (int i = 0; i < 20_000; i++) {
            int x = random.nextInt(600) - 300;
            int y = random.nextInt(160) - 10;
            int z = random.nextInt(600) - 300;
            Box expected = boxes.stream().filter(b -> b.contains(x, y, z)).findFirst().orElse(null);
            assertEquals(expected, index.at("w", x, y, z), "at " + x + " " + y + " " + z);
        }
    }
}

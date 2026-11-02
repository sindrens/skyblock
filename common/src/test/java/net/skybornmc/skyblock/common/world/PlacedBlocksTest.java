package net.skybornmc.skyblock.common.world;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Placed blocks never count (Complete Developer Spec §7.3): bounded, TTL, per world. */
class PlacedBlocksTest {

    private final UUID world = UUID.randomUUID();

    @Test
    void aPlacedBlockIsRememberedUntilBrokenOrExpired() {
        PlacedBlocks p = new PlacedBlocks(1_000, 10);
        p.placed(world, 5, 64, -7, 0);
        assertTrue(p.isPlaced(world, 5, 64, -7, 500));
        assertFalse(p.isPlaced(world, 5, 65, -7, 500), "the block above was not placed");
        assertFalse(p.isPlaced(UUID.randomUUID(), 5, 64, -7, 500), "other world");
        assertFalse(p.isPlaced(world, 5, 64, -7, 1_001), "expired");
        p.placed(world, 1, 1, 1, 0);
        p.forget(world, 1, 1, 1);
        assertFalse(p.isPlaced(world, 1, 1, 1, 1));
    }

    @Test
    void memoryIsBoundedOldestFirst() {
        PlacedBlocks p = new PlacedBlocks(60_000, 3);
        for (int i = 0; i < 5; i++) {
            p.placed(world, i, 0, 0, i);
        }
        assertEquals(3, p.size());
        assertFalse(p.isPlaced(world, 0, 0, 0, 10));
        assertTrue(p.isPlaced(world, 4, 0, 0, 10));
        p.prune(60_004);
        assertEquals(1, p.size(), "only the block placed at 4 is younger than the TTL");
    }

    @Test
    void negativeCoordinatesDoNotCollide() {
        assertNotEquals(PlacedBlocks.pack(-1, 64, 0), PlacedBlocks.pack(1, 64, 0));
        assertNotEquals(PlacedBlocks.pack(0, 64, -1), PlacedBlocks.pack(0, 64, 1));
        assertNotEquals(PlacedBlocks.pack(0, -64, 0), PlacedBlocks.pack(0, 64, 0));
    }
}

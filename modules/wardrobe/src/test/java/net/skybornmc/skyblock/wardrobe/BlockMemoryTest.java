package net.skybornmc.skyblock.wardrobe;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Generator blocks and grown logs: remembered once, forgotten on break, bounded (oldest first). */
class BlockMemoryTest {

    private final UUID world = UUID.randomUUID();

    @Test
    void rememberedOnceAndForgottenOnBreak() {
        BlockMemory m = new BlockMemory(10);
        m.add(world, 1, 64, -3);
        assertTrue(m.contains(world, 1, 64, -3));
        assertFalse(m.contains(UUID.randomUUID(), 1, 64, -3), "per world");
        assertTrue(m.remove(world, 1, 64, -3));
        assertFalse(m.remove(world, 1, 64, -3), "a second break gives nothing");
    }

    @Test
    void oldestAreForgottenFirst() {
        BlockMemory m = new BlockMemory(3);
        for (int i = 0; i < 5; i++) {
            m.add(world, i, 0, 0);
        }
        assertEquals(3, m.size());
        assertFalse(m.contains(world, 0, 0, 0));
        assertTrue(m.contains(world, 4, 0, 0));
        assertEquals(0, new BlockMemory(0).size());
    }

    @Test
    void packIsUniqueAroundTheOrigin() {
        Set<Long> seen = new HashSet<>();
        for (int x = -3; x <= 3; x++) {
            for (int y = -64; y <= 320; y += 64) {
                for (int z = -3; z <= 3; z++) {
                    assertTrue(seen.add(BlockMemory.pack(x, y, z)));
                }
            }
        }
        assertTrue(seen.add(BlockMemory.pack(30_000_000, 100, -30_000_000)));
    }
}

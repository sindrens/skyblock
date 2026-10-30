package net.skybornmc.skyblock.progression;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Anti place-and-break for the island log source: the last N log positions placed on one island. A log broken at a
 * remembered position gives no pillar XP (and is forgotten). Bounded, O(1) per call. Main thread only; unit-tested.
 */
final class PlacedBlocks {

    private final int capacity;
    private final LinkedHashMap<Long, Boolean> placed;

    PlacedBlocks(int capacity) {
        this.capacity = Math.max(0, capacity);
        this.placed = new LinkedHashMap<>(Math.min(this.capacity, 1024) * 2 + 1, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
                return size() > PlacedBlocks.this.capacity;
            }
        };
    }

    /** Packs a block position (x/z ±33M, y −2048..2047) into one long. */
    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    void placed(int x, int y, int z) {
        if (capacity == 0) {
            return;
        }
        long key = pack(x, y, z);
        placed.remove(key);
        placed.put(key, Boolean.TRUE);
    }

    /** True (and forgets it) if a player placed a block at this position recently. */
    boolean brokePlaced(int x, int y, int z) {
        return capacity > 0 && placed.remove(pack(x, y, z)) != null;
    }

    int size() {
        return placed.size();
    }
}

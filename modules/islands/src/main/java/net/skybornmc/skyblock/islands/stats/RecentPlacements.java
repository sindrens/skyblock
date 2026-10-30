package net.skybornmc.skyblock.islands.stats;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Anti place-and-break farming for island stats (M2.6): remembers the last N block positions one player placed. A block
 * broken at a remembered position does not count as mined, chopped or harvested (and is forgotten). Bounded, O(1) per
 * call. Not thread-safe (main thread).
 */
public final class RecentPlacements {

    private final int capacity;
    private final LinkedHashMap<Long, Boolean> placed;

    public RecentPlacements(int capacity) {
        this.capacity = Math.max(0, capacity);
        this.placed = new LinkedHashMap<>(Math.min(this.capacity, 1024) * 2, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
                return size() > RecentPlacements.this.capacity;
            }
        };
    }

    /** Packs a block position (x/z ±33M, y −2048..2047) into one long. */
    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public void placed(int x, int y, int z) {
        if (capacity == 0) {
            return;
        }
        long key = pack(x, y, z);
        placed.remove(key); // re-insert as newest
        placed.put(key, Boolean.TRUE);
    }

    /** True (and forgets it) if the player placed a block at this position recently. */
    public boolean brokeOwnPlacement(int x, int y, int z) {
        return capacity > 0 && placed.remove(pack(x, y, z)) != null;
    }

    public int size() {
        return placed.size();
    }
}

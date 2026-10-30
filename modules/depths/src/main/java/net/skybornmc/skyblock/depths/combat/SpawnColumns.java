package net.skybornmc.skyblock.depths.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * QUICK1 (PERFORMANCE P-10, the {@code depths-combat} slow warning): remembers the standing height found per arena
 * column, so a spawn attempt does not walk the column block by block every cycle. The arenas are protected (DEPTHSPROT),
 * so the floor rarely changes; the whole cache is dropped every {@code lifeMillis} anyway (and on start/reload).
 * Main thread only.
 */
final class SpawnColumns {

    /** Marks a column that has no standing spot (cached as well, so it is not rescanned either). */
    static final int NONE = Integer.MIN_VALUE;
    /** Columns kept at most; when full the cache starts over (bounded memory for huge arenas). */
    static final int MAX_COLUMNS = 65_536;

    private final Map<Long, Integer> heights = new HashMap<>();
    private long createdAt = Long.MIN_VALUE;

    /**
     * The cached height of column (x, z) of a zone, or {@code compute} (stored for next time).
     *
     * @param zoneKey   identifies the zone and its arena box (a changed box never reuses old heights)
     * @param lifeMillis cache life; 0 or less = never cache
     */
    int height(int zoneKey, int x, int z, long now, long lifeMillis, IntSupplier compute) {
        if (lifeMillis <= 0) {
            return compute.getAsInt();
        }
        if (createdAt == Long.MIN_VALUE || now - createdAt >= lifeMillis || heights.size() >= MAX_COLUMNS) {
            heights.clear();
            createdAt = now;
        }
        long key = key(zoneKey, x, z);
        Integer cached = heights.get(key);
        if (cached != null) {
            return cached;
        }
        int y = compute.getAsInt();
        heights.put(key, y);
        return y;
    }

    void clear() {
        heights.clear();
        createdAt = Long.MIN_VALUE;
    }

    int size() {
        return heights.size();
    }

    /** Column key: zone hash in the top bits, then x and z (21 bits each; Depths coordinates stay far below ±1M). */
    static long key(int zoneKey, int x, int z) {
        long zx = (x & 0x1FFFFFL);
        long zz = (z & 0x1FFFFFL);
        return ((long) (zoneKey & 0x3FFFFF) << 42) | (zx << 21) | zz;
    }
}

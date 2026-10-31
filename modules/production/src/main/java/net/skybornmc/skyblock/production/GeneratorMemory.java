package net.skybornmc.skyblock.production;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which blocks an island's generator really made (PHASE-7 M7.2, like the wardrobe's {@code BlockMemory}): position → ore id,
 * per island, bounded (oldest forgotten first). Only remembered blocks give Mining pillar XP and count as
 * {@code generatorMade} for the gather hook, so placing (or piston-moving) ores earns nothing. A restart forgets everything:
 * a few blocks give no XP, never extra. Main thread only; unit-tested.
 */
final class GeneratorMemory {

    private final Map<UUID, LinkedHashMap<Long, String>> islands = new HashMap<>();
    private int capacity;

    GeneratorMemory(int capacity) {
        this.capacity = Math.max(0, capacity);
    }

    /** New capacity (a smaller one forgets everything, simplest and safe). */
    void capacity(int capacity) {
        int c = Math.max(0, capacity);
        if (c < this.capacity) {
            islands.clear();
        }
        this.capacity = c;
    }

    /** Packs block coordinates (x/z ±33M, y ±2048) into one long. */
    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** The generator formed {@code oreId} at this position (replaces whatever was remembered there). */
    void remember(UUID island, int x, int y, int z, String oreId) {
        if (capacity == 0) {
            return;
        }
        LinkedHashMap<Long, String> map = islands.computeIfAbsent(island, k -> new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, String> eldest) {
                return size() > GeneratorMemory.this.capacity;
            }
        });
        long key = pack(x, y, z);
        map.remove(key); // re-insert so a busy position counts as the newest
        map.put(key, oreId);
    }

    /** Forgets a position (placed over, formed plain, broken). */
    void forget(UUID island, int x, int y, int z) {
        LinkedHashMap<Long, String> map = islands.get(island);
        if (map != null && map.remove(pack(x, y, z)) != null && map.isEmpty()) {
            islands.remove(island);
        }
    }

    /** The ore the generator made at this position, forgotten in the same step (each block counts once); null if none. */
    String take(UUID island, int x, int y, int z) {
        LinkedHashMap<Long, String> map = islands.get(island);
        if (map == null) {
            return null;
        }
        String ore = map.remove(pack(x, y, z));
        if (map.isEmpty()) {
            islands.remove(island);
        }
        return ore;
    }

    void clear(UUID island) {
        islands.remove(island);
    }

    void clear() {
        islands.clear();
    }

    int size(UUID island) {
        LinkedHashMap<Long, String> map = islands.get(island);
        return map == null ? 0 : map.size();
    }
}

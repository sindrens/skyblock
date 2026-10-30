package net.skybornmc.skyblock.wardrobe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers block positions (island generator blocks, logs of trees that grew) so only those give set XP: a block a
 * player placed is never in here, so placing and breaking blocks earns nothing. Bounded: the oldest positions are
 * forgotten first; a restart forgets everything (a few lost XP points, never extra ones). Main thread, unit-tested.
 */
final class BlockMemory {

    private record Key(UUID world, long pos) {
    }

    private final int capacity;
    private final LinkedHashMap<Key, Boolean> positions;

    BlockMemory(int capacity) {
        this.capacity = Math.max(0, capacity);
        this.positions = new LinkedHashMap<>(Math.min(1024, Math.max(16, this.capacity)), 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, Boolean> eldest) {
                return size() > BlockMemory.this.capacity;
            }
        };
    }

    /** Packs block coordinates (x/z ±33M, y ±2048) into one long. */
    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    void add(UUID world, int x, int y, int z) {
        if (capacity > 0) {
            positions.put(new Key(world, pack(x, y, z)), Boolean.TRUE);
        }
    }

    /** Forgets a position; true if it was remembered. */
    boolean remove(UUID world, int x, int y, int z) {
        return positions.remove(new Key(world, pack(x, y, z))) != null;
    }

    boolean contains(UUID world, int x, int y, int z) {
        return positions.containsKey(new Key(world, pack(x, y, z)));
    }

    int size() {
        return positions.size();
    }

    void clear() {
        positions.clear();
    }
}

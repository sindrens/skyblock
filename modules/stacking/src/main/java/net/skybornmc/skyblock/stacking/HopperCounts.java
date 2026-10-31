package net.skybornmc.skyblock.stacking;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Hoppers per loaded chunk (owner F13), free of Bukkit so it is unit-tested. Only chunks with at least one hopper have
 * an entry: a loaded chunk without an entry has 0 (every chunk of a counted world is counted once when it loads, see
 * {@link HopperCounter}). Main thread only.
 */
final class HopperCounts {

    /** The busiest chunk of an area. */
    record Hotspot(int chunkX, int chunkZ, int count) {
    }

    /** Chunk filter for {@link #busiest}: chunk coordinates. */
    @FunctionalInterface
    interface ChunkFilter {
        boolean test(int chunkX, int chunkZ);
    }

    private final Map<String, Map<Long, int[]>> worlds = new HashMap<>();

    /** Same packing as Paper's {@code Chunk.getChunkKey(x, z)}. */
    static long key(int chunkX, int chunkZ) {
        return (long) chunkX & 0xffffffffL | ((long) chunkZ & 0xffffffffL) << 32;
    }

    static int chunkX(long key) {
        return (int) key;
    }

    static int chunkZ(long key) {
        return (int) (key >>> 32);
    }

    /** Hoppers in the chunk (0 for an unknown chunk). */
    int get(String world, long chunk) {
        Map<Long, int[]> map = worlds.get(world);
        int[] c = map == null ? null : map.get(chunk);
        return c == null ? 0 : c[0];
    }

    /** Sets the exact count (chunk load, recount). */
    void set(String world, long chunk, int count) {
        if (count <= 0) {
            unload(world, chunk);
            return;
        }
        Map<Long, int[]> map = worlds.computeIfAbsent(world, w -> new HashMap<>());
        int[] c = map.get(chunk);
        if (c == null) {
            map.put(chunk, new int[]{count});
        } else {
            c[0] = count;
        }
    }

    /** Adds {@code delta} (place +1, break -1) and returns the new count; never below 0. */
    int add(String world, long chunk, int delta) {
        int next = Math.max(0, get(world, chunk) + delta);
        set(world, chunk, next);
        return next;
    }

    void unload(String world, long chunk) {
        Map<Long, int[]> map = worlds.get(world);
        if (map != null && map.remove(chunk) != null && map.isEmpty()) {
            worlds.remove(world);
        }
    }

    void unloadWorld(String world) {
        worlds.remove(world);
    }

    void clear() {
        worlds.clear();
    }

    /** Chunks with at least one hopper. */
    int chunks() {
        int n = 0;
        for (Map<Long, int[]> map : worlds.values()) {
            n += map.size();
        }
        return n;
    }

    /** The chunk with the most hoppers among the loaded chunks that pass {@code filter}; ties: the first found. */
    Optional<Hotspot> busiest(String world, ChunkFilter filter) {
        Map<Long, int[]> map = worlds.get(world);
        if (map == null) {
            return Optional.empty();
        }
        Hotspot best = null;
        for (Map.Entry<Long, int[]> e : map.entrySet()) {
            long k = e.getKey();
            int x = chunkX(k);
            int z = chunkZ(k);
            int count = e.getValue()[0];
            if ((best == null || count > best.count()) && filter.test(x, z)) {
                best = new Hotspot(x, z, count);
            }
        }
        return Optional.ofNullable(best);
    }
}

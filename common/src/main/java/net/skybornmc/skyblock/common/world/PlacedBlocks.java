package net.skybornmc.skyblock.common.world;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers blocks a player placed, so breaking them again never counts (milestones, Complete Developer Spec §7.3: placed
 * blocks never count). Pure and bounded: positions are kept per world for {@code ttlMillis} and at most {@code maxPerWorld}
 * per world (oldest dropped first), so memory stays flat however much is built. Main thread only; no Bukkit types, so it
 * is unit-tested. One instance is published as a service by the module that listens to block place / break events.
 */
public final class PlacedBlocks {

    private final long ttlMillis;
    private final int maxPerWorld;
    private final Map<UUID, LinkedHashMap<Long, Long>> worlds = new HashMap<>();

    public PlacedBlocks(long ttlMillis, int maxPerWorld) {
        this.ttlMillis = Math.max(1, ttlMillis);
        this.maxPerWorld = Math.max(1, maxPerWorld);
    }

    /** One long per block position (x and z 26 bits, y 12 bits; the vanilla packing). */
    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** A player placed a block here at {@code now}. */
    public void placed(UUID world, int x, int y, int z, long now) {
        LinkedHashMap<Long, Long> map = worlds.computeIfAbsent(world, w -> new LinkedHashMap<>(256, 0.75f, false));
        long key = pack(x, y, z);
        map.remove(key); // re-insert so it is the newest
        map.put(key, now);
        while (map.size() > maxPerWorld) {
            Long oldest = map.keySet().iterator().next();
            map.remove(oldest);
        }
    }

    /**
     * True when a player placed this block within the TTL. Breaking it should call {@link #forget}; a block that grew
     * there (crops, generator output) was never placed, so it counts.
     */
    public boolean isPlaced(UUID world, int x, int y, int z, long now) {
        LinkedHashMap<Long, Long> map = worlds.get(world);
        if (map == null) {
            return false;
        }
        Long at = map.get(pack(x, y, z));
        if (at == null) {
            return false;
        }
        if (now - at > ttlMillis) {
            map.remove(pack(x, y, z));
            return false;
        }
        return true;
    }

    /** The block is gone (broken, moved): forget it. */
    public void forget(UUID world, int x, int y, int z) {
        LinkedHashMap<Long, Long> map = worlds.get(world);
        if (map != null) {
            map.remove(pack(x, y, z));
        }
    }

    /** Drops expired entries (call now and then, e.g. every minute). */
    public void prune(long now) {
        for (LinkedHashMap<Long, Long> map : worlds.values()) {
            map.values().removeIf(at -> now - at > ttlMillis);
        }
    }

    public int size() {
        int n = 0;
        for (Map<Long, Long> map : worlds.values()) {
            n += map.size();
        }
        return n;
    }
}

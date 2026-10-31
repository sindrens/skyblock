package net.skybornmc.skyblock.stacking;

import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Chunk-indexed cache of the stackable mobs in the stacking worlds, kept up to date by entity add/remove events (also
 * fired on chunk load/unload), so merging never scans entities: a lookup touches only the few chunks around a point.
 * Moving (natural) mobs are re-indexed by the periodic merge task. Main thread only.
 */
final class MobIndex {

    private record Entry(LivingEntity entity, UUID world, long chunk) {
    }

    private final Map<UUID, Map<Long, List<LivingEntity>>> worlds = new HashMap<>();
    private final Map<UUID, Entry> byEntity = new HashMap<>();
    /** Mobs with AI (they move), visited round-robin by the merge task. */
    private final List<LivingEntity> moving = new ArrayList<>();
    private int cursor;

    static long key(int chunkX, int chunkZ) {
        return (chunkX & 0xffffffffL) | ((chunkZ & 0xffffffffL) << 32);
    }

    void add(LivingEntity entity, boolean moves) {
        UUID id = entity.getUniqueId();
        if (byEntity.containsKey(id)) {
            return;
        }
        UUID world = entity.getWorld().getUID();
        long chunk = key(entity.getLocation().getBlockX() >> 4, entity.getLocation().getBlockZ() >> 4);
        byEntity.put(id, new Entry(entity, world, chunk));
        worlds.computeIfAbsent(world, w -> new HashMap<>()).computeIfAbsent(chunk, c -> new ArrayList<>(4)).add(entity);
        if (moves) {
            moving.add(entity);
        }
    }

    void remove(LivingEntity entity) {
        Entry entry = byEntity.remove(entity.getUniqueId());
        if (entry == null) {
            return;
        }
        Map<Long, List<LivingEntity>> chunks = worlds.get(entry.world());
        if (chunks != null) {
            List<LivingEntity> list = chunks.get(entry.chunk());
            if (list != null) {
                list.remove(entity);
                if (list.isEmpty()) {
                    chunks.remove(entry.chunk());
                }
            }
        }
        // Moving list entries are dropped lazily by nextMoving() (no O(n) removal here).
    }

    boolean contains(LivingEntity entity) {
        return byEntity.containsKey(entity.getUniqueId());
    }

    /** Re-files a mob that may have walked into another chunk. */
    void update(LivingEntity entity) {
        Entry entry = byEntity.get(entity.getUniqueId());
        if (entry == null) {
            return;
        }
        long now = key(entity.getLocation().getBlockX() >> 4, entity.getLocation().getBlockZ() >> 4);
        if (now == entry.chunk() && entity.getWorld().getUID().equals(entry.world())) {
            return;
        }
        remove(entity); // leaves the moving list alone
        byEntity.put(entity.getUniqueId(), new Entry(entity, entity.getWorld().getUID(), now));
        worlds.computeIfAbsent(entity.getWorld().getUID(), w -> new HashMap<>()).computeIfAbsent(now, c -> new ArrayList<>(4)).add(entity);
    }

    /** Every indexed mob in the chunks touched by a square of {@code radius} blocks around (x, z). */
    void near(UUID world, double x, double z, double radius, Consumer<LivingEntity> action) {
        Map<Long, List<LivingEntity>> chunks = worlds.get(world);
        if (chunks == null) {
            return;
        }
        int minX = (int) Math.floor(x - radius) >> 4;
        int maxX = (int) Math.floor(x + radius) >> 4;
        int minZ = (int) Math.floor(z - radius) >> 4;
        int maxZ = (int) Math.floor(z + radius) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                List<LivingEntity> list = chunks.get(key(cx, cz));
                if (list != null) {
                    // Copy-free iteration is safe: callers never add/remove while iterating (they collect first).
                    for (int i = 0; i < list.size(); i++) {
                        action.accept(list.get(i));
                    }
                }
            }
        }
    }

    /** The indexed mobs of one chunk (live list, do not modify). */
    List<LivingEntity> chunk(UUID world, int chunkX, int chunkZ) {
        Map<Long, List<LivingEntity>> chunks = worlds.get(world);
        if (chunks == null) {
            return List.of();
        }
        List<LivingEntity> list = chunks.get(key(chunkX, chunkZ));
        return list == null ? List.of() : list;
    }

    /**
     * R3-26: indexed mobs (each one is a stack, a single mob is a stack of 1) in one chunk, without {@code except} when it
     * is filed there. O(1): the size of the chunk list that the add/remove events keep up to date (no entity scan).
     */
    int chunkTotal(UUID world, int chunkX, int chunkZ, LivingEntity except) {
        Map<Long, List<LivingEntity>> chunks = worlds.get(world);
        if (chunks == null) {
            return 0;
        }
        long chunk = key(chunkX, chunkZ);
        List<LivingEntity> list = chunks.get(chunk);
        if (list == null) {
            return 0;
        }
        int n = list.size();
        if (except != null) {
            Entry entry = byEntity.get(except.getUniqueId());
            if (entry != null && entry.chunk() == chunk && entry.world().equals(world)) {
                n--;
            }
        }
        return Math.max(0, n);
    }

    /** The next moving mob for the merge task, or null when there are none. Invalid entries are dropped on the way. */
    LivingEntity nextMoving() {
        while (!moving.isEmpty()) {
            if (cursor >= moving.size()) {
                cursor = 0;
            }
            LivingEntity entity = moving.get(cursor);
            if (entity.isValid() && byEntity.containsKey(entity.getUniqueId())) {
                cursor++;
                return entity;
            }
            // Swap-remove keeps this O(1).
            int last = moving.size() - 1;
            moving.set(cursor, moving.get(last));
            moving.remove(last);
        }
        return null;
    }

    int movingSize() {
        return moving.size();
    }

    int size() {
        return byEntity.size();
    }

    void clear() {
        worlds.clear();
        byEntity.clear();
        moving.clear();
        cursor = 0;
    }
}

package net.skybornmc.skyblock.depths.engine;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable lookup "which region is this block in?" built once per region change.
 *
 * <p>Per world, every chunk a region touches maps to the (usually one) regions overlapping it, so a lookup is one
 * string-keyed map hit (world), one primitive map hit (chunk) and a bounds check per candidate: no allocation, a few
 * dozen nanoseconds. Blocks in worlds without regions return after the first map miss. When regions overlap, the one
 * earlier in the list wins.</p>
 *
 * @param <R> region type
 */
public final class RegionIndex<R extends Bounds> {

    /** Upper limit of chunks one region may cover (a 2048×2048 area is 16 384 chunks). */
    public static final int MAX_CHUNKS_PER_REGION = 65_536;

    private static final Object[] NONE = new Object[0];

    private final List<R> regions;
    private final Map<String, Long2ObjectOpenHashMap<Object[]>> worlds;

    private RegionIndex(List<R> regions, Map<String, Long2ObjectOpenHashMap<Object[]>> worlds) {
        this.regions = regions;
        this.worlds = worlds;
    }

    public static <R extends Bounds> RegionIndex<R> empty() {
        return new RegionIndex<>(List.of(), Map.of());
    }

    /**
     * Builds the index. Order matters for overlaps (first wins).
     *
     * @throws IllegalArgumentException if a region covers more than {@link #MAX_CHUNKS_PER_REGION} chunks
     */
    public static <R extends Bounds> RegionIndex<R> of(List<R> regions) {
        Map<String, Long2ObjectOpenHashMap<List<R>>> building = new HashMap<>();
        for (R region : regions) {
            int cx0 = region.minX() >> 4;
            int cx1 = region.maxX() >> 4;
            int cz0 = region.minZ() >> 4;
            int cz1 = region.maxZ() >> 4;
            long chunks = (long) (cx1 - cx0 + 1) * (cz1 - cz0 + 1);
            if (chunks > MAX_CHUNKS_PER_REGION) {
                throw new IllegalArgumentException("region covers " + chunks + " chunks (max " + MAX_CHUNKS_PER_REGION + ")");
            }
            Long2ObjectOpenHashMap<List<R>> world = building.computeIfAbsent(region.world(), w -> new Long2ObjectOpenHashMap<>());
            for (int cx = cx0; cx <= cx1; cx++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    world.computeIfAbsent(BlockKeys.chunk(cx, cz), k -> new ArrayList<>(1)).add(region);
                }
            }
        }
        Map<String, Long2ObjectOpenHashMap<Object[]>> worlds = new HashMap<>();
        building.forEach((name, chunks) -> {
            Long2ObjectOpenHashMap<Object[]> frozen = new Long2ObjectOpenHashMap<>(chunks.size());
            for (Long2ObjectOpenHashMap.Entry<List<R>> e : chunks.long2ObjectEntrySet()) {
                frozen.put(e.getLongKey(), e.getValue().toArray());
            }
            frozen.trim();
            worlds.put(name, frozen);
        });
        return new RegionIndex<>(List.copyOf(regions), worlds);
    }

    /** The region containing the block, or {@code null}. */
    @SuppressWarnings("unchecked")
    public R at(String world, int x, int y, int z) {
        Long2ObjectOpenHashMap<Object[]> chunks = worlds.get(world);
        if (chunks == null) {
            return null;
        }
        Object[] candidates = chunks.getOrDefault(BlockKeys.chunk(x >> 4, z >> 4), NONE);
        for (Object candidate : candidates) {
            R region = (R) candidate;
            if (region.contains(x, y, z)) {
                return region;
            }
        }
        return null;
    }

    /** True if any region exists in this world (cheap early-out for world-wide events). */
    public boolean hasWorld(String world) {
        return worlds.containsKey(world);
    }

    /** True if any region touches this chunk. */
    public boolean touchesChunk(String world, int chunkX, int chunkZ) {
        Long2ObjectOpenHashMap<Object[]> chunks = worlds.get(world);
        return chunks != null && chunks.containsKey(BlockKeys.chunk(chunkX, chunkZ));
    }

    /** Every region, in priority order. */
    public List<R> regions() {
        return Collections.unmodifiableList(regions);
    }

    public boolean isEmpty() {
        return regions.isEmpty();
    }
}

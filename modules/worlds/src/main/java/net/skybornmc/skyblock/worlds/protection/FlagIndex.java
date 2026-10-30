package net.skybornmc.skyblock.worlds.protection;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.skybornmc.skyblock.common.builds.BuildBox;
import net.skybornmc.skyblock.common.region.FlagRegion;
import net.skybornmc.skyblock.common.region.RegionFlag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chunk-indexed flag regions plus world flags (pure, unit-tested, immutable). A lookup is a world map get, a chunk map
 * get (primitive long key, no boxing) and a box check per region in that chunk; nothing is allocated.
 *
 * <p>Resolution of one flag at a block: among the regions containing it whose kind sets the flag, the highest priority
 * wins; on a tie deny wins. No region sets it → the world's value. Nothing → {@link FlagRules#NONE} (allowed).</p>
 */
public final class FlagIndex {

    /** One indexed region: its kind's priority and flags, plus a label for debug output. */
    record Entry(String owner, String id, String kind, BuildBox box, int priority, byte[] flags) {

        String label() {
            return owner + ":" + id + " (" + kind + ", " + priority + ")";
        }
    }

    /** One world: its flags and its regions by chunk. */
    static final class WorldEntry {
        byte[] flags;
        final Long2ObjectOpenHashMap<Entry[]> chunks = new Long2ObjectOpenHashMap<>();
    }

    /** A region as handed to {@link #build}: who owns it ({@code build id} or module id) and the region. */
    public record Source(String owner, FlagRegion region) {
    }

    public static final FlagIndex EMPTY = new FlagIndex(Map.of(), 0);

    private final Map<String, WorldEntry> worlds;
    private final int regionCount;

    private FlagIndex(Map<String, WorldEntry> worlds, int regionCount) {
        this.worlds = worlds;
        this.regionCount = regionCount;
    }

    /**
     * Builds the index: every source's kind goes through {@link FlagRules#kindOf} (overrides), regions whose kind sets
     * no flag are left out. World names are matched case-insensitively.
     */
    public static FlagIndex build(FlagRules rules, List<Source> sources) {
        Map<String, WorldEntry> worlds = new HashMap<>();
        for (String w : rules.worlds()) {
            worlds.computeIfAbsent(w, k -> new WorldEntry()).flags = rules.world(w);
        }
        Map<String, Map<Long, List<Entry>>> lists = new HashMap<>();
        int count = 0;
        for (Source s : sources) {
            FlagRegion r = s.region();
            String kind = rules.kindOf(s.owner(), r.id(), r.kind());
            FlagRules.KindRule rule = rules.kind(kind).orElse(null);
            if (rule == null || rule.isEmpty()) {
                continue;
            }
            Entry e = new Entry(s.owner(), r.id(), kind, r.box(), rule.priority(), rule.flags());
            String world = r.world().toLowerCase(Locale.ROOT);
            Map<Long, List<Entry>> chunks = lists.computeIfAbsent(world, k -> new HashMap<>());
            for (int cx = r.box().minChunkX(); cx <= r.box().maxChunkX(); cx++) {
                for (int cz = r.box().minChunkZ(); cz <= r.box().maxChunkZ(); cz++) {
                    chunks.computeIfAbsent(chunkKey(cx, cz), k -> new ArrayList<>()).add(e);
                }
            }
            count++;
        }
        Comparator<Entry> order = Comparator.comparingInt(Entry::priority).reversed();
        lists.forEach((world, chunks) -> {
            WorldEntry we = worlds.computeIfAbsent(world, k -> new WorldEntry());
            chunks.forEach((key, list) -> {
                list.sort(order);
                we.chunks.put((long) key, list.toArray(new Entry[0]));
            });
        });
        return new FlagIndex(Map.copyOf(worlds), count);
    }

    /** Number of indexed regions. */
    public int regionCount() {
        return regionCount;
    }

    /** True if the world has flags or regions (cheap pre-check). */
    public boolean hasWorld(String world) {
        return worlds.containsKey(world.toLowerCase(Locale.ROOT));
    }

    /** {@link FlagRules#ALLOW}, {@link FlagRules#DENY} or {@link FlagRules#NONE}. */
    public byte resolve(String world, int x, int y, int z, RegionFlag flag) {
        WorldEntry we = worlds.get(world);
        if (we == null) {
            we = worlds.get(world.toLowerCase(Locale.ROOT));
            if (we == null) {
                return FlagRules.NONE;
            }
        }
        Entry[] entries = we.chunks.get(chunkKey(x >> 4, z >> 4));
        if (entries != null) {
            int ordinal = flag.ordinal();
            int bestPriority = Integer.MIN_VALUE;
            byte best = FlagRules.NONE;
            for (Entry e : entries) {
                if (best != FlagRules.NONE && e.priority < bestPriority) {
                    break; // sorted by priority, highest first: nothing below can win
                }
                byte v = e.flags[ordinal];
                if (v != FlagRules.NONE && e.box.contains(x, y, z)) {
                    if (best == FlagRules.NONE || e.priority > bestPriority) {
                        best = v;
                        bestPriority = e.priority;
                    } else if (v == FlagRules.DENY) {
                        best = FlagRules.DENY; // same priority: deny wins
                    }
                }
            }
            if (best != FlagRules.NONE) {
                return best;
            }
        }
        return we.flags == null ? FlagRules.NONE : we.flags[flag.ordinal()];
    }

    public boolean denied(String world, int x, int y, int z, RegionFlag flag) {
        return resolve(world, x, y, z, flag) == FlagRules.DENY;
    }

    /** Labels of the regions at a block, highest priority first. */
    public List<String> regionsAt(String world, int x, int y, int z) {
        WorldEntry we = worlds.get(world.toLowerCase(Locale.ROOT));
        if (we == null) {
            return List.of();
        }
        Entry[] entries = we.chunks.get(chunkKey(x >> 4, z >> 4));
        if (entries == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.box.contains(x, y, z)) {
                out.add(e.label());
            }
        }
        return out;
    }

    /** Ids of the flags denied at a block. */
    public List<String> deniedAt(String world, int x, int y, int z) {
        List<String> out = new ArrayList<>();
        for (RegionFlag f : RegionFlag.values()) {
            if (denied(world, x, y, z, f)) {
                out.add(f.id());
            }
        }
        return out;
    }

    static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }
}

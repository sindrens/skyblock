package net.skybornmc.skyblock.progression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Block worth per island chunk (PHASE-5 M5.2 source 8), main thread only. Live events add or remove worth; a full
 * rescan replaces a chunk's value. Changes made to a chunk while its rescan runs (after its copy was taken) are kept
 * on top of the scanned value, so nothing placed during a scan is lost. Pure, unit-tested.
 */
final class BlockWorthLedger {

    /** Island + chunk. */
    record Key(UUID island, int chunkX, int chunkZ) {
    }

    private static final class ChunkWorth {
        double blocks;
        double spawners;
        boolean scanning;
        double scanDelta;
    }

    private final Map<UUID, Map<Long, ChunkWorth>> islands = new HashMap<>();
    private final Set<Key> dirty = new LinkedHashSet<>();
    private final Set<UUID> touched = new HashSet<>();

    static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    static int chunkX(long key) {
        return (int) (key >> 32);
    }

    static int chunkZ(long key) {
        return (int) key;
    }

    private ChunkWorth chunk(UUID island, int x, int z) {
        return islands.computeIfAbsent(island, k -> new HashMap<>()).computeIfAbsent(chunkKey(x, z), k -> new ChunkWorth());
    }

    /** A live change of placed-block worth (negative for broken blocks). Never goes below 0 (the rescan fixes drift). */
    void add(UUID island, int x, int z, double delta) {
        if (island == null || delta == 0 || !Double.isFinite(delta)) {
            return;
        }
        ChunkWorth c = chunk(island, x, z);
        c.blocks = Math.max(0, c.blocks + delta);
        if (c.scanning) {
            c.scanDelta += delta;
        }
        dirty.add(new Key(island, x, z));
        touched.add(island);
    }

    /** The spawner worth of a chunk, counted again from its spawner blocks. */
    void setSpawners(UUID island, int x, int z, double worth) {
        if (island == null || !Double.isFinite(worth)) {
            return;
        }
        ChunkWorth c = chunk(island, x, z);
        double v = Math.max(0, worth);
        if (c.spawners != v) {
            c.spawners = v;
            dirty.add(new Key(island, x, z));
            touched.add(island);
        }
    }

    /** The rescan took its copy of this chunk: later live changes are remembered on top of the scanned value. */
    void beginScan(UUID island, int x, int z) {
        ChunkWorth c = chunk(island, x, z);
        c.scanning = true;
        c.scanDelta = 0;
    }

    /** The rescan counted the chunk: blocks = scanned + live changes since the copy; spawners = scanned. */
    void finishScan(UUID island, int x, int z, double scannedBlocks, double scannedSpawners) {
        ChunkWorth c = chunk(island, x, z);
        c.blocks = Math.max(0, (Double.isFinite(scannedBlocks) ? scannedBlocks : 0) + (c.scanning ? c.scanDelta : 0));
        c.spawners = Math.max(0, Double.isFinite(scannedSpawners) ? scannedSpawners : 0);
        c.scanning = false;
        c.scanDelta = 0;
        dirty.add(new Key(island, x, z));
    }

    void abortScan(UUID island, int x, int z) {
        Map<Long, ChunkWorth> m = islands.get(island);
        ChunkWorth c = m == null ? null : m.get(chunkKey(x, z));
        if (c != null) {
            c.scanning = false;
            c.scanDelta = 0;
        }
    }

    /** Chunks of the island outside {@code keep} (the border shrank, or stale rows) are set to 0. */
    void clearOutside(UUID island, Set<Long> keep) {
        Map<Long, ChunkWorth> m = islands.get(island);
        if (m == null) {
            return;
        }
        for (Map.Entry<Long, ChunkWorth> e : m.entrySet()) {
            if (!keep.contains(e.getKey()) && (e.getValue().blocks != 0 || e.getValue().spawners != 0)) {
                e.getValue().blocks = 0;
                e.getValue().spawners = 0;
                dirty.add(new Key(island, chunkX(e.getKey()), chunkZ(e.getKey())));
            }
        }
    }

    /** Puts a stored row back (load at start). */
    void restore(UUID island, int x, int z, double blocks, double spawners) {
        ChunkWorth c = chunk(island, x, z);
        c.blocks = Math.max(0, blocks);
        c.spawners = Math.max(0, spawners);
    }

    /**
     * PERF11: stored rows grouped per island and chunk off the main thread; {@link #adopt} puts them back on the main
     * thread with the same result as {@link #restore} per row.
     */
    static final class Restored {
        private final Map<UUID, Map<Long, ChunkWorth>> islands = new HashMap<>();

        int islands() {
            return islands.size();
        }
    }

    /** Any thread: the stored rows as chunk values (a later row of the same chunk wins, as with {@link #restore}). */
    static Restored prepare(List<LevelStore.WorthRow> rows) {
        Restored out = new Restored();
        for (LevelStore.WorthRow r : rows) {
            ChunkWorth c = out.islands.computeIfAbsent(r.island(), k -> new HashMap<>())
                    .computeIfAbsent(chunkKey(r.chunkX(), r.chunkZ()), k -> new ChunkWorth());
            c.blocks = Math.max(0, r.blocks());
            c.spawners = Math.max(0, r.spawners());
        }
        return out;
    }

    /**
     * Main thread: hands prepared rows over. An island not known yet takes the prepared chunks as they are; a known
     * chunk keeps its object (and a running scan) and gets the stored blocks and spawners, like {@link #restore}.
     * {@code restored} must not be used afterwards.
     */
    void adopt(Restored restored) {
        for (Map.Entry<UUID, Map<Long, ChunkWorth>> e : restored.islands.entrySet()) {
            Map<Long, ChunkWorth> known = islands.putIfAbsent(e.getKey(), e.getValue());
            if (known == null) {
                continue;
            }
            for (Map.Entry<Long, ChunkWorth> chunk : e.getValue().entrySet()) {
                ChunkWorth c = known.putIfAbsent(chunk.getKey(), chunk.getValue());
                if (c != null) {
                    c.blocks = chunk.getValue().blocks;
                    c.spawners = chunk.getValue().spawners;
                }
            }
        }
        restored.islands.clear();
    }

    /** Money worth of the island's placed blocks and spawners. */
    double worth(UUID island) {
        Map<Long, ChunkWorth> m = islands.get(island);
        if (m == null) {
            return 0;
        }
        double sum = 0;
        for (ChunkWorth c : m.values()) {
            sum += c.blocks + c.spawners;
        }
        return sum;
    }

    double blocks(UUID island, int x, int z) {
        Map<Long, ChunkWorth> m = islands.get(island);
        ChunkWorth c = m == null ? null : m.get(chunkKey(x, z));
        return c == null ? 0 : c.blocks;
    }

    double spawners(UUID island, int x, int z) {
        Map<Long, ChunkWorth> m = islands.get(island);
        ChunkWorth c = m == null ? null : m.get(chunkKey(x, z));
        return c == null ? 0 : c.spawners;
    }

    boolean known(UUID island) {
        return islands.containsKey(island);
    }

    /** Changed chunks since the last drain, with their current values. */
    List<LevelStore.WorthRow> drainDirty() {
        List<LevelStore.WorthRow> out = new ArrayList<>(dirty.size());
        for (Key k : dirty) {
            Map<Long, ChunkWorth> m = islands.get(k.island());
            ChunkWorth c = m == null ? null : m.get(chunkKey(k.chunkX(), k.chunkZ()));
            if (c != null) {
                out.add(new LevelStore.WorthRow(k.island(), k.chunkX(), k.chunkZ(), c.blocks, c.spawners));
            }
        }
        dirty.clear();
        return out;
    }

    /** A failed write: mark the rows dirty again (their current values are written next time). */
    void markDirty(List<LevelStore.WorthRow> rows) {
        for (LevelStore.WorthRow r : rows) {
            dirty.add(new Key(r.island(), r.chunkX(), r.chunkZ()));
        }
    }

    boolean hasDirty() {
        return !dirty.isEmpty();
    }

    /** Islands with live changes since the last drain (the nightly rescan counts them again). */
    Set<UUID> drainTouched() {
        Set<UUID> out = new HashSet<>(touched);
        touched.clear();
        return out;
    }

    void touch(UUID island) {
        if (island != null) {
            touched.add(island);
        }
    }

    void forget(UUID island) {
        islands.remove(island);
        touched.remove(island);
        dirty.removeIf(k -> k.island().equals(island));
    }
}

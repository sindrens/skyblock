package net.skybornmc.skyblock.worlds.portals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Chunk-indexed portal triggers (pure, unit-tested): a lookup is two hash gets and a few box checks, so it can run
 * on every block-changing move. Immutable.
 */
public final class PortalIndex {

    public static final PortalIndex EMPTY = new PortalIndex(List.of());

    private final List<Portal> portals;
    private final Map<String, Map<Long, List<Portal>>> byWorld = new HashMap<>();

    public PortalIndex(List<Portal> portals) {
        this.portals = List.copyOf(portals);
        for (Portal p : this.portals) {
            Map<Long, List<Portal>> chunks = byWorld.computeIfAbsent(p.world(), w -> new HashMap<>());
            for (int cx = p.box().minChunkX(); cx <= p.box().maxChunkX(); cx++) {
                for (int cz = p.box().minChunkZ(); cz <= p.box().maxChunkZ(); cz++) {
                    chunks.computeIfAbsent(chunkKey(cx, cz), k -> new ArrayList<>()).add(p);
                }
            }
        }
    }

    public List<Portal> all() {
        return portals;
    }

    public boolean isEmpty() {
        return portals.isEmpty();
    }

    /** True if the world has at least one portal (cheap pre-check). */
    public boolean hasWorld(String world) {
        return byWorld.containsKey(world);
    }

    /** The portal whose trigger contains this block, if any. */
    public Optional<Portal> at(String world, int x, int y, int z) {
        Map<Long, List<Portal>> chunks = byWorld.get(world);
        if (chunks == null) {
            return Optional.empty();
        }
        List<Portal> list = chunks.get(chunkKey(x >> 4, z >> 4));
        if (list == null) {
            return Optional.empty();
        }
        for (Portal p : list) {
            if (p.box().contains(x, y, z)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }
}

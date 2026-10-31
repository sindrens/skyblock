package net.skybornmc.skyblock.common.visibility;

import net.skybornmc.skyblock.common.builds.BuildPoints;
import net.skybornmc.skyblock.common.builds.BuildRegion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PERF11: makes the visibility position check ({@code visibility-zones}, every {@code region-check-ticks}) cheap without
 * changing what it decides:
 * <ul>
 *   <li>the configured regions are looked up in the build points only when the builds changed
 *       ({@link BuildPoints#revision}), the service was replaced or the region ids changed, instead of on every check
 *       (each lookup walked every region of every applied build);</li>
 *   <li>a player's zone is computed again only when they stand on another block or in another world than at their last
 *       check, or the places changed ({@link #moved}); the zone only depends on those, so the answer is the same;</li>
 *   <li>the zone test itself ({@link #inSettingZone}) uses block coordinates and allocates nothing.</li>
 * </ul>
 * Main thread only (no locking). Pure, unit-tested.
 */
final class ZoneTracker {

    /** Where a player stood at their last check, and the places it was checked against (compared by identity). */
    private static final class Spot {
        String world;
        int x;
        int y;
        int z;
        List<BuildRegion> regions;
        List<String> worlds;
    }

    private final Map<UUID, Spot> spots = new HashMap<>();
    private BuildPoints cachedBuilds;
    private long cachedRevision = -1;
    private List<String> cachedIds;
    private List<BuildRegion> cachedRegions = List.of();

    /**
     * The regions with the configured ids, in id order (missing ids skipped). The same list object is returned while
     * the build points service, its revision and the id list stay the same; an untracked revision (negative) looks up
     * every time.
     */
    List<BuildRegion> regions(BuildPoints builds, List<String> ids) {
        if (ids == null || ids.isEmpty() || builds == null) {
            return List.of();
        }
        long revision = builds.revision();
        if (revision >= 0 && builds == cachedBuilds && revision == cachedRevision && ids == cachedIds) {
            return cachedRegions;
        }
        List<BuildRegion> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                builds.region("regions", id.trim()).ifPresent(out::add);
            }
        }
        List<BuildRegion> regions = List.copyOf(out);
        if (revision >= 0) {
            cachedBuilds = builds;
            cachedRevision = revision;
            cachedIds = ids;
            cachedRegions = regions;
        }
        return regions;
    }

    /**
     * True when the player's zone has to be computed: their first check (or after {@link #forget}), another block or
     * world than at the last check, or other places to check against. Records the new spot either way.
     */
    boolean moved(UUID id, String world, int x, int y, int z, List<BuildRegion> regions, List<String> worlds) {
        Spot s = spots.get(id);
        if (s == null) {
            s = new Spot();
            spots.put(id, s);
        } else if (s.x == x && s.y == y && s.z == z && s.regions == regions && s.worlds == worlds && s.world.equals(world)) {
            return false;
        }
        s.world = world;
        s.x = x;
        s.y = y;
        s.z = z;
        s.regions = regions;
        s.worlds = worlds;
        return true;
    }

    /** The next check of this player computes their zone again (quit, or their zone was computed elsewhere). */
    void forget(UUID id) {
        spots.remove(id);
    }

    void clear() {
        spots.clear();
        cachedBuilds = null;
        cachedRevision = -1;
        cachedIds = null;
        cachedRegions = List.of();
    }

    int tracked() {
        return spots.size();
    }

    /** Inside a place where the Show players choice applies: a listed world, or inside one of the regions of that world. */
    static boolean inSettingZone(String world, int x, int y, int z, List<String> worlds, List<BuildRegion> regions) {
        if (worlds != null && worlds.contains(world)) {
            return true;
        }
        for (int i = 0, n = regions.size(); i < n; i++) {
            BuildRegion r = regions.get(i);
            if (r.world().equals(world) && r.box().contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }
}

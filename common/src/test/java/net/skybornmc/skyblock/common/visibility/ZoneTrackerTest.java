package net.skybornmc.skyblock.common.visibility;

import net.skybornmc.skyblock.common.builds.BuildBox;
import net.skybornmc.skyblock.common.builds.BuildPoint;
import net.skybornmc.skyblock.common.builds.BuildPoints;
import net.skybornmc.skyblock.common.builds.BuildRegion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF11: the cheap visibility position check decides exactly what the old one did. */
class ZoneTrackerTest {

    private static final BuildRegion POOL = new BuildRegion("spawn", "regions", "afk_pool", "afk", "spawn",
            new BuildBox(10, 60, 10, 20, 70, 20), Map.of());
    private static final BuildRegion PLOT = new BuildRegion("spawn", "regions", "plot", "plot", "spawn",
            new BuildBox(-5, 0, -5, 5, 10, 5), Map.of());

    /** Build points that count lookups; revision as set by the test. */
    private static final class Builds implements BuildPoints {
        final List<BuildRegion> regions = new ArrayList<>(List.of(POOL, PLOT));
        long revision;
        int lookups;

        @Override
        public List<BuildPoint> points(String category) {
            return List.of();
        }

        @Override
        public List<BuildPoint> points(String build, String category) {
            return List.of();
        }

        @Override
        public Optional<BuildPoint> point(String category, String id) {
            return Optional.empty();
        }

        @Override
        public List<BuildRegion> regions(String category) {
            return regions.stream().filter(r -> r.category().equals(category)).toList();
        }

        @Override
        public Optional<BuildRegion> region(String category, String id) {
            lookups++;
            return regions(category).stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public Set<String> categories() {
            return Set.of();
        }

        @Override
        public Set<String> builds() {
            return Set.of("spawn");
        }

        @Override
        public long revision() {
            return revision;
        }
    }

    @Test
    void regionsAreLookedUpAgainOnlyOnAChange() {
        ZoneTracker t = new ZoneTracker();
        Builds builds = new Builds();
        List<String> ids = List.of("afk_pool", " missing ", "plot");
        List<BuildRegion> first = t.regions(builds, ids);
        assertEquals(List.of(POOL, PLOT), first, "id order, missing ids skipped, ids trimmed (as before)");
        int lookups = builds.lookups;
        assertSame(first, t.regions(builds, ids));
        assertEquals(lookups, builds.lookups, "no lookup while nothing changed");

        builds.revision++;
        builds.regions.remove(PLOT);
        List<BuildRegion> second = t.regions(builds, ids);
        assertEquals(List.of(POOL), second, "a re-applied build is seen at the next check");
        assertNotSame(first, second);

        List<String> otherIds = List.of("plot");
        builds.regions.add(PLOT);
        builds.revision++;
        assertEquals(List.of(PLOT), t.regions(builds, otherIds), "changed ids are looked up");

        Builds replaced = new Builds();
        replaced.revision = builds.revision;
        replaced.regions.remove(POOL);
        assertEquals(List.of(PLOT), t.regions(replaced, ids), "another service is looked up even with the same revision");
    }

    @Test
    void untrackedRevisionLooksUpEveryTime() {
        ZoneTracker t = new ZoneTracker();
        Builds builds = new Builds();
        builds.revision = -1;
        List<String> ids = List.of("afk_pool");
        t.regions(builds, ids);
        t.regions(builds, ids);
        assertEquals(2, builds.lookups);
    }

    @Test
    void noBuildsOrNoIdsMeansNoRegions() {
        ZoneTracker t = new ZoneTracker();
        assertEquals(List.of(), t.regions(null, List.of("afk_pool")));
        assertEquals(List.of(), t.regions(new Builds(), List.of()));
        assertEquals(List.of(), t.regions(new Builds(), null));
    }

    @Test
    void movedOnlyOnAnotherBlockWorldOrPlaces() {
        ZoneTracker t = new ZoneTracker();
        UUID id = UUID.randomUUID();
        List<BuildRegion> regions = List.of(POOL);
        List<String> worlds = List.of("depths");
        assertTrue(t.moved(id, "spawn", 1, 64, 1, regions, worlds), "first check");
        assertFalse(t.moved(id, "spawn", 1, 64, 1, regions, worlds), "same block");
        assertTrue(t.moved(id, "spawn", 2, 64, 1, regions, worlds));
        assertTrue(t.moved(id, "spawn", 2, 65, 1, regions, worlds));
        assertTrue(t.moved(id, "spawn", 2, 65, 0, regions, worlds));
        assertTrue(t.moved(id, "depths", 2, 65, 0, regions, worlds), "another world, same block numbers");
        assertTrue(t.moved(id, "depths", 2, 65, 0, List.of(POOL, PLOT), worlds), "other regions");
        List<BuildRegion> now = List.of(POOL, PLOT);
        t.moved(id, "depths", 2, 65, 0, now, worlds);
        assertTrue(t.moved(id, "depths", 2, 65, 0, now, List.of("depths")), "another world list (a reload)");
        t.forget(id);
        assertTrue(t.moved(id, "depths", 2, 65, 0, now, worlds), "forgotten: computed again");
        assertEquals(1, t.tracked());
        t.clear();
        assertEquals(0, t.tracked());
    }

    @Test
    void zoneTestMatchesTheOldLocationTest() {
        List<BuildRegion> regions = List.of(POOL, PLOT);
        List<String> worlds = List.of("depths");
        assertTrue(ZoneTracker.inSettingZone("depths", 0, 0, 0, worlds, List.of()), "a listed world counts everywhere");
        assertTrue(ZoneTracker.inSettingZone("spawn", 10, 60, 10, worlds, regions), "corners are inside");
        assertTrue(ZoneTracker.inSettingZone("spawn", 20, 70, 20, worlds, regions));
        assertFalse(ZoneTracker.inSettingZone("spawn", 21, 70, 20, worlds, regions));
        assertFalse(ZoneTracker.inSettingZone("other", 15, 65, 15, worlds, regions), "a region only counts in its world");
        assertTrue(ZoneTracker.inSettingZone("spawn", -5, 0, -5, worlds, regions), "second region");
        assertFalse(ZoneTracker.inSettingZone("spawn", 0, 0, 0, null, List.of()));
        // Old test: box.contains(x, y, z) with doubles floors them; the new one gets Location#getBlockX (also floor).
        double[][] positions = {{9.99, 65, 15}, {10.0, 65, 15}, {20.99, 70.5, 20.2}, {21.0, 65, 15}, {-5.5, 5, 0}, {-4.99, 5, 0}};
        for (double[] p : positions) {
            boolean old = regions.stream().anyMatch(r -> r.box().contains(p[0], p[1], p[2]));
            boolean now = ZoneTracker.inSettingZone("spawn", (int) Math.floor(p[0]), (int) Math.floor(p[1]), (int) Math.floor(p[2]),
                    List.of(), regions);
            assertEquals(old, now, "at " + p[0] + "," + p[1] + "," + p[2]);
        }
    }
}

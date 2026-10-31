package net.skybornmc.skyblock.common.builds;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Spots and regions of the builds applied with {@code /skyblock builds apply} (store: {@code build-points.yml}),
 * published by the worlds module as a service ({@code core.services().optional(BuildPoints.class)}). Later modules
 * (NPCs, holograms, leaderboards, crates, particles, tutorial, treasure hunts) read their positions here instead of
 * hardcoding them. Read-only and thread-safe; entries change only when a build is applied again.
 */
public interface BuildPoints {

    /** Every point of one category ({@code npcs}, {@code holograms}, {@code leaderboards}, {@code crates}...), all builds. */
    List<BuildPoint> points(String category);

    /** Points of one category of one build. */
    List<BuildPoint> points(String build, String category);

    /** The first point with this id in a category (ids are unique per build; NPC/crate ids are unique server-wide). */
    Optional<BuildPoint> point(String category, String id);

    /** Every region of one category ({@code regions} or {@code portals}), all builds. */
    List<BuildRegion> regions(String category);

    /** The first region with this id in a category. */
    Optional<BuildRegion> region(String category, String id);

    /** Every point category that has at least one point. */
    Set<String> categories();

    /** Ids of the applied builds. */
    Set<String> builds();

    /**
     * PERF11: a number that changes whenever any entry changes (a build applied again, the file reloaded), so callers
     * may cache what they looked up while it stays the same. Negative = not tracked: look up every time.
     */
    default long revision() {
        return -1;
    }
}

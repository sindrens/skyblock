package net.skybornmc.skyblock.common.builds;

import java.util.Map;
import java.util.Objects;

/**
 * A cuboid of a pasted build's metadata: {@code regions} (protected, safe, plot, tier gate, fields...) and
 * {@code portals} (teleport triggers with {@code target} / {@code landing} in {@link #extra}).
 *
 * @param category metadata list it came from ({@code regions} or {@code portals})
 * @param kind     the builder's {@code kind} ({@code protected}, {@code safe}, {@code plot}...), empty if none
 * @param extra    every other key of the entry as text
 */
public record BuildRegion(String build, String category, String id, String kind, String world, BuildBox box,
                          Map<String, String> extra) {

    public BuildRegion {
        Objects.requireNonNull(build, "build");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(box, "box");
        kind = kind == null ? "" : kind;
        extra = Map.copyOf(extra);
    }
}

package net.skybornmc.skyblock.common.builds;

import java.util.Map;
import java.util.Objects;

/**
 * One marked spot of a pasted build (NPC pad, hologram, leaderboard, crate, particle, tutorial node, viewpoint,
 * treasure spot...), from the build's metadata file ({@code BUILDER_BRIEF.md} §3.6). Coordinates are world
 * coordinates exactly as the builder wrote them: block positions for pads ({@code x: 4}), feet positions with
 * {@code .5} for spawns.
 *
 * @param build    build id ({@code spawn_market_square_v1})
 * @param category metadata list it came from ({@code npcs}, {@code holograms}, {@code particles}...)
 * @param id       the builder's id inside that list ({@code bank}); unique per build and category
 * @param extra    every other key of the entry as text ({@code facing}, {@code size}, {@code type}, {@code text}...)
 */
public record BuildPoint(String build, String category, String id, String world, double x, double y, double z, float yaw,
                         float pitch, Map<String, String> extra) {

    public BuildPoint {
        Objects.requireNonNull(build, "build");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(world, "world");
        extra = Map.copyOf(extra);
    }

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    /** One extra value, or {@code fallback}. */
    public String extra(String key, String fallback) {
        return extra.getOrDefault(key, fallback);
    }
}

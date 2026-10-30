package net.skybornmc.skyblock.common.region;

import net.skybornmc.skyblock.common.builds.BuildBox;

import java.util.Objects;

/**
 * A box handed to {@link RegionFlags} by a module (the depths module: its ore/log fields as kind {@code field}, its
 * zones as {@code mine_zone}, {@code forest_zone}, {@code combat_zone}, the boss arena as {@code boss_arena}). Which
 * flags a kind sets is configured in {@code worlds.yml → protection.kind-flags}.
 *
 * @param id   unique within the owner (shown by debug tools)
 * @param kind region kind, matched against the configured kinds (lowercase)
 */
public record FlagRegion(String id, String kind, String world, BuildBox box) {

    public FlagRegion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(box, "box");
    }
}

package net.skybornmc.skyblock.islands;

import java.util.Optional;

/**
 * Safe-spot rules (pure; the world is read through {@link Column}). A spot is safe when the block below the feet is
 * solid ground that doesn't hurt, and the feet and head blocks are passable and not liquid or dangerous.
 */
public final class SafeSpot {

    /** What a block means for standing. */
    public enum Kind {
        /** Air, flowers, signs, open doors … */
        PASSABLE,
        /** Full ground you can stand on. */
        SOLID,
        /** Water and other liquids (can't stand in or on). */
        LIQUID,
        /** Lava, fire, magma, cactus, campfires, powder snow, sweet berries … */
        DANGER
    }

    /** Reads block kinds of the world around a spot. */
    @FunctionalInterface
    public interface Column {
        Kind kind(int x, int y, int z);
    }

    /** A block position. */
    public record Spot(int x, int y, int z) {
    }

    private SafeSpot() {
    }

    /** True if a player can stand with their feet at x,y,z. */
    public static boolean isSafe(Column world, int x, int y, int z) {
        return world.kind(x, y - 1, z) == Kind.SOLID
                && world.kind(x, y, z) == Kind.PASSABLE
                && world.kind(x, y + 1, z) == Kind.PASSABLE;
    }

    /**
     * Like {@link #isSafe(Column, int, int, int)} for an exact feet height: standing on a slab, path or farmland puts the
     * feet inside that (partial, solid) block, so then the two blocks above it must be free.
     */
    public static boolean isSafeAt(Column world, int x, double feetY, int z) {
        int by = (int) Math.floor(feetY);
        if (feetY - by < 1.0E-3) {
            return isSafe(world, x, by, z);
        }
        return world.kind(x, by, z) == Kind.SOLID
                && world.kind(x, by + 1, z) == Kind.PASSABLE
                && world.kind(x, by + 2, z) == Kind.PASSABLE;
    }

    /** The highest safe feet position in column x,z between minY and maxY (inclusive), if any. */
    public static Optional<Spot> highestIn(Column world, int x, int z, int minY, int maxY) {
        for (int y = maxY; y >= minY; y--) {
            if (isSafe(world, x, y, z)) {
                return Optional.of(new Spot(x, y, z));
            }
        }
        return Optional.empty();
    }

    /**
     * The safe spot nearest to x,y,z: first the spot itself, then the same column (closest Y first), then columns in
     * growing square rings up to {@code radius}. Columns outside {@code inside} are skipped.
     */
    public static Optional<Spot> nearest(Column world, int x, int y, int z, int radius, int minY, int maxY, ColumnFilter inside) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int cx = x + dx;
                    int cz = z + dz;
                    if (!inside.test(cx, cz)) {
                        continue;
                    }
                    Optional<Spot> spot = closestY(world, cx, y, cz, minY, maxY);
                    if (spot.isPresent()) {
                        return spot;
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Which columns may be used. */
    @FunctionalInterface
    public interface ColumnFilter {
        boolean test(int x, int z);
    }

    private static Optional<Spot> closestY(Column world, int x, int y, int z, int minY, int maxY) {
        int span = Math.max(maxY - y, y - minY);
        for (int d = 0; d <= span; d++) {
            int up = y + d;
            if (up <= maxY && up >= minY && isSafe(world, x, up, z)) {
                return Optional.of(new Spot(x, up, z));
            }
            int down = y - d;
            if (d > 0 && down >= minY && down <= maxY && isSafe(world, x, down, z)) {
                return Optional.of(new Spot(x, down, z));
            }
        }
        return Optional.empty();
    }
}

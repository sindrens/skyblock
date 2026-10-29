package net.skybornmc.skyblock.depths.tiers;

import net.skybornmc.skyblock.depths.engine.BlockKeys;
import net.skybornmc.skyblock.depths.zones.Box;
import net.skybornmc.skyblock.depths.zones.Side;

/**
 * Geometry of tier gates (M4.6), free of Bukkit so it is unit-tested: crossing a zone's boundary, which way to push a
 * player back, the orientation of a gate opening and the chunks a box touches.
 *
 * <p>Coordinates follow {@link Box#contains(double, double, double)}: a position is inside a block when its floored
 * coordinates are, so a box covers the continuous range {@code [min, max + 1)} on every axis.</p>
 */
public final class GateMath {

    private GateMath() {
    }

    /** True if a move from {@code (fx, fy, fz)} to {@code (tx, ty, tz)} enters {@code box} (outside → inside). */
    public static boolean enters(Box box, double fx, double fy, double fz, double tx, double ty, double tz) {
        return box.contains(tx, ty, tz) && !box.contains(fx, fy, fz);
    }

    /**
     * The face of {@code box} that a point lies beyond, on the x/z plane. For a point outside it is the side with the
     * largest distance (the one the player came from); for a point inside it is the nearest face (the shortest way out).
     */
    public static Side outside(Box box, double x, double z) {
        double west = box.minX() - x;
        double east = x - (box.maxX() + 1);
        double north = box.minZ() - z;
        double south = z - (box.maxZ() + 1);
        Side side = Side.WEST;
        double best = west;
        if (east > best) {
            best = east;
            side = Side.EAST;
        }
        if (north > best) {
            best = north;
            side = Side.NORTH;
        }
        if (south > best) {
            side = Side.SOUTH;
        }
        return side;
    }

    /** Unit x of the push away from the box over {@code side} (west -1, east +1, else 0). */
    public static int pushX(Side side) {
        return side.alongX() ? (side.positive() ? 1 : -1) : 0;
    }

    /** Unit z of the push away from the box over {@code side} (north -1, south +1, else 0). */
    public static int pushZ(Side side) {
        return side.alongX() ? 0 : (side.positive() ? 1 : -1);
    }

    /**
     * True if the gate opening spans the z axis (a wall plane of constant x, so bars in it connect north/south);
     * false if it spans the x axis (connect east/west). A square or thick box counts as spanning x when it is wider
     * on x.
     */
    public static boolean spansZ(Box gate) {
        return gate.sizeZ() > gate.sizeX();
    }

    /** Chunk keys ({@link BlockKeys#chunk}) of every chunk the box's footprint touches. */
    public static long[] chunks(Box box) {
        int cx0 = box.minX() >> 4;
        int cx1 = box.maxX() >> 4;
        int cz0 = box.minZ() >> 4;
        int cz1 = box.maxZ() >> 4;
        long[] out = new long[(cx1 - cx0 + 1) * (cz1 - cz0 + 1)];
        int i = 0;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                out[i++] = BlockKeys.chunk(cx, cz);
            }
        }
        return out;
    }

    /** Block keys ({@link BlockKeys#block}) of every block in the box, x-major. */
    public static long[] blocks(Box box) {
        long[] out = new long[(int) box.volume()];
        int i = 0;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    out[i++] = BlockKeys.block(x, y, z);
                }
            }
        }
        return out;
    }
}

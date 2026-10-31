package net.skybornmc.skyblock.crates;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * The maths of the crate lid (pure, unit-tested). A crate model is 16 units wide; item displays with transform NONE draw
 * it with its centre (8,8,8) on the display origin, so one unit is 1/16 block. The lid hangs on a hinge line along X at
 * (hingeY, hingeZ) (the back edge: the model's front is +z). To swing the lid by a rotation {@code R} around that line
 * the display transformation is {@code scale s} with {@code translation = p - R·p}, where {@code p} is the hinge relative
 * to the model centre in blocks times the scale: a point {@code v} then lands on {@code p + R·(s·v - p)}, so the hinge
 * stays where it is.
 *
 * <p>Clients interpolate the translation linearly but the rotation on an arc, so one long step would let the hinge drift
 * (up to about a tenth of a block at 100 degrees). {@link #path} therefore gives several short key frames; the caller sets
 * one per segment with a matching interpolation duration.
 */
final class CrateHinge {

    /** Model units per block. */
    static final float UNITS = 16f;
    /** Model centre in model units (on each axis). */
    static final float CENTER = 8f;

    private CrateHinge() {
    }

    /** One display pose: {@code v' = translation + rotation·(scale·v)}. */
    record Pose(Vector3f translation, Quaternionf rotation, float scale) {

        /** Where a point (blocks, relative to the model centre, unscaled) ends up. */
        Vector3f apply(Vector3f point) {
            return rotation.transform(new Vector3f(point).mul(scale)).add(translation);
        }
    }

    /** The hinge relative to the model centre, in blocks, after scaling. */
    static Vector3f pivot(double hingeY, double hingeZ, double scale) {
        return new Vector3f(0f, (float) ((hingeY - CENTER) / UNITS * scale), (float) ((hingeZ - CENTER) / UNITS * scale));
    }

    /**
     * The lid pose opened by {@code openDegrees} (0 = closed = no translation, no rotation). A positive angle swings the
     * front (+z) of the lid up and back over the hinge: a rotation of {@code -openDegrees} around X.
     */
    static Pose lid(double hingeY, double hingeZ, double scale, double openDegrees) {
        Quaternionf rotation = new Quaternionf().rotateX((float) Math.toRadians(-openDegrees));
        Vector3f p = pivot(hingeY, hingeZ, scale);
        Vector3f translation = new Vector3f(p).sub(rotation.transform(new Vector3f(p)));
        return new Pose(translation, rotation, (float) scale);
    }

    /** The whole crate tilted around its centre (the small shake before the lid opens), roll in degrees. */
    static Pose roll(double scale, double degrees) {
        return new Pose(new Vector3f(), new Quaternionf().rotateZ((float) Math.toRadians(degrees)), (float) scale);
    }

    /**
     * Angles from {@code from} to {@code to} in {@code steps} key frames (the last one is exactly {@code to}). Opening
     * eases out with an optional overshoot ({@code back} 0 = none; about 1.7 swings roughly 10 percent past), closing
     * eases in and out.
     */
    static float[] path(double from, double to, int steps, double back) {
        int n = Math.max(1, steps);
        float[] out = new float[n];
        boolean opening = to > from;
        for (int i = 1; i <= n; i++) {
            double t = (double) i / n;
            double e = opening ? easeOutBack(t, back) : t * t * (3 - 2 * t);
            out[i - 1] = i == n ? (float) to : (float) (from + (to - from) * e);
        }
        return out;
    }

    /** Ease out with a swing past the end: 0 at 0, 1 at 1; {@code c1} = 0 is a plain cubic ease out. */
    static double easeOutBack(double t, double c1) {
        double c = Math.max(0, c1);
        double u = t - 1;
        return 1 + (c + 1) * u * u * u + c * u * u;
    }

    /** Ticks of each key frame so that {@code steps} frames take about {@code totalTicks} (at least 1). */
    static int segmentTicks(int totalTicks, int steps) {
        return Math.max(1, Math.round((float) totalTicks / Math.max(1, steps)));
    }

    /** Entity yaw for a horizontal direction name (model front = south = yaw 0); null when it is not one. */
    static Float yaw(String direction) {
        if (direction == null) {
            return null;
        }
        return switch (direction.trim().toLowerCase(Locale.ROOT)) {
            case "south" -> 0f;
            case "west" -> 90f;
            case "north" -> 180f;
            case "east" -> -90f;
            default -> null;
        };
    }
}

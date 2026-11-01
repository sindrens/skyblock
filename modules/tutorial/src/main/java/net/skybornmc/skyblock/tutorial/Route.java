package net.skybornmc.skyblock.tutorial;

import java.util.List;

/**
 * A found guide route (TUT-FIX2): points on the walking surface ({@code x, y, z} block centres, {@code y} = surface
 * height) with the distance walked up to each point. Immutable and pure; the trail and Pip read it without allocating
 * ({@link #pointAt} writes into a caller's array).
 */
final class Route {

    private final double[] xs;
    private final double[] ys;
    private final double[] zs;
    /** Distance along the route from the first point to point i. */
    private final double[] along;

    private Route(double[] xs, double[] ys, double[] zs) {
        this.xs = xs;
        this.ys = ys;
        this.zs = zs;
        this.along = new double[xs.length];
        for (int i = 1; i < xs.length; i++) {
            double dx = xs[i] - xs[i - 1];
            double dy = ys[i] - ys[i - 1];
            double dz = zs[i] - zs[i - 1];
            along[i] = along[i - 1] + Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    static Route of(List<double[]> points) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        int n = points.size();
        double[] xs = new double[n];
        double[] ys = new double[n];
        double[] zs = new double[n];
        for (int i = 0; i < n; i++) {
            double[] p = points.get(i);
            xs[i] = p[0];
            ys[i] = p[1];
            zs[i] = p[2];
        }
        return new Route(xs, ys, zs);
    }

    int size() {
        return xs.length;
    }

    double length() {
        return along[along.length - 1];
    }

    double alongAt(int index) {
        return along[index];
    }

    /** The last point (where the route ends, next to the target). */
    double endX() {
        return xs[xs.length - 1];
    }

    double endY() {
        return ys[ys.length - 1];
    }

    double endZ() {
        return zs[zs.length - 1];
    }

    /**
     * The index of the route point nearest to {@code (x, y, z)}, searched around {@code hint} (the player's last index:
     * players walk forward, so a window of a few points back and more ahead is enough). Squared distance in {@code out[0]}.
     */
    int nearest(double x, double y, double z, int hint, double[] out) {
        int from = Math.max(0, hint - 12);
        int to = Math.min(xs.length - 1, hint + 40);
        if (hint <= 0) {
            to = xs.length - 1; // first lookup: the whole route
        }
        int best = from;
        double bestD = Double.MAX_VALUE;
        for (int i = from; i <= to; i++) {
            double dx = xs[i] - x;
            double dy = (ys[i] - y) * 0.5; // height matters less than the ground distance
            double dz = zs[i] - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        if (out != null) {
            out[0] = bestD;
        }
        return best;
    }

    /** The point {@code distance} along the route (clamped to its ends), written into {@code out} as {@code x, y, z}. */
    void pointAt(double distance, double[] out) {
        if (distance <= 0 || xs.length == 1) {
            out[0] = xs[0];
            out[1] = ys[0];
            out[2] = zs[0];
            return;
        }
        if (distance >= length()) {
            out[0] = endX();
            out[1] = endY();
            out[2] = endZ();
            return;
        }
        int lo = 0;
        int hi = along.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (along[mid] <= distance) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double seg = along[hi] - along[lo];
        double t = seg <= 1e-9 ? 0 : (distance - along[lo]) / seg;
        out[0] = xs[lo] + (xs[hi] - xs[lo]) * t;
        out[1] = ys[lo] + (ys[hi] - ys[lo]) * t;
        out[2] = zs[lo] + (zs[hi] - zs[lo]) * t;
    }

    /**
     * How big a trail dot is at {@code ahead} blocks in front of the player (TUT-FIX2: the trail fades with distance):
     * the full size near the player, down to 45% at {@code reach}. Pure.
     */
    static float fade(double ahead, double reach, float size) {
        if (reach <= 0) {
            return size;
        }
        double t = Math.max(0, Math.min(1, ahead / reach));
        return (float) (size * (1 - 0.55 * t));
    }
}

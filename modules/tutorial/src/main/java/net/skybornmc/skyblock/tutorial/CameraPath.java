package net.skybornmc.skyblock.tutorial;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a camera shot into the moves of the Java camera entity (TUTORIAL.md §9.4): the first waypoint is a cut (0 ticks),
 * then straight segments of at most {@code segmentTicks} each (a display entity's teleport smoothing is limited to
 * 59 ticks), every waypoint facing the shot's look-at point. Minecraft angles: yaw 0 = south (+z), 90 = west, 180 =
 * north; pitch positive = looking down. Pure, unit-tested.
 */
final class CameraPath {

    /** Longest teleport smoothing a display entity accepts (ticks). */
    static final int MAX_SEGMENT_TICKS = 59;

    /** Where the camera is after {@code ticks} of smooth movement from the previous waypoint (0 = cut). */
    record Waypoint(double x, double y, double z, float yaw, float pitch, int ticks) {
    }

    private CameraPath() {
    }

    static List<Waypoint> waypoints(double[] from, double[] to, double[] look, int ticks, int segmentTicks) {
        int seg = Math.max(1, Math.min(MAX_SEGMENT_TICKS, segmentTicks));
        int total = Math.max(1, ticks);
        List<Waypoint> out = new ArrayList<>();
        out.add(at(from, look, 0));
        int parts = (total + seg - 1) / seg;
        int base = total / parts;
        int extra = total % parts;
        for (int i = 1; i <= parts; i++) {
            double t = i / (double) parts;
            double[] pos = {from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t, from[2] + (to[2] - from[2]) * t};
            out.add(at(pos, look, base + (i <= extra ? 1 : 0)));
        }
        return out;
    }

    private static Waypoint at(double[] pos, double[] look, int ticks) {
        double dx = look[0] - pos[0];
        double dy = look[1] - pos[1];
        double dz = look[2] - pos[2];
        return new Waypoint(pos[0], pos[1], pos[2], yaw(dx, dz), pitch(dx, dy, dz), ticks);
    }

    /** Minecraft yaw facing the direction (dx, dz); 0 when there is no horizontal direction. */
    static float yaw(double dx, double dz) {
        if (Math.abs(dx) < 1e-9 && Math.abs(dz) < 1e-9) {
            return 0f;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Minecraft pitch facing (dx, dy, dz): positive looks down. */
    static float pitch(double dx, double dy, double dz) {
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1e-9 && Math.abs(dy) < 1e-9) {
            return 0f;
        }
        return (float) -Math.toDegrees(Math.atan2(dy, horizontal));
    }

    /** Sum of the waypoint ticks (the camera's running time). */
    static int duration(List<Waypoint> path) {
        int sum = 0;
        for (Waypoint w : path) {
            sum += w.ticks();
        }
        return sum;
    }
}

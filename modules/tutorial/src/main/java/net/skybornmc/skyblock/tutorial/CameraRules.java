package net.skybornmc.skyblock.tutorial;

/**
 * The safety rules of the tutorial cameras (TUT-FIX, owner report 31 Oct). Pure, unit-tested:
 * <ul>
 *   <li><b>Line of sight:</b> a shot may never fly through, start in or look straight into solid blocks. Each shot is
 *   checked against the world when it is about to play, with room around the camera; a blocked shot is first moved
 *   (shortened, raised, closer, back), then played still at a clear end, and dropped only when nothing is clear
 *   ({@link #resolve}; {@link #check} is the old plain rule).</li>
 *   <li><b>Input:</b> any move key, jump or sneak during a camera skips it ({@link #skipOnInput}), so a camera can
 *   never leave a player stuck.</li>
 *   <li><b>Watchdog:</b> a camera still running a few seconds after its own length is ended ({@link #overdue}).</li>
 * </ul>
 */
final class CameraRules {

    /** Distance between two samples on a line (blocks). */
    static final double SAMPLE = 0.25;
    /** The first blocks of the view toward the look-at point must be open (blocks). */
    static final double VIEW_CLEAR = 8;

    /** The world as the check sees it: {@code TRUE} solid, {@code FALSE} open, {@code null} unknown (chunk not loaded). */
    interface Blocks {
        Boolean solid(int x, int y, int z);
    }

    /** What happens to a shot after the check. */
    enum Fix {
        /** Plays as written. */
        OK,
        /** TUT-FIX2: the whole shot was raised a few blocks (it plays as a moving shot). */
        RAISED,
        /** TUT-FIX2: the move stops early, where the path is still clear. */
        SHORTENED,
        /** TUT-FIX2: the shot moved closer to the thing it looks at (interiors, a wall behind the camera). */
        CLOSER,
        /** TUT-FIX2: the shot moved back, away from the thing it looks at (something right in front of it). */
        BACK,
        /** Its path is blocked: plays as a still shot at its start. */
        STILL_FROM,
        /** Its path and start are blocked: plays as a still shot at its end. */
        STILL_TO,
        /** No clear place: not played. */
        DROP
    }

    /** A shot after {@link #resolve}: where it really starts and ends, and what was changed. */
    record Resolved(double[] from, double[] to, Fix fix, double amount) {
    }

    /** Fractions of the move kept when a shot is shortened (longest first). */
    private static final double[] SHORTEN = {0.85, 0.7, 0.55, 0.4};
    /** Distance factors toward the look-at point (closer) and away from it (back). */
    private static final double[] CLOSER = {0.8, 0.65, 0.5};
    private static final double[] BACK = {1.25, 1.5};
    /** Never closer to the look-at point than this (blocks): the camera would sit on the thing it shows. */
    private static final double MIN_LOOK_DISTANCE = 2.5;

    /** Something that happens to a player during a camera. */
    enum Interruption { MOVE_KEY, JUMP, SNEAK, COMMAND, LEFT_CAMERA, TELEPORT, DEATH, WATCHDOG, QUIT, STAFF_CHANGE, MODULE_STOP, CHAT, DAMAGE }

    /** What the camera does about it. Every action except {@code IGNORE} gives the player's saved state back. */
    enum Action {
        /** Ends now, state back, the camera's follow-up runs (the step goes on). */
        SKIP,
        /** Ends, state back but at the teleport's destination, follow-up runs. */
        END_AT_DESTINATION,
        /** Ends, state back, no follow-up (the player left, staff changed the tutorial, the module stops). */
        STOP,
        /** Nothing to do: it can't hurt (chat, damage while invulnerable, anything during a titles-only camera). */
        IGNORE
    }

    private CameraRules() {
    }

    /**
     * The one table of what every interruption does (TUT-FIX: "any input either skips the camera instantly or is
     * ignored safely"). {@code holdsPlayer}: the camera changed the player (smooth or cut-and-fade); a titles-only
     * camera changes nothing, so only leaving and staff changes end it early.
     */
    static Action action(Interruption what, boolean holdsPlayer) {
        return switch (what) {
            case QUIT, STAFF_CHANGE, MODULE_STOP -> Action.STOP;
            case COMMAND, WATCHDOG -> Action.SKIP;
            case MOVE_KEY, JUMP, SNEAK, LEFT_CAMERA, DEATH -> holdsPlayer ? Action.SKIP : Action.IGNORE;
            case TELEPORT -> holdsPlayer ? Action.END_AT_DESTINATION : Action.IGNORE;
            case CHAT, DAMAGE -> Action.IGNORE;
        };
    }

    /** Checks one shot (points are absolute world coordinates). Unknown blocks count as blocked (never guess). */
    static Fix check(double[] from, double[] to, double[] look, Blocks world) {
        boolean fromOk = clearLine(from, from, world) && clearView(from, look, world);
        boolean moves = distance(from, to) > 0.01;
        if (!moves) {
            return fromOk ? Fix.OK : Fix.DROP;
        }
        boolean toOk = clearLine(to, to, world) && clearView(to, look, world);
        if (fromOk && toOk && clearLine(from, to, world)) {
            return Fix.OK;
        }
        if (fromOk) {
            return Fix.STILL_FROM;
        }
        return toOk ? Fix.STILL_TO : Fix.DROP;
    }

    /**
     * TUT-FIX2 (owner, 31 Oct: "cameras must NEVER pass through blocks"): the shot as written when it is clear with
     * {@code clearance} blocks of room around the camera; otherwise the first clear one of: the move shortened, the whole
     * shot raised 1 to {@code maxRaise} blocks, moved closer to the look-at point, moved back from it; then a still shot
     * at a clear end (also raised); else {@link Fix#DROP}. Unknown blocks (unloaded chunks) count as blocked. Pure; at
     * most a few thousand block reads, once per camera start.
     */
    static Resolved resolve(double[] from, double[] to, double[] look, Blocks world, double clearance, int maxRaise) {
        double r = Math.max(0, clearance);
        if (shotClear(from, to, look, world, r)) {
            return new Resolved(from, to, Fix.OK, 0);
        }
        boolean moves = distance(from, to) > 0.01;
        boolean fromOk = spotClear(from, look, world, r);
        if (moves && fromOk) {
            for (double keep : SHORTEN) {
                double[] end = lerp(from, to, keep);
                if (shotClear(from, end, look, world, r)) {
                    return new Resolved(from, end, Fix.SHORTENED, keep);
                }
            }
        }
        for (int dy = 1; dy <= maxRaise; dy++) {
            double[] f = {from[0], from[1] + dy, from[2]};
            double[] t = {to[0], to[1] + dy, to[2]};
            if (shotClear(f, t, look, world, r)) {
                return new Resolved(f, t, Fix.RAISED, dy);
            }
        }
        for (double k : CLOSER) {
            double[] f = lerp(look, from, k);
            double[] t = lerp(look, to, k);
            if (distance(f, look) >= MIN_LOOK_DISTANCE && distance(t, look) >= MIN_LOOK_DISTANCE && shotClear(f, t, look, world, r)) {
                return new Resolved(f, t, Fix.CLOSER, k);
            }
        }
        for (double k : BACK) {
            double[] f = lerp(look, from, k);
            double[] t = lerp(look, to, k);
            if (shotClear(f, t, look, world, r)) {
                return new Resolved(f, t, Fix.BACK, k);
            }
        }
        if (fromOk) {
            return new Resolved(from, from, Fix.STILL_FROM, 0);
        }
        if (moves && spotClear(to, look, world, r)) {
            return new Resolved(to, to, Fix.STILL_TO, 0);
        }
        for (int dy = 1; dy <= maxRaise; dy++) {
            double[] f = {from[0], from[1] + dy, from[2]};
            if (spotClear(f, look, world, r)) {
                return new Resolved(f, f, Fix.STILL_FROM, dy);
            }
        }
        return new Resolved(from, to, Fix.DROP, 0);
    }

    /** The whole shot is clear: both ends (with room around them), their views, the path and the view halfway. */
    static boolean shotClear(double[] from, double[] to, double[] look, Blocks world, double clearance) {
        if (!spotClear(from, look, world, clearance)) {
            return false;
        }
        if (distance(from, to) <= 0.01) {
            return true;
        }
        return spotClear(to, look, world, clearance) && clearSwept(from, to, world, clearance)
                && clearView(lerp(from, to, 0.5), look, world);
    }

    /** One camera position is clear: the room around it and the first blocks of its view. */
    static boolean spotClear(double[] pos, double[] look, Blocks world, double clearance) {
        return clearSwept(pos, pos, world, clearance) && clearView(pos, look, world);
    }

    /**
     * True when a box of {@code ±clearance} around the camera stays in open blocks all the way from a to b (the camera's
     * near plane never touches a block face). {@code clearance} 0 = the line itself ({@link #clearLine}).
     */
    static boolean clearSwept(double[] a, double[] b, Blocks world, double clearance) {
        if (clearance <= 0) {
            return clearLine(a, b, world);
        }
        double length = distance(a, b);
        int steps = Math.max(1, (int) Math.ceil(length / SAMPLE));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = a[0] + (b[0] - a[0]) * t;
            double y = a[1] + (b[1] - a[1]) * t;
            double z = a[2] + (b[2] - a[2]) * t;
            int x0 = floor(x - clearance);
            int x1 = floor(x + clearance);
            int y0 = floor(y - clearance);
            int y1 = floor(y + clearance);
            int z0 = floor(z - clearance);
            int z1 = floor(z + clearance);
            for (int bx = x0; bx <= x1; bx++) {
                for (int by = y0; by <= y1; by++) {
                    for (int bz = z0; bz <= z1; bz++) {
                        Boolean solid = world.solid(bx, by, bz);
                        if (solid == null || solid) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static double[] lerp(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    /** True when every block on the straight line a→b (both ends included) is open. */
    static boolean clearLine(double[] a, double[] b, Blocks world) {
        double length = distance(a, b);
        int steps = Math.max(1, (int) Math.ceil(length / SAMPLE));
        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = floor(a[0] + (b[0] - a[0]) * t);
            int y = floor(a[1] + (b[1] - a[1]) * t);
            int z = floor(a[2] + (b[2] - a[2]) * t);
            if (x == lastX && y == lastY && z == lastZ) {
                continue;
            }
            lastX = x;
            lastY = y;
            lastZ = z;
            Boolean solid = world.solid(x, y, z);
            if (solid == null || solid) {
                return false;
            }
        }
        return true;
    }

    /**
     * True when the view from {@code pos} toward {@code look} is open for its first {@link #VIEW_CLEAR} blocks (or 60%
     * of the way when the look-at point is closer): the thing looked at may itself be solid, a wall right in front of
     * the camera may not.
     */
    static boolean clearView(double[] pos, double[] look, Blocks world) {
        double d = distance(pos, look);
        if (d < 1) {
            return true;
        }
        double reach = Math.min(VIEW_CLEAR, d * 0.6);
        double k = reach / d;
        double[] end = {pos[0] + (look[0] - pos[0]) * k, pos[1] + (look[1] - pos[1]) * k, pos[2] + (look[2] - pos[2]) * k};
        return clearLine(pos, end, world);
    }

    /**
     * True when this input must skip the camera: sneak always; a move key or jump once {@code ticksSinceStart} reached
     * {@code grace} (a key still held from before the camera started does not count). Sprint alone moves nothing.
     */
    static boolean skipOnInput(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean sneak,
                               long ticksSinceStart, int grace) {
        if (sneak) {
            return true;
        }
        boolean moves = forward || backward || left || right || jump;
        return moves && ticksSinceStart >= grace;
    }

    /** True when a camera that started at {@code startedAt} and lasts {@code totalTicks} should have ended long ago. */
    static boolean overdue(long startedAt, int totalTicks, long now, int extraSeconds) {
        return now - startedAt > totalTicks * 50L + extraSeconds * 1000L;
    }

    /** Cut-and-fade: the player moved this far away from where the camera holds them (blocks) = a skip. */
    static boolean movedAway(double dx, double dy, double dz, double limit) {
        return dx * dx + dy * dy + dz * dz > limit * limit;
    }

    static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }
}

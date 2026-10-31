package net.skybornmc.skyblock.tutorial;

/**
 * The safety rules of the tutorial cameras (TUT-FIX, owner report 31 Oct). Pure, unit-tested:
 * <ul>
 *   <li><b>Line of sight:</b> a shot may never fly through, start in or look straight into solid blocks. Each shot is
 *   checked against the world when it is about to play; a bad moving shot becomes a still shot at a clear end, a bad
 *   still shot is dropped ({@link #check}).</li>
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
        /** Its path is blocked: plays as a still shot at its start. */
        STILL_FROM,
        /** Its path and start are blocked: plays as a still shot at its end. */
        STILL_TO,
        /** No clear place: not played. */
        DROP
    }

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

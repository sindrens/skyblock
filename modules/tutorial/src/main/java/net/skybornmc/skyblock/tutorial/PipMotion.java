package net.skybornmc.skyblock.tutorial;

/**
 * Where Pip the parrot flies (TUTORIAL.md §9.3, TQ9; TUT-FIX2, owner 31 Oct: "Pip should clearly WANT the player to
 * follow"). Pure, unit-testable; points are {@code {x, y, z}}.
 * <ul>
 *   <li><b>LEAD</b>: a few blocks ahead of the player along the guide trail ({@link Route}), bobbing.</li>
 *   <li><b>WAIT</b>: the player stopped or fell behind: Pip circles on the spot ahead, faces them and calls.</li>
 *   <li><b>RETURN</b>: the player is far away (took another way): Pip flies back to their shoulder.</li>
 *   <li><b>PERCH</b>: the player is near the target: Pip lands on it (the chest, an NPC's head), hops, hearts.</li>
 *   <li><b>FOLLOW</b> / <b>IDLE</b>: no trail or no target: over the shoulder, a little toward the target; little hops
 *   while the player stands still.</li>
 * </ul>
 */
final class PipMotion {

    /** What Pip is doing. */
    enum Mode { IDLE, FOLLOW, LEAD, WAIT, RETURN, PERCH }

    /** Height of one hop (blocks) and how long it takes (ms). */
    static final double HOP_HEIGHT = 0.35;
    static final long HOP_MILLIS = 500;
    /** Radius of the "come on!" circle while waiting (blocks). */
    static final double CIRCLE = 0.8;

    private PipMotion() {
    }

    /**
     * Pip's mode. {@code toPerch}: the player's horizontal distance to the perch point; {@code pipToPlayer}: Pip's
     * distance to the player; {@code playerStill}: the player has not moved for a moment. RETURN lasts until Pip is back
     * within 3 blocks (no flapping between modes).
     */
    static Mode mode(boolean hasPerch, double toPerch, boolean hasRoute, double pipToPlayer, boolean playerStill,
                     double perchRange, double waitDistance, double returnDistance, Mode previous) {
        if (hasPerch && toPerch <= perchRange) {
            return Mode.PERCH;
        }
        if (pipToPlayer > returnDistance || (previous == Mode.RETURN && pipToPlayer > 3)) {
            return Mode.RETURN;
        }
        if (hasRoute) {
            return playerStill || pipToPlayer > waitDistance ? Mode.WAIT : Mode.LEAD;
        }
        return !hasPerch && playerStill ? Mode.IDLE : Mode.FOLLOW;
    }

    /** The spot {@code lead} blocks ahead of the player's place on the route, {@code height} above the ground. */
    static void lead(Route route, int cursor, double lead, double height, double[] out) {
        route.pointAt(route.alongAt(cursor) + lead, out);
        out[1] += height;
    }

    /** A point on the small waiting circle around {@code c} at {@code angle} (radians). */
    static void circle(double[] c, double angle, double radius, double[] out) {
        out[0] = c[0] + Math.cos(angle) * radius;
        out[1] = c[1];
        out[2] = c[2] + Math.sin(angle) * radius;
    }

    /** The gentle up and down of a flying parrot (blocks) at time {@code millis}. */
    static double bob(long millis, double height) {
        return Math.sin(millis / 280.0) * height;
    }

    /** Height of a hop {@code since} ms after it started (a small arc), 0 outside the hop. */
    static double hop(long since) {
        if (since < 0 || since > HOP_MILLIS) {
            return 0;
        }
        double t = since / (double) HOP_MILLIS;
        return 4 * HOP_HEIGHT * t * (1 - t);
    }

    /**
     * The spot over the player's shoulder (FOLLOW, IDLE, RETURN), a little toward the target when there is one.
     *
     * @param player     the player's feet
     * @param yaw        the player's yaw (0 = south)
     * @param target     the step's target, or {@code null}
     * @param perchRange horizontal distance from which Pip lands on the target
     * @param side       how far from the player Pip flies
     * @param height     how high above the feet Pip flies
     */
    static double[] desired(double[] player, float yaw, double[] target, double perchRange, double side, double height) {
        if (target != null) {
            double dx = target[0] - player[0];
            double dz = target[2] - player[2];
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist <= perchRange) {
                return new double[]{target[0], target[1], target[2]};
            }
            if (dist > 1e-6) {
                return new double[]{player[0] + dx / dist * side, player[1] + height, player[2] + dz / dist * side};
            }
        }
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        // Left of the facing direction: facing south (+z) the left is east (+x).
        double lx = fz;
        double lz = -fx;
        return new double[]{player[0] + lx * side * 0.7 + fx * 0.3, player[1] + height, player[2] + lz * side * 0.7 + fz * 0.3};
    }

    /** One move from {@code current} toward {@code desired}: at most {@code maxStep} blocks, a jump beyond {@code snap}. */
    static double[] step(double[] current, double[] desired, double maxStep, double snap) {
        if (current == null) {
            return desired.clone();
        }
        double[] out = new double[3];
        stepInto(current, desired, maxStep, snap, out);
        return out;
    }

    /** {@link #step} without allocating: writes the next position into {@code out} (may be {@code current}). */
    static void stepInto(double[] current, double[] desired, double maxStep, double snap, double[] out) {
        double dx = desired[0] - current[0];
        double dy = desired[1] - current[1];
        double dz = desired[2] - current[2];
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d <= maxStep || d > snap) {
            out[0] = desired[0];
            out[1] = desired[1];
            out[2] = desired[2];
            return;
        }
        double f = maxStep / d;
        out[0] = current[0] + dx * f;
        out[1] = current[1] + dy * f;
        out[2] = current[2] + dz * f;
    }

    /** True when Pip sits on the target (it then perches instead of flapping). */
    static boolean perched(double[] pip, double[] target) {
        if (pip == null || target == null) {
            return false;
        }
        double dx = pip[0] - target[0];
        double dy = pip[1] - target[1];
        double dz = pip[2] - target[2];
        return dx * dx + dy * dy + dz * dz < 0.01;
    }

    /** {@code step: dx,dy,dz} lines to the offset of {@code step}, or {@code {0,0,0}}. Pure. */
    static double[] perchOffset(java.util.List<String> lines, String step) {
        if (lines != null && step != null) {
            for (String line : lines) {
                int colon = line.indexOf(':');
                if (colon <= 0 || !line.substring(0, colon).strip().equalsIgnoreCase(step)) {
                    continue;
                }
                String[] parts = line.substring(colon + 1).split(",");
                if (parts.length != 3) {
                    continue;
                }
                try {
                    return new double[]{Double.parseDouble(parts[0].strip()), Double.parseDouble(parts[1].strip()),
                            Double.parseDouble(parts[2].strip())};
                } catch (NumberFormatException ignored) {
                    // a bad line: no offset
                }
            }
        }
        return new double[]{0, 0, 0};
    }
}

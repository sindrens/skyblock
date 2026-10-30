package net.skybornmc.skyblock.tutorial;

/**
 * Where Pip the parrot flies (TUTORIAL.md §9.3, TQ9): over the player's shoulder, a little toward the next target, and
 * it perches on the target once the player is close. It moves in small steps so it seems to fly; it jumps straight to
 * the spot after a long distance (teleport, world change). Pure, unit-tested. Points are {@code {x, y, z}}.
 */
final class PipMotion {

    private PipMotion() {
    }

    /**
     * The spot Pip wants to be at.
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
        double dx = desired[0] - current[0];
        double dy = desired[1] - current[1];
        double dz = desired[2] - current[2];
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d <= maxStep || d > snap) {
            return desired.clone();
        }
        double f = maxStep / d;
        return new double[]{current[0] + dx * f, current[1] + dy * f, current[2] + dz * f};
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
}

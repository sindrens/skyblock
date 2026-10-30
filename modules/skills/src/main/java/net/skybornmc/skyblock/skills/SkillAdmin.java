package net.skybornmc.skyblock.skills;

/**
 * Pure math of the staff command {@code /skills set|add <player> <skill> <amount>} (owner #68, admin + owner): the
 * amount is in <b>levels</b>. {@code set} jumps to that level (start of the level); {@code add} adds levels and keeps
 * the progress inside the current level. Results are clamped to 0..max level. No Bukkit types (unit-tested).
 */
final class SkillAdmin {

    enum Mode { SET, ADD }

    /** New total XP and level for a change. */
    record Result(int level, double xp) {
    }

    private SkillAdmin() {
    }

    /** The level after the change, clamped to 0..max. */
    static int targetLevel(Mode mode, int current, int amount, int max) {
        long target = mode == Mode.SET ? amount : (long) current + amount;
        return (int) Math.clamp(target, 0L, max);
    }

    /** New total XP for the change on {@code curve}, given the player's current total XP. */
    static Result apply(Mode mode, double currentXp, int amount, SkillCurve curve) {
        int current = curve.levelFor(currentXp);
        int level = targetLevel(mode, current, amount, curve.maxLevel());
        double xp = curve.totalFor(level);
        if (mode == Mode.ADD && level < curve.maxLevel()) {
            // Keep the share of the level already done, so adding a level never wipes progress.
            xp += curve.progress(currentXp) * curve.step(level);
            if (curve.levelFor(xp) != level) {
                xp = curve.totalFor(level);
            }
        }
        return new Result(level, xp);
    }
}

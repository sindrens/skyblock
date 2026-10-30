package net.skybornmc.skyblock.achievements;

import java.util.ArrayList;
import java.util.List;

/** Pure tier and progress math for achievements (unit-tested). */
final class ProgressMath {

    private ProgressMath() {
    }

    /** Tiers whose goal {@code value} has reached (0 to {@link AchievementDef#tiers()}). */
    static int tiersDone(AchievementDef def, long value) {
        int done = 0;
        for (long goal : def.goals()) {
            if (value >= goal) {
                done++;
            } else {
                break;
            }
        }
        return done;
    }

    static boolean complete(AchievementDef def, long value) {
        return value >= def.finalGoal();
    }

    /** The goal of the next tier, or the final goal when everything is done. */
    static long nextGoal(AchievementDef def, long value) {
        int done = tiersDone(def, value);
        return done >= def.tiers() ? def.finalGoal() : def.goal(done + 1);
    }

    /** Progress towards the next goal, 0.0 to 1.0 (1.0 when complete). */
    static double fraction(AchievementDef def, long value) {
        long next = nextGoal(def, value);
        if (value >= next) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, (double) Math.max(0, value) / next));
    }

    /** Filled segments of a bar of {@code length} for a fraction (never full before 1.0, never empty after a start). */
    static int filled(double fraction, int length) {
        if (fraction >= 1.0) {
            return length;
        }
        if (fraction <= 0.0) {
            return 0;
        }
        int filled = (int) Math.floor(fraction * length);
        return Math.max(1, Math.min(length - 1, filled));
    }

    static int percent(double fraction) {
        return (int) Math.floor(Math.max(0.0, Math.min(1.0, fraction)) * 100);
    }

    /** Tiers newly reached: every tier above {@code unlocked} up to what {@code value} reaches, lowest first. */
    static List<Integer> newTiers(AchievementDef def, long value, int unlocked) {
        int done = tiersDone(def, value);
        List<Integer> out = new ArrayList<>();
        for (int tier = Math.max(0, unlocked) + 1; tier <= done; tier++) {
            out.add(tier);
        }
        return out;
    }

    /** a + b without overflow (stays at Long.MAX_VALUE). */
    static long add(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? (b > 0 ? Long.MAX_VALUE : Long.MIN_VALUE) : r;
    }

    /** Roman numeral for tier names (1-10 is all a tier list can hold; larger numbers are plain digits). */
    static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(n);
        };
    }
}

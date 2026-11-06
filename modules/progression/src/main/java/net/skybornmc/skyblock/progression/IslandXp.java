package net.skybornmc.skyblock.progression;

/**
 * Island level v2 (owner 7 Nov 2026, plans/TOOLS_V5.md section 3), pure: the island level is the island's XP. Level n
 * needs {@code base x n^exponent} XP in total (1,500 and 1.5: level 1 = 1,500, level 10 = 47,434, level 50 = 530,330).
 */
final class IslandXp {

    private IslandXp() {
    }

    /** Total XP for {@code level} (0 for level 0). */
    static double totalFor(int level, double base, double exponent) {
        return level <= 0 ? 0 : base * Math.pow(level, exponent);
    }

    /** The level for {@code xp} total XP. */
    static int level(double xp, double base, double exponent) {
        if (!(xp > 0) || !(base > 0)) {
            return 0;
        }
        int lv = (int) Math.floor(Math.pow(xp / base, 1.0 / exponent) + 1e-9);
        while (lv > 0 && totalFor(lv, base, exponent) > xp + 1e-6) {
            lv--;
        }
        while (totalFor(lv + 1, base, exponent) <= xp + 1e-6) {
            lv++;
        }
        return lv;
    }

    /** 0..1 from the current level to the next. */
    static double progress(double xp, double base, double exponent) {
        int lv = level(xp, base, exponent);
        double from = totalFor(lv, base, exponent);
        double to = totalFor(lv + 1, base, exponent);
        return Math.max(0, Math.min(1, (Math.max(0, xp) - from) / Math.max(1e-9, to - from)));
    }

    /** Money into the island bank for reaching {@code level}. */
    static long money(int level, long perLevel) {
        return Math.max(0, perLevel) * Math.max(0, level);
    }

    /** Crystals per member for reaching {@code level} (only every 5 levels). */
    static long crystals(int level, long perLevel) {
        return level > 0 && level % 5 == 0 ? Math.max(0, perLevel) * level : 0;
    }

    /** True when {@code level} gives a crate key (every 5 levels). */
    static boolean key(int level) {
        return level > 0 && level % 5 == 0;
    }
}

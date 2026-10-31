package net.skybornmc.skyblock.pass;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pure battle pass math (no Bukkit; unit-tested). Levels 1..{@code max} cost a flat {@code perLevel} XP each (P10-24); after
 * the last level every {@code overflow} XP is one more "overflow tier" (RX31), numbered {@code max + 1, max + 2, ...}.
 */
final class PassMath {

    /** The level curve of a season. {@code overflow = 0} means no overflow tiers. */
    record Curve(int max, int perLevel, int overflow) {

        Curve {
            max = Math.max(1, max);
            perLevel = Math.max(1, perLevel);
            overflow = Math.max(0, overflow);
        }

        /** Total XP needed to reach a tier (free track tier = level; tiers above max are overflow steps). */
        long xpFor(int tier) {
            if (tier <= 0) {
                return 0;
            }
            if (tier <= max) {
                return (long) tier * perLevel;
            }
            if (overflow == 0) {
                return Long.MAX_VALUE;
            }
            return (long) max * perLevel + (long) (tier - max) * overflow;
        }

        /** Highest tier reached with this XP (at most {@code max} without overflow). */
        int tier(long xp) {
            if (xp <= 0) {
                return 0;
            }
            long top = (long) max * perLevel;
            if (xp < top) {
                return (int) (xp / perLevel);
            }
            if (overflow == 0) {
                return max;
            }
            long extra = (xp - top) / overflow;
            return (int) Math.min(Integer.MAX_VALUE - (long) max, extra) + max;
        }

        /** The pass level shown to players (capped at {@code max}). */
        int level(long xp) {
            return Math.min(max, tier(xp));
        }

        /** XP inside the current step and the size of that step (for the progress bar). */
        long[] progress(long xp) {
            int t = tier(xp);
            if (t >= max && overflow == 0) {
                return new long[]{perLevel, perLevel};
            }
            long from = xpFor(t);
            long to = xpFor(t + 1);
            return new long[]{Math.max(0, xp - from), Math.max(1, to - from)};
        }
    }

    private PassMath() {
    }

    /**
     * Day of the season (1 = the start day), or 0 when there is no start date, it lies in the future or the season is over.
     */
    static int seasonDay(LocalDate start, LocalDate today, int days) {
        if (start == null || today == null || today.isBefore(start)) {
            return 0;
        }
        long d = ChronoUnit.DAYS.between(start, today) + 1;
        return d > days ? 0 : (int) d;
    }

    /** Days left in the season including today (0 = unknown or over). */
    static int daysLeft(LocalDate start, LocalDate today, int days) {
        int day = seasonDay(start, today, days);
        return day == 0 ? 0 : days - day + 1;
    }

    /**
     * RX30 catch-up: the XP multiplier for a profile at {@code level} on season day {@code day}. The day line is
     * {@code day x levelsPerDay} (capped at the last level); being {@code behind} or more levels below it gives
     * {@code multiplier}, else 1.
     */
    static double catchUp(boolean enabled, int day, int level, int max, double levelsPerDay, int behind, double multiplier) {
        if (!enabled || day <= 0 || multiplier <= 1) {
            return 1;
        }
        double line = Math.min(max, day * levelsPerDay);
        return line - level >= behind ? multiplier : 1;
    }

    /** XP actually added: the base amount (cut to {@code cap}) times the multiplier, rounded down, never negative. */
    static int applied(int base, double multiplier, int cap) {
        if (base <= 0) {
            return 0;
        }
        long capped = Math.min(base, Math.max(1, cap));
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.floor(capped * Math.max(1, multiplier)));
    }

    /** Stage tier from the best stage skill level (same rule as crates, quests and the calendar). */
    static int tierFor(int level, int[] tierLevels) {
        int tier = 0;
        for (int need : tierLevels) {
            if (level >= need) {
                tier++;
            }
        }
        return Math.max(1, tier);
    }

    /** Money for {@code minutes} of income at a tier (REF(t) per minute x minutes). */
    static long money(double minutes, int tier, long[] refPerMinute) {
        if (minutes <= 0 || refPerMinute.length == 0) {
            return 0;
        }
        long ref = refPerMinute[Math.max(0, Math.min(tier, refPerMinute.length) - 1)];
        return Math.round(ref * minutes);
    }

    /** Crystals of a pouch: base x growth^(tier-1), rounded. */
    static long crystals(long base, int tier, double growth) {
        if (base <= 0) {
            return 0;
        }
        return Math.round(base * Math.pow(Math.max(1, growth), Math.max(0, tier - 1)));
    }
}

package net.skybornmc.skyblock.crates;

import java.util.ArrayList;
import java.util.List;

/**
 * Numbers of crate opens (pure, unit-tested): pouch sizes that follow the opener's tier (PHASE-10 "Money and crystal
 * pouches scale with your tier"), the stage tier from skill levels (same rule as the AFK pool) and which keys an open
 * uses (bound first).
 */
final class CrateMath {

    /** Which keys a batch of opens takes: {@code bound} bound keys first, then {@code tradeable}. */
    record KeyUse(int bound, int tradeable) {

        int total() {
            return bound + tradeable;
        }

        /** True when open number {@code index} (0-based) of the batch used a bound key (bound keys go first). */
        boolean boundAt(int index) {
            return index < bound;
        }
    }

    private CrateMath() {
    }

    /**
     * Keys for {@code requested} opens (capped at {@code maxPerClick}) from what the profile has: bound keys first
     * (PHASE-10: "Opening uses bound keys first").
     */
    static KeyUse keys(long bound, long tradeable, int requested, int maxPerClick) {
        long want = Math.max(0, Math.min(requested, Math.max(1, maxPerClick)));
        long b = Math.min(Math.max(0, bound), want);
        long t = Math.min(Math.max(0, tradeable), want - b);
        return new KeyUse((int) b, (int) t);
    }

    /**
     * Staff take ({@code /crates admin take}, P10-41): which keys {@code amount} takes from what the profile has, bound
     * keys first (the same order as opening), then tradeable. {@code null} when the profile has fewer than
     * {@code amount} keys in total or the amount is not positive: a staff take never goes below zero and never takes
     * only part of what was asked.
     */
    static KeyUse staffTake(long bound, long tradeable, long amount) {
        long b = Math.max(0, bound);
        long t = Math.max(0, tradeable);
        if (amount < 1 || amount > Integer.MAX_VALUE || b + t < amount) {
            return null;
        }
        long fromBound = Math.min(b, amount);
        return new KeyUse((int) fromBound, (int) (amount - fromBound));
    }

    /** Stage tier (1..n) for the highest stage skill level; {@code tierLevels[i]} unlocks tier i+1. */
    static int tierFor(int level, int[] tierLevels) {
        int tier = 1;
        for (int i = 0; i < tierLevels.length; i++) {
            if (level >= tierLevels[i]) {
                tier = i + 1;
            }
        }
        return tier;
    }

    /** Reference income per minute at a tier ({@code refPerMinute[t-1]}, the last value above the list, 0 when empty). */
    static long refPerMinute(int tier, long[] refPerMinute) {
        if (refPerMinute.length == 0) {
            return 0;
        }
        return refPerMinute[Math.max(0, Math.min(refPerMinute.length - 1, tier - 1))];
    }

    /** Money pouch: {@code minutes} of reference income at the tier, rounded, never negative, never overflowing. */
    static long money(int minutes, int tier, long[] refPerMinute) {
        long ref = refPerMinute(tier, refPerMinute);
        if (minutes <= 0 || ref <= 0) {
            return 0;
        }
        return ref > Long.MAX_VALUE / minutes ? Long.MAX_VALUE : ref * minutes;
    }

    /** Crystal pouch: {@code base} x growth^(tier-1), rounded (ECONOMY_BALANCE §9.1: crystals grow x1.30 per tier). */
    static long crystals(long base, int tier, double growth) {
        if (base <= 0) {
            return 0;
        }
        double g = growth > 0 && Double.isFinite(growth) ? growth : 1.0;
        double v = base * Math.pow(g, Math.max(0, tier - 1));
        return v >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0, Math.round(v));
    }

    /** {@code raw} whole numbers ≥ 0; anything else goes to {@code problems} and is skipped. */
    static long[] longs(List<String> raw, String where, List<String> problems) {
        List<Long> out = new ArrayList<>();
        for (String s : raw == null ? List.<String>of() : raw) {
            try {
                long v = Long.parseLong(String.valueOf(s).trim().replace("_", "").replace(",", ""));
                if (v < 0) {
                    throw new NumberFormatException();
                }
                out.add(v);
            } catch (NumberFormatException e) {
                problems.add(where + ": '" + s + "' is not a whole number >= 0 (skipped)");
            }
        }
        return out.stream().mapToLong(Long::longValue).toArray();
    }

    /** {@code raw} as ascending whole numbers ≥ 0 (tier unlock levels); bad or falling values are skipped. */
    static int[] levels(List<String> raw, String where, List<String> problems) {
        List<Integer> out = new ArrayList<>();
        for (String s : raw == null ? List.<String>of() : raw) {
            try {
                int v = Integer.parseInt(String.valueOf(s).trim());
                if (v < 0 || (!out.isEmpty() && v < out.getLast())) {
                    problems.add(where + ": '" + s + "' must be >= 0 and not lower than the one before (skipped)");
                    continue;
                }
                out.add(v);
            } catch (NumberFormatException e) {
                problems.add(where + ": '" + s + "' is not a whole number (skipped)");
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** A chance as shown in the preview: {@code 12}, {@code 1.5}, {@code 0.25} (at most 2 decimals, no trailing zeros). */
    static String percent(double value) {
        double rounded = Math.round(value * 100.0) / 100.0;
        if (rounded == Math.rint(rounded)) {
            return String.valueOf((long) rounded);
        }
        String s = String.format(java.util.Locale.ROOT, "%.2f", rounded);
        while (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}

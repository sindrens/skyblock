package net.skybornmc.skyblock.depths.drops;

import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * Key fragment drops (REVIEW3 R3-18): mine and forest blocks of tier {@code minTier}+ drop a fragment at 1 in
 * {@code oneIn}, scaled by the rare-find bonus family (Treasure Hunter). Three fragments make a Phoenix key in
 * {@code /crates}. Pure; unit-tested.
 */
public final class FragmentDrops {

    private FragmentDrops() {
    }

    /** Chance per block (0..1) for a wing and tier, 0 where fragments don't drop. */
    public static double chance(String wing, int tier, List<String> wings, int minTier, int oneIn, double rareMultiplier) {
        if (wing == null || oneIn < 1 || tier < minTier || wings == null) {
            return 0;
        }
        String w = wing.toLowerCase(Locale.ROOT);
        boolean listed = wings.stream().anyMatch(x -> x != null && x.strip().equalsIgnoreCase(w));
        if (!listed) {
            return 0;
        }
        double bonus = Double.isFinite(rareMultiplier) ? Math.max(0, rareMultiplier) : 1.0;
        return Math.min(1.0, bonus / oneIn);
    }

    /** True if this block drops a fragment. */
    public static boolean drops(double chance, RandomGenerator rng) {
        return chance > 0 && rng.nextDouble() < chance;
    }

    /** Expected played hours per fragment at {@code blocksPerHour} (docs and the balance check). */
    public static double hoursPerFragment(int oneIn, double blocksPerHour, double rareMultiplier) {
        double perBlock = Math.min(1.0, Math.max(0, rareMultiplier) / Math.max(1, oneIn));
        return perBlock <= 0 || blocksPerHour <= 0 ? Double.POSITIVE_INFINITY : 1.0 / (perBlock * blocksPerHour);
    }
}

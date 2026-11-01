package net.skybornmc.skyblock.stacking;

/**
 * ECON-SPAWNERS step 7 (pure): the per-island cap of one spawner tier (S3: 32, plus a credit perk). An island above its cap
 * (spawners placed before the cap existed) is grandfathered: nothing is removed, it just can't add more.
 */
final class SpawnerRules {

    private SpawnerRules() {
    }

    /** The cap with the perk bonus clamped to {@code perkMax}; 0 = no cap. */
    static int cap(int maxPerTier, int perkBonus, int perkMax) {
        if (maxPerTier <= 0) {
            return 0;
        }
        long total = (long) maxPerTier + Math.max(0, Math.min(perkBonus, perkMax));
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** How many more spawners of a tier fit on the island ({@code cap} 0 = unlimited). Never negative. */
    static long room(long ownedOfTier, int cap) {
        if (cap <= 0) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, cap - Math.max(0, ownedOfTier));
    }
}

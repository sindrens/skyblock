package net.skybornmc.skyblock.depths.engine;

/**
 * Depths v2 tier palette rules (DV2-SPIKE), free of Bukkit so they are unit-tested.
 *
 * <p>A palette zone has one real "slot" block per spot and several tiers (index 1 = the softest, real block). Each
 * player sees the block of their <b>active tier</b>: the highest tier their skill level opens, or a lower one they
 * picked (D2, a staff toggle in the spike). Tier 1 is always open: it is what the world really holds.</p>
 */
public final class PaletteMath {

    /** "No forced tier": follow the skill level. */
    public static final int AUTO = 0;

    private PaletteMath() {
    }

    /**
     * Highest open tier (1-based) for {@code level}. {@code required[i]} is the level tier {@code i + 1} needs (any
     * order). Tier 1 counts as open even when its requirement is above the level (the real block is shown anyway).
     *
     * @param level skill level, or {@code null} when no skill system gives that skill (only tier 1)
     */
    public static int highestOpen(Integer level, int[] required) {
        if (required == null || required.length == 0 || level == null) {
            return 1;
        }
        int best = 1;
        for (int i = 1; i < required.length; i++) {
            if (level >= required[i] && i + 1 > best) {
                best = i + 1;
            }
        }
        return best;
    }

    /**
     * The tier a player sees: {@link #highestOpen}, or {@code forced} when it is a lower open tier (D2). A forced tier
     * above what the level opens is clamped (never shows rewards of a locked tier); {@link #AUTO} or a value below 1
     * follows the level.
     */
    public static int activeTier(Integer level, int[] required, int forced) {
        int open = highestOpen(level, required);
        if (forced < 1) {
            return open;
        }
        return Math.min(forced, open);
    }

    /** True if this tier differs from the real block, so the player needs block-change packets for the slots. */
    public static boolean needsPackets(int tier) {
        return tier > 1;
    }

    /**
     * Whether a tier change is an unlock moment (title, sound, particle wave): the tier went up on a skill level-up.
     * Joining, a profile switch, the periodic check (skills still loading) and the staff toggle swap quietly.
     */
    public static boolean isUnlock(int oldTier, int newTier, boolean levelUp) {
        return levelUp && newTier > oldTier;
    }

    /**
     * Index after the last entry of {@code sortedDist2} (squared distances, ascending) that lies inside
     * {@code radius}, starting at {@code from}. Used to play the unlock particle wave ring by ring.
     */
    public static int ringEnd(int[] sortedDist2, int from, double radius) {
        double limit = radius * radius;
        int i = Math.max(0, from);
        while (i < sortedDist2.length && sortedDist2[i] < limit) {
            i++;
        }
        return i;
    }

    /** Blocks one player may receive per tick during a swap ({@code max <= 0} = no limit). */
    public static int perTick(boolean bedrock, int bedrockMax, int javaMax) {
        int max = bedrock ? bedrockMax : javaMax;
        return max <= 0 ? Integer.MAX_VALUE : max;
    }

    /**
     * The wing tier whose rewards apply to a break: the event's shown tier (palette zones), else the zone's own tier.
     * Used by drops, tool XP and blueprints so every hook agrees.
     */
    public static int rewardTier(int eventTier, int zoneTier) {
        return eventTier > 0 ? eventTier : zoneTier;
    }

    /**
     * Sorts block keys nearest to the point first (tier swaps send the nearest blocks first; the unlock wave starts at
     * the player). Returns the squared distances in the new order.
     */
    public static int[] sortNearest(long[] keys, int px, int py, int pz) {
        int n = keys.length;
        if (n >= (1 << 24)) {
            throw new IllegalArgumentException("Too many slots in one swap: " + n);
        }
        long[] packed = new long[n];
        for (int i = 0; i < n; i++) {
            long dx = BlockKeys.x(keys[i]) - px;
            long dy = BlockKeys.y(keys[i]) - py;
            long dz = BlockKeys.z(keys[i]) - pz;
            long d2 = Math.min(Integer.MAX_VALUE, dx * dx + dy * dy + dz * dz);
            packed[i] = (d2 << 24) | i;
        }
        java.util.Arrays.sort(packed);
        long[] sorted = new long[n];
        int[] dist2 = new int[n];
        for (int i = 0; i < n; i++) {
            sorted[i] = keys[(int) (packed[i] & 0xFFFFFF)];
            dist2[i] = (int) (packed[i] >>> 24);
        }
        System.arraycopy(sorted, 0, keys, 0, n);
        return dist2;
    }
}

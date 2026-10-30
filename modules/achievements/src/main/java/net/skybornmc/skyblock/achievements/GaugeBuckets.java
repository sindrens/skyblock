package net.skybornmc.skyblock.achievements;

import java.util.UUID;

/** Spreads per-player work over time: a stable bucket per player (QUICK1, PERFORMANCE P-12/P-20). */
final class GaugeBuckets {

    private GaugeBuckets() {
    }

    /** A stable bucket in {@code [0, buckets)} for this player; 0 when {@code buckets <= 1}. */
    static int bucket(UUID player, int buckets) {
        if (buckets <= 1 || player == null) {
            return 0;
        }
        long mixed = player.getMostSignificantBits() ^ player.getLeastSignificantBits();
        mixed ^= (mixed >>> 33);
        return (int) Math.floorMod(mixed, (long) buckets);
    }
}

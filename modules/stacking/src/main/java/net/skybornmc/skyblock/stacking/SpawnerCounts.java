package net.skybornmc.skyblock.stacking;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owner F2: spawners per island and tier, kept in memory (no scans per tick). {@link SpawnerManager} updates it when a
 * stacked spawner is loaded, placed, stacked onto, taken from, frozen or cleared with its island, and rebuilds it after
 * a reload (tier lists may change). Counted per tier (card D2 question 2 default). Outposts (later) add their spawners
 * with {@link #add} under the island that owns them. Main thread only.
 */
final class SpawnerCounts {

    private final Map<UUID, long[]> byIsland = new HashMap<>();

    /** Adds {@code delta} (negative to remove) spawners of {@code tier} (1-5) on {@code island}; never below 0. */
    void add(UUID island, int tier, long delta) {
        if (island == null || tier < 1 || tier > SpawnerTiers.TIERS || delta == 0) {
            return;
        }
        long[] counts = byIsland.computeIfAbsent(island, k -> new long[SpawnerTiers.TIERS + 1]);
        counts[tier] = Math.max(0, counts[tier] + delta);
        if (empty(counts)) {
            byIsland.remove(island);
        }
    }

    /** Spawners of {@code tier} on {@code island} (0 when unknown). */
    long get(UUID island, int tier) {
        if (island == null || tier < 1 || tier > SpawnerTiers.TIERS) {
            return 0;
        }
        long[] counts = byIsland.get(island);
        return counts == null ? 0 : counts[tier];
    }

    /** All spawners on {@code island}, every tier. */
    long total(UUID island) {
        long[] counts = island == null ? null : byIsland.get(island);
        if (counts == null) {
            return 0;
        }
        long sum = 0;
        for (long c : counts) {
            sum += c;
        }
        return sum;
    }

    void clearIsland(UUID island) {
        if (island != null) {
            byIsland.remove(island);
        }
    }

    void clear() {
        byIsland.clear();
    }

    /** Islands with at least one counted spawner. */
    int islands() {
        return byIsland.size();
    }

    private static boolean empty(long[] counts) {
        for (long c : counts) {
            if (c != 0) {
                return false;
            }
        }
        return true;
    }
}

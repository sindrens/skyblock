package net.skybornmc.skyblock.islands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * PERF8: the pure part of closing banned leaders' islands (owner answer 522). The first ban-list refresh after every
 * start sees every banned account as "new" (nothing was known before), and each one used to walk every island; this
 * finds the islands of all new bans in <b>one</b> pass, in the same order as before (bans in list order, islands in
 * island order). Unit-tested.
 */
final class BanSweep {

    private BanSweep() {
    }

    /** The players in {@code next} that are not in {@code before}, in {@code next}'s order. */
    static Set<UUID> newlyBanned(Set<UUID> before, Collection<UUID> next) {
        Set<UUID> out = new LinkedHashSet<>();
        for (UUID id : next) {
            if (!before.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * The items whose leader (by {@code leaderOf}, null = none) is one of {@code players}, grouped per player in the
     * order of {@code players}; within a player in the order of {@code items}. Players without items are left out.
     */
    static <T> Map<UUID, List<T>> byLeader(Collection<T> items, Function<T, UUID> leaderOf, Set<UUID> players) {
        if (players.isEmpty() || items.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<T>> found = new HashMap<>();
        for (T item : items) {
            UUID leader = leaderOf.apply(item);
            if (leader != null && players.contains(leader)) {
                found.computeIfAbsent(leader, k -> new ArrayList<>()).add(item);
            }
        }
        Map<UUID, List<T>> out = new java.util.LinkedHashMap<>();
        for (UUID player : players) {
            List<T> list = found.get(player);
            if (list != null) {
                out.put(player, list);
            }
        }
        return out;
    }
}

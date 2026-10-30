package net.skybornmc.skyblock.common.profile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Leaderboards use each player's <b>best</b> profile (owner decision P7). Pure logic, unit-tested; use it through
 * {@link ProfileService#bestPerOwner(Map)} so the live profile/owner mapping is applied.
 */
public final class BestProfile {

    /** The best value of one player and the profile it came from. */
    public record Entry(UUID owner, UUID profile, long value) {
    }

    /** Highest value first; ties: the smaller owner UUID first so the order is stable. */
    public static final Comparator<Entry> RANKING = Comparator.comparingLong(Entry::value).reversed()
            .thenComparing(Entry::owner);

    private BestProfile() {
    }

    /**
     * Collapses values per profile key into one entry per player (the highest value wins), sorted by {@link #RANKING}.
     *
     * @param valuesByProfile value per profile key
     * @param ownerOf         profile key → player UUID
     * @param live            false for deleted profiles (their rows are ignored)
     */
    public static List<Entry> collapse(Map<UUID, Long> valuesByProfile, Function<UUID, UUID> ownerOf, Predicate<UUID> live) {
        Map<UUID, Entry> best = new HashMap<>();
        valuesByProfile.forEach((profile, value) -> {
            if (profile == null || value == null || !live.test(profile)) {
                return;
            }
            UUID owner = ownerOf.apply(profile);
            if (owner == null) {
                return;
            }
            Entry current = best.get(owner);
            if (current == null || value > current.value()
                    || (value == current.value() && profile.equals(owner))) {
                best.put(owner, new Entry(owner, profile, value));
            }
        });
        List<Entry> out = new ArrayList<>(best.values());
        out.sort(RANKING);
        return out;
    }

    /** {@link #collapse} cut to the first {@code limit} entries. */
    public static List<Entry> top(Map<UUID, Long> valuesByProfile, Function<UUID, UUID> ownerOf, Predicate<UUID> live, int limit) {
        List<Entry> all = collapse(valuesByProfile, ownerOf, live);
        return all.size() <= limit ? all : List.copyOf(all.subList(0, Math.max(0, limit)));
    }
}

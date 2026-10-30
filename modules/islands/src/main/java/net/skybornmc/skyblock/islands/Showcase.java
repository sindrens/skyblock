package net.skybornmc.skyblock.islands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Visit browser rankings (M2.4, pure). {@link Stats} come from {@link VisitStore#stats} (refreshed every
 * {@code browser.refresh-seconds}) plus local changes since then.
 */
final class Showcase {

    private Showcase() {
    }

    /** What the browser shows per island: unique visits in the window, ratings this season, likes this season. */
    record Stats(int visits, long ratingSum, int ratingCount, int likes) {
        static final Stats EMPTY = new Stats(0, 0, 0, 0);

        double average() {
            return VisitRules.average(ratingSum, ratingCount);
        }

        Stats withRating(Integer oldStars, int newStars) {
            return oldStars == null
                    ? new Stats(visits, ratingSum + newStars, ratingCount + 1, likes)
                    : new Stats(visits, ratingSum - oldStars + newStars, ratingCount, likes);
        }

        Stats withLike() {
            return new Stats(visits, ratingSum, ratingCount, likes + 1);
        }

        Stats withVisit() {
            return new Stats(visits + 1, ratingSum, ratingCount, likes);
        }
    }

    /** Browser tabs. */
    enum Sort {
        VISITS, RATING, LIKES, NEWEST;

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** One listed island. */
    record Entry(UUID islandId, long createdAt, Stats stats) {
    }

    /**
     * Sorts {@code entries} for a tab. Top rated only lists islands with at least {@code minRatings} ratings (fewer
     * ratings are too easy to fake); ties go to more ratings, then the newer island. Everything else: highest first,
     * then newest.
     */
    static List<Entry> sort(List<Entry> entries, Sort sort, int minRatings) {
        List<Entry> list = new ArrayList<>(entries);
        Comparator<Entry> newest = Comparator.comparingLong(Entry::createdAt).reversed();
        switch (sort) {
            case VISITS -> list.sort(Comparator.comparingInt((Entry e) -> e.stats().visits()).reversed().thenComparing(newest));
            case LIKES -> list.sort(Comparator.comparingInt((Entry e) -> e.stats().likes()).reversed().thenComparing(newest));
            case NEWEST -> list.sort(newest);
            case RATING -> {
                list.removeIf(e -> e.stats().ratingCount() < Math.max(1, minRatings));
                list.sort(Comparator.comparingDouble((Entry e) -> e.stats().average()).reversed()
                        .thenComparing(Comparator.comparingInt((Entry e) -> e.stats().ratingCount()).reversed())
                        .thenComparing(newest));
            }
        }
        return list;
    }

    /** Entries for every listed island id (missing stats = empty). */
    static List<Entry> entries(Iterable<Island> islands, Map<UUID, Stats> stats) {
        List<Entry> list = new ArrayList<>();
        for (Island island : islands) {
            list.add(new Entry(island.id(), island.createdAt(), stats.getOrDefault(island.id(), Stats.EMPTY)));
        }
        return list;
    }
}

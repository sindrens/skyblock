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

    /** Browser tabs. DISCOVER (owner answer 290) is the default: every island, random, favouring more playtime. */
    enum Sort {
        DISCOVER, VISITS, RATING, LIKES, NEWEST;

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
            // Random per open: callers use discover(); without playtimes it is a plain newest-first list.
            case DISCOVER -> list.sort(newest);
            case RATING -> {
                list.removeIf(e -> e.stats().ratingCount() < Math.max(1, minRatings));
                list.sort(Comparator.comparingDouble((Entry e) -> e.stats().average()).reversed()
                        .thenComparing(Comparator.comparingInt((Entry e) -> e.stats().ratingCount()).reversed())
                        .thenComparing(newest));
            }
        }
        return list;
    }

    /**
     * Owner answer 290: every island in random order, islands with more playtime more likely near the top (weighted random
     * order, Efraimidis-Spirakis: key = u^(1/w)). Weight = 1 + {@code factor} x ln(1 + playtime hours), so a new island
     * still shows up and a 100-hour island is about 5.6x as likely to be ahead of it (factor 1). {@code factor} 0 = plain
     * random.
     */
    static List<Entry> discover(List<Entry> entries, java.util.function.ToLongFunction<UUID> playtimeSeconds, double factor,
                                java.util.random.RandomGenerator random) {
        List<Entry> list = new ArrayList<>(entries);
        Map<UUID, Double> keys = new java.util.HashMap<>();
        for (Entry entry : list) {
            double hours = Math.max(0, playtimeSeconds.applyAsLong(entry.islandId())) / 3600.0;
            double weight = 1 + Math.max(0, factor) * Math.log1p(hours);
            double u = Math.max(Double.MIN_VALUE, random.nextDouble());
            keys.put(entry.islandId(), Math.pow(u, 1.0 / weight));
        }
        list.sort(Comparator.comparingDouble((Entry e) -> keys.get(e.islandId())).reversed());
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

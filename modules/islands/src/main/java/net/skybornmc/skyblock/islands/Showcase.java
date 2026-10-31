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

    /**
     * What the browser shows per island: unique visits in the window, ratings this season, likes this season, and from how
     * many different addresses the ratings came ({@code ratingIps}, owner R3-33; ratings from before V013 have no address
     * and don't count there).
     */
    record Stats(int visits, long ratingSum, int ratingCount, int likes, int ratingIps) {
        static final Stats EMPTY = new Stats(0, 0, 0, 0, 0);

        /** Stats where every rating came from its own address (tests, old callers). */
        Stats(int visits, long ratingSum, int ratingCount, int likes) {
            this(visits, ratingSum, ratingCount, likes, ratingCount);
        }

        double average() {
            return VisitRules.average(ratingSum, ratingCount);
        }

        Stats withRating(Integer oldStars, int newStars) {
            // The address count is only known after the next refresh (browser.refresh-seconds): a new address can't be
            // told apart from a known one here, so it stays as loaded (never over-counts).
            return oldStars == null
                    ? new Stats(visits, ratingSum + newStars, ratingCount + 1, likes, ratingIps)
                    : new Stats(visits, ratingSum - oldStars + newStars, ratingCount, likes, ratingIps);
        }

        Stats withLike() {
            return new Stats(visits, ratingSum, ratingCount, likes + 1, ratingIps);
        }

        Stats withVisit() {
            return new Stats(visits + 1, ratingSum, ratingCount, likes, ratingIps);
        }
    }

    /** Browser tabs. DISCOVER (owner answer 290) is the default: every island, random, favouring more playtime. */
    enum Sort {
        DISCOVER, VISITS, RATING, LIKES, NEWEST;

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * Owner R3-33 "Top rated" rules: at least {@code minRatings} ratings from at least {@code minIps} different addresses,
     * ranked by a smoothed (Bayesian) average with {@code priorWeight} virtual ratings at the server-wide mean.
     */
    record TopRated(int minRatings, int minIps, double priorWeight) {
        /** The old rule: only a rating count, plain average. */
        static TopRated plain(int minRatings) {
            return new TopRated(minRatings, 1, 0);
        }
    }

    /** Fallback prior mean (middle of 1-5 stars) while nothing is rated yet. */
    static final double DEFAULT_PRIOR_MEAN = 3.0;

    /** Server-wide mean stars over every rating in {@code entries} ({@link #DEFAULT_PRIOR_MEAN} when there is none). */
    static double globalMean(List<Entry> entries) {
        long sum = 0;
        long count = 0;
        for (Entry e : entries) {
            sum += e.stats().ratingSum();
            count += e.stats().ratingCount();
        }
        return count == 0 ? DEFAULT_PRIOR_MEAN : (double) sum / count;
    }

    /**
     * Smoothed (Bayesian) average: {@code (weight x mean + sum) / (weight + count)}. Few ratings stay near the server-wide
     * mean; many ratings move to their own average. {@code weight} 0 = plain average.
     */
    static double bayesian(long sum, int count, double mean, double weight) {
        double w = Math.max(0, weight);
        if (count <= 0 && w == 0) {
            return 0;
        }
        return (w * mean + sum) / (w + Math.max(0, count));
    }

    /** True if an island may be listed under "Top rated" (owner R3-33: enough ratings and enough different addresses). */
    static boolean topRatedEligible(Stats stats, TopRated rules) {
        return stats.ratingCount() >= Math.max(1, rules.minRatings()) && stats.ratingIps() >= Math.max(1, rules.minIps());
    }

    /** One listed island. */
    record Entry(UUID islandId, long createdAt, Stats stats) {
    }

    /**
     * Sorts {@code entries} for a tab. Top rated only lists islands with at least {@code minRatings} ratings (fewer
     * ratings are too easy to fake), plain average; ties go to more ratings, then the newer island. Everything else:
     * highest first, then newest.
     */
    static List<Entry> sort(List<Entry> entries, Sort sort, int minRatings) {
        return sort(entries, sort, TopRated.plain(minRatings));
    }

    /** Like {@link #sort(List, Sort, int)}; Top rated follows {@code rules} (owner R3-33). */
    static List<Entry> sort(List<Entry> entries, Sort sort, TopRated rules) {
        List<Entry> list = new ArrayList<>(entries);
        Comparator<Entry> newest = Comparator.comparingLong(Entry::createdAt).reversed();
        switch (sort) {
            case VISITS -> list.sort(Comparator.comparingInt((Entry e) -> e.stats().visits()).reversed().thenComparing(newest));
            case LIKES -> list.sort(Comparator.comparingInt((Entry e) -> e.stats().likes()).reversed().thenComparing(newest));
            case NEWEST -> list.sort(newest);
            // Random per open: callers use discover(); without playtimes it is a plain newest-first list.
            case DISCOVER -> list.sort(newest);
            case RATING -> {
                double mean = globalMean(entries); // every rating counts for the prior, also of islands not listed yet
                list.removeIf(e -> !topRatedEligible(e.stats(), rules));
                list.sort(Comparator.comparingDouble((Entry e) -> bayesian(e.stats().ratingSum(), e.stats().ratingCount(), mean,
                                rules.priorWeight())).reversed()
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

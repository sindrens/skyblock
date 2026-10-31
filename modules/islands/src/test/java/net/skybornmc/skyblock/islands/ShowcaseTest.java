package net.skybornmc.skyblock.islands;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Visit browser sorting (M2.4). */
class ShowcaseTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    private static List<UUID> ids(List<Showcase.Entry> entries) {
        return entries.stream().map(Showcase.Entry::islandId).toList();
    }

    private final List<Showcase.Entry> entries = List.of(
            new Showcase.Entry(A, 100, new Showcase.Stats(10, 20, 4, 3)),    // 5.0 from 4 ratings
            new Showcase.Entry(B, 300, new Showcase.Stats(30, 10, 2, 9)),    // 5.0 from 2 ratings (too few)
            new Showcase.Entry(C, 200, new Showcase.Stats(10, 12, 4, 1)));   // 3.0 from 4 ratings

    @Test
    void mostVisitedTiesGoToTheNewerIsland() {
        assertEquals(List.of(B, C, A), ids(Showcase.sort(entries, Showcase.Sort.VISITS, 3)));
    }

    @Test
    void topRatedNeedsEnoughRatings() {
        assertEquals(List.of(A, C), ids(Showcase.sort(entries, Showcase.Sort.RATING, 3)));
        assertEquals(List.of(A, B, C), ids(Showcase.sort(entries, Showcase.Sort.RATING, 1)), "same average: more ratings first");
    }

    @Test
    void topRatedNeedsRatingsFromFiveDifferentAddresses() {
        // Owner R3-33: 9 ratings of 5 stars, but all from 2 addresses (alt accounts) -> not listed.
        UUID alts = new UUID(0, 9);
        List<Showcase.Entry> list = List.of(
                new Showcase.Entry(alts, 100, new Showcase.Stats(0, 45, 9, 0, 2)),
                new Showcase.Entry(A, 100, new Showcase.Stats(0, 20, 5, 0, 5)),   // 4.0 from 5 addresses: listed
                new Showcase.Entry(B, 100, new Showcase.Stats(0, 25, 5, 0, 4)));  // 4 addresses: one short
        Showcase.TopRated rules = new Showcase.TopRated(3, 5, 5);
        assertEquals(List.of(A), ids(Showcase.sort(list, Showcase.Sort.RATING, rules)));
        assertTrue(Showcase.topRatedEligible(new Showcase.Stats(0, 25, 5, 0, 5), rules));
        assertFalse(Showcase.topRatedEligible(new Showcase.Stats(0, 25, 5, 0, 4), rules));
        assertFalse(Showcase.topRatedEligible(new Showcase.Stats(0, 10, 2, 0, 5), rules), "min ratings still applies");
    }

    @Test
    void bayesianAveragePullsFewRatingsToTheServerMean() {
        assertEquals(4.0, Showcase.bayesian(40, 10, 3.0, 0), 1e-9, "weight 0 = plain average");
        assertEquals((5 * 3.0 + 25) / 10, Showcase.bayesian(25, 5, 3.0, 5), 1e-9);
        assertEquals(3.0, Showcase.bayesian(0, 0, 3.0, 5), 1e-9, "no ratings = the mean");
        assertEquals(0.0, Showcase.bayesian(0, 0, 3.0, 0), 1e-9);
        assertEquals(Showcase.DEFAULT_PRIOR_MEAN, Showcase.globalMean(List.of()), 1e-9);
        assertEquals(3.5, Showcase.globalMean(List.of(
                new Showcase.Entry(A, 1, new Showcase.Stats(0, 25, 5, 0)),
                new Showcase.Entry(B, 1, new Showcase.Stats(0, 10, 5, 0)))), 1e-9, "mean over all ratings, not over islands");
    }

    @Test
    void manyGoodRatingsBeatAFewPerfectOnes() {
        // 5 x 5 stars vs 100 ratings averaging 4.8, server mean around 4.0, weight 5.
        UUID perfect = new UUID(0, 10);
        UUID popular = new UUID(0, 11);
        UUID filler = new UUID(0, 12);
        List<Showcase.Entry> list = List.of(
                new Showcase.Entry(perfect, 300, new Showcase.Stats(0, 25, 5, 0, 5)),
                new Showcase.Entry(popular, 100, new Showcase.Stats(0, 480, 100, 0, 80)),
                new Showcase.Entry(filler, 200, new Showcase.Stats(0, 500, 200, 0, 150)));  // 2.5 average
        Showcase.TopRated rules = new Showcase.TopRated(3, 5, 5);
        assertEquals(List.of(popular, perfect, filler), ids(Showcase.sort(list, Showcase.Sort.RATING, rules)));
        // With a plain average the 5 perfect ratings would win.
        assertEquals(List.of(perfect, popular, filler), ids(Showcase.sort(list, Showcase.Sort.RATING, new Showcase.TopRated(3, 5, 0))));
    }

    @Test
    void mostLikedAndNewest() {
        assertEquals(List.of(B, A, C), ids(Showcase.sort(entries, Showcase.Sort.LIKES, 3)));
        assertEquals(List.of(B, C, A), ids(Showcase.sort(entries, Showcase.Sort.NEWEST, 3)));
    }

    @Test
    void localChangesUpdateTheStats() {
        Showcase.Stats stats = Showcase.Stats.EMPTY.withRating(null, 4).withRating(null, 2);
        assertEquals(2, stats.ratingCount());
        assertEquals(3.0, stats.average());
        stats = stats.withRating(2, 5);
        assertEquals(2, stats.ratingCount(), "changing a rating keeps the count");
        assertEquals(4.5, stats.average());
        assertEquals(1, stats.withLike().likes());
        assertEquals(1, stats.withVisit().visits());
    }

    @Test
    void discoverListsEveryIslandFavouringPlaytime() {
        java.util.Map<UUID, Long> playtime = java.util.Map.of(A, 0L, B, 500L * 3600, C, 0L);
        java.util.Random random = new java.util.Random(42);
        int bFirst = 0;
        for (int i = 0; i < 2000; i++) {
            List<Showcase.Entry> list = Showcase.discover(entries, id -> playtime.getOrDefault(id, 0L), 1.0, random);
            assertEquals(3, list.size(), "every island is listed (owner answer 290)");
            if (list.get(0).islandId().equals(B)) {
                bFirst++;
            }
        }
        // weight of B = 1 + ln(501) = 7.2 against 1 + 1: B leads about 7.2 / 9.2 = 78% of the time
        org.junit.jupiter.api.Assertions.assertTrue(bFirst > 1400 && bFirst < 1700, "B first " + bFirst + " of 2000");
        int aFirst = 0;
        for (int i = 0; i < 3000; i++) {
            if (Showcase.discover(entries, id -> 0L, 1.0, random).get(0).islandId().equals(A)) {
                aFirst++;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(aFirst > 850 && aFirst < 1150, "no playtime: plain random, " + aFirst);
    }
}

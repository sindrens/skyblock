package net.skybornmc.skyblock.islands;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

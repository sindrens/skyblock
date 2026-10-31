package net.skybornmc.skyblock.collection;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilestonesTest {

    private static final List<Integer> P = List.of(25, 50, 75, 100);

    private static Catalog catalog() {
        return Catalog.build(List.of("mine", "forest"),
                Map.of("mine", List.of("1: a", "2: b", "3: c", "4: d"), "forest", List.of("1: e", "2: f", "3: g")),
                List.of(), true, x -> List.of(), id -> true, p -> { });
    }

    private static Milestones.Rewards rewards() {
        return new Milestones.Rewards(true, "blaze", 1, "keeper_of_ashes", "phoenix", 1, "");
    }

    @Test
    void percentsAreSortedCleanAndAlwaysEndWithAFullPage() {
        List<String> problems = new ArrayList<>();
        assertEquals(List.of(25, 50, 100), Milestones.percents(List.of("50", "25", "25%", "0", "abc", "120"), problems::add));
        assertEquals(3, problems.size());
    }

    @Test
    void thresholdsRoundUp() {
        assertFalse(Milestones.reached(0, 0, 25), "an empty page reaches nothing");
        assertTrue(Milestones.reached(1, 4, 25));
        assertFalse(Milestones.reached(0, 3, 25));
        assertTrue(Milestones.reached(1, 3, 25));   // 33%
        assertFalse(Milestones.reached(1, 3, 50));
        assertTrue(Milestones.reached(2, 3, 50));
        assertFalse(Milestones.reached(2, 3, 75));   // 66%
        assertTrue(Milestones.reached(3, 3, 100));
    }

    @Test
    void newlyReachedSkipsWhatIsStored() {
        Catalog c = catalog();
        Set<String> found = new HashSet<>(Set.of("a", "b"));
        assertEquals(List.of("mine:25", "mine:50"), Milestones.reachedNow(c, found, P));
        assertEquals(List.of("mine:50"), Milestones.newlyReached(c, found, Set.of("mine:25"), P));
    }

    @Test
    void wholeLogNeedsEveryPageFull() {
        Catalog c = catalog();
        Set<String> found = new HashSet<>(Set.of("a", "b", "c", "d"));
        assertFalse(Milestones.reachedNow(c, found, P).contains(Milestones.ALL));
        found.addAll(Set.of("e", "f", "g"));
        List<String> all = Milestones.reachedNow(c, found, P);
        assertEquals(Milestones.ALL, all.getLast());
        assertEquals(9, all.size());
        // A configured page without entries (e.g. crates module off) blocks the whole log.
        Catalog withEmpty = Catalog.build(List.of("mine", "crates"), Map.of("mine", List.of("1: a")), List.of(), true,
                x -> List.of(), id -> true, p -> { });
        assertFalse(Milestones.reachedNow(withEmpty, Set.of("a"), P).contains(Milestones.ALL));
    }

    @Test
    void grantsPerMilestone() {
        Milestones.Rewards r = rewards();
        assertEquals(List.of(), Milestones.grants("mine:50", r), "small milestones only give island points");
        assertEquals(List.of(new Milestones.Grant(Milestones.TITLE, "collection_mine", 1), new Milestones.Grant(Milestones.KEY, "blaze", 1)),
                Milestones.grants("mine:100", r));
        assertEquals(List.of(new Milestones.Grant(Milestones.TITLE, "keeper_of_ashes", 1), new Milestones.Grant(Milestones.KEY, "phoenix", 1)),
                Milestones.grants(Milestones.ALL, r));
        Milestones.Rewards withSet = new Milestones.Rewards(false, "", 0, "", "phoenix", 2, "ashen");
        assertEquals(List.of(), Milestones.grants("mine:100", withSet));
        assertEquals(List.of(new Milestones.Grant(Milestones.KEY, "phoenix", 2), new Milestones.Grant(Milestones.SET, "ashen", 1)),
                Milestones.grants(Milestones.ALL, withSet));
    }

    @Test
    void pageMilestonesCountForTheIslandLevel() {
        assertTrue(Milestones.isPageMilestone("mine:25"));
        assertTrue(Milestones.isPageMilestone("crates:100"));
        assertFalse(Milestones.isPageMilestone(Milestones.ALL));
        assertEquals(-1, Milestones.percentOf("mine:x"));
    }

    @Test
    void tabSlotsAreCentred() {
        assertEquals(List.of(4), box(CollectionMenu.tabSlots(1)));
        assertEquals(List.of(3, 5), box(CollectionMenu.tabSlots(2)));
        assertEquals(List.of(2, 4, 6), box(CollectionMenu.tabSlots(3)));
        assertEquals(List.of(1, 3, 5, 7), box(CollectionMenu.tabSlots(4)));
        assertEquals(List.of(2, 3, 4, 5, 6), box(CollectionMenu.tabSlots(5)));
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), box(CollectionMenu.tabSlots(9)));
    }

    private static List<Integer> box(int[] a) {
        List<Integer> out = new ArrayList<>();
        for (int i : a) {
            out.add(i);
        }
        return out;
    }
}

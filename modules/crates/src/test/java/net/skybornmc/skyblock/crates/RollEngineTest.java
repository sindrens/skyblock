package net.skybornmc.skyblock.crates;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RollEngineTest {

    private static final CrateCatalog.Reward COMMON = Fixtures.reward("common", RewardType.CRYSTALS, 90, false);
    private static final CrateCatalog.Reward RARE = Fixtures.reward("rare", RewardType.MONEY, 10, true);

    @Test
    void weightsDecideTheShare() {
        CrateCatalog.Crate crate = Fixtures.crate(0, COMMON, RARE);
        Random rng = new Random(42);
        int top = 0;
        int n = 200_000;
        for (int i = 0; i < n; i++) {
            if (RollEngine.rollOne(crate, 0, rng, k -> false).top()) {
                top++;
            }
        }
        assertEquals(0.10, top / (double) n, 0.005);
    }

    @Test
    void bundledEmberMatchesItsTable() {
        CrateCatalog.Crate ember = Fixtures.bundled().crate("ember").orElseThrow();
        Random rng = new Random(7);
        Map<String, Integer> counts = new HashMap<>();
        int n = 400_000;
        for (int i = 0; i < n; i++) {
            counts.merge(RollEngine.pick(ember.rewards(), rng).id(), 1, Integer::sum);
        }
        for (CrateCatalog.Reward r : ember.rewards()) {
            assertEquals(r.chance() / 100.0, counts.getOrDefault(r.id(), 0) / (double) n, 0.004, r.id());
        }
    }

    @Test
    void pityForcesTheTopBandAtItsNumber() {
        CrateCatalog.Crate crate = Fixtures.crate(5, Fixtures.reward("never", RewardType.CRYSTALS, 1_000_000, false),
                Fixtures.reward("top", RewardType.MONEY, 0.000001, true));
        assertFalse(RollEngine.forced(crate, 3));
        assertTrue(RollEngine.forced(crate, 4), "the 5th open after 4 misses is forced");
        RollEngine.Batch batch = RollEngine.roll(crate, 0, 12, new Random(1), k -> false);
        List<Boolean> tops = batch.rolls().stream().map(RollEngine.Roll::top).toList();
        assertEquals(List.of(false, false, false, false, true, false, false, false, false, true, false, false), tops);
        assertTrue(batch.rolls().get(4).forced());
        assertEquals(2, batch.pityAfter(), "two misses after the last top");
    }

    @Test
    void aNaturalTopResetsTheCounter() {
        CrateCatalog.Crate crate = Fixtures.crate(30, Fixtures.reward("top", RewardType.MONEY, 100, true));
        RollEngine.Batch batch = RollEngine.roll(crate, 17, 1, new Random(1), k -> false);
        assertTrue(batch.rolls().getFirst().top());
        assertFalse(batch.rolls().getFirst().forced());
        assertEquals(0, batch.pityAfter());
        assertEquals(18, RollEngine.next(17, false));
    }

    @Test
    void pityOffWhenZeroOrNoTopBand() {
        assertFalse(RollEngine.forced(Fixtures.crate(0, COMMON, RARE), 1_000));
        assertFalse(RollEngine.forced(Fixtures.crate(3, COMMON), 1_000));
    }

    @Test
    void ownedCosmeticsReRollIntoNewOnes() {
        CrateCatalog.Reward tags = Fixtures.cosmetic("tags", RewardType.TAG, 100, false, List.of("spark", "ash"));
        CrateCatalog.Crate crate = Fixtures.crate(0, tags);
        for (int i = 0; i < 50; i++) {
            RollEngine.Roll r = RollEngine.rollOne(crate, 0, new Random(i), k -> k.equals("tag:spark"));
            assertEquals("ash", r.cosmeticId());
            assertFalse(r.fallback());
        }
    }

    @Test
    void sameBandOtherCosmeticWhenAllOfOneAreOwned() {
        CrateCatalog.Reward tags = Fixtures.cosmetic("tags", RewardType.TAG, 99, false, List.of("spark"));
        CrateCatalog.Reward titles = Fixtures.cosmetic("titles", RewardType.TITLE, 1, false, List.of("hero"));
        CrateCatalog.Reward topTag = Fixtures.cosmetic("top_tag", RewardType.TAG, 0.0001, true, List.of("phoenix"));
        CrateCatalog.Crate crate = Fixtures.crate(0, tags, titles, topTag);
        RollEngine.Roll r = RollEngine.rollOne(crate, 0, new Random(3), k -> k.equals("tag:spark"));
        assertEquals("titles", r.reward().id(), "re-rolled into the other cosmetic of the same (non-top) band");
        assertEquals("hero", r.cosmeticId());
        assertFalse(r.top(), "never jumps into the top band");
    }

    @Test
    void ownEverythingPaysCrystals() {
        CrateCatalog.Reward tags = Fixtures.cosmetic("tags", RewardType.TAG, 100, false, List.of("spark", "ash"));
        RollEngine.Roll r = RollEngine.rollOne(Fixtures.crate(0, tags), 0, new Random(1), k -> true);
        assertTrue(r.fallback());
        assertNull(r.cosmeticId());
        RollEngine.Roll empty = RollEngine.rollOne(Fixtures.crate(0, Fixtures.cosmetic("t", RewardType.TITLE, 1, false, List.of())),
                0, new Random(1), k -> false);
        assertTrue(empty.fallback(), "an empty pool pays its crystal value");
    }

    @Test
    void aBatchNeverGivesTheSameCosmeticTwice() {
        CrateCatalog.Reward tags = Fixtures.cosmetic("tags", RewardType.TAG, 100, false, List.of("a", "b", "c"));
        RollEngine.Batch batch = RollEngine.roll(Fixtures.crate(0, tags), 0, 5, new Random(9), k -> false);
        Set<String> seen = new HashSet<>();
        int fallbacks = 0;
        for (RollEngine.Roll r : batch.rolls()) {
            if (r.fallback()) {
                fallbacks++;
            } else {
                assertNotNull(r.cosmeticId());
                assertTrue(seen.add(r.cosmeticId()), "duplicate " + r.cosmeticId());
            }
        }
        assertEquals(3, seen.size());
        assertEquals(2, fallbacks);
    }

    @Test
    void forcedPityWithinABatchUsesTheCounterSoFar() {
        CrateCatalog.Crate crate = Fixtures.crate(3, Fixtures.reward("miss", RewardType.CRYSTALS, 1_000_000, false),
                Fixtures.reward("top", RewardType.MONEY, 0.000001, true));
        RollEngine.Batch batch = RollEngine.roll(crate, 1, 2, new Random(5), k -> false);
        assertFalse(batch.rolls().get(0).top());
        assertTrue(batch.rolls().get(1).forced(), "counter 1 + one miss = 2, the next is the 3rd");
        assertEquals(0, batch.pityAfter());
    }
}

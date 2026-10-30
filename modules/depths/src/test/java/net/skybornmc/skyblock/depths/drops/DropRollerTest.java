package net.skybornmc.skyblock.depths.drops;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropRollerTest {

    private static final DropTable.TierDrops COAL = new DropTable.TierDrops("depths_coal", 1, 0.05, 1, List.of());

    @Test
    void sameSeedSameRolls() {
        DropTable.TierDrops t = new DropTable.TierDrops("depths_iron", 1, 0.3, 2, List.of(new DropTable.RareDrop("depths_geode", 0.1)));
        Random a = new Random(42);
        Random b = new Random(42);
        for (int i = 0; i < 200; i++) {
            assertEquals(DropRoller.roll(t, 1.4, 1.2, 1.0, 3, a), DropRoller.roll(t, 1.4, 1.2, 1.0, 3, b));
        }
    }

    @Test
    void noFortuneMeansExactlyTheTableAmount() {
        Random rng = new Random(1);
        for (int i = 0; i < 1000; i++) {
            assertEquals(1, DropRoller.roll(COAL, 1.0, 1.0, 1.0, 3, rng).items());
        }
        DropTable.TierDrops two = new DropTable.TierDrops("depths_coal", 2, 0, 1, List.of());
        assertEquals(2, DropRoller.roll(two, 1.0, 1.0, 1.0, 1, rng).items(), "the table amount is always reached");
    }

    @Test
    void fortuneIsAnExpectedValueAndCapped() {
        SplittableRandom rng = new SplittableRandom(7);
        int n = 200_000;
        long items = 0;
        for (int i = 0; i < n; i++) {
            items += DropRoller.roll(COAL, 1.55, 1.0, 1.0, 3, rng).items(); // tier 12 tool: 1 + 0.05 x 11
        }
        assertEquals(1.55, items / (double) n, 0.01);
        for (int i = 0; i < 1000; i++) {
            assertTrue(DropRoller.roll(COAL, 9.0, 1.0, 1.0, 3, rng).items() <= 3, "max 3 drops per block");
        }
    }

    @Test
    void crystalRatesMatchTheBalanceSheet() {
        SplittableRandom rng = new SplittableRandom(11);
        int n = 400_000;
        long crystals = 0;
        for (int i = 0; i < n; i++) {
            crystals += DropRoller.roll(COAL, 1.0, 1.0, 1.0, 3, rng).crystals();
        }
        assertEquals(0.05, crystals / (double) n, 0.002); // ECONOMY_BALANCE §9.1 tier 1: 1 per 20 blocks
        crystals = 0;
        for (int i = 0; i < n; i++) {
            crystals += DropRoller.roll(COAL, 1.0, 1.5, 1.0, 3, rng).crystals();
        }
        assertEquals(0.075, crystals / (double) n, 0.002, "crystal bonus scales the chance");
    }

    @Test
    void crystalChanceAboveOneGivesSeveralHits() {
        DropTable.TierDrops t = new DropTable.TierDrops("depths_coal", 1, 2.5, 3, List.of());
        SplittableRandom rng = new SplittableRandom(3);
        long total = 0;
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            long c = DropRoller.roll(t, 1.0, 1.0, 1.0, 3, rng).crystals();
            assertTrue(c == 6 || c == 9);
            total += c;
        }
        assertEquals(7.5, total / (double) n, 0.05);
    }

    @Test
    void rareFindsUseTheirChanceAndBonus() {
        DropTable.TierDrops t = new DropTable.TierDrops("depths_amethyst", 1, 0, 1, List.of(new DropTable.RareDrop("depths_geode", 0.01)));
        SplittableRandom rng = new SplittableRandom(5);
        int n = 300_000;
        int found = 0;
        int boosted = 0;
        for (int i = 0; i < n; i++) {
            found += DropRoller.roll(t, 1.0, 1.0, 1.0, 3, rng).rare().getOrDefault("depths_geode", 0);
            boosted += DropRoller.roll(t, 1.0, 1.0, 2.0, 3, rng).rare().getOrDefault("depths_geode", 0);
        }
        assertEquals(0.01, found / (double) n, 0.001);
        assertEquals(0.02, boosted / (double) n, 0.0015);
    }

    @Test
    void emptyCasesAllocateNothing() {
        assertSame(DropRoller.Roll.NOTHING, DropRoller.roll(null, 1, 1, 1, 3, new Random()));
        DropRoller.Roll r = DropRoller.roll(new DropTable.TierDrops("depths_coal", 1, 0, 1, List.of()), 1, 1, 1, 3, new Random(1));
        assertTrue(r.rare().isEmpty());
        assertEquals(0, r.crystals());
        assertEquals(0, DropRoller.expected(-1, new Random()));
        assertEquals(0, DropRoller.expected(Double.NaN, new Random()));
    }
}

package net.skybornmc.skyblock.progression;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lowest island bank balance of the last 24 h (PHASE-5 M5.2 source 7, owner P5-5). */
class BankSamplesTest {

    private static final long H = 3_600_000L;
    private static final long DAY = 24 * H;
    private static final long GAP = 15 * 60_000L;

    @Test
    void notObservedForAWholeWindowCountsNothing() {
        BankSamples s = new BankSamples();
        s.add(0, 50_000_000, GAP);
        assertEquals(OptionalLong.empty(), s.lowest(10 * H, DAY));
        assertEquals(OptionalLong.of(50_000_000), s.lowest(DAY, DAY));
    }

    @Test
    void depositJustBeforeACheckDoesNothing() {
        BankSamples s = new BankSamples();
        s.add(0, 1_000_000, GAP);
        s.add(30 * H, 1_000_000_000, GAP); // big deposit at hour 30
        assertEquals(OptionalLong.of(1_000_000), s.lowest(31 * H, DAY));
        assertEquals(OptionalLong.of(1_000_000), s.lowest(53 * H, DAY), "still inside 24 h of the old balance");
        assertEquals(OptionalLong.of(1_000_000_000), s.lowest(54 * H + 1, DAY), "a full day later it counts");
    }

    @Test
    void withdrawalCountsAtOnce() {
        BankSamples s = new BankSamples();
        s.add(0, 10_000_000, GAP);
        s.add(DAY + H, 5, GAP);
        assertEquals(OptionalLong.of(5), s.lowest(DAY + H, DAY));
    }

    @Test
    void samplesAreThrottledButDropsAlwaysKept() {
        BankSamples s = new BankSamples();
        assertTrue(s.add(0, 100, GAP));
        assertFalse(s.add(60_000, 200, GAP), "higher balance within the gap is skipped");
        assertTrue(s.add(120_000, 50, GAP), "a drop is always kept");
        assertTrue(s.add(GAP + 120_000, 500, GAP));
        assertFalse(s.add(10, 1, GAP), "never out of order");
        assertEquals(3, s.size());
    }

    @Test
    void pruneKeepsTheBalanceAtTheWindowStart() {
        BankSamples s = new BankSamples();
        s.add(0, 100, GAP);
        s.add(H, 300, GAP);
        s.add(2 * H, 200, GAP);
        s.add(3 * DAY, 400, GAP);
        assertEquals(2, s.prune(3 * DAY + H, DAY));
        assertEquals(2 * H, s.oldest());
        assertEquals(OptionalLong.of(200), s.lowest(3 * DAY + H, DAY));
    }
}

package net.skybornmc.skyblock.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF5: rescan chunk copies stop for the tick once the budget is used, but at least one runs every tick. */
class TickBudgetTest {

    private static final long MS = 1_000_000L;

    @Test
    void firstWorkOfATickIsAlwaysAllowed() {
        TickBudget b = new TickBudget();
        assertTrue(b.allow(1, 2 * MS));
        b.spent(1, 50 * MS); // one very slow copy
        assertFalse(b.allow(1, 2 * MS), "budget used up");
        assertTrue(b.allow(2, 2 * MS), "next tick starts fresh");
        assertEquals(0, b.count(2));
    }

    @Test
    void allowsWorkUntilTheBudgetIsSpent() {
        TickBudget b = new TickBudget();
        int done = 0;
        while (b.allow(7, 2 * MS) && done < 100) {
            b.spent(7, MS / 2);
            done++;
        }
        assertEquals(4, done, "4 x 0.5 ms in a 2 ms budget");
        assertEquals(4, b.count(7));
        b.spent(8, -5);
        assertTrue(b.allow(8, 1), "negative times never count");
    }
}

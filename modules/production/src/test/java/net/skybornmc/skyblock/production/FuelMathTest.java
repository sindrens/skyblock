package net.skybornmc.skyblock.production;

import net.skybornmc.skyblock.common.production.FuelState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Island Fuel rules (PHASE-7 M7.1): state machine, tank cap, burn with the efficiency cap, cells, time text. */
class FuelMathTest {

    private static final long MIN = FuelMath.MINUTE;
    private static final long H = FuelMath.HOUR;

    // ------------------------------------------------------------------ state machine

    @Test
    void activeIslandEarnsOneMinutePerMinute() {
        FuelMath.Step s = FuelMath.step(10 * MIN, 12 * H, FuelState.ASLEEP, true, 1.0, MIN);
        assertEquals(11 * MIN, s.fuelMs());
        assertEquals(FuelState.ACTIVE, s.state());
        assertFalse(s.fellAsleep());
    }

    @Test
    void noActiveMemberBurnsAndStaysFueledWhileFuelIsLeft() {
        FuelMath.Step s = FuelMath.step(2 * H, 12 * H, FuelState.ACTIVE, false, 1.0, 30 * MIN);
        assertEquals(90 * MIN, s.fuelMs());
        assertEquals(FuelState.FUELED, s.state());
        assertFalse(s.fellAsleep());
    }

    @Test
    void emptyTankFallsAsleepOnceAndNeverGoesNegative() {
        FuelMath.Step s = FuelMath.step(5 * MIN, 12 * H, FuelState.FUELED, false, 1.0, 10 * MIN);
        assertEquals(0, s.fuelMs());
        assertEquals(FuelState.ASLEEP, s.state());
        assertTrue(s.fellAsleep());
        FuelMath.Step again = FuelMath.step(s.fuelMs(), 12 * H, s.state(), false, 1.0, 10 * MIN);
        assertEquals(FuelState.ASLEEP, again.state());
        assertFalse(again.fellAsleep(), "told only once");
    }

    @Test
    void anAsleepIslandWakesWhenAMemberPlays() {
        FuelMath.Step s = FuelMath.step(0, 12 * H, FuelState.ASLEEP, true, 1.0, MIN);
        assertEquals(FuelState.ACTIVE, s.state());
        assertEquals(MIN, s.fuelMs());
    }

    @Test
    void negativeElapsedChangesNothing() {
        FuelMath.Step s = FuelMath.step(H, 12 * H, FuelState.FUELED, false, 1.0, -5 * MIN);
        assertEquals(H, s.fuelMs());
    }

    // ------------------------------------------------------------------ tank

    @Test
    void tankIsTwelveHoursPlusTwoPerLevel() {
        assertEquals(12 * H, FuelMath.capacityMs(12, 2, 0));
        assertEquals(20 * H, FuelMath.capacityMs(12, 2, 4));
        assertEquals(36 * H, FuelMath.capacityMs(12, 2, 12));
        assertEquals(12 * H, FuelMath.capacityMs(12, 2, -3));
    }

    @Test
    void earningStopsAtTheTankAndAShrunkTankKeepsItsFuel() {
        assertEquals(12 * H, FuelMath.step(12 * H - 30_000, 12 * H, FuelState.ACTIVE, true, 1.0, MIN).fuelMs());
        assertEquals(14 * H, FuelMath.step(14 * H, 12 * H, FuelState.ACTIVE, true, 1.0, MIN).fuelMs(), "never taken away by a smaller cap");
    }

    @Test
    void grantsFillAsMuchAsFitsAndAFullTankRefuses() {
        assertEquals(15 * MIN, FuelMath.grantable(H, 12 * H, 15 * MIN));
        assertEquals(10 * MIN, FuelMath.grantable(12 * H - 10 * MIN, 12 * H, 15 * MIN));
        assertEquals(0, FuelMath.grantable(12 * H, 12 * H, 15 * MIN));
        assertEquals(0, FuelMath.grantable(H, 12 * H, 0));
    }

    @Test
    void aCellIsOnlyUsedWhenItFitsCompletely() {
        assertTrue(FuelMath.cellFits(9 * H, 12 * H, 3 * H));
        assertFalse(FuelMath.cellFits(9 * H + MIN, 12 * H, 3 * H));
        assertFalse(FuelMath.cellFits(0, 12 * H, 0));
    }

    // ------------------------------------------------------------------ burn and efficiency

    @Test
    void efficiencyIsCappedAtFortyPercent() {
        assertEquals(30, FuelMath.efficiency(30, 40));
        assertEquals(40, FuelMath.efficiency(30 + 10 + 5, 40), "mastery 30 + achievements 10 + more: capped");
        assertEquals(0, FuelMath.efficiency(-5, 40));
        assertEquals(0, FuelMath.efficiency(Double.NaN, 40));
    }

    @Test
    void efficiencyMakesFuelLastLonger() {
        assertEquals(1.0, FuelMath.burnRate(1.0, 0), 1e-9);
        assertEquals(1 / 1.2, FuelMath.burnRate(1.0, 20), 1e-9);
        double capped = FuelMath.burnRate(1.0, FuelMath.efficiency(80, 40));
        assertEquals(1 / 1.4, capped, 1e-9);
        // 14 h of fuel at the cap lasts 19.6 h: after 14 h of burning 4 h are left
        FuelMath.Step s = FuelMath.step(14 * H, 36 * H, FuelState.FUELED, false, capped, 14 * H);
        assertEquals(4 * H, s.fuelMs(), MIN);
        assertEquals(1.0, FuelMath.burnRate(0, 0), 1e-9, "a broken base rate falls back to 1.0");
    }

    // ------------------------------------------------------------------ cells and text

    @Test
    void cellsSplitLargestFirstWithTheRestAsOneSmallCell() {
        long[] sizes = FuelMath.cellSizes(60, 180, 360);
        assertEquals(List.of(360L), FuelMath.splitIntoCells(360, sizes, 16));
        assertEquals(List.of(180L, 60L), FuelMath.splitIntoCells(240, sizes, 16));
        assertEquals(List.of(60L, 60L), FuelMath.splitIntoCells(120, sizes, 16));
        assertEquals(List.of(60L), FuelMath.splitIntoCells(15, sizes, 16), "at least one cell");
        assertEquals(List.of(360L, 360L), FuelMath.splitIntoCells(100_000, sizes, 2), "capped number of cells");
        assertEquals(List.of(), FuelMath.splitIntoCells(60, new long[0], 16));
    }

    @Test
    void cellSizesAreSortedAndCleaned() {
        assertEquals(List.of(360L, 60L), java.util.Arrays.stream(FuelMath.cellSizes(60, 360, 60, 0)).boxed().toList());
    }

    @Test
    void timeReadsInHoursAndMinutes() {
        assertEquals("7h 20m", FuelMath.time(7 * H + 20 * MIN + 59_000));
        assertEquals("36h", FuelMath.time(36 * H));
        assertEquals("45m", FuelMath.time(45 * MIN));
        assertEquals("0m", FuelMath.time(-1));
        assertEquals("0.71", FuelMath.rate(1 / 1.4));
        assertEquals("1", FuelMath.rate(1.0));
    }
}

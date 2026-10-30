package net.skybornmc.skyblock.economy.sell;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dynamic drift and recovery over time, reservations, catch-up after a restart, the rolling volume. */
class PriceBookTest {

    private static final long MIN = 60_000L;
    private static final long T0 = 1_800_000_000_000L;

    private DynamicSettings settings(double impact) {
        DynamicSettings d = DynamicSettings.DEFAULTS;
        return new DynamicSettings(true, d.floor(), d.ceiling(), d.recovery(), d.intervalMinutes(), d.target(), impact,
                d.minImpact(), Math.max(impact, d.maxImpact()), d.minVolume(), d.quietMinutes(), d.driftPercentPerDay(),
                d.trendThresholdPercent(), d.catchUpMaxHours(), d.hotEnabled(), d.hotPercent());
    }

    @Test
    void saleLowersThenRecoversTowardBase() {
        PriceBook book = new PriceBook(() -> settings(0.001));
        book.ensure(List.of("coal"));
        book.reserve(Map.of("coal", 100L), T0);
        assertEquals(0.9, book.get("coal").multiplier(), 1e-9);
        assertEquals(100, book.get("coal").volume().total(T0));
        for (int i = 1; i <= 6; i++) {
            book.step(T0 + i * 10 * MIN);
        }
        assertEquals(1 - 0.1 * Math.pow(0.98, 6), book.get("coal").multiplier(), 1e-9);
    }

    @Test
    void floorAndCeilingHold() {
        PriceBook book = new PriceBook(() -> settings(0.01));
        book.reserve(Map.of("coal", 1_000_000L), T0);
        assertEquals(0.4, book.get("coal").multiplier(), 1e-9);
        PriceBook quiet = new PriceBook(() -> settings(0.01));
        quiet.ensure(List.of("wheat"));
        for (int i = 0; i < 144 * 30; i++) {
            quiet.step(T0 + i * 10 * MIN);
        }
        assertEquals(1.5, quiet.get("wheat").multiplier(), 1e-9);
    }

    @Test
    void unsoldItemsDriftOnlyAfterTheQuietTime() {
        PriceBook book = new PriceBook(() -> settings(0.0));
        book.reserve(Map.of("coal", 1L), T0); // impact 0: stays at base, but counts as a sale
        book.step(T0 + 10 * MIN);
        assertEquals(1.0, book.get("coal").multiplier(), 1e-12);
        book.step(T0 + 70 * MIN); // 70 min without a sale > 60 quiet minutes
        assertTrue(book.get("coal").multiplier() > 1.0);
    }

    @Test
    void undoGivesTheReservationBack() {
        PriceBook book = new PriceBook(() -> settings(0.001));
        book.ensure(List.of("coal"));
        PriceBook.Reservation r = book.reserve(Map.of("coal", 200L), T0);
        assertEquals(0.8, book.get("coal").multiplier(), 1e-9);
        book.undo(r);
        assertEquals(1.0, book.get("coal").multiplier(), 1e-9);
        assertEquals(0, book.get("coal").volume().total(T0));
        assertEquals(0, book.get("coal").soldTotal());
    }

    @Test
    void recomputeAdaptsImpactAndSetsTrendReference() {
        PriceBook book = new PriceBook(() -> settings(1e-6));
        book.reserve(Map.of("coal", 1_909L * 10 * 24), T0);
        double after = book.get("coal").multiplier();
        book.recompute(T0 + MIN);
        PriceState s = book.get("coal");
        assertEquals(0.95e-6, s.impact(), 0.01e-6);
        assertEquals(after, s.hourAgo(), 1e-12);
    }

    @Test
    void loadCatchesUpMissedSteps() {
        PriceBook book = new PriceBook(() -> settings(1e-6));
        PriceState saved = new PriceState("coal", 0.5, 1e-6, T0, 0.5, VolumeWindow.EMPTY, 10);
        book.load(List.of(saved), Map.of("coal", T0), T0 + 60 * MIN); // off for one hour = 6 steps
        assertEquals(1 - 0.5 * Math.pow(0.98, 6), book.get("coal").multiplier(), 1e-9);
        // A week off: back to base, then quiet → drifted above base. Capped at catch-up-max-hours (168 h).
        PriceBook week = new PriceBook(() -> settings(1e-6));
        week.load(List.of(saved), Map.of("coal", T0), T0 + 168L * 60 * MIN);
        PriceBook longer = new PriceBook(() -> settings(1e-6));
        longer.load(List.of(saved), Map.of("coal", T0), T0 + 1000L * 60 * MIN);
        assertTrue(week.get("coal").multiplier() > 1.0);
        assertTrue(week.get("coal").multiplier() <= 1.5);
        assertEquals(week.get("coal").multiplier(), longer.get("coal").multiplier(), 1e-12);
    }

    @Test
    void dirtyStatesAreTakenOnce() {
        PriceBook book = new PriceBook(() -> settings(1e-6));
        book.ensure(List.of("coal", "wheat"));
        assertEquals(2, book.takeDirty().size());
        assertEquals(0, book.takeDirty().size());
        book.reserve(Map.of("coal", 5L), T0);
        List<PriceState> dirty = book.takeDirty();
        assertEquals(1, dirty.size());
        book.markDirty(dirty);
        assertEquals(1, book.takeDirty().size());
    }

    @Test
    void volumeWindowRollsOverTwentyFourHours() {
        long hour = 3_600_000L;
        VolumeWindow v = VolumeWindow.EMPTY.add(T0, 10).add(T0 + hour, 5);
        assertEquals(15, v.total(T0 + hour));
        assertEquals(5, v.total(T0 + 24 * hour));       // the first hour left the window
        assertEquals(0, v.total(T0 + 30 * hour));
        VolumeWindow back = VolumeWindow.decode(v.encode());
        assertEquals(v.total(T0 + hour), back.total(T0 + hour));
        assertEquals(VolumeWindow.EMPTY, VolumeWindow.decode("garbage;1:x;"));
        assertEquals(0, VolumeWindow.EMPTY.add(T0, -5).total(T0)); // never negative
    }
}

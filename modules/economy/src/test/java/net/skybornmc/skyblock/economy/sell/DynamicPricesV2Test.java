package net.skybornmc.skyblock.economy.sell;

import net.skybornmc.skyblock.common.sell.SellPrice;
import net.skybornmc.skyblock.common.sell.SellQuote;
import net.skybornmc.skyblock.common.sell.Trend;
import net.skybornmc.skyblock.economy.EconomyConfig;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dynamic prices v2 (docs/plans/DYNAMIC_PRICES_V2.md): day price per island, weekly drift, day counts, quotes. */
class DynamicPricesV2Test {

    private static final DayPrice D = DayPrice.DEFAULTS;
    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    @Test
    void theFirst3000PayFullThenOnePercentPer100DownTo60() {
        assertEquals(1.0, D.factor(0));
        assertEquals(1.0, D.factor(2_999));
        assertEquals(0.99, D.factor(3_000), 1e-9);
        assertEquals(0.99, D.factor(3_099), 1e-9);
        assertEquals(0.98, D.factor(3_100), 1e-9);
        assertEquals(0.60, D.factor(6_900), 1e-9);
        assertEquals(0.60, D.factor(7_000), 1e-9);
        assertEquals(0.60, D.factor(1_000_000_000L), 1e-9);
        assertEquals(100, D.percent(0));
        assertEquals(85, D.percent(4_450));
        assertEquals(500, D.fullLeft(2_500));
        assertEquals(0, D.fullLeft(9_000));
    }

    @Test
    void theSumMatchesItemByItem() {
        long[][] cases = {{0, 64}, {2_950, 100}, {3_000, 1}, {3_050, 333}, {6_850, 500}, {0, 10_000}, {12_000, 64}};
        for (long[] c : cases) {
            double slow = 0;
            for (long i = c[0]; i < c[0] + c[1]; i++) {
                slow += D.factor(i);
            }
            assertEquals(slow, D.sum(c[0], c[1]), 1e-6, "start " + c[0] + " amount " + c[1]);
        }
        assertEquals(0, D.sum(10, 0));
        assertEquals(3_000, D.sum(0, 3_000), 1e-9, "the free amount pays exactly 100%");
        DayPrice off = new DayPrice(false, 3_000, 100, 1, 60, 50, 4);
        assertEquals(10_000, off.sum(50_000, 10_000), 1e-9);
        assertEquals(1.0, off.factor(1_000_000));
    }

    @Test
    void halfTheExcessCarriesIntoTheNextDay() {
        assertEquals(0, D.rollover(2_000, 1), "a normal day resets fully");
        assertEquals(500, D.rollover(4_000, 1));
        assertEquals(0, D.rollover(4_000, 2));
        assertEquals(4_000, D.rollover(4_000, 0));
        assertEquals(498_500, D.rollover(1_000_000, 1));
        assertEquals(0, D.rollover(Long.MAX_VALUE, 100));
        DayPrice full = new DayPrice(true, 3_000, 100, 1, 60, 0, 4);
        assertEquals(0, full.rollover(50_000, 1));
    }

    @Test
    void theSalesDayStartsAt4Oslo() {
        long before = ZonedDateTime.of(2026, 11, 7, 3, 59, 0, 0, OSLO).toInstant().toEpochMilli();
        long after = ZonedDateTime.of(2026, 11, 7, 4, 0, 0, 0, OSLO).toInstant().toEpochMilli();
        assertEquals(D.day(before, OSLO) + 1, D.day(after, OSLO));
        assertEquals(java.time.LocalDate.of(2026, 11, 7).toEpochDay(), D.day(after, OSLO));
    }

    @Test
    void weeklyDriftIsInStepsSameForEveryoneAndChangesOnMonday() {
        WeeklyDrift w = WeeklyDrift.DEFAULTS;
        int week = WeeklyDrift.week(ZonedDateTime.of(2026, 11, 8, 23, 59, 0, 0, OSLO).toInstant().toEpochMilli(), OSLO);
        int monday = WeeklyDrift.week(ZonedDateTime.of(2026, 11, 9, 0, 0, 0, 0, OSLO).toInstant().toEpochMilli(), OSLO);
        assertEquals(202645, week);
        assertEquals(202646, monday);
        Set<Double> seen = new HashSet<>();
        for (String item : List.of("wheat", "carrot", "potato", "coal", "iron_ingot", "gold_ingot", "diamond", "cod",
                "salmon", "bone", "string", "melon_slice", "pumpkin", "cactus", "sugar_cane", "kelp", "cobblestone",
                "redstone", "lapis_lazuli", "emerald", "rotten_flesh", "gunpowder", "ender_pearl", "blaze_rod")) {
            double p = w.percent(week, item);
            assertTrue(p >= -10 && p <= 10, item + " " + p);
            assertEquals(0, Math.round(p * 1000) % 2000, "a multiple of 2%: " + p);
            assertEquals(p, w.percent(week, item), "the same every time");
            seen.add(p);
        }
        assertTrue(seen.size() >= 5, "items spread over the range: " + seen);
        boolean changed = false;
        for (String item : List.of("wheat", "carrot", "potato", "coal", "diamond")) {
            changed |= w.percent(week, item) != w.percent(monday, item);
        }
        assertTrue(changed, "a new week moves prices");
        assertEquals(1.10, w.maxFactor(), 1e-9);
        assertEquals(0, new WeeklyDrift(false, 10, 2, 1).percent(week, "wheat"));
    }

    @Test
    void dayCountsShareAnIslandAndFollowTheHigherScope() {
        DayCounts c = new DayCounts(() -> D);
        long day = 20_000;
        List<String> alice = List.of("p:alice", "i:one");
        List<String> bob = List.of("p:bob", "i:one");
        c.reserve(alice, Map.of("wheat", 2_000L), day);
        c.reserve(bob, Map.of("wheat", 1_500L), day);
        assertEquals(3_500, c.count(alice, "wheat", day), "the island counts both members");
        assertEquals(2_000, c.count("p:alice", "wheat", day));
        // Alice moves to another island: her own count still holds
        assertEquals(2_000, c.count(List.of("p:alice", "i:two"), "wheat", day));
        DayCounts.Reservation r = c.reserve(alice, Map.of("wheat", 100L), day);
        c.undo(r);
        assertEquals(3_500, c.count(alice, "wheat", day), "a failed sale gives its items back");
        // the next day: half of the excess over 3,000 carries over
        assertEquals(250, c.count(alice, "wheat", day + 1));
        assertEquals(0, c.count("p:bob", "wheat", day + 1));
        assertTrue(c.takeDirty().size() >= 3);
        assertTrue(c.takeDirty().isEmpty());
    }

    @Test
    void loadSkipsRowsThatRolledToNothing() {
        DayCounts c = new DayCounts(() -> D);
        int kept = c.load(List.of(new DayCounts.Row("i:a", "wheat", 10, 2_000), new DayCounts.Row("i:a", "coal", 10, 9_000),
                new DayCounts.Row("i:a", "cod", 11, 100)), 11);
        assertEquals(2, kept);
        assertEquals(3_000, c.count("i:a", "coal", 11));
        assertEquals(100, c.count("i:a", "cod", 11));
        assertEquals(0, c.count("i:a", "wheat", 11));
    }

    @Test
    void aQuotePaysTheDayFactorItemByItem() {
        SellPrice wheat = new SellPrice("wheat", "crops", 100, 1.06, false, 1.0, Trend.RISING);
        DynamicSettings legacyOff = new EconomyConfig().dynamicSettings();
        assertTrue(!legacyOff.enabled(), "the old model is off by default");
        SellQuote fresh = SaleCalculator.quote(List.of("wheat"), List.of(64L), k -> Optional.of(wheat), k -> 0.0, k -> 0L, D,
                legacyOff, 0, 300);
        assertEquals(6_784, fresh.subtotal(), "64 at 106 each");
        assertEquals(PriceMath.roundDown(100 * 1.0 * 1.06 * 64), fresh.subtotal());
        SellQuote late = SaleCalculator.quote(List.of("wheat"), List.of(200L), k -> Optional.of(wheat), k -> 0.0, k -> 3_000L, D,
                legacyOff, 0, 300);
        assertEquals(PriceMath.roundDown(100 * 1.0 * 1.06 * D.sum(3_000, 200)), late.subtotal());
        assertEquals(20_882, late.subtotal(), 1.0, "100 at 99% and 100 at 98% of 106");
        assertEquals(1.06 * 0.99, late.lines().get(0).price().multiplier(), 1e-9, "the line shows the price of its first item");
        assertNotEquals(fresh.lines().get(0).price().multiplier(), late.lines().get(0).price().multiplier());
    }

    @Test
    void theWeekIsTheMultiplierWhenTheOldModelIsOff() {
        DynamicSettings off = new EconomyConfig().dynamicSettings();
        SellCatalog.Entry e = new SellCatalog.Entry("wheat", "crops", 100, true);
        SellPrice p = SaleCalculator.price(e, PriceState.fresh("wheat", 1e-6), null, off, 0.94);
        assertEquals(0.94, p.multiplier(), 1e-9);
        assertEquals(Trend.FALLING, p.trend());
        assertEquals(94, p.unit(), 1e-9);
        SellPrice hot = SaleCalculator.price(e, PriceState.fresh("wheat", 1e-6), "wheat", off, 1.0);
        assertEquals(125, hot.unit(), 1e-9);
        assertEquals(Trend.STEADY, hot.trend());
        assertEquals(100 * 1.1 * 1.25 * 4, SaleCalculator.maxUnit(100, off, WeeklyDrift.DEFAULTS, 300), 1e-9);
    }

    @Test
    void configDefaultsMatchThePlan() {
        EconomyConfig c = new EconomyConfig();
        assertEquals(D, c.dayPrice());
        assertEquals(WeeklyDrift.DEFAULTS, c.weeklyDrift());
    }
}

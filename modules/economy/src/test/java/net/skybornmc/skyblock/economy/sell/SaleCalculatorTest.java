package net.skybornmc.skyblock.economy.sell;

import net.skybornmc.skyblock.common.sell.SellPrice;
import net.skybornmc.skyblock.common.sell.SellQuote;
import net.skybornmc.skyblock.common.sell.Trend;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Totals: same items added together, bonus summed then capped once (+300%), hot item, unsellable stacks, rounding. */
class SaleCalculatorTest {

    private static final DynamicSettings S = DynamicSettings.DEFAULTS;
    private static final Map<String, SellPrice> PRICES = Map.of(
            "coal", new SellPrice("coal", "ores", 10, 1.0, false, 1.0, Trend.STEADY),
            "wheat", new SellPrice("wheat", "crops", 10, 0.8, false, 1.0, Trend.FALLING),
            "cobblestone", new SellPrice("cobblestone", "ores", 1, 0.4, false, 1.0, Trend.STEADY),
            "diamond", new SellPrice("diamond", "ores", 61, 1.0, true, 1.25, Trend.RISING));

    private static SellQuote quote(List<String> keys, List<Long> amounts, double bonus) {
        return SaleCalculator.quote(keys, amounts, k -> Optional.ofNullable(PRICES.get(k)), k -> 0.0, S, bonus, 300);
    }

    @Test
    void maxUnitIsCeilingTimesHotTimesCap() {
        // Defaults: ceiling 150%, Hot Item +25%, cap +300% -> x7.5.
        assertEquals(75.0, SaleCalculator.maxUnit(10, S, 300), 1e-9);
        assertEquals(10 * 1.5 * 1.25, SaleCalculator.maxUnit(10, S, 0), 1e-9);
        DynamicSettings flat = new DynamicSettings(false, 0.4, 1.5, 0.02, 10, 0.85, 1e-6, 1e-8, 1e-4, 1_000, 60, 10, 0.5, 168, false, 25);
        assertEquals(40.0, SaleCalculator.maxUnit(10, flat, 300), 1e-9);
    }

    @Test
    void stacksOfTheSameItemAreAddedTogether() {
        SellQuote q = quote(List.of("coal", "wheat", "coal"), List.of(64L, 10L, 36L), 0);
        assertEquals(2, q.lines().size());
        assertEquals("coal", q.lines().get(0).price().itemKey());
        assertEquals(100, q.lines().get(0).amount());
        assertEquals(110, q.items());
        assertEquals(100 * 10 + 10 * 8, q.subtotal());
        assertEquals(q.subtotal(), q.total());
    }

    @Test
    void bonusIsAddedUpThenCappedOnce() {
        SellQuote q = quote(List.of("coal"), List.of(100L), 50);
        assertEquals(1_000, q.subtotal());
        assertEquals(1_500, q.total());
        assertFalse(q.capped());
        // +350% total bonus (e.g. 150 + 100 + 100) → capped at +300% (x4).
        SellQuote capped = quote(List.of("coal"), List.of(100L), 350);
        assertEquals(4_000, capped.total());
        assertEquals(300, capped.cappedBonusPercent(), 1e-9);
        assertEquals(350, capped.bonusPercent(), 1e-9);
        assertTrue(capped.capped());
        // Negative bonuses never make a negative price.
        assertEquals(0, quote(List.of("coal"), List.of(100L), -150).total());
    }

    @Test
    void hotItemChangesThePriceNotTheBonus() {
        SellQuote q = quote(List.of("diamond"), List.of(4L), 300);
        assertEquals(305, q.subtotal());           // 4 x 61 x 1.25
        assertEquals(1_220, q.total());            // x4, the cap never touches the hot boost
    }

    @Test
    void subtotalIsRoundedDownOnceNotPerItem() {
        // Cobblestone at 40%: $0.40 each. Per item that would be $0; together 64 x 0.4 = 25.6 → 25.
        SellQuote q = quote(List.of("cobblestone"), List.of(64L), 0);
        assertEquals(25, q.subtotal());
        SellQuote one = quote(List.of("cobblestone"), List.of(2L), 0);
        assertEquals(0, one.total()); // too cheap: the service refuses a $0 sale
    }

    @Test
    void unsellableStacksAreListedAndNotPaid() {
        SellQuote q = quote(Arrays.asList("coal", null, "dirt", "wheat"), List.of(1L, 5L, 5L, 0L), 0);
        assertEquals(List.of(1, 2, 3), q.unsellable());
        assertEquals(1, q.lines().size());
        assertEquals(10, q.total());
        assertTrue(quote(List.of("dirt"), List.of(5L), 0).isEmpty());
    }

    @Test
    void impactLowersBigLinesAndReservationAmountsMatch() {
        SellQuote q = SaleCalculator.quote(List.of("coal", "coal"), List.of(64L, 64L), k -> Optional.ofNullable(PRICES.get(k)),
                k -> 0.001, S, 0, 300);
        double expected = 10 * PriceMath.saleFactor(1.0, 0.001, 128, S.floor());
        assertEquals(expected, q.lines().get(0).value(), 1e-9);
        assertEquals((long) Math.floor(expected), q.subtotal());
        assertEquals(Map.of("coal", 128L), SaleCalculator.amounts(q));
    }

    @Test
    void priceFromEntryAndState() {
        SellCatalog.Entry entry = new SellCatalog.Entry("coal", "ores", 10, true);
        PriceState state = new PriceState("coal", 0.9, 1e-6, 0, 1.0, VolumeWindow.EMPTY, 0);
        SellPrice p = SaleCalculator.price(entry, state, "coal", S);
        assertTrue(p.hot());
        assertEquals(10 * 0.9 * 1.25, p.unit(), 1e-9);
        assertEquals(12.5, p.percentFromBase(), 1e-9);
        assertEquals(Trend.FALLING, p.trend());
        SellPrice notHot = SaleCalculator.price(entry, state, "wheat", S);
        assertEquals(-10, notHot.percentFromBase(), 1e-9);
        // Dynamic prices off: always base.
        DynamicSettings off = new DynamicSettings(false, 0.4, 1.5, 0.02, 10, 0.85, 1e-6, 1e-8, 1e-4, 1000, 60, 10, 0.5, 168,
                false, 25);
        SellPrice base = SaleCalculator.price(entry, state, "coal", off);
        assertEquals(1.0, base.multiplier(), 1e-12);
        assertFalse(base.hot());
    }
}

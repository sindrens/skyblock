package net.skybornmc.skyblock.economy.sell;

import net.skybornmc.skyblock.common.economy.MultiplierCap;
import net.skybornmc.skyblock.common.sell.SellPrice;
import net.skybornmc.skyblock.common.sell.SellQuote;
import net.skybornmc.skyblock.common.sell.Trend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Turns priced items into a {@link SellQuote} (M3.4). Pure, unit-tested.
 *
 * <ol>
 *   <li>Stacks of the same item are added together (one line per item key, first-seen order).</li>
 *   <li>Each line's value follows the dynamic price item by item ({@link PriceMath#lineValue}).</li>
 *   <li>The subtotal is rounded down once.</li>
 *   <li>The sell bonus (every source added up) is capped once with {@link MultiplierCap} (+300%) and applied to the
 *       subtotal, rounded down, never overflowing.</li>
 * </ol>
 */
public final class SaleCalculator {

    private SaleCalculator() {
    }

    /** The price of one item from its catalog entry and state. */
    public static SellPrice price(SellCatalog.Entry entry, PriceState state, String hotKey, DynamicSettings s) {
        boolean hot = s.hotEnabled() && entry.key().equals(hotKey);
        double m = s.enabled() ? PriceMath.clamp(state.multiplier(), s.floor(), s.ceiling()) : 1.0;
        return new SellPrice(entry.key(), entry.category(), entry.base(), m, hot, hot ? s.hotFactor() : 1.0,
                PriceMath.trend(m, state.hourAgo(), s.trendThresholdPercent()));
    }

    /**
     * Dynamic prices v2: the price of one item for everyone this week. The old global model ({@code sell.dynamic}) is
     * used instead while it is still switched on; otherwise the multiplier is this week's drift ({@link WeeklyDrift}).
     */
    public static SellPrice price(SellCatalog.Entry entry, PriceState state, String hotKey, DynamicSettings s, double weekFactor) {
        if (s.enabled()) {
            return price(entry, state, hotKey, s);
        }
        boolean hot = s.hotEnabled() && entry.key().equals(hotKey);
        double m = weekFactor > 0 && Double.isFinite(weekFactor) ? weekFactor : 1.0;
        Trend trend = m > 1.0 + 1e-9 ? Trend.RISING : m < 1.0 - 1e-9 ? Trend.FALLING : Trend.STEADY;
        return new SellPrice(entry.key(), entry.category(), entry.base(), m, hot, hot ? s.hotFactor() : 1.0, trend);
    }

    /** {@code p} for a seller who already sold {@code count} today: the day factor of the next item folded into the multiplier. */
    public static SellPrice forSeller(SellPrice p, DayPrice day, long count) {
        double f = day.factor(count);
        return f == 1.0 ? p : new SellPrice(p.itemKey(), p.category(), p.base(), p.multiplier() * f, p.hot(), p.hotFactor(), p.trend());
    }

    /**
     * The highest price one item can reach: base x ceiling (when dynamic prices are on) x the Hot Item factor x
     * (1 + cap / 100). Shops price above this so buying and selling back can never make money.
     */
    public static double maxUnit(double base, DynamicSettings s, double capPercent) {
        double ceiling = s.enabled() ? Math.max(1.0, s.ceiling()) : 1.0;
        return base * ceiling * s.hotFactor() * (1.0 + Math.max(0.0, capPercent) / 100.0);
    }

    /** Dynamic prices v2: like {@link #maxUnit(double, DynamicSettings, double)} with the weekly drift's top (x1.10). */
    public static double maxUnit(double base, DynamicSettings s, WeeklyDrift week, double capPercent) {
        double ceiling = s.enabled() ? Math.max(1.0, s.ceiling()) : Math.max(1.0, week.maxFactor());
        return base * ceiling * s.hotFactor() * (1.0 + Math.max(0.0, capPercent) / 100.0);
    }

    /**
     * Dynamic prices v2 quote: {@code prices} gives this week's price (drift, Hot Item), {@code dayCount} how many of an
     * item the seller's island sold today; each line pays the day factor item by item ({@link DayPrice#sum}). While the
     * old global model is on, this is {@link #quote(List, List, Function, Function, DynamicSettings, double, double)}.
     */
    public static SellQuote quote(List<String> keys, List<Long> amounts, Function<String, Optional<SellPrice>> prices,
                                  Function<String, Double> impacts, java.util.function.ToLongFunction<String> dayCount, DayPrice day,
                                  DynamicSettings s, double bonusPercent, double capPercent) {
        if (s.enabled()) {
            return quote(keys, amounts, prices, impacts, s, bonusPercent, capPercent);
        }
        Map<String, Long> totals = new LinkedHashMap<>();
        Map<String, SellPrice> priced = new LinkedHashMap<>();
        List<Integer> unsellable = new ArrayList<>();
        group(keys, amounts, prices, totals, priced, unsellable);
        List<SellQuote.Line> lines = new ArrayList<>();
        double money = 0;
        for (Map.Entry<String, Long> e : totals.entrySet()) {
            SellPrice p = priced.get(e.getKey());
            long start = Math.max(0, dayCount.applyAsLong(e.getKey()));
            double value = p.base() <= 0 ? 0 : p.base() * p.hotFactor() * p.multiplier() * day.sum(start, e.getValue());
            lines.add(new SellQuote.Line(forSeller(p, day, start), e.getValue(), value));
            money += value;
        }
        long subtotal = PriceMath.roundDown(money);
        double capped = MultiplierCap.cappedBonus(bonusPercent, capPercent);
        long total = MultiplierCap.apply(subtotal, bonusPercent, capPercent);
        return new SellQuote(lines, subtotal, Double.isFinite(bonusPercent) ? bonusPercent : 0, capped, total, unsellable);
    }

    private static void group(List<String> keys, List<Long> amounts, Function<String, Optional<SellPrice>> prices,
                              Map<String, Long> totals, Map<String, SellPrice> priced, List<Integer> unsellable) {
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            long amount = i < amounts.size() && amounts.get(i) != null ? amounts.get(i) : 0;
            Optional<SellPrice> price = key == null ? Optional.empty() : priced.containsKey(key) ? Optional.of(priced.get(key))
                    : prices.apply(key);
            if (price.isEmpty() || amount <= 0) {
                unsellable.add(i);
                continue;
            }
            priced.putIfAbsent(key, price.get());
            totals.merge(key, amount, SaleCalculator::add);
        }
    }

    /**
     * @param keys         one item key per stack (null = this stack can't be sold); index = stack index
     * @param amounts      amount per stack (same indexes)
     * @param prices       today's price per key (empty = not sellable)
     * @param impacts      impact per key (multiplier lost per item sold)
     * @param bonusPercent summed sell bonus before the cap
     * @param capPercent   the economy's sell cap (+300)
     */
    public static SellQuote quote(List<String> keys, List<Long> amounts, Function<String, Optional<SellPrice>> prices,
                                  Function<String, Double> impacts, DynamicSettings s, double bonusPercent, double capPercent) {
        Map<String, Long> totals = new LinkedHashMap<>();
        Map<String, SellPrice> priced = new LinkedHashMap<>();
        List<Integer> unsellable = new ArrayList<>();
        group(keys, amounts, prices, totals, priced, unsellable);
        List<SellQuote.Line> lines = new ArrayList<>();
        double money = 0;
        for (Map.Entry<String, Long> e : totals.entrySet()) {
            SellPrice p = priced.get(e.getKey());
            double impact = s.enabled() ? Math.max(0, impacts.apply(e.getKey())) : 0;
            double value = PriceMath.lineValue(p.base(), p.hotFactor(), p.multiplier(), impact, e.getValue(), s.floor());
            lines.add(new SellQuote.Line(p, e.getValue(), value));
            money += value;
        }
        long subtotal = PriceMath.roundDown(money);
        double capped = MultiplierCap.cappedBonus(bonusPercent, capPercent);
        long total = MultiplierCap.apply(subtotal, bonusPercent, capPercent);
        return new SellQuote(lines, subtotal, Double.isFinite(bonusPercent) ? bonusPercent : 0, capped, total, unsellable);
    }

    /** Amount per key of a quote (for the price reservation). */
    public static Map<String, Long> amounts(SellQuote quote) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (SellQuote.Line line : quote.lines()) {
            out.merge(line.price().itemKey(), line.amount(), SaleCalculator::add);
        }
        return out;
    }

    private static long add(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? Long.MAX_VALUE : r;
    }
}

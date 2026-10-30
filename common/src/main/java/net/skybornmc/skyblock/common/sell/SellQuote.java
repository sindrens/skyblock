package net.skybornmc.skyblock.common.sell;

import java.util.List;

/**
 * What a set of items would sell for right now (M3.4): one line per item key, the subtotal at dynamic prices, and the
 * sell bonus (all sources added up, then capped once at {@code EconomyService#sellBonusCapPercent}, +300%).
 *
 * @param lines            one line per item key (same items from several stacks are added together)
 * @param subtotal         money before the bonus (rounded down once, never per item)
 * @param bonusPercent     the sum of every sell bonus source, before the cap
 * @param cappedBonusPercent the bonus that is actually paid ({@code min(cap, bonusPercent)}, never below -100)
 * @param total            money paid: {@code subtotal × (1 + cappedBonus / 100)}, rounded down
 * @param unsellable       indexes (into the stacks that were quoted) of stacks that can't be sold
 */
public record SellQuote(List<Line> lines, long subtotal, double bonusPercent, double cappedBonusPercent, long total,
                        List<Integer> unsellable) {

    /**
     * @param price  the price the first item of this line was quoted at
     * @param amount how many items
     * @param value  money for this line before the bonus (the price drops a little with every item sold, so a big line
     *               is worth slightly less than {@code amount × price.unit()})
     */
    public record Line(SellPrice price, long amount, double value) {
    }

    public SellQuote {
        lines = List.copyOf(lines);
        unsellable = List.copyOf(unsellable);
    }

    /** Whether the cap cut the bonus (menus show "capped"). */
    public boolean capped() {
        return cappedBonusPercent < bonusPercent;
    }

    /** Items in all lines. */
    public long items() {
        long sum = 0;
        for (Line line : lines) {
            sum += line.amount();
        }
        return sum;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}

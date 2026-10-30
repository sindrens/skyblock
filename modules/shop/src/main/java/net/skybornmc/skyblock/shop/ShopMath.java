package net.skybornmc.skyblock.shop;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.Function;

/**
 * The builder shop's numbers (M3.5). Pure, unit-tested.
 *
 * <ul>
 *   <li>Totals never overflow: a total above {@code Long.MAX_VALUE} is refused, not wrapped.</li>
 *   <li><b>Buy &gt; sell:</b> an item must cost more than the most it could ever bring with {@code /sell}, either sold as it
 *       is or turned back into what it {@code contains} (crafting it back, breaking it). Otherwise buying and selling
 *       would make money out of nothing.</li>
 * </ul>
 */
public final class ShopMath {

    private ShopMath() {
    }

    /** {@code unit × amount}, or empty if it does not fit in a long or the inputs are not positive. */
    public static OptionalLong total(long unit, long amount) {
        if (unit < 1 || amount < 1) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Math.multiplyExact(unit, amount));
        } catch (ArithmeticException e) {
            return OptionalLong.empty();
        }
    }

    /**
     * The most one item could ever bring with {@code /sell}: the larger of selling it as it is and selling what it turns
     * back into. 0 if neither can be sold.
     *
     * @param maxSell the highest sell price of one item by item id (empty = can't be sold)
     */
    public static double maxSellValue(String itemId, Map<String, Integer> contains, Function<String, OptionalDouble> maxSell) {
        double self = maxSell.apply(itemId).orElse(0.0);
        double parts = 0;
        for (Map.Entry<String, Integer> e : contains.entrySet()) {
            parts += maxSell.apply(e.getKey()).orElse(0.0) * e.getValue();
        }
        return Math.max(self, parts);
    }

    /** How many a balance can pay for at {@code price} each ("You can afford: N"); 0 for a free or broken price. */
    public static long affordable(long price, long balance) {
        return price <= 0 || balance <= 0 ? 0 : balance / price;
    }

    /** True if buying the item for {@code price} and selling it (or its parts) could ever pay {@code price} or more. */
    public static boolean undercutsSell(ShopCatalog.Item item, Function<String, OptionalDouble> maxSell) {
        return undercutsSell(item.id(), item.price(), item.contains(), maxSell);
    }

    public static boolean undercutsSell(String itemId, long price, Map<String, Integer> contains,
                                        Function<String, OptionalDouble> maxSell) {
        return price <= maxSellValue(itemId, contains, maxSell);
    }

    /**
     * How many a player may buy in one go: at most {@code configMax}, at most what fits in the inventory, and at most what
     * the total price allows without overflowing. 0 = nothing can be bought.
     */
    public static long maxAmount(long unit, long fits, long configMax) {
        if (unit < 1) {
            return 0;
        }
        long max = Math.max(0, Math.min(fits, configMax));
        return Math.min(max, Long.MAX_VALUE / unit);
    }

    /** Splits {@code amount} items into stack sizes of at most {@code maxStack} (for handing them out). */
    public static int[] stacks(long amount, int maxStack) {
        if (amount < 1 || maxStack < 1) {
            return new int[0];
        }
        long count = (amount + maxStack - 1) / maxStack;
        if (count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("too many stacks");
        }
        int[] out = new int[(int) count];
        long left = amount;
        for (int i = 0; i < out.length; i++) {
            out[i] = (int) Math.min(maxStack, left);
            left -= out[i];
        }
        return out;
    }
}

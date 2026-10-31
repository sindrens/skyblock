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

    /**
     * What one purchase really buys (SELL2 partial buys).
     *
     * @param amount  items to buy and charge for (0 = nothing: no room or a bad request)
     * @param partial true when fewer than requested fit ("Bought 40 of 64: no more room")
     */
    public record BuyPlan(int amount, boolean partial) {
    }

    /**
     * Plans a purchase of {@code requested} items when {@code fits} fit in the inventory and one purchase may buy at most
     * {@code configMax}: buys as many as fit (only those are charged), never more than asked, never more than the max.
     */
    public static BuyPlan plan(long requested, long fits, long configMax) {
        if (requested < 1 || configMax < 1) {
            return new BuyPlan(0, false);
        }
        long want = Math.min(requested, configMax);
        long room = Math.max(0, fits);
        long amount = Math.min(want, room);
        if (amount > Integer.MAX_VALUE) {
            amount = Integer.MAX_VALUE;
        }
        return new BuyPlan((int) amount, amount > 0 && amount < want);
    }

    // ------------------------------------------------------------------ Shop v2 buy page (GUI-3)

    /**
     * The quick amounts of the buy page by stack size (SHOP_DESIGN §4): {@code [1, 16, 64]} for items that stack to 64,
     * {@code [1, 8, 16]} for 16-stackers ("Buy 16" buys 8 there), only {@code [1]} for items that don't stack (buckets,
     * beds, tools).
     */
    public static int[] quickAmounts(int maxStack) {
        if (maxStack <= 1) {
            return new int[]{1};
        }
        int middle = maxStack >= 64 ? maxStack / 4 : Math.max(1, maxStack / 2);
        return middle <= 1 || middle >= maxStack ? new int[]{1, maxStack} : new int[]{1, middle, maxStack};
    }

    /**
     * "Fill inventory": as many as fit in the inventory and the balance pays for, at most {@code configMax}. 0 = nothing
     * (no room, no money or a broken price).
     */
    public static long fillAmount(long price, long balance, long fits, long configMax) {
        if (price < 1) {
            return 0;
        }
        return Math.max(0, Math.min(Math.min(fits, configMax), affordable(price, balance)));
    }

    /**
     * F14 big buys: true when {@code total} is more than {@code percent}% of {@code balance} (then a second click is
     * needed). A buy the balance can't pay is not "big" (it fails with "not enough money" instead).
     */
    public static boolean bigBuy(long total, long balance, int percent) {
        if (total < 1 || balance < 1 || total > balance || percent >= 100) {
            return false;
        }
        if (percent <= 0) {
            return true;
        }
        // total / balance > percent / 100, without overflow
        return java.math.BigInteger.valueOf(total).multiply(java.math.BigInteger.valueOf(100))
                .compareTo(java.math.BigInteger.valueOf(balance).multiply(java.math.BigInteger.valueOf(percent))) > 0;
    }

    /** Whole percent of the balance a buy takes ("That is 74% of your money"), 0-100. */
    public static int percentOf(long total, long balance) {
        if (total <= 0 || balance <= 0) {
            return 0;
        }
        if (total >= balance) {
            return 100;
        }
        return (int) java.math.BigInteger.valueOf(total).multiply(java.math.BigInteger.valueOf(100))
                .divide(java.math.BigInteger.valueOf(balance)).longValue();
    }

    /**
     * Hours a spawner needs to earn back its price ({@code incomePerHour} at full output, scaled by how well the next one
     * works, owner F2). Empty when it never pays back (0% output or no income).
     */
    public static OptionalDouble paybackHours(long price, long incomePerHour, int outputPercent) {
        if (price < 1 || incomePerHour < 1 || outputPercent < 1) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(price / (incomePerHour * (outputPercent / 100.0)));
    }

    /** Owner F2: how good a spawner buy is, by its payback time (green, yellow, red "Poor deal"). */
    public enum Payback {
        GOOD, OK, POOR
    }

    /** GOOD up to {@code goodHours}, OK up to {@code okHours}, POOR above (or when it never pays back). */
    public static Payback payback(OptionalDouble hours, long goodHours, long okHours) {
        if (hours.isEmpty()) {
            return Payback.POOR;
        }
        double h = hours.getAsDouble();
        return h <= goodHours ? Payback.GOOD : h <= Math.max(goodHours, okHours) ? Payback.OK : Payback.POOR;
    }
}

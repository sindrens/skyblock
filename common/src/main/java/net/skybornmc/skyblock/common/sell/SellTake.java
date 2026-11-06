package net.skybornmc.skyblock.common.sell;

/**
 * Which part of each matching stack a "sell N of this item" takes (GUI-P3 4.2). Pure, unit-tested.
 * Slots are taken in slot order (lowest first), the last one partly; {@code want <= 0} takes everything.
 */
public final class SellTake {

    private SellTake() {
    }

    /**
     * @param slotAmounts amounts of the matching stacks in slot order
     * @param want        how many to take; {@code <= 0} = all, above the total = all
     * @return how many to take from each stack (same length; never negative, never above a stack's amount)
     */
    public static int[] plan(int[] slotAmounts, long want) {
        int[] take = new int[slotAmounts == null ? 0 : slotAmounts.length];
        long left = want <= 0 ? Long.MAX_VALUE : want;
        for (int i = 0; i < take.length && left > 0; i++) {
            int have = Math.max(0, slotAmounts[i]);
            int t = (int) Math.min(have, left);
            take[i] = t;
            left -= t;
        }
        return take;
    }
}

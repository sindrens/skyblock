package net.skybornmc.skyblock.economy.sell;

import java.util.List;

/**
 * Pure stack moving for the sell chest (SELL2): put a stack into a row of slots the way vanilla shift-click does (top up
 * similar stacks first, then empty slots). No Bukkit types: {@code T} is the stack, {@link Ops} reads it. Items that go
 * back to a player never touch the ender chest or the floor: {@code SellFeature#giveBack} fills the inventory and sends the
 * rest to {@code /box} (ECON-SELL-SHOP-AH A5).
 */
final class ChestFill {

    private ChestFill() {
    }

    /** How to read and copy a stack. */
    interface Ops<T> {
        boolean similar(T a, T b);

        int amount(T stack);

        int maxStack(T stack);

        /** A copy of {@code stack} with this amount (1..max). */
        T withAmount(T stack, int amount);
    }

    /**
     * Puts {@code stack} into {@code slots} (null = empty; the list is changed in place). Similar stacks are topped up
     * first, then empty slots are filled. Returns how many items did <b>not</b> fit (0 = all moved).
     */
    static <T> int insert(List<T> slots, T stack, Ops<T> ops) {
        if (stack == null) {
            return 0;
        }
        int left = ops.amount(stack);
        int max = Math.max(1, ops.maxStack(stack));
        for (int i = 0; i < slots.size() && left > 0; i++) {
            T here = slots.get(i);
            if (here != null && ops.similar(here, stack)) {
                int room = Math.max(0, Math.max(1, ops.maxStack(here)) - ops.amount(here));
                int move = Math.min(room, left);
                if (move > 0) {
                    slots.set(i, ops.withAmount(here, ops.amount(here) + move));
                    left -= move;
                }
            }
        }
        for (int i = 0; i < slots.size() && left > 0; i++) {
            if (slots.get(i) == null) {
                int move = Math.min(max, left);
                slots.set(i, ops.withAmount(stack, move));
                left -= move;
            }
        }
        return left;
    }
}

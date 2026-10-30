package net.skybornmc.skyblock.economy.sell;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure stack moving for the sell chest (SELL2) and for handing items back: put a stack into a row of slots the way
 * vanilla shift-click does (top up similar stacks first, then empty slots), and split items that must go back between
 * the inventory, the ender chest and the floor. No Bukkit types: {@code T} is the stack, {@link Ops} reads it.
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

    /** Somewhere items can be put: returns what did not fit (like {@code Inventory#addItem}). */
    interface Target<T> {
        List<T> add(T stack);
    }

    /**
     * Where returned items ended up.
     *
     * @param toInventory stacks that went (fully) into the inventory
     * @param toEnder     stacks (or parts) that went into the ender chest
     * @param dropped     what fit nowhere: drop it at the player's feet
     */
    record Returned<T>(int toInventory, int toEnder, List<T> dropped) {
        boolean overflowed() {
            return toEnder > 0 || !dropped.isEmpty();
        }
    }

    /**
     * Hands stacks back: inventory first, then the ender chest ({@code ender} may be null = skip), and whatever is left is
     * returned to be dropped at the feet. Nothing is ever lost: every item ends up in exactly one place.
     */
    static <T> Returned<T> giveBack(List<T> stacks, Target<T> inventory, Target<T> ender, Ops<T> ops) {
        int toInventory = 0;
        int toEnder = 0;
        List<T> dropped = new ArrayList<>();
        for (T stack : stacks) {
            if (stack == null) {
                continue;
            }
            List<T> rest = inventory.add(stack);
            if (rest.isEmpty()) {
                toInventory++;
                continue;
            }
            for (T part : rest) {
                int before = ops.amount(part);
                List<T> still = ender == null ? List.of(part) : ender.add(part);
                int after = 0;
                for (T s : still) {
                    after += ops.amount(s);
                }
                if (after < before) {
                    toEnder++;
                }
                dropped.addAll(still);
            }
        }
        return new Returned<>(toInventory, toEnder, dropped);
    }
}

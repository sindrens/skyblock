package net.skybornmc.skyblock.box;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Which stacks fit into an inventory's storage slots, taken in order (each planned stack fills slots before the next
 * one is checked). Generic so it is unit-tested without Bukkit: {@code T} is the item kind, {@code similar} says whether
 * two kinds stack together.
 */
final class FitPlan {

    /** One storage slot: {@code kind == null} = empty. */
    static final class Slot<T> {
        T kind;
        int amount;
        int max;

        Slot(T kind, int amount, int max) {
            this.kind = kind;
            this.amount = amount;
            this.max = max;
        }
    }

    /** A stack to place. */
    record Stack<T>(T kind, int amount, int maxStack) {
    }

    private FitPlan() {
    }

    /**
     * Indexes of {@code stacks} that fit completely, in order. With {@code stopAtFirstMiss} the plan ends at the first
     * stack that does not fit; otherwise later (smaller) stacks are still tried ("Take all that fit").
     * {@code slots} is changed (it ends as the planned inventory).
     */
    static <T> List<Integer> plan(List<Slot<T>> slots, List<Stack<T>> stacks, BiPredicate<T, T> similar, boolean stopAtFirstMiss) {
        List<Integer> fits = new ArrayList<>();
        for (int i = 0; i < stacks.size(); i++) {
            Stack<T> s = stacks.get(i);
            if (place(slots, s, similar)) {
                fits.add(i);
            } else if (stopAtFirstMiss) {
                break;
            }
        }
        return fits;
    }

    /** Places the whole stack (similar stacks first, then empty slots), or changes nothing and returns false. */
    static <T> boolean place(List<Slot<T>> slots, Stack<T> s, BiPredicate<T, T> similar) {
        int max = Math.max(1, s.maxStack());
        int room = 0;
        for (Slot<T> slot : slots) {
            if (slot.kind == null) {
                room += max;
            } else if (similar.test(slot.kind, s.kind())) {
                room += Math.max(0, Math.min(slot.max, max) - slot.amount);
            }
            if (room >= s.amount()) {
                break;
            }
        }
        if (room < s.amount()) {
            return false;
        }
        int left = s.amount();
        for (Slot<T> slot : slots) {
            if (left <= 0) {
                break;
            }
            if (slot.kind != null && similar.test(slot.kind, s.kind())) {
                int add = Math.min(left, Math.max(0, Math.min(slot.max, max) - slot.amount));
                slot.amount += add;
                left -= add;
            }
        }
        for (Slot<T> slot : slots) {
            if (left <= 0) {
                break;
            }
            if (slot.kind == null) {
                int add = Math.min(left, max);
                slot.kind = s.kind();
                slot.amount = add;
                slot.max = max;
                left -= add;
            }
        }
        return true;
    }
}

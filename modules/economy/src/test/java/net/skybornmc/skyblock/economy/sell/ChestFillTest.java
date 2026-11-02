package net.skybornmc.skyblock.economy.sell;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Moving stacks into the sell chest (SELL2). */
class ChestFillTest {

    record Stack(String id, int amount, int max) {
    }

    static final ChestFill.Ops<Stack> OPS = new ChestFill.Ops<>() {
        @Override
        public boolean similar(Stack a, Stack b) {
            return a.id().equals(b.id());
        }

        @Override
        public int amount(Stack stack) {
            return stack.amount();
        }

        @Override
        public int maxStack(Stack stack) {
            return stack.max();
        }

        @Override
        public Stack withAmount(Stack stack, int amount) {
            return new Stack(stack.id(), amount, stack.max());
        }
    };

    static List<Stack> slots(int n) {
        return new ArrayList<>(Arrays.asList(new Stack[n]));
    }

    static int total(List<Stack> slots, String id) {
        return slots.stream().filter(s -> s != null && s.id().equals(id)).mapToInt(Stack::amount).sum();
    }

    @Test
    void topsUpSimilarStacksBeforeEmptySlots() {
        List<Stack> slots = slots(4);
        slots.set(2, new Stack("coal", 60, 64));
        int left = ChestFill.insert(slots, new Stack("coal", 10, 64), OPS);
        assertEquals(0, left);
        assertEquals(64, slots.get(2).amount());
        assertEquals(6, slots.get(0).amount(), "the rest goes to the first empty slot");
        assertEquals(70, total(slots, "coal"));
    }

    @Test
    void neverMixesDifferentItems() {
        List<Stack> slots = slots(2);
        slots.set(0, new Stack("wheat", 1, 64));
        assertEquals(0, ChestFill.insert(slots, new Stack("coal", 5, 64), OPS));
        assertEquals(new Stack("wheat", 1, 64), slots.get(0));
        assertEquals(new Stack("coal", 5, 64), slots.get(1));
    }

    @Test
    void reportsWhatDidNotFitAndKeepsItemCount() {
        List<Stack> slots = slots(2);
        slots.set(0, new Stack("pearl", 10, 16));
        int left = ChestFill.insert(slots, new Stack("pearl", 30, 16), OPS);
        // 6 top up slot 0, 16 fill slot 1, 8 are left.
        assertEquals(8, left);
        assertEquals(32, total(slots, "pearl"));
        assertEquals(16, slots.get(1).amount(), "respects the max stack size");
    }

    @Test
    void fullChestTakesNothing() {
        List<Stack> slots = slots(1);
        slots.set(0, new Stack("dirt", 64, 64));
        assertEquals(12, ChestFill.insert(slots, new Stack("dirt", 12, 64), OPS));
        assertEquals(64, total(slots, "dirt"));
        assertEquals(0, ChestFill.insert(slots, null, OPS));
    }
}

package net.skybornmc.skyblock.economy.sell;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Moving stacks into the sell chest and handing items back (SELL2). */
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

    /** A container that holds at most {@code room} items. */
    static final class Box implements ChestFill.Target<Stack> {
        int room;
        final List<Stack> got = new ArrayList<>();

        Box(int room) {
            this.room = room;
        }

        @Override
        public List<Stack> add(Stack stack) {
            int take = Math.min(room, stack.amount());
            room -= take;
            if (take > 0) {
                got.add(new Stack(stack.id(), take, stack.max()));
            }
            int rest = stack.amount() - take;
            return rest > 0 ? List.of(new Stack(stack.id(), rest, stack.max())) : List.of();
        }

        int items() {
            return got.stream().mapToInt(Stack::amount).sum();
        }
    }

    @Test
    void giveBackFillsInventoryFirst() {
        Box inv = new Box(1000);
        Box ender = new Box(1000);
        var r = ChestFill.giveBack(List.of(new Stack("coal", 64, 64), new Stack("iron", 3, 64)), inv, ender, OPS);
        assertEquals(2, r.toInventory());
        assertEquals(0, r.toEnder());
        assertTrue(r.dropped().isEmpty());
        assertFalse(r.overflowed());
        assertEquals(67, inv.items());
    }

    @Test
    void overflowGoesToEnderChestThenFeetAndNothingIsLost() {
        Box inv = new Box(50);
        Box ender = new Box(20);
        List<Stack> back = List.of(new Stack("coal", 64, 64), new Stack("iron", 30, 64));
        var r = ChestFill.giveBack(back, inv, ender, OPS);
        int dropped = r.dropped().stream().mapToInt(Stack::amount).sum();
        assertEquals(94, inv.items() + ender.items() + dropped, "every item ends up somewhere, exactly once");
        assertEquals(50, inv.items());
        assertEquals(20, ender.items());
        assertEquals(24, dropped);
        assertTrue(r.overflowed());
        assertEquals(2, r.toEnder(), "the coal rest and part of the iron went into the ender chest");
    }

    @Test
    void withoutEnderChestOverflowDrops() {
        Box inv = new Box(0);
        var r = ChestFill.giveBack(List.of(new Stack("coal", 5, 64)), inv, null, OPS);
        assertEquals(0, r.toEnder());
        assertEquals(List.of(new Stack("coal", 5, 64)), r.dropped());
    }
}

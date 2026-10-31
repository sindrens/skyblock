package net.skybornmc.skyblock.progression;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ItemMemoTest {

    @Test
    void equalSignatureReusesTheItem() {
        ItemMemo<Object> memo = new ItemMemo<>();
        AtomicInteger n = new AtomicInteger();
        Object a = memo.get(10, List.of("size", 2, "READY"), () -> "item" + n.incrementAndGet());
        Object b = memo.get(10, List.of("size", 2, "READY"), () -> "item" + n.incrementAndGet());
        assertSame(a, b);
        assertEquals(1, memo.builds());
    }

    @Test
    void changedInputRebuildsOnlyThatSlot() {
        ItemMemo<Object> memo = new ItemMemo<>();
        memo.get(10, List.of(1, "READY"), () -> "a1");
        memo.get(11, List.of(5, "TASKS_OPEN"), () -> "b1");
        assertEquals("a2", memo.get(10, List.of(1, "NO_MONEY"), () -> "a2"));
        assertEquals("b1", memo.get(11, List.of(5, "TASKS_OPEN"), () -> "b2"));
        assertEquals(3, memo.builds());
        // the newest signature is the one kept
        assertEquals("a2", memo.get(10, List.of(1, "NO_MONEY"), () -> "a3"));
    }

    @Test
    void nullSignatureAndClearAlwaysRebuild() {
        ItemMemo<Object> memo = new ItemMemo<>();
        memo.get(1, null, () -> "x");
        memo.get(1, null, () -> "y");
        assertEquals(2, memo.builds());
        memo.get(2, List.of(1), () -> "z");
        memo.clear();
        assertEquals("w", memo.get(2, List.of(1), () -> "w"));
    }
}

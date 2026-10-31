package net.skybornmc.skyblock.common.text;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextMemoTest {

    @Test
    void buildsOncePerKeyAndGeneration() {
        TextMemo<String> memo = new TextMemo<>(16);
        AtomicInteger builds = new AtomicInteger();
        String a = memo.get(1, "k", k -> k + builds.incrementAndGet());
        String b = memo.get(1, "k", k -> k + builds.incrementAndGet());
        assertSame(a, b);
        assertEquals(1, builds.get());
        memo.get(1, "other", k -> k + builds.incrementAndGet());
        assertEquals(2, builds.get());
    }

    @Test
    void aNewGenerationRebuilds() {
        TextMemo<String> memo = new TextMemo<>(16);
        AtomicInteger builds = new AtomicInteger();
        assertEquals("k1", memo.get(1, "k", k -> k + builds.incrementAndGet()));
        assertEquals("k2", memo.get(2, "k", k -> k + builds.incrementAndGet()));
        assertEquals("k2", memo.get(2, "k", k -> k + builds.incrementAndGet()));
        assertEquals(2, builds.get());
    }

    @Test
    void staysBounded() {
        TextMemo<Integer> memo = new TextMemo<>(4);
        for (int i = 0; i < 100; i++) {
            int v = i;
            memo.get(0, "k" + i, k -> v);
            assertTrue(memo.size() <= 4);
        }
    }

    @Test
    void nullIsNotCached() {
        TextMemo<String> memo = new TextMemo<>(4);
        AtomicInteger builds = new AtomicInteger();
        assertNull(memo.get(0, "k", k -> {
            builds.incrementAndGet();
            return null;
        }));
        memo.get(0, "k", k -> {
            builds.incrementAndGet();
            return null;
        });
        assertEquals(2, builds.get());
        assertEquals(0, memo.size());
    }

    @Test
    void clearForgets() {
        TextMemo<String> memo = new TextMemo<>(4);
        memo.get(0, "k", k -> "v");
        memo.clear();
        assertEquals(0, memo.size());
    }
}

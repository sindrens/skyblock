package net.skybornmc.skyblock.islands.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsTrendTest {

    @Test
    void trends() {
        assertEquals(new StatsTrend.Trend(StatsTrend.Kind.NONE, 0), StatsTrend.of(0, 0));
        assertEquals(new StatsTrend.Trend(StatsTrend.Kind.NEW, 0), StatsTrend.of(5, 0));
        assertEquals(new StatsTrend.Trend(StatsTrend.Kind.UP, 50), StatsTrend.of(150, 100));
        assertEquals(new StatsTrend.Trend(StatsTrend.Kind.DOWN, 100), StatsTrend.of(0, 100));
        assertEquals(new StatsTrend.Trend(StatsTrend.Kind.DOWN, 33), StatsTrend.of(2, 3));
        assertEquals(new StatsTrend.Trend(StatsTrend.Kind.SAME, 0), StatsTrend.of(1000, 1001));
        assertEquals(new StatsTrend.Trend(StatsTrend.Kind.UP, 900), StatsTrend.of(10, 1));
    }
}

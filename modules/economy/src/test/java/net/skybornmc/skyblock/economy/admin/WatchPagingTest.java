package net.skybornmc.skyblock.economy.admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** GUI-4c: the economy dashboard lists page by 28 (top earners, biggest transactions, reasons, alerts). */
class WatchPagingTest {

    @Test
    void pagesOf28() {
        assertEquals(1, WatchMenu.pages(0), "an empty list still has one page");
        assertEquals(1, WatchMenu.pages(28));
        assertEquals(2, WatchMenu.pages(29));
        assertEquals(3, WatchMenu.pages(84), "the default 84 rows fill three pages");
        assertEquals(10, WatchMenu.pages(280));
    }

    @Test
    void untouchedListSizesGrowToThreePages() {
        WatchConfig config = new WatchConfig();
        assertEquals(84, config.topSize);
        assertEquals(84, config.bigSize);
        var change = config.valueChanges().get(3);
        assertEquals(28, change.get("dashboard.top-size").from());
        assertEquals(84, change.get("dashboard.big-size").to());
    }
}

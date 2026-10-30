package net.skybornmc.skyblock.economy.sell;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The daily Hot Item: stable for a day, changes over days, only sellable entries of the chosen categories. */
class HotItemTest {

    private static final List<SellCatalog.Entry> ENTRIES = List.of(
            new SellCatalog.Entry("coal", "ores", 10, true),
            new SellCatalog.Entry("emerald", "ores", 82, false),
            new SellCatalog.Entry("wheat", "crops", 10, true),
            new SellCatalog.Entry("carrot", "crops", 14, true),
            new SellCatalog.Entry("bone", "mob-loot", 18, true));

    @Test
    void sameDaySameItem() {
        LocalDate day = LocalDate.of(2026, 10, 30);
        assertEquals(HotItem.pick(day, ENTRIES, List.of(), 7), HotItem.pick(day, ENTRIES, List.of(), 7));
    }

    @Test
    void itChangesOverDaysAndNeverPicksASwitchedOffItem() {
        Set<String> seen = new HashSet<>();
        LocalDate day = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 60; i++) {
            String key = HotItem.pick(day.plusDays(i), ENTRIES, List.of(), 7);
            assertTrue(!key.equals("emerald"));
            seen.add(key);
        }
        assertEquals(4, seen.size());
    }

    @Test
    void categoriesLimitTheChoice() {
        LocalDate day = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 30; i++) {
            String key = HotItem.pick(day.plusDays(i), ENTRIES, List.of("Crops"), 7);
            assertTrue(key.equals("wheat") || key.equals("carrot"), key);
        }
        assertNull(HotItem.pick(day, ENTRIES, List.of("nothing"), 7));
        assertNull(HotItem.pick(day, List.of(), List.of(), 7));
    }
}

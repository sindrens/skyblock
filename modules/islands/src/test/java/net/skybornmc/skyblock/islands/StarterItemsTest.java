package net.skybornmc.skyblock.islands;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarterItemsTest {

    private static final Set<String> KNOWN = Set.of("lava_bucket", "bread", "ice", "oak_sapling");

    @Test
    void parsesMaterialAndAmount() {
        StarterItems.Result r = StarterItems.parse(List.of("lava_bucket:1", "bread:16", " ICE ", "minecraft:oak_sapling:2"), KNOWN::contains);
        assertEquals(List.of(new StarterItems.Entry("lava_bucket", 1), new StarterItems.Entry("bread", 16),
                new StarterItems.Entry("ice", 1), new StarterItems.Entry("oak_sapling", 2)), r.entries());
        assertEquals(List.of(), r.problems());
    }

    @Test
    void reportsBadLinesAndKeepsTheRest() {
        StarterItems.Result r = StarterItems.parse(List.of("bread:x", "bread:0", "bread:65", "diamond_block:1", "", "ice:3"), KNOWN::contains);
        assertEquals(List.of(new StarterItems.Entry("ice", 3)), r.entries());
        assertEquals(4, r.problems().size());
    }

    @Test
    void defaultConfigMatchesThePlanDefault() {
        // owner 3 Nov 2026 start flow: 8 wheat seeds, 2 saplings, 16 bread, water, lava, 2 ice (no bone meal)
        StarterItems.Result r = StarterItems.parse(new IslandsConfig().starterItems, key -> true);
        assertEquals(6, r.entries().size());
        assertEquals(new StarterItems.Entry("wheat_seeds", 8), r.entries().get(0));
        assertEquals(new StarterItems.Entry("bread", 16), r.entries().get(2));
        assertEquals(new StarterItems.Entry("ice", 2), r.entries().get(5));
        assertEquals(List.of(), r.problems());
        var change = new IslandsConfig().valueChanges().get(3).get("starter-chest.items");
        assertTrue(change.matches(new java.util.ArrayList<>(IslandsConfig.STARTER_ITEMS_V2)), "an untouched live list is upgraded");
        assertFalse(change.matches(List.of("bread:1")), "an owner-chosen list stays");
    }
}

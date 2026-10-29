package net.skybornmc.skyblock.islands;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        // plan open question 2: lava bucket, ice, 2 bone meal, 16 bread, 2 saplings, sugar cane, cactus, melon + pumpkin seeds
        StarterItems.Result r = StarterItems.parse(new IslandsConfig().starterItems, key -> true);
        assertEquals(9, r.entries().size());
        assertEquals(new StarterItems.Entry("bread", 16), r.entries().get(3));
        assertEquals(List.of(), r.problems());
    }
}

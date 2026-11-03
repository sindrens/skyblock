package net.skybornmc.skyblock.shop;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 3 Nov 2026: /shop stone, /shop string, /shop spaw. */
class ShopSearchTest {

    private static ShopCatalog.Item item(String id) {
        return new ShopCatalog.Item(id, "t", 1, Map.of(), Map.of(), true);
    }

    @Test
    void exactFirstThenPrefixThenWords() {
        List<ShopCatalog.Item> items = List.of(item("stone_bricks"), item("stone"), item("cobblestone"), item("string"),
                item("spawner_pig"), item("spawner_cow"), item("oak_log"));
        List<ShopSearch.Hit> stone = ShopSearch.search("stone", items, 10);
        assertEquals("stone", stone.get(0).item().id());
        assertEquals(100, stone.get(0).score());
        assertEquals("stone_bricks", stone.get(1).item().id());
        assertEquals("cobblestone", stone.get(2).item().id());
        assertEquals("string", ShopSearch.search("string", items, 10).get(0).item().id());
        List<ShopSearch.Hit> spaw = ShopSearch.search("spaw", items, 10);
        assertEquals(2, spaw.size(), "spaw finds the spawners");
        assertTrue(spaw.stream().allMatch(h -> h.item().id().startsWith("spawner_")));
        assertEquals("oak_log", ShopSearch.search("log", items, 10).get(0).item().id());
        assertEquals("oak_log", ShopSearch.search("oak log", items, 10).get(0).item().id(), "spaces work too");
        assertEquals("stone", ShopSearch.search("stome", items, 10).get(0).item().id(), "one typo");
        assertTrue(ShopSearch.search("xyz", items, 10).isEmpty());
    }
}

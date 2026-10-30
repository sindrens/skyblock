package net.skybornmc.skyblock.progression;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Block worth per block (PHASE-5 M5.2, block-values.yml, owner P5-33 default). */
class BlockValuesTest {

    private static final Map<String, Long> SHOP = Map.of("obsidian", 60L, "dirt", 4L, "beacon", 100_000L, "quartz_block", 999L);
    private static final Map<String, Double> SELL = Map.of("iron_ingot", 20.0, "quartz", 3.0);

    private static BlockValues values(BlockValuesConfig c, List<String> problems) {
        return BlockValues.load(c, id -> SHOP.containsKey(id) ? OptionalLong.of(SHOP.get(id)) : OptionalLong.empty(),
                id -> SELL.containsKey(id) ? OptionalDouble.of(SELL.get(id)) : OptionalDouble.empty(), problems::add);
    }

    @Test
    void defaultsAreHalfTheShopPrice() {
        List<String> problems = new ArrayList<>();
        BlockValues v = values(new BlockValuesConfig(), problems);
        assertEquals(List.of(), problems);
        assertEquals(30, v.worth("obsidian"), 1e-9);
        assertEquals(30, v.worth("minecraft:OBSIDIAN"), 1e-9);
        assertEquals(0, v.worth("dirt"), 1e-9, "utility/filler blocks are worth 0");
        assertEquals(0, v.worth("bedrock"), 1e-9, "blocks nobody sells are worth 0 (staff items)");
        assertEquals(0, v.worth("spawner"), 1e-9, "spawners count through their stacks");
    }

    @Test
    void storageBlocksUseTheSellBasePrice() {
        BlockValues v = values(new BlockValuesConfig(), new ArrayList<>());
        assertEquals(9 * 20 * 0.5, v.worth("iron_block"), 1e-9);
        assertEquals(4 * 3 * 0.5, v.worth("quartz_block"), 1e-9, "storage wins over the shop price");
        assertEquals(0, v.worth("gold_block"), 1e-9, "no sell price, no worth");
    }

    @Test
    void exactValuesWinAndBrokenEntriesAreReported() {
        BlockValuesConfig c = new BlockValuesConfig();
        c.values = List.of("beacon: 150000", "dirt: 1", "broken", "x: -5");
        c.storageBlocks = List.of("iron_block: iron_ingot", "gold_block: gold_ingot 9");
        c.spawnerTierValues = List.of("1: 100", "0: 5", "two: 7");
        List<String> problems = new ArrayList<>();
        BlockValues v = values(c, problems);
        assertEquals(150_000, v.worth("beacon"), 1e-9);
        assertEquals(1, v.worth("dirt"), 1e-9, "an exact value wins over the zero list");
        assertEquals(5, problems.size(), problems.toString());
        assertEquals(300, v.spawnerWorth(1, 3), 1e-9);
        assertEquals(0, v.spawnerWorth(4, 3), 1e-9);
        assertEquals(0, v.spawnerWorth(1, 0), 1e-9);
    }

    @Test
    void spawnerTiersDefaultToHalfTheFirstShopPrice() {
        BlockValues v = values(new BlockValuesConfig(), new ArrayList<>());
        assertEquals(58_500, v.spawnerWorth(1, 1), 1e-9);
        assertEquals(1_050_000 * 2, v.spawnerWorth(5, 2), 1e-9, "per spawner in a stack");
        assertEquals(List.of(1, 2, 3, 4, 5), v.spawnerTiers());
    }
}

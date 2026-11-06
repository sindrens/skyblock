package net.skybornmc.skyblock.islandtools;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tools v5 (owner 7 Nov 2026): personal perks on long tracks, priced for ~13 blocks a second. */
class PerkSeedTest {

    private static final IslandToolsConfig C = new IslandToolsConfig();

    @Test
    void oldIslandLevelsKeepTheirEffect() {
        assertEquals(111, PerkSeed.convert(Upgrades.Effect.SHARDS, 37, 2500), "37 x 3% = 111 x 1%");
        assertEquals(167, PerkSeed.convert(Upgrades.Effect.DROPS, 50, 1000), "50 x 0.5% = 167 x 0.15%");
        assertEquals(25, PerkSeed.convert(Upgrades.Effect.SELL, 50, 500), "50 x 0.1% = 25 x 0.2%");
        assertEquals(1000, PerkSeed.convert(Upgrades.Effect.LUCKY, 100, 1000));
        assertEquals(3, PerkSeed.convert(Upgrades.Effect.ENCHANT, 3, 3));
        assertEquals(1, PerkSeed.convert(Upgrades.Effect.SELL, 1, 500), "never down to 0");
        assertEquals(0, PerkSeed.convert(Upgrades.Effect.SHARDS, 0, 2500));
        assertEquals(37 * 3.0, PerkSeed.convert(Upgrades.Effect.SHARDS, 37, 2500) * C.shardFinderPerLevel, 1e-9);
    }

    @Test
    void longTracksAndCheapFirstLevels() {
        List<String> problems = new ArrayList<>();
        Upgrades u = Upgrades.parse(C.perks, problems::add);
        assertEquals(List.of(), problems);
        for (Kind k : Kind.values()) {
            Upgrades.Def finder = u.get(k, "shard_finder").orElseThrow();
            assertEquals(2500, finder.max());
            assertTrue(finder.cost(1, C.priceExponent, 1) <= 100, "the first Shard Finder in a few seconds");
            long last = 0;
            for (int l = 1; l <= finder.max(); l += 101) {
                long c = finder.cost(l, C.priceExponent, 1);
                assertTrue(c >= last, "every level costs a bit more: " + l);
                last = c;
            }
        }
        assertEquals(1000, u.get(Kind.HOE, "extra_drops").orElseThrow().max());
        assertEquals(150, 1000 * C.dropsPerLevel, 1e-9, "Extra Drops at the max: +150%");
        assertEquals(100, 500 * C.sellPerLevel, 1e-9, "Sell Bonus at the max: +100%");
        assertTrue(C.sweepShards && C.areaShards, "every block counts for shards");
        assertEquals(600, C.shardPickaxe);
    }
}

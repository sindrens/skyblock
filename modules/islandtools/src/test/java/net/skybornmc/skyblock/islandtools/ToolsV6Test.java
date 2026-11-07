package net.skybornmc.skyblock.islandtools;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tools v6 (owner 7 Nov 2026, plans/TOOLS_V6_COMPOUND.md): compound Shard Finder, rounding up, one-time perks. */
class ToolsV6Test {

    private static final IslandToolsConfig C = new IslandToolsConfig();

    @Test
    void shardFinderCompoundsAndShardsRoundUp() {
        assertEquals(3.0, C.shardFinderPerLevel);
        assertEquals(1.0, ToolRules.compound(3, 0), 1e-12);
        assertEquals(1.03, ToolRules.compound(3, 1), 1e-12);
        assertEquals(1.03 * 1.03, ToolRules.compound(3, 2), 1e-12, "3% on top of the last level, not on the start");
        assertTrue(ToolRules.compound(3, 250) > 1500 && ToolRules.compound(3, 250) < 1700, "250 levels: about 1,600x");
        assertEquals(10, C.shardHoe / 100.0, 1e-9, "10 shards a crop (was 6)");
        assertEquals(11, ToolRules.rollUp(10 * ToolRules.compound(3, 1), 0.99), "10.3 is 11: always up");
        assertEquals(7, ToolRules.rollUp(6.6, 0.99), "the owner's example: 6.6 is 7");
        assertEquals(7, ToolRules.rollUp(7.0, 0.0), "a whole number stays");
        assertEquals(1, ToolRules.rollUp(0.4, 0.3), "below 1: a chance");
        assertEquals(0, ToolRules.rollUp(0.4, 0.5));
    }

    @Test
    void shardFinderPriceCompoundsToo() {
        Upgrades u = Upgrades.parse(C.perks, w -> { });
        Upgrades.Def finder = u.get(Kind.PICKAXE, "shard_finder").orElseThrow();
        assertEquals(150, finder.rawCost(1, C.priceExponent), 1e-9);
        assertEquals(150 * 1.035, finder.rawCost(2, C.priceExponent), 1e-9);
        // Blocks per level stay in the same range from the start to the end (price +3.5% a level, shards +3% a level).
        double first = finder.rawCost(1, C.priceExponent) / (10 * ToolRules.compound(3, 0));
        double last = finder.rawCost(250, C.priceExponent) / (10 * ToolRules.compound(3, 249));
        assertTrue(first >= 10 && last <= 60, first + " / " + last);
        List<String> problems = new ArrayList<>();
        Upgrades.parse(List.of("hoe:x:shards:10:1:0*3", "hoe:y:shards:10:1:5*200"), problems::add);
        assertEquals(2, problems.size(), "a compound price needs a base above 0 and at most +100% a level");
    }

    @Test
    void autoSellAndCompactorAreBoughtOnceForAllTools() {
        Upgrades u = Upgrades.parse(C.perks, w -> { });
        assertTrue(IslandToolsModule.accountWide(u.get(Kind.HOE, "auto_sell").orElseThrow()));
        assertTrue(IslandToolsModule.accountWide(u.get(Kind.PICKAXE, "compactor").orElseThrow()));
        assertFalse(IslandToolsModule.accountWide(u.get(Kind.HOE, "shard_finder").orElseThrow()));
        assertFalse(IslandToolsModule.accountWide(u.get(Kind.HOE, "haste").orElseThrow()));
    }

    @Test
    void newEnchantsUnlockOverTheLevelsCloserNearTheTop() {
        GrindEnchants g = GrindEnchants.parse(C.enchantList, w -> { });
        List<Integer> levels = g.of(Kind.PICKAXE).stream().map(GrindEnchants.Def::unlockLevel).sorted().toList();
        assertEquals(5, levels.stream().filter(l -> l == 1).count());
        assertTrue(levels.getLast() <= C.maxLevel, "every enchant opens before the first prestige");
        long early = levels.stream().filter(l -> l > 1 && l <= 25).count();
        long late = levels.stream().filter(l -> l > 25).count();
        assertTrue(late > early, "more new enchants in the second half of the levels");
        for (String id : List.of("shard_storm", "frenzy", "golden_touch", "duplicator", "xp_surge", "crystal_find", "booster_find",
                "chain_reaction", "shard_rain")) {
            assertTrue(g.get(Kind.PICKAXE, id).isPresent() && g.get(Kind.HOE, id).isPresent(), id);
        }
        assertTrue(g.get(Kind.ROD, "frenzy").isEmpty() && g.get(Kind.ROD, "chain_reaction").isEmpty(), "no area or Haste on the rod");
        assertTrue(GrindEnchants.Effect.CHAIN.passive());
        assertEquals(0, EnchantMenu.group(g.get(Kind.PICKAXE, "chain_reaction").orElseThrow()));
        assertEquals(1, EnchantMenu.group(g.get(Kind.PICKAXE, "booster_find").orElseThrow()));
        assertEquals(2, EnchantMenu.group(g.get(Kind.PICKAXE, "keyfinder").orElseThrow()));
        assertEquals(3, EnchantMenu.group(g.get(Kind.PICKAXE, "frenzy").orElseThrow()));
    }

    @Test
    void hoeImprovementsRound1To3() {
        GrindEnchants g = GrindEnchants.parse(C.enchantList, w -> { });
        GrindEnchants.Def burst = g.get(Kind.HOE, "shard_burst").orElseThrow();
        assertEquals(1, EnchantMenu.affordable(burst, 0, 10, burst.priceOf(1)), "Buy Max: exactly one level");
        assertEquals(0, EnchantMenu.affordable(burst, 0, 10, burst.priceOf(1) - 1));
        assertEquals(3, EnchantMenu.affordable(burst, 5, 3, Long.MAX_VALUE / 4), "never past the cap");
        assertEquals("<c:success>|||||</c:success><c:muted>|||||</c:muted>", EnchantMenu.bar(50, 100));
        assertEquals("farmhand", IslandToolsModule.hoeTitle(Kind.HOE, 1, false, 0));
        assertEquals("harvest_king", IslandToolsModule.hoeTitle(Kind.HOE, 50, false, 0));
        assertEquals("reborn_reaper", IslandToolsModule.hoeTitle(Kind.HOE, 0, true, 1));
        assertEquals(null, IslandToolsModule.hoeTitle(Kind.HOE, 0, true, 2));
        assertEquals(null, IslandToolsModule.hoeTitle(Kind.PICKAXE, 1, false, 0), "titles are the hoe's");
        java.util.Map<String, Integer> rows = new java.util.HashMap<>(java.util.Map.of("hoe:shard_burst", 50));
        assertFalse(EnchantProcs.off(rows, burst));
        rows.put("hoe:off.shard_burst", 1);
        assertTrue(EnchantProcs.off(rows, burst), "switched off, the level stays");
        assertEquals(50, rows.get("hoe:shard_burst"));
        assertEquals(1000.0, C.enchantShardCap);
        assertEquals("blaze", C.prestigeKeyCrate);
        assertEquals("phoenix", C.prestigeKeyCrateTenth);
    }
}

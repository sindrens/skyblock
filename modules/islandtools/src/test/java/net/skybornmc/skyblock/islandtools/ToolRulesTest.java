package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.util.NiceNumbers;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Island tools, owner 4 Nov 2026 rework: two tools, levels and prestige, perks, Sweep bands, the carry-over. */
class ToolRulesTest {

    private static final IslandToolsConfig C = new IslandToolsConfig();

    @Test
    void onlyHoeAndPickaxeWithTheirOwnShards() {
        assertEquals(List.of(Kind.HOE, Kind.PICKAXE), List.of(Kind.values()));
        assertEquals(Currency.SHARDS, Kind.HOE.shards(), "the hoe earns Farming Shards (the old shards key)");
        assertEquals(Currency.MINING_SHARDS, Kind.PICKAXE.shards());
        assertTrue(Kind.legacy("axe") && Kind.legacy("SWORD"));
        assertFalse(Kind.of("axe").isPresent(), "no island axe any more");
        assertEquals("diamond_hoe", ToolRules.material(Kind.HOE), "everything starts at diamond");
        assertEquals("diamond_pickaxe", ToolRules.material(Kind.PICKAXE));
    }

    @Test
    void xpCurveIsTheSawtooth() {
        assertEquals(12, ToolRules.xpNeed(1, 0, 12, 1.6, 0.35));
        assertEquals(36, ToolRules.xpNeed(2, 0, 12, 1.6, 0.35), "12 x 2^1.6");
        assertEquals(16, ToolRules.xpNeed(1, 1, 12, 1.6, 0.35), "+35% per prestige");
        assertTrue(ToolRules.xpNeed(30, 0, 12, 1.6, 0.35) > ToolRules.xpNeed(29, 0, 12, 1.6, 0.35), "each level slower than the last");
        assertEquals(new ToolRules.Level(1, 0, 12), ToolRules.levelOf(0, 0, 50, 12, 1.6, 0.35));
        assertEquals(new ToolRules.Level(2, 0, 36), ToolRules.levelOf(12, 0, 50, 12, 1.6, 0.35));
        assertEquals(new ToolRules.Level(2, 35, 36), ToolRules.levelOf(47, 0, 50, 12, 1.6, 0.35));
        assertEquals(48, ToolRules.xpToMax(0, 3, 12, 1.6, 0.35));
        ToolRules.Level top = ToolRules.levelOf(48, 0, 3, 12, 1.6, 0.35);
        assertTrue(top.maxed());
        assertEquals(3, top.level());
        assertEquals(1.0, top.progress(), 1e-9);
        assertTrue(ToolRules.levelOf(Long.MAX_VALUE / 4, 0, C.maxLevel, C.xpBase, C.xpExponent, C.xpPrestigeFactor).maxed());
        assertEquals(50, C.maxLevel, "owner 4 Nov 2026 11:39: max level 50");
    }

    @Test
    void prestigeStarsBonusAndDiscount() {
        assertEquals("", ToolRules.stars(0, 5));
        assertEquals("★★★", ToolRules.stars(3, 5));
        assertEquals("★★★★★", ToolRules.stars(5, 5));
        assertEquals("★6", ToolRules.stars(6, 5));
        assertEquals(1.10, ToolRules.prestigeBonus(2, 5), 1e-9);
        assertEquals(0.90, ToolRules.priceFactor(2, 5, 50), 1e-9);
        assertEquals(0.50, ToolRules.priceFactor(20, 5, 50), 1e-9, "never under the floor");
    }

    @Test
    void sweepBandsFollowThePrestige() {
        List<ToolRules.Band> bands = ToolRules.bands(C.sweepBands);
        assertEquals(4, bands.size());
        assertNull(ToolRules.band(bands, 0), "locked until prestige 1");
        assertEquals(new ToolRules.Band(1, 2, 10), ToolRules.band(bands, 1));
        assertEquals(4, ToolRules.band(bands, 4).blocks(), "prestige 4 keeps the prestige 3 band");
        assertEquals(new ToolRules.Band(5, 6, 30), ToolRules.band(bands, 9));
        assertEquals(5.0, ToolRules.sweepChance(ToolRules.band(bands, 1), 50, 100), 1e-9);
        assertEquals(10.0, ToolRules.sweepChance(ToolRules.band(bands, 1), 100, 100), 1e-9);
        assertEquals(0, ToolRules.sweepChance(null, 100, 100), 1e-9);
        assertEquals(1, ToolRules.bands(List.of("x", "1:2", "2:3:150", "1:2:10")).size(), "broken bands are skipped");
    }

    @Test
    void bundledPerksParseAndSpreadOverTheLevels() {
        List<String> problems = new ArrayList<>();
        Upgrades u = Upgrades.parse(C.perks, problems::add);
        assertEquals(List.of(), problems);
        assertEquals(12, u.of(Kind.HOE).size());
        assertEquals(12, u.of(Kind.PICKAXE).size(), "with the pickaxe storage");
        assertTrue(u.get(Kind.HOE, "magnet").isEmpty(), "owner 4 Nov 2026: Magnet is built in");
        assertTrue(u.get(Kind.HOE, "row").isEmpty(), "Row became Sweep");
        Upgrades.Def sweep = u.get(Kind.HOE, "sweep").orElseThrow();
        assertEquals(1, sweep.prestige());
        assertEquals(100, sweep.max());
        assertEquals(1, u.get(Kind.PICKAXE, "vein").orElseThrow().prestige());
        // Feedback 5 Nov 2026: fewer, bigger steps (100 levels of 1% instead of 1,000 of 0.1%); the first level is cheap.
        assertEquals(100, u.get(Kind.HOE, "shard_finder").orElseThrow().max());
        assertEquals(1, u.get(Kind.HOE, "shard_finder").orElseThrow().unlock(), "Shard Finder from level 1");
        assertTrue(u.get(Kind.HOE, "shard_finder").orElseThrow().cost(1, C.priceExponent, 1) <= 100, "the first Shard Finder within a minute");
        assertEquals(100, u.get(Kind.HOE, "extra_drops").orElseThrow().max());
        assertEquals(100, u.get(Kind.HOE, "growth").orElseThrow().max());
        Upgrades.Def fortune = u.get(Kind.HOE, "fortune").orElseThrow();
        assertEquals(3, fortune.max(), "enchant-like perks keep their vanilla levels");
        assertEquals(Upgrades.Effect.ENCHANT, fortune.effect());
        assertEquals("fortune", fortune.arg());
        for (Kind k : Kind.values()) {
            for (Upgrades.Def d : u.of(k)) {
                assertTrue(d.unlock() >= 1 && d.unlock() <= C.maxLevel, d.id() + " unlocks inside the tool levels");
            }
        }
    }

    @Test
    void longTrackPricesAreNiceAndGrow() {
        Upgrades.Def d = Upgrades.parse(List.of("hoe:shard_finder:shards:1000:1:300+10"), w -> { }).get(Kind.HOE, "shard_finder").orElseThrow();
        long first = d.cost(1, 1.35, 1);
        assertTrue(first >= 100 && first <= 1000, "first levels cost a few hundred shards: " + first);
        long prev = 0;
        for (int l = 1; l <= 1000; l += 37) {
            long c = d.cost(l, 1.35, 1);
            assertTrue(NiceNumbers.isNice(c), c + " is nice-rounded");
            assertTrue(c >= prev, "never cheaper later");
            prev = c;
        }
        assertTrue(d.cost(1000, 1.35, 1) > 50_000, "the last levels cost tens of thousands");
        assertEquals(d.cost(1, 1.35, 1) + d.cost(2, 1.35, 1), d.sumCost(0, 2, 1.35, 1));
        assertEquals(5, d.buyable(995, 10));
        assertEquals(d.sumCost(995, 5, 1.35, 1), d.sumCost(995, 100, 1.35, 1), "x100 stops at the max");
        assertTrue(d.cost(500, 1.35, 0.5) < d.cost(500, 1.35, 1), "prestige discount");
        Upgrades.Def list = Upgrades.parse(List.of("hoe:fortune:enchant=fortune:3:5:5000/25000/100000"), w -> { })
                .get(Kind.HOE, "fortune").orElseThrow();
        assertEquals(5000, list.cost(1, 1.35, 1));
        assertEquals(100_000, list.cost(3, 1.35, 1));
        assertEquals(130_000, list.sumCost(0, 3, 1.35, 1));
    }

    @Test
    void brokenLinesAreSkipped() {
        List<String> problems = new ArrayList<>();
        Upgrades u = Upgrades.parse(List.of("hoe:x:nope:5:1:10+2", "rod:b:haste:2:1:10/20", "hoe:e:enchant:3:1:5/6/7",
                "hoe:y:drops:5:1:abc", "hoe:z:drops:5:1:10+2", "hoe:z:drops:5:1:10+2", "hoe:auto_sell:autosell:1:20:1.5"), problems::add);
        assertEquals(6, problems.size(), String.join("\n", problems));
        assertEquals(1, u.of(Kind.HOE).size());
    }

    @Test
    void levelsRoundTripSorted() {
        Map<String, Integer> m = Upgrades.decode("efficiency=3, fortune=1,bad=x,zero=0");
        assertEquals(Map.of("efficiency", 3, "fortune", 1), m);
        assertEquals("efficiency=3,fortune=1", Upgrades.encode(Map.of("fortune", 1, "efficiency", 3)));
    }

    @Test
    void oldLevelsCarryOverProportionally() {
        assertEquals(300, ToolRules.carryOver(3, 10, 1000));
        assertEquals(1000, ToolRules.carryOver(5, 5, 1000));
        assertEquals(1, ToolRules.carryOver(1, 3, 3));
        assertEquals(0, ToolRules.carryOver(0, 3, 3));
        Upgrades u = Upgrades.parse(C.perks, w -> { });
        Map<String, Integer> hoe = ToolRules.migrate(Kind.HOE, "row=4,magnet=1,shard_finder=3,fortune=2,storage=10", u);
        assertEquals(Map.of("shard_finder", 300, "fortune", 2, "storage", 250), hoe, "row and magnet are dropped");
        Map<String, Integer> pick = ToolRules.migrate(Kind.PICKAXE, "smelter=5,experience=5,extra_drops=1", u);
        assertEquals(Map.of("smelter", 100, "extra_drops", 100), pick);
    }

    @Test
    void storageRegrowAndRounding() {
        assertEquals(2000, ToolRules.storage(0, 2000, 200));
        assertEquals(52_000, ToolRules.storage(250, 2000, 200));
        assertEquals(60, ToolRules.regrowSeconds(0, 0, 60, 15, 120, 10), 1e-9, "T11: 60 s at the start");
        assertEquals(15, ToolRules.regrowSeconds(120, 0, 60, 15, 120, 10), 1e-9, "T11: 15 s at the top upgrade");
        assertEquals(10, ToolRules.regrowSeconds(120, 100, 60, 15, 120, 10), 1e-9, "G11: perks never go under the floor");
        assertEquals(30, ToolRules.regrowSeconds(0, 100, 60, 15, 120, 10), 1e-9, "the hoe's Growth: +100% = half the time");
        assertEquals(6, ToolRules.burstPercent(120, 0.05, 10), 1e-9);
        assertEquals(2, ToolRules.roll(2.3, 0.5));
        assertEquals(3, ToolRules.roll(2.3, 0.2));
        assertEquals(1, ToolRules.buyAmount(false, false));
        assertEquals(10, ToolRules.buyAmount(true, false));
        assertEquals(100, ToolRules.buyAmount(false, true));
    }

    @Test
    void menuHelpers() {
        assertEquals(2, ToolMenu.start(4), "contiguous, centred");
        assertEquals(0, ToolMenu.start(9));
        assertEquals(4, ToolMenu.start(1));
        assertEquals("12.5%", ToolMenu.pct(12.5));
        assertEquals("0.3%", ToolMenu.pct(0.1 * 3));
        assertEquals("10%", ToolMenu.pct(10));
        assertEquals("0%", ToolMenu.pct(0));
        assertEquals("III", IslandToolsModule.roman(3));
    }

    @Test
    void relevantBlocksUseWildcards() {
        assertTrue(Relevant.matches(C.relevantPickaxe, "iron_ore"));
        assertTrue(Relevant.matches(C.relevantPickaxe, "minecraft:deepslate_diamond_ore"));
        assertTrue(Relevant.matches(C.relevantPickaxe, "coal_ore"), "the /mine's coal");
        assertFalse(Relevant.matches(C.relevantPickaxe, "dirt"), "the pickaxe gets no bonus on dirt");
        assertTrue(Relevant.matches(C.relevantHoe, "wheat"));
        assertFalse(Relevant.matches(C.relevantHoe, "oak_log"));
    }
}

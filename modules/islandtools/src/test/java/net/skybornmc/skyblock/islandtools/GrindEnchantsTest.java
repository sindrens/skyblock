package net.skybornmc.skyblock.islandtools;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 6 Nov 2026 (plans/TOOLS_V3.md): the personal grind enchants; tools v6: 26 on the pickaxe and hoe, compound values. */
class GrindEnchantsTest {

    private static final IslandToolsConfig C = new IslandToolsConfig();

    private static GrindEnchants defaults(List<String> problems) {
        return GrindEnchants.parse(C.enchantList, problems::add);
    }

    @Test
    void defaultsHaveSeventeenPerToolAndNoProblems() {
        List<String> problems = new ArrayList<>();
        GrindEnchants g = defaults(problems);
        assertEquals(List.of(), problems);
        assertEquals(26, g.of(Kind.PICKAXE).size(), "tools v6: 17 + 9 new on the /mine pickaxe");
        assertEquals(26, g.of(Kind.HOE).size(), "tools v6: 17 + 9 new on the /farm hoe");
        assertTrue(g.of(Kind.PICKAXE).stream().allMatch(d -> d.unlockPrestige() == 0), "tools v6: no prestige gates");
        assertTrue(g.get(Kind.PICKAXE, "nuke").isPresent());
        assertTrue(g.get(Kind.HOE, "meteor_shower").isPresent());
        assertFalse(g.get(Kind.HOE, "nuke").isPresent(), "the hoe has its own area set");
        assertFalse(g.get(Kind.PICKAXE, "double_ore").isPresent(), "owner: Double Ore is dropped");
    }

    @Test
    void everyToolHasEveryKindOfEffectAndThreeStarters() {
        GrindEnchants g = defaults(new ArrayList<>());
        for (Kind kind : List.of(Kind.HOE, Kind.PICKAXE)) { // the rod has no area enchants (it breaks no blocks)
            Set<GrindEnchants.Effect> seen = EnumSet.noneOf(GrindEnchants.Effect.class);
            int starters = 0;
            for (GrindEnchants.Def d : g.of(kind)) {
                seen.add(d.effect());
                if (d.unlockLevel() <= 1 && d.unlockPrestige() == 0) {
                    starters++;
                }
            }
            assertEquals(EnumSet.allOf(GrindEnchants.Effect.class), seen, kind + " uses every effect");
            assertEquals(5, starters, kind + ": five enchants at tool level 1 (tools v6)");
        }
    }

    @Test
    void theRodHasItsOwnSetWithoutAreaEnchants() {
        GrindEnchants g = defaults(new ArrayList<>());
        assertEquals(19, g.of(Kind.ROD).size(), "tools v6: 12 + 7 new");
        assertTrue(g.of(Kind.ROD).stream().noneMatch(d -> d.effect() == GrindEnchants.Effect.AREA), "a rod breaks no blocks");
        assertTrue(g.get(Kind.ROD, "big_catch").isPresent() && g.get(Kind.ROD, "golden_fish").isPresent());
        List<List<GrindEnchants.Def>> rows = EnchantMenu.groups(g.of(Kind.ROD));
        assertTrue(rows.getFirst().isEmpty(), "no area row for the rod");
        assertTrue(rows.stream().allMatch(r -> r.size() <= 8));
    }

    @Test
    void theSwordHasFifteenEnchants() {
        GrindEnchants g = defaults(new ArrayList<>());
        assertEquals(15, g.of(Kind.SWORD).size(), "tools v6: 8 + 7 new");
        assertTrue(g.of(Kind.SWORD).stream().noneMatch(d -> d.effect() == GrindEnchants.Effect.AREA || d.effect() == GrindEnchants.Effect.EXTRA_ITEMS
                || d.effect() == GrindEnchants.Effect.PROGRESS), "kills go to the bag: no area, extra items or progress enchants");
    }

    @Test
    void keyfinderStaysRare() {
        GrindEnchants.Def key = defaults(new ArrayList<>()).get(Kind.PICKAXE, "keyfinder").orElseThrow();
        assertTrue(key.rare());
        assertTrue(key.chance(key.max()) <= 0.02, "about 1 key per 5,000 blocks at the max level");
        assertTrue(key.chance(1) < 0.002);
    }

    @Test
    void chancesGrowEvenlyAndValuesCompound() {
        GrindEnchants.Def blast = defaults(new ArrayList<>()).get(Kind.PICKAXE, "blast").orElseThrow();
        assertEquals(0, blast.chance(0));
        assertEquals(0.5, blast.chance(1), 1e-9);
        assertEquals(2500, blast.max(), "tools v6: everyday enchants go to 2,500");
        assertEquals(1.0, blast.chance(2500), 1e-9);
        assertEquals(0.75, blast.chance(1250), 0.002);
        assertEquals(4, blast.value(1), 1e-9);
        assertEquals(18, blast.value(2500), 1e-9);
        assertEquals(18, blast.value(25_000), 1e-9, "above the max counts as the max");
        assertEquals(Math.sqrt(4 * 18), GrindEnchants.grow(4, 18, 1250, 2499), 1e-9, "halfway is the geometric middle");
        GrindEnchants.Def burst = defaults(new ArrayList<>()).get(Kind.PICKAXE, "shard_burst").orElseThrow();
        assertEquals(100, burst.value(1), 1e-9, "tools v6: a Burst gives 100 at level 1");
        assertEquals(1500, burst.value(2500), 1e-9, "x15 at the max");
        double step = burst.value(101) / burst.value(100);
        assertEquals(step, burst.value(2001) / burst.value(2000), 1e-9, "the same few tenths of a percent every level");
        assertEquals(7, GrindEnchants.lerp(3, 7, 1, 1), 1e-9, "a one-level enchant is at its max at once");
        assertEquals(5, GrindEnchants.grow(0, 10, 2, 3), 1e-9, "a zero end grows evenly");
        GrindEnchants.Def pity = defaults(new ArrayList<>()).get(Kind.HOE, "lucky_streak").orElseThrow();
        assertEquals(100, pity.value(1), 1e-9);
        assertEquals(30, pity.value(pity.max()), 1e-9, "Lucky Streak: 100 blocks down to 30");
        assertEquals(1000, pity.max());
        assertEquals(500, defaults(new ArrayList<>()).get(Kind.HOE, "tornado").orElseThrow().max(), "rare enchants go to 500");
    }

    @Test
    void pricesGrowAndSumUp() {
        GrindEnchants.Def d = defaults(new ArrayList<>()).get(Kind.PICKAXE, "shard_burst").orElseThrow();
        assertEquals(200, d.priceOf(1), "owner 7 Nov 2026: level 1 costs 200 shards (shards x10)");
        long last = 0;
        for (int l = 1; l <= 2500; l += 37) {
            assertTrue(d.priceOf(l) >= last);
            last = d.priceOf(l);
        }
        assertEquals(d.priceOf(1) + d.priceOf(2) + d.priceOf(3), d.price(0, 3));
        assertEquals(0, d.price(5, 0));
        assertEquals(10, d.buyable(0, 10));
        assertEquals(3, d.buyable(2497, 10), "never past the max");
        assertEquals(0, d.buyable(2500, 1));
    }

    @Test
    void capGrowsWithLifetimeLevelsFasterNearTheTop() {
        assertEquals(20, GrindEnchants.capByLevels(2500, 1, 20, 100, 1.5, 0), "a new tool: the base");
        assertEquals(80, GrindEnchants.capByLevels(2500, 10, 20, 100, 1.5, 0));
        assertEquals(884, GrindEnchants.capByLevels(2500, 50, 20, 100, 1.5, 0), "level 50 at prestige 0: about a third");
        assertEquals(2500, GrindEnchants.capByLevels(2500, 100, 20, 100, 1.5, 0), "prestige 2: the whole track");
        assertEquals(2500, GrindEnchants.capByLevels(2500, 2550, 20, 100, 1.5, 0));
        int early = GrindEnchants.capByLevels(2500, 20, 20, 100, 1.5, 0) - GrindEnchants.capByLevels(2500, 10, 20, 100, 1.5, 0);
        int late = GrindEnchants.capByLevels(2500, 90, 20, 100, 1.5, 0) - GrindEnchants.capByLevels(2500, 80, 20, 100, 1.5, 0);
        assertTrue(late > early * 2, "ten levels near the top open far more than ten levels at the start");
        assertEquals(380, GrindEnchants.capByLevels(2500, 10, 20, 100, 1.5, 300), "Mine Mastery 3 adds 300 levels");
        assertEquals(5, GrindEnchants.capByLevels(5, 1, 20, 100, 1.5, 0), "never above the enchant's max");
    }

    @Test
    void oldCapRisesEveryPrestige() {
        assertEquals(20, GrindEnchants.cap(1000, 0, 20, 20));
        assertEquals(40, GrindEnchants.cap(1000, 1, 20, 20));
        assertEquals(1000, GrindEnchants.cap(1000, 50, 20, 20), "1000 at prestige 50");
        assertEquals(1000, GrindEnchants.cap(1000, 500, 20, 20));
        assertEquals(5, GrindEnchants.cap(5, 0, 20, 20), "never above the enchant's max");
        assertEquals(320, GrindEnchants.cap(1000, 0, 20, 20, 300), "Mine Mastery 3 adds 300 levels (island synergy)");
        assertEquals(1000, GrindEnchants.cap(1000, 10, 20, 20, 1000));
    }

    @Test
    void streakBonusIsCapped() {
        assertEquals(0, GrindEnchants.streak(0, 0.1, 15), 1e-9);
        assertEquals(5, GrindEnchants.streak(50, 0.1, 15), 1e-9);
        assertEquals(15, GrindEnchants.streak(10_000, 0.1, 15), 1e-9);
    }

    @Test
    void badLinesAreSkippedWithAReason() {
        List<String> problems = new ArrayList<>();
        GrindEnchants g = GrindEnchants.parse(List.of(
                "pickaxe:blast:10:2:1:2:3:4:0",              // 9 fields: no unlock (allowed)
                "pickaxe:blast:10:2:1:2:3:4:0:1:0",          // twice: the first is used
                "axe:blast:10:2:1:2:3:4:0",                  // unknown tool
                "pickaxe:double_ore:10:2:1:2:3:4:0",         // unknown enchant
                "pickaxe:scholar:0:2:1:2:3:4:0",             // max < 1
                "pickaxe:keyfinder:10:x:1:2:3:4:0",          // not a number
                "pickaxe:rich_vein:10:2:1"), problems::add);  // too short
        assertEquals(1, g.of(Kind.PICKAXE).size());
        assertEquals(0, g.of(Kind.PICKAXE).getFirst().unlockLevel());
        assertEquals(6, problems.size(), String.join("\n", problems));
    }

    @Test
    void passiveEnchantsNeverRoll() {
        assertTrue(GrindEnchants.Effect.SKILL_XP.passive());
        assertTrue(GrindEnchants.Effect.PITY.passive());
        assertFalse(GrindEnchants.Effect.KEY.passive());
        assertFalse(GrindEnchants.Effect.AREA.passive());
    }

    @Test
    void menuRowsFitAndHoldEveryEnchant() {
        GrindEnchants g = defaults(new ArrayList<>());
        for (Kind kind : List.of(Kind.HOE, Kind.PICKAXE)) {
            List<List<GrindEnchants.Def>> rows = EnchantMenu.groups(g.of(kind));
            assertEquals(4, rows.size());
            int total = 0;
            for (List<GrindEnchants.Def> row : rows) {
                assertTrue(row.size() <= 8, "a banner and at most 8 enchants");
                assertFalse(row.isEmpty());
                total += row.size();
            }
            assertEquals(26, total);
        }
        assertEquals(10, EnchantMenu.nextAmount(1));
        assertEquals(100, EnchantMenu.nextAmount(10));
        assertEquals(1, EnchantMenu.nextAmount(100));
        assertEquals("4.5", EnchantMenu.num(4.49));
        assertEquals("1,250", EnchantMenu.num(1250));
    }

    @Test
    void everyEnchantHasTextAndAnItemIcon() throws Exception {
        YamlConfiguration y;
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/messages/islandtools.yml"))) {
            y = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        Set<String> ids = new HashSet<>();
        GrindEnchants g = defaults(new ArrayList<>());
        for (Kind kind : Kind.values()) {
            g.of(kind).forEach(d -> ids.add(d.id()));
        }
        for (String id : ids) {
            assertNotNull(y.getString("enchant." + id + ".name"), id + " has a name");
            assertFalse(y.getStringList("enchant." + id + ".lore").isEmpty(), id + " has a lore line");
            Material icon = Material.matchMaterial(Objects.requireNonNull(y.getString("enchant." + id + ".icon"), id + " icon"));
            assertNotNull(icon, id + " icon is an item id");
        }
        for (GrindEnchants.Effect e : GrindEnchants.Effect.values()) {
            assertNotNull(y.getString("enchant.menu.gives." + e.name().toLowerCase(java.util.Locale.ROOT)), e + " has a 'gives' text");
        }
        assertNotNull(y.getString("enchant.crate." + C.enchantKeyCrate));
    }

    @Test
    void breakthroughsJumpThenFlatten() {
        List<String> problems = new ArrayList<>();
        List<GrindEnchants.Breakthrough> b = GrindEnchants.Breakthrough.parse(C.enchantBreakthroughs, problems::add);
        assertEquals(List.of(), problems);
        GrindEnchants g = GrindEnchants.parse(C.enchantList, b, problems::add);
        GrindEnchants.Def blast = g.get(Kind.PICKAXE, "meteor").orElseThrow(); // a 500-level area enchant
        assertEquals(List.of(10, 50, 150, 500), b.stream().map(x -> x.level(500)).toList(), "a 500-level enchant");
        assertEquals(List.of(2, 10, 30, 100), b.stream().map(x -> x.level(100)).toList(), "a 100-level enchant");
        assertEquals(1, blast.boost(9));
        assertEquals(1.5, blast.boost(10));
        assertEquals(2, blast.boost(149));
        assertEquals(3, blast.boost(500));
        assertEquals(blast.chance(9) * 1.5, blast.chance(10), 0.01, "a jump at the breakthrough");
        assertEquals(0.08 * 3, blast.chance(500), 1e-9, "x3 at the max");
        assertEquals(40, blast.value(500), 1e-9, "a proc's amount stays (its chance grows)");
        assertTrue(blast.crosses(49, 50));
        assertFalse(blast.crosses(50, 120));
        assertEquals(50, blast.next(12).orElseThrow().level(500));
        assertTrue(blast.next(500).isEmpty());
        GrindEnchants.Def combo = g.get(Kind.PICKAXE, "combo").orElseThrow();
        assertEquals(225, combo.value(2500), 1e-9, "a steady enchant: its value x3");
        GrindEnchants.Def pity = g.get(Kind.PICKAXE, "lucky_streak").orElseThrow();
        assertEquals(30, pity.value(pity.max()), 1e-9, "Lucky Streak's block count is not boosted");
        GrindEnchants.Def rod = g.get(Kind.ROD, "big_catch").orElseThrow();
        assertTrue(rod.chance(rod.max()) <= 100, "never above 100%");
        assertEquals(1, GrindEnchants.Breakthrough.parse(List.of("10:2", "x:1", "0:2", "50:0.5"), problems::add).size());
        assertEquals(3, problems.size());
    }

    @Test
    void shardsAreTenTimesBiggerWithTheSameValue() {
        GrindEnchants now = GrindEnchants.parse(IslandToolsConfig.ENCHANTS_V16, w -> { });
        GrindEnchants old = GrindEnchants.parse(IslandToolsConfig.ENCHANTS_V13, w -> { });
        for (Kind k : Kind.values()) {
            for (GrindEnchants.Def d : now.of(k)) {
                GrindEnchants.Def o = old.get(k, d.id()).orElseThrow();
                assertEquals(o.cost() * 10, d.cost(), 1e-9, d.key());
                double shards = d.effect() == GrindEnchants.Effect.SHARDS ? 10 : 1;
                assertEquals(o.valueMax() * shards, d.valueMax(), 1e-9, d.key());
            }
        }
        assertEquals(1000, C.shardHoe, "tools v6: 10 shards a crop");
        assertEquals(2_500, C.luckyMax);
        assertEquals(17, C.version());
        var v17 = C.valueChanges().get(17);
        assertEquals(IslandToolsConfig.ENCHANTS_V16, v17.get("enchants.list").from());
        assertEquals(C.enchantList, v17.get("enchants.list").to());
        assertEquals(IslandToolsConfig.PERKS_V16, v17.get("perks").from());
    }
}

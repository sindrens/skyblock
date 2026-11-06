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

/** Owner 6 Nov 2026 (plans/TOOLS_V3.md): the personal grind enchants, 17 per tool. */
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
        assertEquals(17, g.of(Kind.PICKAXE).size(), "owner: 17 enchants on the /mine pickaxe");
        assertEquals(17, g.of(Kind.HOE).size(), "owner: 17 enchants on the /farm hoe");
        assertTrue(g.get(Kind.PICKAXE, "nuke").isPresent());
        assertTrue(g.get(Kind.HOE, "meteor_shower").isPresent());
        assertFalse(g.get(Kind.HOE, "nuke").isPresent(), "the hoe has its own area set");
        assertFalse(g.get(Kind.PICKAXE, "double_ore").isPresent(), "owner: Double Ore is dropped");
    }

    @Test
    void everyToolHasEveryKindOfEffectAndThreeStarters() {
        GrindEnchants g = defaults(new ArrayList<>());
        for (Kind kind : Kind.values()) {
            Set<GrindEnchants.Effect> seen = EnumSet.noneOf(GrindEnchants.Effect.class);
            int starters = 0;
            for (GrindEnchants.Def d : g.of(kind)) {
                seen.add(d.effect());
                if (d.unlockLevel() <= 1 && d.unlockPrestige() == 0) {
                    starters++;
                }
            }
            assertEquals(EnumSet.allOf(GrindEnchants.Effect.class), seen, kind + " uses every effect");
            assertEquals(3, starters, kind + ": Shard Burst, the first area enchant and Scholar at level 1");
        }
    }

    @Test
    void keyfinderStaysRare() {
        GrindEnchants.Def key = defaults(new ArrayList<>()).get(Kind.PICKAXE, "keyfinder").orElseThrow();
        assertTrue(key.rare());
        assertTrue(key.chance(key.max()) <= 0.02, "about 1 key per 5,000 blocks at the max level");
        assertTrue(key.chance(1) < 0.002);
    }

    @Test
    void chanceAndValueGrowLinearly() {
        GrindEnchants.Def blast = defaults(new ArrayList<>()).get(Kind.PICKAXE, "blast").orElseThrow();
        assertEquals(0, blast.chance(0));
        assertEquals(0.5, blast.chance(1), 1e-9);
        assertEquals(1.0, blast.chance(1000), 1e-9);
        assertEquals(0.75, blast.chance(500), 0.001);
        assertEquals(4, blast.value(1), 1e-9);
        assertEquals(18, blast.value(1000), 1e-9);
        assertEquals(18, blast.value(5000), 1e-9, "above the max counts as the max");
        assertEquals(7, GrindEnchants.lerp(3, 7, 1, 1), 1e-9, "a one-level enchant is at its max at once");
        GrindEnchants.Def pity = defaults(new ArrayList<>()).get(Kind.HOE, "lucky_streak").orElseThrow();
        assertEquals(100, pity.value(1), 1e-9);
        assertEquals(50, pity.value(1000), 1e-9, "Lucky Streak: 100 blocks down to 50");
    }

    @Test
    void pricesGrowAndSumUp() {
        GrindEnchants.Def d = defaults(new ArrayList<>()).get(Kind.PICKAXE, "shard_burst").orElseThrow();
        assertEquals(2, d.priceOf(1), "level 1 costs about the base cost");
        long last = 0;
        for (int l = 1; l <= 1000; l += 37) {
            assertTrue(d.priceOf(l) >= last);
            last = d.priceOf(l);
        }
        assertEquals(d.priceOf(1) + d.priceOf(2) + d.priceOf(3), d.price(0, 3));
        assertEquals(0, d.price(5, 0));
        assertEquals(10, d.buyable(0, 10));
        assertEquals(3, d.buyable(997, 10), "never past the max");
        assertEquals(0, d.buyable(1000, 1));
    }

    @Test
    void capRisesEveryPrestige() {
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
        for (Kind kind : Kind.values()) {
            List<List<GrindEnchants.Def>> rows = EnchantMenu.groups(g.of(kind));
            assertEquals(4, rows.size());
            int total = 0;
            for (List<GrindEnchants.Def> row : rows) {
                assertTrue(row.size() <= 7, "a centred row with room around it");
                assertFalse(row.isEmpty());
                total += row.size();
            }
            assertEquals(17, total);
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
}

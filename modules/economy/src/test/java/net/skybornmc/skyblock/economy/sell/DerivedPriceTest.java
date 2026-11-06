package net.skybornmc.skyblock.economy.sell;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ECON-SELL-SHOP-AH A2: derived prices, classes, excludes, lowering and the no-loop rule. */
class DerivedPriceTest {

    static Map<SellDerive.Cls, Double> factors() {
        Map<SellDerive.Cls, Double> f = new EnumMap<>(SellDerive.Cls.class);
        for (SellDerive.Cls c : SellDerive.Cls.values()) {
            f.put(c, c == SellDerive.Cls.STORAGE || c == SellDerive.Cls.PROCESSING ? 100.0 : 90.0);
        }
        return f;
    }

    static SellDerive.Recipe r(String out, int count, SellDerive.Kind kind, List<List<String>> in) {
        return new SellDerive.Recipe(out, count, in, kind);
    }

    static final List<SellDerive.Recipe> RECIPES = List.of(
            r("oak_planks", 4, SellDerive.Kind.SHAPELESS, List.of(List.of("oak_log"))),
            r("stick", 4, SellDerive.Kind.SHAPED, List.of(List.of("oak_planks", "birch_planks"), List.of("oak_planks", "birch_planks"))),
            r("iron_block", 1, SellDerive.Kind.SHAPED, Collections.nCopies(9, List.of("iron_ingot"))),
            r("iron_ingot", 9, SellDerive.Kind.SHAPELESS, List.of(List.of("iron_block"))),
            r("iron_ingot", 1, SellDerive.Kind.SMELTING, List.of(List.of("raw_iron"))),
            r("stone", 1, SellDerive.Kind.SMELTING, List.of(List.of("cobblestone"))),
            r("stone_bricks", 4, SellDerive.Kind.SHAPED, Collections.nCopies(4, List.of("stone"))),
            r("red_wool", 1, SellDerive.Kind.SHAPELESS, List.of(List.of("white_wool"), List.of("red_dye"))),
            r("iron_pickaxe", 1, SellDerive.Kind.SHAPED, List.of(List.of("iron_ingot"), List.of("iron_ingot"), List.of("iron_ingot"),
                    List.of("stick"), List.of("stick"))),
            r("bone_meal", 3, SellDerive.Kind.SHAPELESS, List.of(List.of("bone"))),
            r("mystery", 1, SellDerive.Kind.SHAPELESS, List.of(List.of("unpriced"))));

    static Map<String, Double> priced() {
        return new HashMap<>(Map.of("oak_log", 1.0, "birch_planks", 0.1, "cobblestone", 1.0, "iron_ingot", 18.0, "raw_iron", 20.0,
                "red_dye", 2.0, "white_wool", 3.0, "bone", 4.0, "stone", 5.0));
    }

    @Test
    void pricesComeFromTheCheapestRecipeWithTheClassFactor() {
        List<String> notes = new ArrayList<>();
        Map<String, Double> d = SellDerive.derive(priced(), RECIPES, factors(), List.of("*_pickaxe", "bone_meal"), 6, notes::add);
        assertEquals(0.22, d.get("oak_planks"), 1e-9, "planks from a log: shaping 90%, rounded down");
        assertEquals(0.04, d.get("stick"), 1e-9, "tag alternatives take the cheapest (birch planks 0.1)");
        assertEquals(162.0, d.get("iron_block"), 1e-9, "storage 100%");
        assertEquals(4.5, d.get("red_wool"), 1e-9, "dyeing 90% with the dye at its own price");
        assertEquals(0.9, d.get("stone_bricks"), 1e-9, "decorative 90% of the lowered stone");
        assertFalse(d.containsKey("iron_pickaxe"), "tools are never derived");
        assertFalse(d.containsKey("bone_meal"), "owner S7: bone meal stays unsellable");
        assertFalse(d.containsKey("mystery"), "an unpriced part means unsellable");
        assertEquals(1.0, d.get("stone"), 1e-9, "a hand price above the derived one is lowered");
        assertEquals(1, notes.size(), String.join("\n", notes));
        assertFalse(d.containsKey("iron_ingot"), "kept its listed price (smelting raw iron would be dearer)");
    }

    @Test
    void classesFollowTheRuleTable() {
        assertEquals(SellDerive.Cls.SHAPING, SellDerive.classOf(RECIPES.get(0)));
        assertEquals(SellDerive.Cls.STORAGE, SellDerive.classOf(RECIPES.get(2)));
        assertEquals(SellDerive.Cls.STORAGE, SellDerive.classOf(RECIPES.get(3)));
        assertEquals(SellDerive.Cls.PROCESSING, SellDerive.classOf(RECIPES.get(4)));
        assertEquals(SellDerive.Cls.DECORATIVE, SellDerive.classOf(RECIPES.get(6)));
        assertEquals(SellDerive.Cls.DYEING, SellDerive.classOf(RECIPES.get(7)));
        assertEquals(SellDerive.Cls.SHAPING, SellDerive.classOf(r("stone_stairs", 1, SellDerive.Kind.STONECUTTING, List.of(List.of("stone")))));
    }

    @Test
    void noRecipeTurnsItsPartsIntoMoreMoney() {
        java.util.Random random = new java.util.Random(7);
        for (int run = 0; run < 200; run++) {
            Map<String, Double> roots = priced();
            roots.replaceAll((k, v) -> v * (0.4 + random.nextDouble() * 1.1));
            Map<String, Double> all = new HashMap<>(roots);
            all.putAll(SellDerive.derive(roots, RECIPES, factors(), List.of(), 6, n -> { }));
            for (SellDerive.Recipe rec : RECIPES) {
                Double out = all.get(rec.output());
                if (out == null) {
                    continue;
                }
                double parts = 0;
                boolean priced = true;
                for (List<String> alts : rec.inputs()) {
                    double cheapest = alts.stream().map(all::get).filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue)
                            .min().orElse(Double.NaN);
                    if (Double.isNaN(cheapest)) {
                        priced = false;
                        break;
                    }
                    parts += cheapest;
                }
                if (priced) {
                    assertTrue(out * rec.count() <= parts + 1e-6, rec.output() + " x" + rec.count() + " sells above its parts");
                }
            }
        }
    }

    @Test
    void depthIsCapped() {
        List<SellDerive.Recipe> chain = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            chain.add(r("item_" + (i + 1), 1, SellDerive.Kind.SHAPELESS, List.of(List.of("item_" + i))));
        }
        Collections.reverse(chain); // worst order: one step per round
        Map<String, Double> d = SellDerive.derive(new HashMap<>(Map.of("item_0", 100.0)), chain, factors(), List.of(), 3, n -> { });
        assertTrue(d.containsKey("item_3"));
        assertFalse(d.containsKey("item_9"), "deeper than max-depth stays unsellable");
    }
}

package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import net.skybornmc.skyblock.common.wardrobe.ArmorSlots;
import net.skybornmc.skyblock.common.wardrobe.WornPiece;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** E5 armor enchant effects stay within their caps; warzone keeps only what W2 allows (EN16, EN17, WD18). */
class ArmorMathTest {

    private final EnchantRegistry registry = Fixtures.registry();
    private final ArmorRules rules = new ArmorRules(ArmorSlots.DEFAULTS, true);

    private EnchantDef def(String id) {
        return registry.get(id).orElseThrow();
    }

    private static WornPiece worn(ArmorPiece piece, int level, ToolPerk... perks) {
        return new WornPiece(piece, UUID.randomUUID(), level, List.of(perks));
    }

    @Test
    void vitalityOnAllPiecesIsCappedAtTenHearts() {
        List<WornPiece> all = List.of(worn(ArmorPiece.HEAD, 50, new ToolPerk(1, "vitality", 5)), worn(ArmorPiece.CHEST, 50, new ToolPerk(1, "vitality", 5)),
                worn(ArmorPiece.LEGS, 50, new ToolPerk(1, "vitality", 5)), worn(ArmorPiece.FEET, 50, new ToolPerk(1, "vitality", 5)));
        Map<String, List<Integer>> working = ArmorMath.working(all, rules, registry::get);
        assertEquals(List.of(5, 5, 5, 5), working.get("vitality"));
        assertEquals(10, ArmorMath.vitalityHearts(def("vitality"), working.get("vitality")), 1e-9, "all armor together max +10 hearts");
        assertEquals(3, ArmorMath.vitalityHearts(def("vitality"), List.of(1, 2)), 1e-9);
    }

    @Test
    void dormantEnchantsDoNothing() {
        List<WornPiece> merged = List.of(worn(ArmorPiece.HEAD, 0, new ToolPerk(1, "vitality", 5), new ToolPerk(2, "fortitude", 4)));
        assertTrue(ArmorMath.working(merged, rules, registry::get).isEmpty(), "a merged set at Lv 0 keeps its enchants dormant");
        List<WornPiece> mid = List.of(worn(ArmorPiece.HEAD, 30, new ToolPerk(1, "vitality", 3), new ToolPerk(2, "fortitude", 4)));
        Map<String, List<Integer>> w = ArmorMath.working(mid, rules, registry::get);
        assertEquals(List.of(3), w.get("vitality"));
        assertFalse(w.containsKey("fortitude"), "Fortitude IV needs set Lv 38 (gate)");
    }

    @Test
    void fortitudeAddsUpPerPieceAndTheFamilyCapsIt() {
        assertEquals(48, ArmorMath.sum(def("fortitude"), "percent", List.of(4, 4, 4, 4)), 1e-9, "12% per piece");
        assertEquals(40, ArmorMath.reduced(100, 60), 1e-9);
        assertEquals(0, ArmorMath.reduced(100, 150), 1e-9, "never below zero damage");
        assertEquals(100, ArmorMath.reduced(100, -5), 1e-9);
        assertEquals(BonusFamily.DAMAGE_REDUCTION, def("fortitude").family());
        assertEquals(BonusFamily.HEALTH, def("vitality").family());
    }

    @Test
    void warzoneKeepsOnlyWhatW2Allows() {
        // W2 default: damage_reduction 0.5, conquest 1; everything else off.
        java.util.function.ToDoubleFunction<BonusFamily> w2 = f -> f == BonusFamily.DAMAGE_REDUCTION ? 0.5 : f == BonusFamily.CONQUEST ? 1 : 0;
        assertEquals(1, ArmorMath.strength(def("vitality"), false, w2), 1e-9);
        assertEquals(0, ArmorMath.strength(def("vitality"), true, w2), 1e-9, "health is not a warzone family");
        assertEquals(0.33, ArmorMath.strength(def("fortitude"), true, w2), 1e-9, "PvP value -1% per level");
        assertEquals(1, ArmorMath.strength(def("featherfall"), true, w2), 1e-9);
        assertEquals(0, ArmorMath.strength(def("night_eye"), true, w2), 1e-9);
        assertEquals(0, ArmorMath.strength(def("swiftfoot"), true, w2), 1e-9);
        assertEquals(0, ArmorMath.strength(def("ember_ward"), true, w2), 1e-9);
        assertEquals(0, ArmorMath.strength(def("phoenix_rebirth"), true, w2), 1e-9, "off in the warzone");
        assertEquals(0.5, ArmorMath.strength(def("second_wind"), true, w2), 1e-9);
    }

    @Test
    void thornsAreCapped() {
        assertEquals(30, ArmorMath.thornsPercent(def("phoenix_thorns"), List.of(3, 3), 1, 30), 1e-9);
        assertEquals(12, ArmorMath.thornsPercent(def("phoenix_thorns"), List.of(3, 3), 0.4, 30), 1e-9, "PvP 40%");
        assertEquals(5, ArmorMath.thornsPercent(def("phoenix_thorns"), List.of(1), 1, 30), 1e-9);
    }

    @Test
    void secondWindWaitsForAQuietMoment() {
        EnchantDef sw = def("second_wind");
        assertEquals(0, ArmorMath.secondWindHeal(sw, 3, 4_000, 1, 1), 1e-9, "5 s without damage first");
        assertEquals(3, ArmorMath.secondWindHeal(sw, 3, 5_000, 1, 1), 1e-9, "1.5 hearts per second");
        assertEquals(1.5, ArmorMath.secondWindHeal(sw, 3, 9_000, 1, 0.5), 1e-9, "half in PvP");
        assertEquals(0, ArmorMath.secondWindHeal(sw, 0, 9_000, 1, 1), 1e-9);
    }

    @Test
    void rebirthOncePerCooldown() {
        assertTrue(ArmorMath.lethal(10, 8, 1));
        assertFalse(ArmorMath.lethal(8, 8, 1), "absorption soaks the rest");
        assertTrue(ArmorMath.rebirthReady(null, 0, 600));
        assertFalse(ArmorMath.rebirthReady(1_000L, 1_000L + 599_000, 600));
        assertTrue(ArmorMath.rebirthReady(1_000L, 1_000L + 600_000, 600));
        assertEquals(600, def("phoenix_rebirth").value("cooldown", 1), 1e-9);
    }
}

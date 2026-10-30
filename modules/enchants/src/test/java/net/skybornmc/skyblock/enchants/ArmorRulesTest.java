package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import net.skybornmc.skyblock.common.wardrobe.ArmorSlots;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** E5 slot rules on wardrobe set pieces (EN12, EN2, EN4 by set level). */
class ArmorRulesTest {

    private final EnchantRegistry registry = Fixtures.registry();
    private final ArmorRules rules = new ArmorRules(ArmorSlots.DEFAULTS, true);

    private EnchantRules.Problem apply(ArmorPiece piece, List<ToolPerk> on, int setLevel, int slot, String id, int level) {
        return rules.canApply(piece, on, setLevel, slot, registry.get(id).orElseThrow(), level, registry::get).problem();
    }

    @Test
    void allNineArmorEnchantsAreArmorOnly() {
        for (String id : List.of("vitality", "featherfall", "night_eye", "fortitude", "swiftfoot", "ember_ward", "second_wind",
                "phoenix_thorns", "phoenix_rebirth")) {
            assertTrue(registry.get(id).orElseThrow().armorOnly(), id);
        }
        assertFalse(ArmorRules.fits(registry.get("swiftness").orElseThrow(), ArmorPiece.HEAD), "tool enchants never fit a piece");
    }

    @Test
    void pieceTypes() {
        assertEquals(EnchantRules.Problem.OK, apply(ArmorPiece.FEET, List.of(), 25, 1, "featherfall", 1));
        assertEquals(EnchantRules.Problem.WRONG_TOOL, apply(ArmorPiece.HEAD, List.of(), 25, 1, "featherfall", 1), "boots only");
        assertEquals(EnchantRules.Problem.WRONG_TOOL, apply(ArmorPiece.LEGS, List.of(), 25, 1, "second_wind", 1), "chestplate only");
        assertEquals(EnchantRules.Problem.OK, apply(ArmorPiece.LEGS, List.of(), 25, 1, "phoenix_thorns", 1));
        assertEquals(EnchantRules.Problem.WRONG_TOOL, apply(ArmorPiece.HEAD, List.of(), 50, 1, "vein_rend", 1));
    }

    @Test
    void secondSlotAtLevel25() {
        assertEquals(EnchantRules.Problem.SLOT_LOCKED, apply(ArmorPiece.HEAD, List.of(), 24, 2, "vitality", 1));
        EnchantRules.Verdict v = rules.canApply(ArmorPiece.HEAD, List.of(), 24, 2, registry.get("vitality").orElseThrow(), 1, registry::get);
        assertEquals(25, v.needLevel(), "the set level that opens it");
        assertEquals(EnchantRules.Problem.OK, apply(ArmorPiece.HEAD, List.of(new ToolPerk(1, "night_eye", 1)), 25, 2, "vitality", 1));
        assertEquals(EnchantRules.Problem.SLOT_USED, apply(ArmorPiece.HEAD, List.of(new ToolPerk(1, "night_eye", 1)), 25, 1, "vitality", 1));
    }

    @Test
    void mythicSlotOnlyOnTheChestplateAtLevel50() {
        assertEquals(EnchantRules.Problem.SLOT_LOCKED, apply(ArmorPiece.CHEST, List.of(), 49, 3, "phoenix_rebirth", 1));
        assertEquals(EnchantRules.Problem.OK, apply(ArmorPiece.CHEST, List.of(), 50, 3, "phoenix_rebirth", 1));
        assertEquals(EnchantRules.Problem.PRESTIGE_ONLY, apply(ArmorPiece.CHEST, List.of(), 50, 1, "phoenix_rebirth", 1), "EN2");
        assertEquals(EnchantRules.Problem.MYTHIC_ONLY_SLOT, apply(ArmorPiece.CHEST, List.of(), 50, 3, "vitality", 1));
        assertEquals(EnchantRules.Problem.SLOT_LOCKED, apply(ArmorPiece.LEGS, List.of(), 50, 3, "vitality", 1), "no mythic slot on leggings");
        ArmorRules any = new ArmorRules(ArmorSlots.DEFAULTS, false);
        assertEquals(EnchantRules.Problem.OK, any.canApply(ArmorPiece.CHEST, List.of(), 50, 3, registry.get("vitality").orElseThrow(), 1,
                registry::get).problem(), "config: any enchant in the mythic slot");
    }

    @Test
    void levelGateBySetLevel() {
        assertEquals(EnchantRules.Problem.LEVEL_GATE, apply(ArmorPiece.HEAD, List.of(), 0, 1, "vitality", 1), "level 0 set: nothing yet");
        assertEquals(EnchantRules.Problem.OK, apply(ArmorPiece.HEAD, List.of(), 1, 1, "vitality", 1));
        EnchantRules.Verdict v = rules.canApply(ArmorPiece.HEAD, List.of(), 25, 1, registry.get("vitality").orElseThrow(), 5, registry::get);
        assertEquals(EnchantRules.Problem.LEVEL_GATE, v.problem());
        assertEquals(41, v.needLevel());
    }

    @Test
    void sameEnchantOncePerPieceAndConflicts() {
        assertEquals(EnchantRules.Problem.ALREADY_ON_TOOL, apply(ArmorPiece.HEAD, List.of(new ToolPerk(1, "vitality", 1)), 25, 2, "vitality", 1));
    }

    @Test
    void upgradeAndCombine() {
        ToolPerk perk = new ToolPerk(1, "vitality", 3);
        EnchantDef vit = registry.get("vitality").orElseThrow();
        assertEquals(EnchantRules.Problem.LEVEL_GATE, rules.canUpgrade(ArmorPiece.HEAD, perk, 25, vit).problem());
        assertEquals(EnchantRules.Problem.OK, rules.canUpgrade(ArmorPiece.HEAD, perk, 40, vit).problem());
        assertEquals(EnchantRules.Problem.MAX_LEVEL, rules.canUpgrade(ArmorPiece.HEAD, new ToolPerk(1, "vitality", 5), 50, vit).problem());
        assertEquals(EnchantRules.Problem.SLOT_LOCKED, rules.canUpgrade(ArmorPiece.HEAD, new ToolPerk(2, "vitality", 1), 10, vit).problem(),
                "a dormant enchant in a closed slot can't be raised");
        assertEquals(EnchantRules.Problem.NOT_A_PAIR, rules.canCombineOn(ArmorPiece.HEAD, perk, 50, vit, "vitality", 2).problem());
        assertEquals(EnchantRules.Problem.OK, rules.canCombineOn(ArmorPiece.HEAD, perk, 50, vit, "vitality", 3).problem());
    }

    @Test
    void workingAndDormant() {
        assertTrue(rules.working(ArmorPiece.HEAD, new ToolPerk(1, "vitality", 3), 25, registry::get));
        assertFalse(rules.working(ArmorPiece.HEAD, new ToolPerk(1, "vitality", 3), 0, registry::get), "merged set at Lv 0");
        assertFalse(rules.working(ArmorPiece.HEAD, new ToolPerk(1, "featherfall", 1), 50, registry::get), "wrong piece never works");
        assertFalse(rules.working(ArmorPiece.HEAD, new ToolPerk(1, "gone_enchant", 1), 50, registry::get));
        List<ToolPerk> after = ArmorRules.withApplied(List.of(new ToolPerk(2, "fortitude", 1)), 1, registry.get("vitality").orElseThrow(), 2, 75);
        assertEquals(List.of(new ToolPerk(1, "vitality", 2, 1, 75), new ToolPerk(2, "fortitude", 1)), after);
    }

    @Test
    void armorMessageKeysExist() {
        var yml = Fixtures.resource("messages/enchants.yml");
        for (EnchantRules.Problem p : EnchantRules.Problem.values()) {
            String key = EnchantMenus.armorProblemKey(p);
            assertTrue(yml.contains("rule." + key), "rule." + key);
            assertTrue(yml.contains("why." + key), "why." + key);
        }
    }
}

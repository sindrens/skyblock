package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.tools.ToolInfo;
import net.skybornmc.skyblock.common.tools.ToolKind;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.tools.ToolSlots;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Slot, conflict and level-gate rules (ENCHANTS §2.1, §2.2, §2.4, §2.5, §4.3, §4.4). */
class EnchantRulesTest {

    private final EnchantRegistry registry = Fixtures.registry();
    private final EnchantRules rules = Fixtures.rules();
    private final Function<String, Optional<EnchantDef>> lookup = registry::get;

    private EnchantRules.Problem apply(ToolInfo tool, int slot, String enchant, int level) {
        return rules.canApply(tool, slot, Fixtures.def(enchant), level, lookup).problem();
    }

    @Test
    void gateTableFromThePlan() {
        // tool level → max-III, max-IV, max-V, max-X (§2.4)
        int[][] table = {{5, 1, 1, 1, 1}, {15, 1, 2, 2, 3}, {30, 2, 3, 3, 6}, {45, 3, 4, 5, 9}, {50, 3, 4, 5, 10}};
        int[] maxes = {3, 4, 5, 10};
        for (int[] row : table) {
            for (int i = 0; i < maxes.length; i++) {
                assertEquals(row[i + 1], ToolSlots.maxEnchantLevel(maxes[i], row[0], 50), "Lv " + row[0] + " max " + maxes[i]);
            }
        }
        ToolInfo prestiged = Fixtures.tool(ToolKind.PICKAXE, 1, 1);
        assertEquals(3, rules.gate(prestiged, Fixtures.def("vein_rend")), "prestiged counts as level 50");
        assertEquals(34, rules.requiredToolLevel(3, 3), "the plan's 'Needs tool Lv 34' for a level III of max III");
        assertEquals(46, rules.requiredToolLevel(10, 10));
        assertEquals(1, rules.requiredToolLevel(5, 1));
    }

    @Test
    void applyHappyPathAndPerkList() {
        ToolInfo pick = Fixtures.tool(ToolKind.PICKAXE, 30, 0);
        assertEquals(EnchantRules.Problem.OK, apply(pick, 1, "swiftness", 1));
        List<ToolPerk> after = EnchantRules.withApplied(pick.perks(), 2, Fixtures.def("dragonfire_blast"), 1, 250);
        assertEquals(List.of(new ToolPerk(2, "dragonfire_blast", 1, 2, 250)), after);
    }

    @Test
    void wrongToolDisabledAlreadyAndConflicts() {
        ToolInfo pick = Fixtures.tool(ToolKind.PICKAXE, 50, 0, new ToolPerk(1, "vein_rend", 1));
        assertEquals(EnchantRules.Problem.WRONG_TOOL, apply(pick, 2, "treefeller", 1));
        assertEquals(EnchantRules.Problem.WRONG_TOOL, apply(pick, 2, "vitality", 1), "armor books can't go on tools");
        assertEquals(EnchantRules.Problem.ALREADY_ON_TOOL, apply(pick, 2, "vein_rend", 1));
        EnchantRules.Verdict conflict = rules.canApply(pick, 2, Fixtures.def("dragonfire_blast"), 1, lookup);
        assertEquals(EnchantRules.Problem.CONFLICT, conflict.problem());
        assertEquals("vein_rend", conflict.other());
        EnchantDef off = withEnabled(Fixtures.def("swiftness"), false);
        assertEquals(EnchantRules.Problem.DISABLED, rules.canApply(pick, 2, off, 1, lookup).problem());
    }

    @Test
    void slotsLockedUsedTwoSlotAndPrestige() {
        ToolInfo lv15 = Fixtures.tool(ToolKind.PICKAXE, 15, 0, new ToolPerk(1, "swiftness", 1));
        EnchantRules.Verdict locked = rules.canApply(lv15, 3, Fixtures.def("prosperity"), 1, lookup);
        assertEquals(EnchantRules.Problem.SLOT_LOCKED, locked.problem());
        assertEquals(30, locked.needLevel());
        assertEquals(EnchantRules.Problem.SLOT_USED, apply(lv15, 1, "prosperity", 1));
        assertEquals(EnchantRules.Problem.NEEDS_TWO_SLOTS, apply(lv15, 2, "dragonfire_blast", 1), "slot 3 is locked");
        ToolInfo lv45 = Fixtures.tool(ToolKind.PICKAXE, 45, 0);
        assertEquals(EnchantRules.Problem.NEEDS_TWO_SLOTS, apply(lv45, 4, "dragonfire_blast", 1), "slot 5 is the prestige slot");
        assertEquals(EnchantRules.Problem.OK, apply(lv45, 3, "dragonfire_blast", 1));
        assertEquals(EnchantRules.Problem.PRESTIGE_ONLY, apply(lv45, 1, "worldbreaker", 1));
        EnchantRules.Verdict prestigeLocked = rules.canApply(lv45, 5, Fixtures.def("worldbreaker"), 1, lookup);
        assertEquals(EnchantRules.Problem.SLOT_LOCKED, prestigeLocked.problem());
        assertEquals(-1, prestigeLocked.needLevel());
        ToolInfo prestiged = Fixtures.tool(ToolKind.PICKAXE, 1, 1, new ToolPerk(1, "vein_rend", 3));
        assertEquals(EnchantRules.Problem.OK, apply(prestiged, 5, "worldbreaker", 1), "Mythic + area enchant is fine");
        assertEquals(EnchantRules.Problem.OK, apply(prestiged, 5, "swiftness", 1), "the prestige slot takes normal enchants too");
        ToolInfo withMythic = Fixtures.tool(ToolKind.SWORD, 1, 1, new ToolPerk(5, "phoenix_wrath", 1));
        assertEquals(EnchantRules.Problem.ALREADY_ON_TOOL, apply(withMythic, 5, "phoenix_wrath", 1));
        assertEquals(EnchantRules.Problem.SLOT_USED, apply(withMythic, 5, "cleave", 1));
        EnchantRules allowPrestige = new EnchantRules(ToolSlots.DEFAULT_SLOT_LEVELS, 50, true);
        ToolInfo prestigedEmpty = Fixtures.tool(ToolKind.PICKAXE, 1, 1);
        assertEquals(EnchantRules.Problem.OK, allowPrestige.canApply(prestigedEmpty, 4, Fixtures.def("dragonfire_blast"), 1, lookup).problem(),
                "rules.two-slot-in-prestige-slot: true lets a 2-slot enchant use slot 5");
    }

    @Test
    void levelGateOnApply() {
        ToolInfo lv30 = Fixtures.tool(ToolKind.PICKAXE, 30, 0);
        assertEquals(EnchantRules.Problem.OK, apply(lv30, 1, "vein_rend", 2));
        EnchantRules.Verdict gate = rules.canApply(lv30, 1, Fixtures.def("vein_rend"), 3, lookup);
        assertEquals(EnchantRules.Problem.LEVEL_GATE, gate.problem());
        assertEquals(34, gate.needLevel());
    }

    @Test
    void upgradeAndCombine() {
        ToolPerk vein2 = new ToolPerk(1, "vein_rend", 2, 1, 709);
        ToolInfo lv30 = Fixtures.tool(ToolKind.PICKAXE, 30, 0, vein2);
        assertEquals(EnchantRules.Problem.LEVEL_GATE, rules.canUpgrade(lv30, vein2, Fixtures.def("vein_rend")).problem());
        ToolInfo lv34 = Fixtures.tool(ToolKind.PICKAXE, 34, 0, vein2);
        assertEquals(EnchantRules.Problem.OK, rules.canUpgrade(lv34, vein2, Fixtures.def("vein_rend")).problem());
        ToolPerk vein3 = vein2.upgraded(3, 29_754);
        assertEquals(30_463, vein3.invested());
        assertEquals(EnchantRules.Problem.MAX_LEVEL, rules.canUpgrade(lv34, vein3, Fixtures.def("vein_rend")).problem());
        assertEquals(EnchantRules.Problem.OK, rules.canCombineOnTool(lv34, vein2, Fixtures.def("vein_rend"), "vein_rend", 2).problem());
        assertEquals(EnchantRules.Problem.NOT_A_PAIR, rules.canCombineOnTool(lv34, vein2, Fixtures.def("vein_rend"), "vein_rend", 1).problem());
        EnchantDef slaughter = Fixtures.def("slaughter");
        assertEquals(EnchantRules.Problem.OK, EnchantRules.canCombineBooks(slaughter, "slaughter", 4, "slaughter", 4).problem());
        assertEquals(EnchantRules.Problem.NOT_A_PAIR, EnchantRules.canCombineBooks(slaughter, "slaughter", 4, "slaughter", 5).problem());
        assertEquals(EnchantRules.Problem.MAX_LEVEL, EnchantRules.canCombineBooks(slaughter, "slaughter", 10, "slaughter", 10).problem());
        assertEquals(List.of(new ToolPerk(2, "swiftness", 1)),
                EnchantRules.replaced(List.of(new ToolPerk(1, "vein_rend", 2), new ToolPerk(2, "swiftness", 1)), "vein_rend", null));
    }

    @Test
    void slotStates() {
        ToolInfo tool = Fixtures.tool(ToolKind.PICKAXE, 45, 0, new ToolPerk(2, "dragonfire_blast", 1, 2));
        assertEquals(EnchantRules.SlotState.EMPTY, rules.slotState(tool, 1));
        assertEquals(EnchantRules.SlotState.FILLED, rules.slotState(tool, 2));
        assertEquals(EnchantRules.SlotState.COVERED, rules.slotState(tool, 3));
        assertEquals(EnchantRules.SlotState.EMPTY, rules.slotState(tool, 4));
        assertEquals(EnchantRules.SlotState.LOCKED, rules.slotState(tool, 5));
        assertEquals(-1, rules.unlockLevel(5));
        assertEquals(30, rules.unlockLevel(3));
        assertTrue(EnchantRules.perkAt(tool, 3).isPresent());
    }

    private static EnchantDef withEnabled(EnchantDef d, boolean enabled) {
        return new EnchantDef(d.id(), d.rarity(), d.maxLevel(), d.targets(), d.slotCost(), d.conflicts(), d.pools(), d.minTier(),
                d.family(), d.passive(), d.values(), d.params(), d.pvpPercent(), d.weight(), enabled);
    }
}

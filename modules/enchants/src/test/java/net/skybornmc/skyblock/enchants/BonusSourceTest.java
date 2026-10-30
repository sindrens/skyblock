package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.DefaultBonusService;
import net.skybornmc.skyblock.common.tools.ToolInfo;
import net.skybornmc.skyblock.common.tools.ToolKind;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Passive enchants reported to the bonus families and capped once (ENCHANTS §8.1). */
class BonusSourceTest {

    private final EnchantRegistry registry = Fixtures.registry();

    @Test
    void passiveEnchantsAddUpPerFamily() {
        ToolInfo pick = Fixtures.tool(ToolKind.PICKAXE, 50, 0, new ToolPerk(1, "swiftness", 5), new ToolPerk(2, "prosperity", 3),
                new ToolPerk(3, "tempering", 2), new ToolPerk(4, "gilded_touch", 5));
        assertEquals(30, EnchantBonusSource.total(registry, pick, BonusFamily.SPEED, null));
        assertEquals(12, EnchantBonusSource.total(registry, pick, BonusFamily.EXTRA_DROPS, null));
        assertEquals(16, EnchantBonusSource.total(registry, pick, BonusFamily.TOOL_XP, null));
        assertEquals(0, EnchantBonusSource.total(registry, pick, BonusFamily.SELL, null), "Gilded Touch is a chance (E2)");
    }

    @Test
    void scholarsInsightOnlyForTheToolsOwnSkill() {
        ToolInfo pick = Fixtures.tool(ToolKind.PICKAXE, 50, 0, new ToolPerk(1, "scholars_insight", 5));
        assertEquals(25, EnchantBonusSource.total(registry, pick, BonusFamily.SKILL_XP, "mining"));
        assertEquals(0, EnchantBonusSource.total(registry, pick, BonusFamily.SKILL_XP, "combat"));
    }

    @Test
    void unknownAndOverMaxLevelsAreSafe() {
        ToolInfo pick = Fixtures.tool(ToolKind.PICKAXE, 50, 0, new ToolPerk(1, "removed_enchant", 3), new ToolPerk(2, "swiftness", 9));
        assertEquals(30, EnchantBonusSource.total(registry, pick, BonusFamily.SPEED, null), "level above max counts as max");
    }

    @Test
    void familiesAreCappedOnceInTheBonusService() {
        DefaultBonusService bonus = new DefaultBonusService(() -> 300, null);
        bonus.register("enchants", (family, ctx) -> family == BonusFamily.TOOL_XP ? 40 : 0);
        bonus.register("booster", (family, ctx) -> family == BonusFamily.TOOL_XP ? 80 : 0);
        BonusContext ctx = BonusContext.of(null);
        assertEquals(120, bonus.total(BonusFamily.TOOL_XP, ctx));
        assertEquals(100, bonus.capped(BonusFamily.TOOL_XP, ctx), "tool XP cap +100%");
        assertTrue(bonus.isCapped(BonusFamily.TOOL_XP, ctx));
        assertEquals(2.0, bonus.multiplier(BonusFamily.TOOL_XP, ctx));
        bonus.caps(Map.of(BonusFamily.TOOL_XP, 150.0, BonusFamily.SELL, 5.0));
        assertEquals(120, bonus.capped(BonusFamily.TOOL_XP, ctx));
        assertFalse(bonus.isCapped(BonusFamily.TOOL_XP, ctx));
        assertEquals(300, bonus.cap(BonusFamily.SELL), "the sell cap is always the economy's");
    }
}

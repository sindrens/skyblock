package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusSource;
import net.skybornmc.skyblock.common.tools.ToolInfo;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.tools.ToolService;

import java.util.Optional;

/**
 * Reports the passive enchants of the tool in the context to the bonus families (ENCHANTS §8.1): Swiftness (speed),
 * Scholar's Insight (skill XP of the tool's own skill only), Prosperity (extra drops, expected value), Crystal Seeker
 * (crystals), Tempering (tool XP), Treasure Hunter (rare finds), Phoenix Edge (mob damage). Chance/proc enchants are
 * implemented by E2/E3 hooks and report nothing here. Disabled enchants add nothing.
 */
final class EnchantBonusSource implements BonusSource {

    static final String ID = "enchants";

    private final EnchantsModule module;

    EnchantBonusSource(EnchantsModule module) {
        this.module = module;
    }

    @Override
    public double value(BonusFamily family, BonusContext context) {
        ToolInfo tool = context.tool();
        if (tool == null && context.item() != null) {
            ToolService tools = module.tools();
            tool = tools == null ? null : tools.read(context.item()).orElse(null);
        }
        if (tool == null || tool.perks().isEmpty()) {
            return 0;
        }
        return total(module.registry(), tool, family, context.activity());
    }

    /** Pure sum (unit-tested). */
    static double total(EnchantRegistry registry, ToolInfo tool, BonusFamily family, String activity) {
        double sum = 0;
        for (ToolPerk perk : tool.perks()) {
            Optional<EnchantDef> def = registry.get(perk.id());
            if (def.isEmpty() || def.get().family() != family || !def.get().fits(tool.kind())) {
                continue;
            }
            // Skill XP enchants only help the tool's own skill (Scholar's Insight: "skill XP of the tool's skill").
            if (family == BonusFamily.SKILL_XP && activity != null && !activity.equals(tool.kind().skill().id())) {
                continue;
            }
            sum += def.get().passiveValue(Math.min(perk.level(), def.get().maxLevel()));
        }
        return sum;
    }
}

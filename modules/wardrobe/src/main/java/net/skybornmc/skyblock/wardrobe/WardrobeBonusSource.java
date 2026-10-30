package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusSource;

/**
 * Reports the worn set bonuses to the bonus families (WARDROBE §2.5, §3.4): read from the player's cached result (never
 * a database or inventory lookup per block). The BonusService adds them to enchants, masteries, ranks and boosters and
 * applies each family cap once (sell: the economy's +300%).
 */
final class WardrobeBonusSource implements BonusSource {

    private final WardrobeModule module;

    WardrobeBonusSource(WardrobeModule module) {
        this.module = module;
    }

    @Override
    public double value(BonusFamily family, BonusContext context) {
        if (context.player() == null) {
            return 0;
        }
        Session s = module.equipment().sessionOrNull(context.player().getUniqueId());
        return s == null ? 0 : s.bonuses.value(family, context.activity());
    }
}

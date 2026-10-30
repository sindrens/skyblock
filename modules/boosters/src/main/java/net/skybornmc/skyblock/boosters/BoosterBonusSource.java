package net.skybornmc.skyblock.boosters;

import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusSource;
import net.skybornmc.skyblock.common.booster.BoosterType;

import java.util.Optional;

/**
 * Boosters as one {@link BonusSource} (id {@code boosters}): the player's running personal booster of the family's type
 * plus the running global booster of that type. They add up with every other source of the family and the family cap
 * applies once (O11: the +300% sell cap for sell boosters). Only for a player (item lore without a player gets 0).
 * Cheap: two map lookups, no database.
 */
final class BoosterBonusSource implements BonusSource {

    private final BoostersModule m;

    BoosterBonusSource(BoostersModule module) {
        this.m = module;
    }

    @Override
    public double value(BonusFamily family, BonusContext context) {
        if (context == null || context.player() == null) {
            return 0;
        }
        Optional<BoosterType> type = BoosterType.of(family);
        if (type.isEmpty()) {
            return 0;
        }
        return m.personal().percent(context.player(), type.get()) + m.global().percent(type.get());
    }
}

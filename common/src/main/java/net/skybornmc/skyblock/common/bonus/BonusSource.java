package net.skybornmc.skyblock.common.bonus;

/**
 * One system that adds bonuses (enchants, masteries, sets, ranks, boosters …), registered with
 * {@link BonusService#register}. Called on the main thread, often (per block/kill): keep it cheap and never block.
 */
@FunctionalInterface
public interface BonusSource {

    /** What this source adds to {@code family} in this context (percent, or hearts for HEALTH); 0 for nothing. */
    double value(BonusFamily family, BonusContext context);
}

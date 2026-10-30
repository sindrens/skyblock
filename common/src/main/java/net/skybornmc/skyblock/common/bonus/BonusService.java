package net.skybornmc.skyblock.common.bonus;

import net.skybornmc.skyblock.common.economy.MultiplierCap;

import java.util.List;
import java.util.Map;

/**
 * Bonus families and caps (ENCHANTS §8.1, EN16), always available: {@code core.services().get(BonusService.class)}
 * (registered by the plugin core, owner {@code core}).
 *
 * <ul>
 *   <li>Systems that <b>give</b> bonuses register a {@link BonusSource} (enchants, later masteries, sets, ranks, boosters).</li>
 *   <li>Systems that <b>use</b> a family ask once per action: {@link #capped}, {@link #multiplier} or {@link #apply}. They
 *       never add bonuses themselves on top, so no combination can pass the cap.</li>
 *   <li>The {@link BonusFamily#SELL} cap is always the economy's ({@code EconomyService#sellBonusCapPercent}, +300%);
 *       sell code applies it with {@link MultiplierCap}, which {@link #apply} uses.</li>
 * </ul>
 * Main thread unless a source says otherwise.
 */
public interface BonusService {

    /** Adds or replaces the source with this id (e.g. {@code enchants}). */
    void register(String id, BonusSource source);

    /** Removes a source (on module disable). */
    void unregister(String id);

    /** The current cap of a family. */
    double cap(BonusFamily family);

    /** Sets family caps (from {@code enchanter.yml}); the SELL cap is ignored here (it is the economy's). */
    void caps(Map<BonusFamily, Double> caps);

    /** Every source's value for this family and context (sources that add nothing are left out). */
    List<BonusPart> breakdown(BonusFamily family, BonusContext context);

    /** Sum of all sources, before the cap. */
    default double total(BonusFamily family, BonusContext context) {
        return BonusMath.sum(breakdown(family, context).stream().map(BonusPart::value).toList());
    }

    /** The total after the cap. */
    default double capped(BonusFamily family, BonusContext context) {
        return BonusMath.capped(total(family, context), cap(family));
    }

    /** Whether the cap cuts this total (menus show "capped"). */
    default boolean isCapped(BonusFamily family, BonusContext context) {
        return BonusMath.isCapped(total(family, context), cap(family));
    }

    /** {@code 1 + capped / 100} (percent families). */
    default double multiplier(BonusFamily family, BonusContext context) {
        return BonusMath.multiplier(total(family, context), cap(family));
    }

    /** {@code base} with the capped bonus, rounded down, never overflowing (whole units: crystals, money). */
    default long apply(BonusFamily family, BonusContext context, long base) {
        return MultiplierCap.apply(base, total(family, context), cap(family));
    }
}

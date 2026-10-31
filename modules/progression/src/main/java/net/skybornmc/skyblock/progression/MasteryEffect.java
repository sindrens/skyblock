package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.bonus.BonusFamily;

import java.util.Locale;
import java.util.Optional;

/**
 * What an island mastery does (PHASE-5 M5.5), the {@code effect} of a {@code masteries.yml} entry. The value of a rank is
 * the effect at that rank (never added up). {@link #live()} effects are wired to an existing feature; the others are
 * hidden on the live server and can't be bought until their feature exists (GUI-2 hide-unbuilt rule; the config entry
 * stays as the hook).
 */
enum MasteryEffect {
    /**
     * MA1 +% logs (EXTRA_DROPS, activity foraging). Not live: while the tools module is loaded the Depths roll asks only
     * the tool's own fortune ({@code ToolService#dropMultiplier}), and island logs have no drop hook.
     */
    TIMBER("timber", false, BonusFamily.EXTRA_DROPS, "foraging"),
    /** MA2 +% break speed (SPEED). Not live: the speed family is read per tool only (no player sources). */
    SWIFT_HANDS("swift-hands", false, BonusFamily.SPEED, null),
    /** MA3 +% damage to mobs (MOB_DAMAGE). The enchants module applies the family to mobs only, never to players. */
    WARRIOR("warrior", true, BonusFamily.MOB_DAMAGE, null),
    /** MA4 +% satchel size ({@code SatchelService#capacityBonus}). */
    DEEP_POCKETS("deep-pockets", true, null, null),
    /** MA6 +% crystals (CRYSTALS: Depths blocks and combat). */
    CRYSTAL_RUSH("crystal-rush", true, BonusFamily.CRYSTALS, null),
    /** MA7 +% rare finds (RARE_FINDS: key fragments, blueprints in the Depths roll). */
    LUCKY_STRIKE("lucky-strike", true, BonusFamily.RARE_FINDS, null),
    /** MA8 island fuel lasts longer (Phase 7): live while Island Fuel is on ({@link IslandFuelFeature}). */
    FUEL_EFFICIENCY("fuel-efficiency", false, null, null),
    /** MA9 faster breeding, more animal drops: no breeding hook yet. */
    BEAST_TAMER("beast-tamer", false, null, null),
    /** MA10 fishing speed and loot: no fishing yet. */
    ANGLER("angler", false, null, null),
    /** MA11 extra shared island homes ({@code IslandService#extraIslandHomesProvider}, owner P5-21). */
    HOMESTEAD("homestead", true, null, null),
    /** MA12 island fly for everyone on the island ({@code IslandService#wingsProvider}). */
    WINGS("wings", true, null, null),
    /** Treasury (owner 169): bank withdrawal fee step ({@code IslandService#bankFeeMasteryProvider}). */
    TREASURY("treasury", true, null, null),
    /** MA5 +% sell prices (SELL family, inside the +300% sell cap). */
    MARKET_CONTACTS("market-contacts", true, BonusFamily.SELL, null);

    private final String id;
    private final boolean live;
    private final BonusFamily family;
    private final String activity;

    MasteryEffect(String id, boolean live, BonusFamily family, String activity) {
        this.id = id;
        this.live = live;
        this.family = family;
        this.activity = activity;
    }

    /** Key in {@code masteries.yml} and the message keys ({@code masteries.effect.<id>}). */
    String id() {
        return id;
    }

    /** True when the feature behind it exists (otherwise hidden, never bought). */
    boolean live() {
        return live || (this == FUEL_EFFICIENCY && IslandFuelFeature.on());
    }

    /** The bonus family this effect adds to, or null (hook effects). */
    BonusFamily family() {
        return family;
    }

    /** The bonus context activity this effect is limited to, or null (any activity). */
    String activity() {
        return activity;
    }

    static Optional<MasteryEffect> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (MasteryEffect e : values()) {
            if (e.id.equals(key)) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }
}

package net.skybornmc.skyblock.progression;

import java.util.function.BooleanSupplier;

/**
 * Whether Island Fuel exists right now (PHASE-7 M7.1): true while the production module publishes its
 * {@code ProductionService} ({@code production.yml → fuel.enabled}). The Fuel Tank upgrade and the Fuel Efficiency
 * mastery (MA8) are live only then; otherwise they stay "Coming soon" / hidden, so nobody pays for nothing.
 */
final class IslandFuelFeature {

    private static volatile BooleanSupplier source = () -> false;

    private IslandFuelFeature() {
    }

    static boolean on() {
        return source.getAsBoolean();
    }

    /** Set by the progression module on enable ({@code null} = off, also on disable and in tests). */
    static void source(BooleanSupplier supplier) {
        source = supplier == null ? () -> false : supplier;
    }
}

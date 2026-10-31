package net.skybornmc.skyblock.progression;

import java.util.function.BooleanSupplier;

/**
 * Whether island farming exists right now (PHASE-7 M7.3): true while the production module publishes its
 * {@code FarmingService} ({@code crops.yml → enabled}). The Crop Growth and Harvest Yield upgrades are live only then;
 * otherwise they stay "Coming soon", so nobody pays for nothing.
 */
final class FarmingFeature {

    private static volatile BooleanSupplier source = () -> false;

    private FarmingFeature() {
    }

    static boolean on() {
        return source.getAsBoolean();
    }

    /** Set by the progression module on enable ({@code null} = off, also on disable and in tests). */
    static void source(BooleanSupplier supplier) {
        source = supplier == null ? () -> false : supplier;
    }
}

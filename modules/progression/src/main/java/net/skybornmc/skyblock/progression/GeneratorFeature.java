package net.skybornmc.skyblock.progression;

import java.util.function.BooleanSupplier;

/**
 * Whether custom island generators exist right now (PHASE-7 M7.2): true while the production module publishes its
 * {@code GeneratorService} ({@code generators.yml → enabled}). The Generator upgrade is live only then; otherwise it stays
 * "Coming soon", so nobody pays for nothing.
 */
final class GeneratorFeature {

    private static volatile BooleanSupplier source = () -> false;

    private GeneratorFeature() {
    }

    static boolean on() {
        return source.getAsBoolean();
    }

    /** Set by the progression module on enable ({@code null} = off, also on disable and in tests). */
    static void source(BooleanSupplier supplier) {
        source = supplier == null ? () -> false : supplier;
    }
}

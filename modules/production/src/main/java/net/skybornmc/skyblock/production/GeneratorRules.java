package net.skybornmc.skyblock.production;

/**
 * Pure generator rules (PHASE-7 M7.2), unit-tested: what a form event does and how much Mining pillar XP a mined block gives.
 */
final class GeneratorRules {

    /** What happens to a block lava + water formed on an island. */
    enum Form {
        /** Nothing custom: vanilla (level 0, below the lowest level, generators off). */
        VANILLA,
        /** Plain block because of the lag guard or an Asleep island. */
        PLAIN,
        /** A roll from the level table. */
        ROLL
    }

    private GeneratorRules() {
    }

    /**
     * @param levelRolls  the island's level can form ores at all
     * @param asleep      the island is Asleep and asleep islands form plain blocks
     * @param withinLimit the lag guard allows one more roll this second (only asked when it matters)
     */
    static Form form(boolean levelRolls, boolean asleep, java.util.function.BooleanSupplier withinLimit) {
        if (!levelRolls) {
            return Form.VANILLA;
        }
        if (asleep) {
            return Form.PLAIN;
        }
        return withinLimit.getAsBoolean() ? Form.ROLL : Form.PLAIN;
    }

    /**
     * Mining pillar XP for a mined generator block (PD8): {@code percent}% of the matching Depths ore's XP, only for an ore
     * (not cobble) the generator really made, mined by a member who counts for that island. Never negative.
     */
    static double pillarXp(GeneratorTable.Ore ore, boolean generatorMade, boolean counts, double percent) {
        if (ore == null || !ore.ore() || !generatorMade || !counts || !(percent > 0)) {
            return 0;
        }
        return ore.depthsXp() * percent / 100.0;
    }
}

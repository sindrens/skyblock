package net.skybornmc.skyblock.islandtools;

/**
 * Tools v5 (owner 7 Nov 2026, docs/plans/TOOLS_V5.md): converts an old shared island perk level (100-level tracks) to
 * the new personal long track with the same effect, so nobody loses anything. Pure.
 */
final class PerkSeed {

    private PerkSeed() {
    }

    /** Old effect per level divided by the new one (shards 3% -> 1%, drops 0.5% -> 0.15% ...); 1 = same per level. */
    static double factor(Upgrades.Effect effect) {
        return switch (effect) {
            case SHARDS -> 3.0;      // 3% a level -> 1%
            case DROPS -> 0.5 / 0.15;
            case WISDOM -> 2.0;      // 2% -> 1%
            case GROWTH -> 0.5 / 0.2;
            case SELL -> 0.1 / 0.2;
            case LUCKY -> 10.0;      // 0.01% -> 0.001%
            default -> 1.0;          // enchant-like, haste, smelter, storage, auto-sell, sweep: the same per level
        };
    }

    /** The new level for an old one (at least 1 when the old was above 0, never above the new max). */
    static int convert(Upgrades.Effect effect, int oldLevel, int newMax) {
        if (oldLevel <= 0) {
            return 0;
        }
        return (int) Math.max(1, Math.min(newMax, Math.round(oldLevel * factor(effect))));
    }
}

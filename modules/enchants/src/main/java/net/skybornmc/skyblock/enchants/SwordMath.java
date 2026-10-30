package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.bonus.BonusMath;

/**
 * Numbers of the Depths sword enchants (ENCHANTS §6.5, §8.3), pure and unit-tested.
 */
final class SwordMath {

    private SwordMath() {
    }

    /**
     * Damage multiplier against a mob: the mob damage family of all sources (Phoenix Edge and later masteries/sets), plus
     * Giant Slayer against bosses, capped once (EN16).
     */
    static double mobMultiplier(double familyTotal, double giantSlayer, boolean boss, double cap) {
        double extra = boss && giantSlayer > 0 ? giantSlayer : 0;
        return BonusMath.multiplier(Math.max(0, familyTotal) + extra, cap);
    }

    /** Damage multiplier against a player (EN17): only Phoenix Edge, at its PvP strength. */
    static double pvpMultiplier(double phoenixEdge, double pvpPercent) {
        if (!(phoenixEdge > 0) || !(pvpPercent > 0)) {
            return 1.0;
        }
        return 1.0 + phoenixEdge * pvpPercent / 100.0 / 100.0;
    }

    /** {@code value} at a strength in percent (PvP values, §8.3). */
    static double scaled(double value, double strengthPercent) {
        return Math.max(0, value) * Math.max(0, strengthPercent) / 100.0;
    }

    /** Bloodthirst: health healed for a hit that dealt {@code dealt} damage. */
    static double heal(double dealt, double percent) {
        return dealt > 0 && percent > 0 ? dealt * percent / 100.0 : 0;
    }

    /** Executioner: a non-boss mob left below the threshold (percent of max health) after this hit dies. */
    static boolean executes(double healthAfter, double maxHealth, double thresholdPercent, boolean boss) {
        if (boss || !(healthAfter > 0) || !(maxHealth > 0) || !(thresholdPercent > 0)) {
            return false;
        }
        return healthAfter / maxHealth * 100.0 < thresholdPercent;
    }

    /** Cleave / Phoenix Wrath: a share of a hit's damage in percent. */
    static double share(double damage, double percent) {
        return damage > 0 && percent > 0 ? damage * percent / 100.0 : 0;
    }
}

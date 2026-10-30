package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.wardrobe.WornPiece;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Armor enchant effect numbers (E5, ENCHANTS §6.8, §8.1, §8.3), pure and unit-tested. The listener asks here and then
 * changes the damage, attributes and effects; families (health, damage reduction) go through the BonusService so their
 * caps hold.
 *
 * <p><b>Warzone</b> (WARDROBE §3.6 WD18 + ENCHANTS §8.3 EN17): an armor enchant works there at its PvP strength
 * ({@code pvp-percent}, 0 = off), and an enchant of a bonus family only if W2 lets that family work in the warzone
 * ({@code wardrobe.yml → warzone.families}); outside the warzone it works fully, and against players at its PvP strength.</p>
 */
final class ArmorMath {

    static final String VITALITY = "vitality";
    static final String FEATHERFALL = "featherfall";
    static final String NIGHT_EYE = "night_eye";
    static final String FORTITUDE = "fortitude";
    static final String SWIFTFOOT = "swiftfoot";
    static final String EMBER_WARD = "ember_ward";
    static final String SECOND_WIND = "second_wind";
    static final String THORNS = "phoenix_thorns";
    static final String REBIRTH = "phoenix_rebirth";

    private ArmorMath() {
    }

    /** The working armor enchants of the worn pieces: id → level on each piece that has it. */
    static Map<String, List<Integer>> working(List<WornPiece> worn, ArmorRules rules, Function<String, Optional<EnchantDef>> lookup) {
        Map<String, List<Integer>> out = new HashMap<>();
        for (WornPiece w : worn) {
            for (ToolPerk perk : w.enchants()) {
                if (rules.working(w.piece(), perk, w.level(), lookup)) {
                    int max = lookup.apply(perk.id()).map(EnchantDef::maxLevel).orElse(perk.level());
                    out.computeIfAbsent(perk.id(), k -> new ArrayList<>()).add(Math.min(perk.level(), max));
                }
            }
        }
        return out;
    }

    /**
     * Strength of an enchant now, 0-1: 1 normally; in the warzone its PvP share, and 0 when its family is not allowed
     * there by W2.
     */
    static double strength(EnchantDef def, boolean warzone, ToDoubleFunction<BonusFamily> warzoneFactor) {
        if (!warzone) {
            return 1;
        }
        if (def.family() != null && !(warzoneFactor.applyAsDouble(def.family()) > 0)) {
            return 0;
        }
        return Math.max(0, Math.min(1, def.pvpPercent() / 100.0));
    }

    /** Sum of a per-level value over every piece that has the enchant. */
    static double sum(EnchantDef def, String key, List<Integer> levels) {
        double total = 0;
        for (int level : levels) {
            double v = def.value(key, level);
            if (Double.isFinite(v)) {
                total += v;
            }
        }
        return total;
    }

    /** The highest level among the pieces (0 = not worn). */
    static int best(List<Integer> levels) {
        int best = 0;
        for (int l : levels) {
            best = Math.max(best, l);
        }
        return best;
    }

    /** Vitality: extra hearts over all pieces, at most {@code all-pieces-max} (§6.8: "all armor together max +10 hearts"). */
    static double vitalityHearts(EnchantDef def, List<Integer> levels) {
        double sum = sum(def, "hearts", levels);
        double cap = def.value("all-pieces-max", 1);
        return Double.isFinite(cap) && cap >= 0 ? Math.min(sum, cap) : sum;
    }

    /** Damage left after a percent reduction (0-100, clamped). */
    static double reduced(double damage, double percent) {
        double p = Math.max(0, Math.min(100, percent));
        return damage * (1 - p / 100.0);
    }

    /** Phoenix Thorns: the reflected share in percent, summed over chestplate and leggings, at most {@code maxPercent}. */
    static double thornsPercent(EnchantDef def, List<Integer> levels, double strength, double maxPercent) {
        return Math.max(0, Math.min(maxPercent, sum(def, "percent", levels) * strength));
    }

    /** Second Wind: health points to heal this refresh (hearts per second × 2 × seconds × strength), 0 while still hurt recently. */
    static double secondWindHeal(EnchantDef def, int level, long msSinceDamage, double refreshSeconds, double strength) {
        if (level <= 0 || !(strength > 0)) {
            return 0;
        }
        double delay = def.value("delay", level);
        if (Double.isFinite(delay) && msSinceDamage < delay * 1000) {
            return 0;
        }
        double hearts = def.value("hearts", level);
        return Double.isFinite(hearts) ? Math.max(0, hearts * 2 * refreshSeconds * strength) : 0;
    }

    /** Phoenix Rebirth: does this hit kill (final damage at least health + absorption)? */
    static boolean lethal(double finalDamage, double health, double absorption) {
        return finalDamage >= health + Math.max(0, absorption);
    }

    /** Phoenix Rebirth cooldown: ready when the last trigger is at least {@code cooldownSeconds} ago (or never). */
    static boolean rebirthReady(Long lastMs, long nowMs, double cooldownSeconds) {
        return lastMs == null || nowMs - lastMs >= (long) (Math.max(0, cooldownSeconds) * 1000);
    }
}

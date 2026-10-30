package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.bonus.BonusFamily;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntToDoubleFunction;

/**
 * How worn pieces turn into bonuses (WARDROBE §3.4, WD17). Pure and unit-tested:
 * <ol>
 *   <li>Count worn pieces per set id; a merged entry counts for both of its sets.</li>
 *   <li>2+ pieces give the 2-piece bonuses, 4 pieces also the 4-piece bonuses.</li>
 *   <li>The multiplier is that of the lowest-level entry contributing pieces to that set.</li>
 *   <li>Disabled sets give nothing. The family caps are applied later, once, by the {@code BonusService}.</li>
 * </ol>
 */
final class SetBonuses {

    /** One worn piece: the entry's set (and merged second set, or null) and the entry's level. */
    record Worn(String setId, String mergedSetId, int level) {
    }

    /** One active bonus line after the multiplier. */
    record Line(String setId, BonusFamily family, double value, List<String> activities, String text, boolean four) {
        boolean appliesTo(String activity) {
            return activities.isEmpty() || (activity != null && activities.contains(activity));
        }
    }

    /** What the worn pieces give, by family. */
    record Result(Map<String, Integer> pieces, Map<BonusFamily, List<Line>> lines) {
        static final Result EMPTY = new Result(Map.of(), Map.of());

        /** Sum of the lines of a family that count for this activity (before the family cap). */
        double value(BonusFamily family, String activity) {
            List<Line> list = lines.get(family);
            if (list == null) {
                return 0;
            }
            double sum = 0;
            for (Line l : list) {
                if (l.appliesTo(activity)) {
                    sum += l.value();
                }
            }
            return sum;
        }

        List<Line> all() {
            List<Line> out = new ArrayList<>();
            lines.values().forEach(out::addAll);
            return out;
        }
    }

    private SetBonuses() {
    }

    /**
     * Warzone/duel normalization (WARDROBE §3.6, WD18): every set counts at {@code multiplier} (1×) whatever its level, and
     * only the listed families work, each scaled by its factor (damage reduction 0.5, conquest 1). Families not listed
     * give nothing there (mob damage, gathering, sell, XP).
     */
    record Normalization(double multiplier, Map<BonusFamily, Double> factors) {
        Normalization {
            factors = Map.copyOf(factors);
        }
    }

    /**
     * @param multiplier bonus multiplier of a set level (see {@link #multiplier})
     */
    static Result compute(SetRegistry registry, Collection<Worn> worn, IntToDoubleFunction multiplier) {
        return compute(registry, worn, multiplier, null);
    }

    /**
     * @param multiplier    bonus multiplier of a set level (see {@link #multiplier})
     * @param normalization warzone rules, or null for the normal rules
     */
    static Result compute(SetRegistry registry, Collection<Worn> worn, IntToDoubleFunction multiplier, Normalization normalization) {
        if (worn.isEmpty()) {
            return Result.EMPTY;
        }
        Map<String, Integer> count = new LinkedHashMap<>();
        Map<String, Integer> lowest = new LinkedHashMap<>();
        for (Worn w : worn) {
            add(count, lowest, w.setId(), w.level());
            if (w.mergedSetId() != null && !w.mergedSetId().equals(w.setId())) {
                add(count, lowest, w.mergedSetId(), w.level());
            }
        }
        Map<BonusFamily, List<Line>> lines = new EnumMap<>(BonusFamily.class);
        for (Map.Entry<String, Integer> e : count.entrySet()) {
            Optional<SetDef> def = registry.get(e.getKey());
            if (def.isEmpty() || !def.get().enabled() || e.getValue() < 2) {
                continue;
            }
            double m = normalization != null ? normalization.multiplier() : multiplier.applyAsDouble(lowest.get(e.getKey()));
            if (!(m > 0)) {
                continue;
            }
            for (SetDef.Bonus b : def.get().two()) {
                addLine(lines, e.getKey(), b, m, false, normalization);
            }
            if (e.getValue() >= 4) {
                for (SetDef.Bonus b : def.get().four()) {
                    addLine(lines, e.getKey(), b, m, true, normalization);
                }
            }
        }
        return new Result(Map.copyOf(count), lines);
    }

    private static void addLine(Map<BonusFamily, List<Line>> lines, String setId, SetDef.Bonus b, double m, boolean four,
                                Normalization normalization) {
        double factor = 1;
        if (normalization != null) {
            Double f = normalization.factors().get(b.family());
            if (f == null || !(f > 0)) {
                return;
            }
            factor = f;
        }
        lines.computeIfAbsent(b.family(), f -> new ArrayList<>())
                .add(new Line(setId, b.family(), b.value() * m * factor, b.activities(), b.text(), four));
    }

    private static void add(Map<String, Integer> count, Map<String, Integer> lowest, String set, int level) {
        count.merge(set, 1, Integer::sum);
        lowest.merge(set, Math.max(0, level), Math::min);
    }

    /**
     * The bonus multiplier of a set level (WD14): {@code maxMultiplier × level / maxLevel} (0× at 0, 1× at 25, 2× at 50),
     * or {@code fixed} while set levels are off.
     */
    static double multiplier(int level, boolean levelsEnabled, double fixed, int maxLevel, double maxMultiplier) {
        if (!levelsEnabled) {
            return Math.max(0, fixed);
        }
        if (maxLevel <= 0) {
            return 0;
        }
        int l = Math.max(0, Math.min(level, maxLevel));
        return maxMultiplier * l / maxLevel;
    }
}

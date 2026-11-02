package net.skybornmc.skyblock.economy.sell;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Every block sellable (ECON-SELL-SHOP-AH A2, ECON-TABLES E): prices derived from recipes. Pure, unit-tested.
 *
 * <p>Input: the priced items (roots + listed + custom, after the price multiplier) and the server's recipes. Repeated up to
 * {@code maxDepth} rounds: for each recipe whose every input has a priced alternative, the candidate is
 * {@code factor(class) x sum(cheapest priced alternative per input) / output count}; the output takes the lower of its
 * price and the candidate. A hand price above the derived value is lowered (with a note): that is how craft-sell loops
 * start, and with every factor at most 100% no recipe can turn its inputs into more money than they were worth
 * ({@code NoCraftLoopTest}). Excluded items (bone meal, tools, armor, containers ...) are never derived.</p>
 */
public final class SellDerive {

    /** One recipe: {@code count} of {@code output} from these inputs (each input = alternatives, e.g. any plank). */
    public record Recipe(String output, int count, List<List<String>> inputs, Kind kind) {

        public Recipe {
            output = output.toLowerCase(Locale.ROOT);
            count = Math.max(1, count);
            inputs = List.copyOf(inputs.stream().map(List::copyOf).toList());
        }
    }

    /** How the server makes it (the class is decided from this and the item ids, {@link #classOf}). */
    public enum Kind { SHAPED, SHAPELESS, SMELTING, STONECUTTING }

    /** The price classes of ECON-TABLES E. */
    public enum Cls { STORAGE, SHAPING, DECORATIVE, DYEING, PROCESSING, OTHER }

    private static final Pattern SHAPING = Pattern.compile(".*(_planks|_stairs|_slab|_wall|_fence|_fence_gate|_pressure_plate|_button|_door|_trapdoor)$");
    private static final Pattern DECORATIVE = Pattern.compile("^(polished_|chiseled_|cut_|smooth_|mossy_|cracked_).*|.*(_bricks|_pillar|_tiles)$");

    private SellDerive() {
    }

    /** ECON-TABLES E rule table, by recipe kind and item ids. */
    static Cls classOf(Recipe r) {
        if (r.kind() == Kind.SMELTING) {
            return Cls.PROCESSING;
        }
        if (r.kind() == Kind.STONECUTTING) {
            return Cls.SHAPING;
        }
        boolean dye = r.inputs().stream().anyMatch(alts -> alts.stream().anyMatch(k -> k.endsWith("_dye")));
        if (dye) {
            return Cls.DYEING;
        }
        // Named shapes and decorative blocks first (planks from a log are shaping, 90%, not 1-to-4 storage).
        if (SHAPING.matcher(r.output()).matches()) {
            return Cls.SHAPING;
        }
        if (DECORATIVE.matcher(r.output()).matches()) {
            return Cls.DECORATIVE;
        }
        Set<String> kinds = new java.util.HashSet<>();
        for (List<String> alts : r.inputs()) {
            kinds.add(String.join("|", alts));
        }
        int n = r.inputs().size();
        // 9 (or 4) of one item into a block, or a block back into 9 (or 4): storage / compacting.
        if (kinds.size() == 1 && (n == 9 || n == 4) && r.count() == 1) {
            return Cls.STORAGE;
        }
        if (n == 1 && (r.count() == 9 || r.count() == 4)) {
            return Cls.STORAGE;
        }
        return Cls.OTHER;
    }

    /**
     * @param priced    item key -> base price (roots, listed and custom items, multiplier applied)
     * @param recipes   the server's recipes (shaped, shapeless, furnace-like, stonecutting)
     * @param factors   class -> factor in percent (above 100 is cut to 100: a factor above 100 would allow loops)
     * @param exclude   item keys or patterns ({@code *_pickaxe}) that are never derived
     * @param maxDepth  rounds (ECON-TABLES E: 6)
     * @param notes     one line per lowered hand price
     * @return derived and lowered prices only (item key -> price); items that kept their price are not in it
     */
    public static Map<String, Double> derive(Map<String, Double> priced, Collection<Recipe> recipes, Map<Cls, Double> factors,
                                             Collection<String> exclude, int maxDepth, Consumer<String> notes) {
        Map<String, Double> price = new HashMap<>(priced);
        List<Pattern> excluded = new ArrayList<>();
        for (String e : exclude) {
            excluded.add(Pattern.compile(e.toLowerCase(Locale.ROOT).replace(".", "\\.").replace("*", ".*")));
        }
        for (int round = 0; round < Math.max(1, maxDepth); round++) {
            boolean changed = false;
            for (Recipe r : recipes) {
                if (isExcluded(r.output(), excluded)) {
                    continue;
                }
                double sum = 0;
                boolean complete = !r.inputs().isEmpty();
                for (List<String> alts : r.inputs()) {
                    double cheapest = Double.POSITIVE_INFINITY;
                    for (String a : alts) {
                        Double p = price.get(a.toLowerCase(Locale.ROOT));
                        if (p != null && p < cheapest) {
                            cheapest = p;
                        }
                    }
                    if (cheapest == Double.POSITIVE_INFINITY) {
                        complete = false;
                        break;
                    }
                    sum += cheapest;
                }
                if (!complete) {
                    continue;
                }
                double factor = Math.min(100.0, Math.max(0.0, factors.getOrDefault(classOf(r), 90.0))) / 100.0;
                double candidate = round2Down(factor * sum / r.count());
                if (candidate < 0.01) {
                    continue; // worth less than a cent: stays unsellable
                }
                Double now = price.get(r.output());
                if (now == null || candidate < now - 1e-9) {
                    price.put(r.output(), candidate);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        Map<String, Double> out = new HashMap<>();
        for (Map.Entry<String, Double> e : price.entrySet()) {
            Double before = priced.get(e.getKey());
            if (before == null) {
                out.put(e.getKey(), e.getValue());
            } else if (e.getValue() < before - 1e-9) {
                out.put(e.getKey(), e.getValue());
                notes.accept("sell.yml: " + e.getKey() + " lowered from " + before + " to " + e.getValue()
                        + " (a recipe makes it cheaper; a higher price would let players craft and sell for profit)");
            }
        }
        return Map.copyOf(out);
    }

    static boolean isExcluded(String key, List<Pattern> excluded) {
        for (Pattern p : excluded) {
            if (p.matcher(key).matches()) {
                return true;
            }
        }
        return false;
    }

    /** Two decimals, rounded down (a derived price never rounds up into a profit). */
    static double round2Down(double v) {
        return Math.floor(v * 100 + 1e-9) / 100.0;
    }
}

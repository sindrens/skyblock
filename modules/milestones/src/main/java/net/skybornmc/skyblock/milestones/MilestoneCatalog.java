package net.skybornmc.skyblock.milestones;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The chains of {@code milestones.yml} (Complete Developer Spec §7.7): curve presets and templates; a template with
 * {@code for-each} makes one chain per target, with {@code ${target}} replaced in the objective and the perks. Parsed from
 * plain maps and lists (the module hands over the YAML as maps), so it is pure and unit-tested.
 *
 * <p>The load validator (spec §7.7, MUST) reports every problem with the chain id and skips the broken chain: unknown
 * menu / world / action, family not in {@code allowed-families}, non-increasing targets, duplicate ids, a perk whose target
 * is not one of the chain's targets (a production perk may name a machine kind), a progress perk on its own chain, a
 * personal chain counting the same action and target as an island chain, and the 15% budget per family and target.</p>
 */
final class MilestoneCatalog {

    /** Actions the engine counts (spec §7.6 source kind {@code game_action}). */
    static final Set<String> ACTIONS = Set.of("harvest_crop", "break_block", "kill_mob");
    /** Production perks name the machine kind they boost, not the chain's target (spec G10). */
    static final Set<String> MACHINES = Set.of("generator", "spawner", "collector");
    static final String PROGRESS = "milestone_progress";

    private final Map<String, MilestoneModel.Chain> chains;
    /** action + ":" + target -> chains counting it (spec §7.6: built once at load, O(1) per event). */
    private final Map<String, List<MilestoneModel.Chain>> index;

    private MilestoneCatalog(Map<String, MilestoneModel.Chain> chains) {
        this.chains = chains;
        Map<String, List<MilestoneModel.Chain>> idx = new HashMap<>();
        for (MilestoneModel.Chain c : chains.values()) {
            for (String t : c.targets()) {
                idx.computeIfAbsent(key(c.action(), t), k -> new ArrayList<>()).add(c);
            }
        }
        idx.replaceAll((k, v) -> List.copyOf(v));
        this.index = Map.copyOf(idx);
    }

    static String key(String action, String target) {
        return action + ":" + (target == null ? "" : target.toUpperCase(Locale.ROOT));
    }

    /** Chains counting this action on this target (empty list, never null). */
    List<MilestoneModel.Chain> chainsFor(String action, String target) {
        return index.getOrDefault(key(action, target), List.of());
    }

    Optional<MilestoneModel.Chain> chain(String id) {
        return Optional.ofNullable(chains.get(id));
    }

    Collection<MilestoneModel.Chain> all() {
        return chains.values();
    }

    int size() {
        return chains.size();
    }

    /**
     * @param root     the whole file as maps ({@code settings}, {@code curves}, {@code templates})
     * @param caps     live family caps (family id -> cap percent); the allowed families must be in here
     * @param problems one line per problem; a broken chain is skipped, the rest loads
     */
    static MilestoneCatalog parse(Map<String, Object> root, Map<String, Double> caps, Consumer<String> problems) {
        Map<String, Object> settings = map(root.get("settings"));
        Set<String> allowed = new LinkedHashSet<>();
        for (Object f : list(settings.get("allowed-families"))) {
            allowed.add(String.valueOf(f).toLowerCase(Locale.ROOT));
        }
        double budgetShare = number(settings.get("budget-percent-of-cap"), 15) / 100.0;
        Map<String, MilestoneModel.Curve> curves = new HashMap<>();
        map(root.get("curves")).forEach((id, raw) -> {
            Map<String, Object> c = map(raw);
            int tiers = (int) number(c.get("tiers"), 5);
            double base = number(c.get("base"), 0);
            double growth = number(c.get("growth"), 0);
            if (base < 1 || growth <= 1 || tiers < 1 || tiers > 20) {
                problems.accept("milestones.yml curves." + id + ": needs base >= 1, growth > 1 and 1-20 tiers; skipped");
            } else {
                curves.put(id, new MilestoneModel.Curve(base, growth, tiers));
            }
        });
        Map<String, MilestoneModel.Chain> out = new LinkedHashMap<>();
        map(root.get("templates")).forEach((templateId, raw) -> {
            Map<String, Object> t = map(raw);
            Map<String, Object> each = map(t.get("for-each"));
            if (each.isEmpty()) {
                build(templateId, templateId, t, Map.of(), curves, allowed, problems).ifPresent(c -> put(out, c, problems));
                return;
            }
            each.forEach((target, overrideRaw) -> {
                Map<String, Object> o = map(overrideRaw);
                String id = text(o.get("id"), templateId + "_" + target.toLowerCase(Locale.ROOT));
                Map<String, String> vars = Map.of("target", target.toUpperCase(Locale.ROOT));
                Map<String, Object> merged = new LinkedHashMap<>(t);
                merged.putAll(o);
                build(id, templateId, merged, vars, curves, allowed, problems).ifPresent(c -> put(out, c, problems));
            });
        });
        dropIslandDuplicates(out, problems);
        checkBudget(out, caps, allowed, budgetShare, problems);
        return new MilestoneCatalog(Map.copyOf(out));
    }

    private static void put(Map<String, MilestoneModel.Chain> out, MilestoneModel.Chain c, Consumer<String> problems) {
        if (out.putIfAbsent(c.id(), c) != null) {
            problems.accept("milestones.yml: chain id '" + c.id() + "' is used twice; the second one is skipped");
        }
    }

    private static Optional<MilestoneModel.Chain> build(String id, String template, Map<String, Object> t, Map<String, String> vars,
                                                        Map<String, MilestoneModel.Curve> curves, Set<String> allowed,
                                                        Consumer<String> problems) {
        String where = "milestones.yml templates." + template + " (" + id + ")";
        if (!id.matches("[a-z0-9_]{1,48}")) {
            problems.accept(where + ": id must be a-z, 0-9, _ (at most 48); skipped");
            return Optional.empty();
        }
        Optional<MilestoneModel.Menu> menu = MilestoneModel.Menu.of(text(t.get("menu"), ""));
        if (menu.isEmpty()) {
            problems.accept(where + ": menu must be island or personal; skipped");
            return Optional.empty();
        }
        Map<String, Object> objective = map(t.get("objective"));
        String action = text(t.containsKey("action") ? t.get("action") : objective.get("action"), "").toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            problems.accept(where + ": unknown action '" + action + "' (known: " + ACTIONS + "); skipped");
            return Optional.empty();
        }
        Map<String, Object> filter = map(objective.get("filter"));
        Optional<MilestoneModel.WorldFilter> world = MilestoneModel.WorldFilter.of(text(filter.get("world"), "any"));
        if (world.isEmpty()) {
            problems.accept(where + ": world must be island, grind or any; skipped");
            return Optional.empty();
        }
        if (menu.get() == MilestoneModel.Menu.ISLAND && world.get() == MilestoneModel.WorldFilter.GRIND) {
            problems.accept(where + ": an island chain can't count /farm and /mine actions (island-world actions only); skipped");
            return Optional.empty();
        }
        Set<String> targets = new LinkedHashSet<>();
        Object rawTargets = filter.containsKey("material") ? filter.get("material") : filter.get("entity");
        for (Object o : rawTargets instanceof List<?> l ? l : List.of(String.valueOf(rawTargets))) {
            String v = fill(String.valueOf(o), vars).toUpperCase(Locale.ROOT).trim();
            if (!v.isEmpty() && !v.equals("NULL")) {
                targets.add(v);
            }
        }
        if (targets.isEmpty() || targets.stream().anyMatch(v -> !v.matches("[A-Z0-9_]{1,64}"))) {
            problems.accept(where + ": needs filter.material or filter.entity (upper-case keys); skipped");
            return Optional.empty();
        }
        MilestoneModel.Curve curve = curves.get(text(t.get("curve"), "standard"));
        if (curve == null) {
            problems.accept(where + ": unknown curve '" + text(t.get("curve"), "standard") + "'; skipped");
            return Optional.empty();
        }
        long[] tiers = curve.targets(number(t.get("difficulty"), 1.0));
        Map<Integer, List<MilestoneModel.Perk>> perks = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map(t.get("perks")).entrySet()) {
            int tier;
            try {
                tier = Integer.parseInt(e.getKey().trim());
            } catch (NumberFormatException ex) {
                problems.accept(where + ": perk tier '" + e.getKey() + "' is not a number; skipped");
                return Optional.empty();
            }
            if (tier < 1 || tier > tiers.length) {
                problems.accept(where + ": perks for tier " + tier + " but the curve has " + tiers.length + " tiers; skipped");
                return Optional.empty();
            }
            List<MilestoneModel.Perk> list = new ArrayList<>();
            for (Object p : list(e.getValue())) {
                Map<String, Object> pm = map(p);
                String family = text(pm.get("family"), "").toLowerCase(Locale.ROOT);
                double value = number(pm.get("value"), 0);
                String target = pm.containsKey("target") ? fill(text(pm.get("target"), ""), vars) : null;
                if (!allowed.contains(family)) {
                    problems.accept(where + ": family '" + family + "' is not in settings.allowed-families; skipped");
                    return Optional.empty();
                }
                if (!(value > 0) || value > 1000) {
                    problems.accept(where + ": perk value must be above 0; skipped");
                    return Optional.empty();
                }
                if (target != null && !target.isBlank()) {
                    String up = target.toUpperCase(Locale.ROOT);
                    boolean machine = family.equals("production") && MACHINES.contains(target.toLowerCase(Locale.ROOT));
                    if (!machine && !targets.contains(up)) {
                        problems.accept(where + ": perk target '" + target + "' is not what the chain counts " + targets
                                + " (perks must help the activity being ground); skipped");
                        return Optional.empty();
                    }
                    target = machine ? target.toLowerCase(Locale.ROOT) : up;
                } else {
                    target = null;
                }
                list.add(new MilestoneModel.Perk(family, value, target));
            }
            perks.put(tier, List.copyOf(list));
        }
        String name = text(t.get("name"), id);
        String icon = text(t.get("icon"), targets.iterator().next()).toUpperCase(Locale.ROOT);
        String category = text(t.get("category"), template);
        int weight = (int) Math.max(1, number(t.get("weight"), 1));
        return Optional.of(new MilestoneModel.Chain(id, name, icon, menu.get(), category, action, Set.copyOf(targets),
                world.get(), tiers, Map.copyOf(perks), weight));
    }

    /** A personal chain must not count the same action and target as an island chain (spec §7.2 rule). */
    private static void dropIslandDuplicates(Map<String, MilestoneModel.Chain> out, Consumer<String> problems) {
        Set<String> island = new java.util.HashSet<>();
        for (MilestoneModel.Chain c : out.values()) {
            if (c.menu() == MilestoneModel.Menu.ISLAND) {
                c.targets().forEach(t -> island.add(key(c.action(), t)));
            }
        }
        out.values().removeIf(c -> {
            if (c.menu() != MilestoneModel.Menu.PERSONAL || c.world() == MilestoneModel.WorldFilter.GRIND) {
                return false;
            }
            boolean dup = c.targets().stream().anyMatch(t -> island.contains(key(c.action(), t)));
            if (dup) {
                problems.accept("milestones.yml (" + c.id() + "): a personal chain counts the same action and target as an "
                        + "island chain outside /farm and /mine; skipped");
            }
            return dup;
        });
    }

    /**
     * Spec §9.2: per family and target, the highest perks reachable (last tier of every chain, island and personal, any
     * world) may sum to at most {@code budgetShare} of the live family cap. Over budget = the whole family's chains for
     * that target are reported (and kept: the owner fixes the numbers; the cap still applies on top).
     */
    private static void checkBudget(Map<String, MilestoneModel.Chain> out, Map<String, Double> caps, Set<String> allowed,
                                    double budgetShare, Consumer<String> problems) {
        Map<String, Double> sums = new LinkedHashMap<>();
        for (MilestoneModel.Chain c : out.values()) {
            Map<String, Double> best = new HashMap<>();
            for (List<MilestoneModel.Perk> tier : c.perks().values()) {
                for (MilestoneModel.Perk p : tier) {
                    best.merge(p.family() + "|" + (p.target() == null ? "*" : p.target()), p.value(), Math::max);
                }
            }
            best.forEach((k, v) -> sums.merge(k, v, Double::sum));
        }
        sums.forEach((k, v) -> {
            String family = k.substring(0, k.indexOf('|'));
            Double cap = caps.get(family);
            if (cap == null) {
                if (allowed.contains(family)) {
                    problems.accept("milestones.yml: family '" + family + "' has no live cap");
                }
                return;
            }
            double budget = cap * budgetShare;
            if (v > budget + 1e-9) {
                problems.accept("milestones.yml: perks for " + k.replace('|', ' ') + " reach " + v + "% but the budget is "
                        + budget + "% (" + Math.round(budgetShare * 100) + "% of the " + cap + "% cap)");
            }
        });
    }

    // ------------------------------------------------------------------ tiny map helpers (YAML as plain maps)

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return Map.of();
    }

    static List<?> list(Object o) {
        return o instanceof List<?> l ? l : List.of();
    }

    static String text(Object o, String fallback) {
        return o == null ? fallback : String.valueOf(o).trim();
    }

    static double number(Object o, double fallback) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(o).trim().replace("_", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static String fill(String s, Map<String, String> vars) {
        String out = s;
        for (Map.Entry<String, String> v : vars.entrySet()) {
            out = out.replace("${" + v.getKey() + "}", v.getValue());
        }
        return out;
    }
}

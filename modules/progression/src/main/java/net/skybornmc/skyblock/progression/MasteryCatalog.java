package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.Pillar;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Every island mastery of {@code masteries.yml} (PHASE-5 M5.5), validated on load: a broken entry is skipped with a
 * readable problem, the rest still load. The file has one section, {@code masteries}; every key inside it is one mastery
 * (no plain settings there; those are in {@code masteries-settings.yml}).
 */
final class MasteryCatalog {

    static final String FILE = "masteries.yml";
    static final String SECTION = "masteries";
    static final int MAX_RANKS = 30;

    /** One rank: price tier (island level points), price from the island bank, the effect, pillar and island level gates. */
    record Rank(int tier, long cost, double value, int pillarLevel, int islandLevel) {
    }

    /**
     * One mastery. {@code ranks.get(0)} is rank 1. {@code pillar} is null when the ranks need no pillar level;
     * {@code requiresMaxed} lists upgrade ids that must be fully upgraded first.
     */
    record Def(String id, String icon, int order, MasteryEffect effect, Pillar pillar, List<String> requiresMaxed, boolean enabled,
               List<Rank> ranks) {

        int maxRank() {
            return ranks.size();
        }

        /** Rank {@code n} (1-based), or null outside 1..max. */
        Rank rank(int n) {
            return n < 1 || n > ranks.size() ? null : ranks.get(n - 1);
        }

        /** The challenge book key of rank {@code n}: {@code mastery.<id>.<n>}. */
        String unlockKey(int n) {
            return unlockKey(id, n);
        }

        static String unlockKey(String id, int n) {
            return "mastery." + id + "." + n;
        }

        /** Shown and buyable: switched on and its feature exists. */
        boolean buyable() {
            return enabled && effect.live();
        }

        /** The effect value at a bought rank (0 for rank 0, the last rank's value above the max). */
        double valueAt(int rank) {
            if (rank <= 0 || ranks.isEmpty()) {
                return 0;
            }
            return ranks.get(Math.min(rank, ranks.size()) - 1).value();
        }
    }

    private final Map<String, Def> byId;
    private final List<Def> shown;

    MasteryCatalog(List<Def> defs) {
        Map<String, Def> ids = new LinkedHashMap<>();
        for (Def d : defs) {
            ids.put(d.id(), d);
        }
        this.byId = Map.copyOf(ids);
        this.shown = ids.values().stream().filter(Def::buyable)
                .sorted(Comparator.comparingInt(Def::order).thenComparing(Def::id)).toList();
    }

    static MasteryCatalog empty() {
        return new MasteryCatalog(List.of());
    }

    Optional<Def> get(String id) {
        return Optional.ofNullable(id == null ? null : byId.get(id));
    }

    /** Every loaded mastery (also switched-off and hidden ones: bought ranks keep working). */
    List<Def> all() {
        return List.copyOf(byId.values());
    }

    /** The masteries shown in the menu (switched on and live), by order. Hidden ones are left out (GUI-2). */
    List<Def> shown() {
        return shown;
    }

    int size() {
        return byId.size();
    }

    // ------------------------------------------------------------------ loading

    static MasteryCatalog load(ConfigurationSection yaml, Predicate<String> isItem, Consumer<String> problems) {
        ConfigurationSection root = yaml == null ? null : yaml.getConfigurationSection(SECTION);
        if (root == null) {
            problems.accept(FILE + ": no '" + SECTION + "' section, no masteries loaded");
            return empty();
        }
        List<Def> defs = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            List<String> errors = new ArrayList<>();
            Def def = parse(id, root.getConfigurationSection(id), isItem, errors);
            if (def == null) {
                errors.forEach(e -> problems.accept(FILE + " → " + SECTION + "." + id + ": " + e + " (skipped)"));
            } else {
                defs.add(def);
            }
        }
        return new MasteryCatalog(defs);
    }

    static Def parse(String id, ConfigurationSection s, Predicate<String> isItem, List<String> errors) {
        if (!UpgradeCatalog.ID.matcher(id).matches()) {
            errors.add("id must be 1-32 characters a-z, 0-9 or _");
            return null;
        }
        if (MasteryRules.ASCENSION_ID.equals(id)) {
            errors.add("'" + id + "' is reserved for Ascension (masteries-settings.yml)");
            return null;
        }
        if (s == null) {
            errors.add("not a section");
            return null;
        }
        MasteryEffect effect = MasteryEffect.byId(s.getString("effect")).orElse(null);
        if (effect == null) {
            errors.add("unknown effect '" + s.getString("effect") + "'");
        }
        String icon = s.getString("icon", "paper");
        if (!isItem.test(icon)) {
            errors.add("unknown icon item '" + icon + "'");
        }
        Pillar pillar = null;
        if (s.contains("pillar")) {
            pillar = Pillar.byId(s.getString("pillar")).orElse(null);
            if (pillar == null) {
                errors.add("unknown pillar '" + s.getString("pillar") + "' (mining, foraging, farming, combat, conquest)");
            }
        }
        List<String> requires = new ArrayList<>();
        for (String r : s.getStringList("requires-maxed")) {
            if (r == null || !UpgradeCatalog.ID.matcher(r.trim()).matches()) {
                errors.add("requires-maxed: '" + r + "' is not an upgrade id");
            } else {
                requires.add(r.trim());
            }
        }
        List<Rank> ranks = new ArrayList<>();
        List<Map<?, ?>> raw = s.getMapList("ranks");
        if (raw.isEmpty()) {
            errors.add("no ranks");
        }
        if (raw.size() > MAX_RANKS) {
            errors.add("more than " + MAX_RANKS + " ranks");
        }
        for (int i = 0; i < raw.size(); i++) {
            Map<?, ?> m = raw.get(i);
            Long tier = number(m.get("tier"));
            Long cost = number(m.get("cost"));
            Double value = decimal(m.get("value"));
            Long pillarLevel = m.containsKey("pillar-level") ? number(m.get("pillar-level")) : Long.valueOf(0);
            Long islandLevel = m.containsKey("island-level") ? number(m.get("island-level")) : Long.valueOf(0);
            int n = i + 1;
            if (tier == null || tier < 1 || tier > UpgradeCatalog.MAX_TIER) {
                errors.add("rank " + n + ": tier must be 1-" + UpgradeCatalog.MAX_TIER);
            }
            if (cost == null || cost < 0) {
                errors.add("rank " + n + ": cost must be a whole number 0 or more");
            }
            if (value == null || value < 0 || !Double.isFinite(value)) {
                errors.add("rank " + n + ": value must be a number 0 or more");
            }
            if (pillarLevel == null || pillarLevel < 0) {
                errors.add("rank " + n + ": pillar-level must be a whole number 0 or more");
            } else if (pillarLevel > 0 && !s.contains("pillar")) {
                errors.add("rank " + n + ": pillar-level needs a 'pillar'");
            }
            if (islandLevel == null || islandLevel < 0) {
                errors.add("rank " + n + ": island-level must be a whole number 0 or more");
            }
            if (tier != null && cost != null && value != null && pillarLevel != null && islandLevel != null) {
                ranks.add(new Rank(tier.intValue(), cost, value, pillarLevel.intValue(), islandLevel.intValue()));
            }
        }
        if (effect != null) {
            checkValues(effect, ranks, errors);
        }
        if (!errors.isEmpty()) {
            return null;
        }
        return new Def(id, icon, s.getInt("order", 0), effect, pillar, List.copyOf(requires), s.getBoolean("enabled", true), List.copyOf(ranks));
    }

    /** Values never go down from rank to rank; counts (homes, fee steps) are whole numbers; Wings has one rank. */
    static void checkValues(MasteryEffect effect, List<Rank> ranks, List<String> errors) {
        double last = -1;
        for (int i = 0; i < ranks.size(); i++) {
            double v = ranks.get(i).value();
            int n = i + 1;
            if (v < last) {
                errors.add("rank " + n + ": value " + UpgradeCatalog.trim(v) + " is lower than rank " + i + "'s");
            }
            last = v;
            boolean whole = v == Math.rint(v);
            switch (effect) {
                case HOMESTEAD -> {
                    if (!whole || v > 50) {
                        errors.add("rank " + n + ": homestead value must be a whole number of homes 0-50");
                    }
                }
                case TREASURY -> {
                    if (!whole || v > 10) {
                        errors.add("rank " + n + ": treasury value must be a whole fee step 0-10 (bank.yml mastery-fee-percent)");
                    }
                }
                default -> {
                }
            }
        }
        if (effect == MasteryEffect.WINGS && ranks.size() != 1) {
            errors.add("wings has exactly one rank");
        }
    }

    private static Long number(Object o) {
        if (o instanceof Number n) {
            double d = n.doubleValue();
            return d == Math.rint(d) ? n.longValue() : null;
        }
        if (o instanceof String str) {
            try {
                return Long.parseLong(str.trim().replace("_", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Double decimal(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o instanceof String str) {
            try {
                return Double.parseDouble(str.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}

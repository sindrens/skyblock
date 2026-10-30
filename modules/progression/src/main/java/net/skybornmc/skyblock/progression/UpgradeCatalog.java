package net.skybornmc.skyblock.progression;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Every island upgrade of {@code upgrades.yml} (PHASE-5 M5.4), validated on load: a broken entry is skipped with a readable
 * problem, the rest still load. The file has one section, {@code upgrades}; every key inside it is one upgrade (no plain
 * settings there; those are in {@code upgrades-settings.yml}).
 */
final class UpgradeCatalog {

    static final String FILE = "upgrades.yml";
    static final String SECTION = "upgrades";
    static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    static final int MAX_TIER = 28;
    static final int MAX_LEVELS = 30;

    /** One level: task tier (island level points), price from the island bank, the effect at this level, island level gate. */
    record Level(int tier, long cost, double value, int minIslandLevel) {
    }

    /** One upgrade. {@code levels.get(0)} is level 1. */
    record Def(String id, UpgradeTab tab, String icon, int order, UpgradeEffect effect, boolean enabled, List<Level> levels) {

        int maxLevel() {
            return levels.size();
        }

        /** Level {@code n} (1-based), or null outside 1..max. */
        Level level(int n) {
            return n < 1 || n > levels.size() ? null : levels.get(n - 1);
        }

        /** The challenge book key of level {@code n}: {@code upgrade.<id>.<n>}. */
        String unlockKey(int n) {
            return unlockKey(id, n);
        }

        static String unlockKey(String id, int n) {
            return "upgrade." + id + "." + n;
        }

        /** Shown and buyable: switched on and its feature exists. */
        boolean buyable() {
            return enabled && effect.live();
        }

        /** The effect value at a bought level (0 for level 0, the last level's value above the max). */
        double valueAt(int level) {
            if (level <= 0 || levels.isEmpty()) {
                return 0;
            }
            return levels.get(Math.min(level, levels.size()) - 1).value();
        }
    }

    private final Map<String, Def> byId;
    private final Map<UpgradeTab, List<Def>> byTab;

    UpgradeCatalog(List<Def> defs) {
        Map<String, Def> ids = new LinkedHashMap<>();
        for (Def d : defs) {
            ids.put(d.id(), d);
        }
        this.byId = Map.copyOf(ids);
        Map<UpgradeTab, List<Def>> tabs = new EnumMap<>(UpgradeTab.class);
        for (UpgradeTab tab : UpgradeTab.values()) {
            tabs.put(tab, ids.values().stream().filter(d -> d.tab() == tab && d.enabled())
                    .sorted(Comparator.comparingInt(Def::order).thenComparing(Def::id)).toList());
        }
        this.byTab = tabs;
    }

    static UpgradeCatalog empty() {
        return new UpgradeCatalog(List.of());
    }

    Optional<Def> get(String id) {
        return Optional.ofNullable(id == null ? null : byId.get(id));
    }

    /** Every loaded upgrade (also switched-off ones: bought levels keep working). */
    List<Def> all() {
        return List.copyOf(byId.values());
    }

    /** Switched-on upgrades of a tab, by order. */
    List<Def> tab(UpgradeTab tab) {
        return byTab.getOrDefault(tab, List.of());
    }

    int size() {
        return byId.size();
    }

    // ------------------------------------------------------------------ loading

    static UpgradeCatalog load(ConfigurationSection yaml, Predicate<String> isItem, Consumer<String> problems) {
        ConfigurationSection root = yaml == null ? null : yaml.getConfigurationSection(SECTION);
        if (root == null) {
            problems.accept(FILE + ": no '" + SECTION + "' section, no upgrades loaded");
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
        return new UpgradeCatalog(defs);
    }

    static Def parse(String id, ConfigurationSection s, Predicate<String> isItem, List<String> errors) {
        if (!ID.matcher(id).matches()) {
            errors.add("id must be 1-32 characters a-z, 0-9 or _");
            return null;
        }
        if (s == null) {
            errors.add("not a section");
            return null;
        }
        UpgradeTab tab = UpgradeTab.byId(s.getString("tab")).orElse(null);
        if (tab == null) {
            errors.add("unknown tab '" + s.getString("tab") + "' (island, economy, depths, production)");
        }
        UpgradeEffect effect = UpgradeEffect.byId(s.getString("effect")).orElse(null);
        if (effect == null) {
            errors.add("unknown effect '" + s.getString("effect") + "'");
        }
        String icon = s.getString("icon", "paper");
        if (!isItem.test(icon)) {
            errors.add("unknown icon item '" + icon + "'");
        }
        List<Level> levels = new ArrayList<>();
        List<Map<?, ?>> raw = s.getMapList("levels");
        if (raw.isEmpty()) {
            errors.add("no levels");
        }
        if (raw.size() > MAX_LEVELS) {
            errors.add("more than " + MAX_LEVELS + " levels");
        }
        for (int i = 0; i < raw.size(); i++) {
            Map<?, ?> m = raw.get(i);
            Long tier = number(m.get("tier"));
            Long cost = number(m.get("cost"));
            Double value = decimal(m.get("value"));
            Long minLevel = m.containsKey("min-island-level") ? number(m.get("min-island-level")) : Long.valueOf(0);
            int n = i + 1;
            if (tier == null || tier < 1 || tier > MAX_TIER) {
                errors.add("level " + n + ": tier must be 1-" + MAX_TIER);
            }
            if (cost == null || cost < 0) {
                errors.add("level " + n + ": cost must be a whole number 0 or more");
            }
            if (value == null || value < 0 || !Double.isFinite(value)) {
                errors.add("level " + n + ": value must be a number 0 or more");
            }
            if (minLevel == null || minLevel < 0) {
                errors.add("level " + n + ": min-island-level must be a whole number 0 or more");
            }
            if (tier != null && cost != null && value != null && minLevel != null) {
                levels.add(new Level(tier.intValue(), cost, value, minLevel.intValue()));
            }
        }
        if (effect != null) {
            checkValues(effect, levels, errors);
        }
        if (!errors.isEmpty()) {
            return null;
        }
        return new Def(id, tab, icon, s.getInt("order", 0), effect, s.getBoolean("enabled", true), List.copyOf(levels));
    }

    /** Values never go down from level to level; sizes are odd border widths; slots whole numbers. */
    static void checkValues(UpgradeEffect effect, List<Level> levels, List<String> errors) {
        double last = -1;
        for (int i = 0; i < levels.size(); i++) {
            double v = levels.get(i).value();
            int n = i + 1;
            if (v < last) {
                errors.add("level " + n + ": value " + trim(v) + " is lower than level " + i + "'s");
            }
            last = v;
            boolean whole = v == Math.rint(v);
            switch (effect) {
                case ISLAND_SIZE -> {
                    if (!whole || v < 3 || v > 31 || ((long) v) % 2 == 0) {
                        errors.add("level " + n + ": island-size value must be an odd border width 3-31 (chunks)");
                    }
                }
                case MEMBER_SLOTS -> {
                    if (!whole || v < 1 || v > 64) {
                        errors.add("level " + n + ": member-slots value must be a whole number 1-64 (leader included)");
                    }
                }
                case WARP_SLOTS, ISLAND_VAULT, SPAWNER_STACK, BANK_CAPACITY, BANK_INTEREST, GENERATOR, FUEL_TANK -> {
                    if (!whole) {
                        errors.add("level " + n + ": " + effect.id() + " value must be a whole number");
                    }
                }
                default -> {
                }
            }
        }
    }

    private static Long number(Object o) {
        if (o instanceof Number n) {
            double d = n.doubleValue();
            return d == Math.rint(d) ? n.longValue() : null;
        }
        if (o instanceof String s) {
            try {
                return Long.parseLong(s.trim().replace("_", ""));
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
        if (o instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}

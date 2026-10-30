package net.skybornmc.skyblock.achievements;

import net.skybornmc.skyblock.common.item.Rarity;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Every category and achievement from {@code achievements.yml}, validated on load: a broken entry is skipped with a
 * readable problem, the rest still load. Pure (no server), unit-tested against the bundled file.
 *
 * <p>The file has exactly two sections, {@code categories} and {@code achievements}; each key inside them is one entry
 * (there are no plain settings in this file: those live in {@code achievements-settings.yml}).</p>
 */
final class AchievementRegistry {

    static final String FILE = "achievements.yml";
    static final String CATEGORIES = "categories";
    static final String ACHIEVEMENTS = "achievements";
    /** Visible characters of a name (Bedrock item names stay on one line). */
    static final int MAX_NAME = 24;
    /** Visible characters of one description line (UI_STYLE §4: lore lines up to 36). */
    static final int MAX_LINE = 36;
    static final int MAX_LINES = 3;
    static final int MAX_TIERS = 10;
    /** The 28 inner slots of a 6-row menu (inside the frame). */
    static final Set<Integer> INNER_SLOTS = Set.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43);

    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,48}");
    private static final Pattern TITLE_ID = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Pattern ICON = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Pattern TAG = Pattern.compile("<[^<>]*>");

    private final Map<String, Category> categories;
    private final Map<String, AchievementDef> achievements;
    private final Map<Metric, List<AchievementDef>> byMetric;

    private AchievementRegistry(Map<String, Category> categories, Map<String, AchievementDef> achievements) {
        this.categories = Collections.unmodifiableMap(categories);
        this.achievements = Collections.unmodifiableMap(achievements);
        Map<Metric, List<AchievementDef>> metric = new EnumMap<>(Metric.class);
        for (AchievementDef def : achievements.values()) {
            metric.computeIfAbsent(def.metric(), m -> new ArrayList<>()).add(def);
        }
        metric.replaceAll((m, list) -> List.copyOf(list));
        this.byMetric = Collections.unmodifiableMap(metric);
    }

    static AchievementRegistry empty() {
        return new AchievementRegistry(new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    Collection<Category> categories() {
        return categories.values();
    }

    Optional<Category> category(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(categories.get(id));
    }

    Collection<AchievementDef> all() {
        return achievements.values();
    }

    Optional<AchievementDef> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(achievements.get(id.toLowerCase(Locale.ROOT)));
    }

    /** Achievements that count this metric (O(1) lookup for event handlers). */
    List<AchievementDef> byMetric(Metric metric) {
        return byMetric.getOrDefault(metric, List.of());
    }

    /** Metrics at least one achievement uses (others need not be tracked). */
    Set<Metric> usedMetrics() {
        return byMetric.keySet();
    }

    List<AchievementDef> inCategory(String category) {
        List<AchievementDef> out = new ArrayList<>();
        for (AchievementDef def : achievements.values()) {
            if (def.category().equals(category)) {
                out.add(def);
            }
        }
        return out;
    }

    int size() {
        return achievements.size();
    }

    /** Distinct reward titles. */
    List<AchievementDef.Title> titles() {
        Map<String, AchievementDef.Title> out = new LinkedHashMap<>();
        for (AchievementDef def : achievements.values()) {
            if (def.hasTitle()) {
                out.putIfAbsent(def.title().id(), def.title());
            }
        }
        return List.copyOf(out.values());
    }

    // ------------------------------------------------------------------ loading

    /**
     * Loads both sections. {@code materialExists} checks icon ids (the server passes a {@code Material} lookup; tests can
     * pass {@code id -> true}). Disabled achievements ({@code enabled: false}) are validated but not loaded.
     */
    static AchievementRegistry load(ConfigurationSection yaml, Predicate<String> materialExists, Consumer<String> problems) {
        Map<String, Category> categories = new LinkedHashMap<>();
        Map<String, AchievementDef> achievements = new LinkedHashMap<>();
        ConfigurationSection cats = yaml == null ? null : yaml.getConfigurationSection(CATEGORIES);
        if (cats == null) {
            problems.accept(FILE + ": no 'categories' section, no achievements loaded");
            return new AchievementRegistry(categories, achievements);
        }
        Set<Integer> slots = new HashSet<>();
        for (String id : cats.getKeys(false)) {
            List<String> errors = new ArrayList<>();
            ConfigurationSection s = cats.getConfigurationSection(id);
            Category category = s == null ? null : parseCategory(id, s, materialExists, errors);
            if (s == null) {
                errors.add("is not a section");
            } else if (category != null && !slots.add(category.slot())) {
                errors.add("slot " + category.slot() + " is used twice");
            }
            if (category == null || !errors.isEmpty()) {
                errors.forEach(e -> problems.accept(FILE + " → categories." + id + ": " + e + " (skipped)"));
                continue;
            }
            categories.put(id, category);
        }
        ConfigurationSection list = yaml.getConfigurationSection(ACHIEVEMENTS);
        if (list == null) {
            problems.accept(FILE + ": no 'achievements' section, no achievements loaded");
            return new AchievementRegistry(categories, achievements);
        }
        for (String id : list.getKeys(false)) {
            List<String> errors = new ArrayList<>();
            ConfigurationSection s = list.getConfigurationSection(id);
            AchievementDef def = s == null ? null : parse(id, s, categories.keySet(), materialExists, errors);
            if (s == null) {
                errors.add("is not a section");
            }
            if (def == null || !errors.isEmpty()) {
                errors.forEach(e -> problems.accept(FILE + " → achievements." + id + ": " + e + " (skipped)"));
                continue;
            }
            if (s.getBoolean("enabled", true)) {
                achievements.put(id, def);
            }
        }
        return new AchievementRegistry(categories, achievements);
    }

    private static Category parseCategory(String id, ConfigurationSection s, Predicate<String> materialExists, List<String> errors) {
        if (!ID.matcher(id).matches()) {
            errors.add("id must be a-z, 0-9 and _ (max 48)");
        }
        String name = s.getString("name", "").trim();
        checkName(name, errors);
        String icon = icon(s, materialExists, errors);
        int slot = s.getInt("slot", -1);
        if (!INNER_SLOTS.contains(slot)) {
            errors.add("slot must be an inner slot (10-16, 19-25, 28-34, 37-43)");
        }
        String description = s.getString("description", "").trim();
        if (visible(description).length() > MAX_LINE) {
            errors.add("description must be at most " + MAX_LINE + " visible characters");
        }
        return errors.isEmpty() ? new Category(id, name, icon, slot, description) : null;
    }

    private static AchievementDef parse(String id, ConfigurationSection s, Set<String> categories, Predicate<String> materialExists,
                                        List<String> errors) {
        if (!ID.matcher(id).matches()) {
            errors.add("id must be a-z, 0-9 and _ (max 48)");
        }
        String name = s.getString("name", "").trim();
        checkName(name, errors);
        List<String> description = s.isList("description") ? s.getStringList("description")
                : s.getString("description", "").isBlank() ? List.of() : List.of(s.getString("description").trim());
        if (description.isEmpty() || description.size() > MAX_LINES) {
            errors.add("description needs 1-" + MAX_LINES + " lines");
        }
        for (String line : description) {
            if (visible(line).length() > MAX_LINE) {
                errors.add("description line '" + visible(line) + "' is longer than " + MAX_LINE + " characters (Bedrock)");
            }
        }
        String icon = icon(s, materialExists, errors);
        String category = s.getString("category", "").trim().toLowerCase(Locale.ROOT);
        if (!categories.contains(category)) {
            errors.add("category must be one of " + categories);
        }
        Optional<Metric> metric = Metric.byKey(s.getString("metric"));
        if (metric.isEmpty()) {
            errors.add("metric '" + s.getString("metric") + "' is unknown (see the list at the top of the file)");
        }
        List<Long> goals = goals(s, errors);
        AchievementDef.Title title = title(s, errors);
        if (!errors.isEmpty() || metric.isEmpty()) {
            return null;
        }
        return new AchievementDef(id, name, description, icon, category, metric.get(), goals, title, s.getBoolean("hidden", false));
    }

    private static void checkName(String name, List<String> errors) {
        int length = visible(name).length();
        if (length == 0 || length > MAX_NAME) {
            errors.add("name must be 1-" + MAX_NAME + " visible characters (Bedrock)");
        }
    }

    private static String icon(ConfigurationSection s, Predicate<String> materialExists, List<String> errors) {
        String icon = s.getString("icon", "").trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
        if (!ICON.matcher(icon).matches() || !materialExists.test(icon)) {
            errors.add("icon '" + icon + "' is not an item id (example: iron_pickaxe)");
        }
        return icon;
    }

    private static List<Long> goals(ConfigurationSection s, List<String> errors) {
        List<Long> goals = new ArrayList<>();
        List<?> raw = s.getList("goals");
        if (raw == null || raw.isEmpty() || raw.size() > MAX_TIERS) {
            errors.add("goals needs 1-" + MAX_TIERS + " numbers, e.g. [10, 100, 1000]");
            return goals;
        }
        long previous = 0;
        for (Object o : raw) {
            if (!(o instanceof Number n) || n.doubleValue() != Math.floor(n.doubleValue())) {
                errors.add("goals must be whole numbers ('" + o + "' is not)");
                return goals;
            }
            long goal = n.longValue();
            if (goal <= previous) {
                errors.add("goals must be positive and rising (" + goal + " after " + previous + ")");
                return goals;
            }
            goals.add(goal);
            previous = goal;
        }
        return goals;
    }

    private static AchievementDef.Title title(ConfigurationSection s, List<String> errors) {
        if (s.isString("title")) {
            String id = s.getString("title", "").trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
                return null;
            }
            if (!TITLE_ID.matcher(id).matches()) {
                errors.add("title must be a title id (a-z, 0-9, _)");
                return null;
            }
            return new AchievementDef.Title(id, null, null);
        }
        ConfigurationSection t = s.getConfigurationSection("title");
        if (t == null) {
            if (s.contains("title")) {
                errors.add("title must be a title id or a section with id, display, rarity");
            }
            return null;
        }
        String id = t.getString("id", "").trim().toLowerCase(Locale.ROOT);
        if (!TITLE_ID.matcher(id).matches()) {
            errors.add("title.id must be a title id (a-z, 0-9, _)");
            return null;
        }
        String display = t.getString("display", "");
        if (visible(display).length() > MAX_NAME) {
            errors.add("title.display must be at most " + MAX_NAME + " visible characters");
        }
        Rarity rarity = Rarity.RARE;
        String rawRarity = t.getString("rarity", "rare").trim().toUpperCase(Locale.ROOT);
        try {
            rarity = Rarity.valueOf(rawRarity);
        } catch (IllegalArgumentException e) {
            errors.add("title.rarity '" + t.getString("rarity") + "' is unknown (common, uncommon, rare, epic, legendary, mythic)");
        }
        return new AchievementDef.Title(id, display, rarity);
    }

    /** Text without MiniMessage tags (for length checks). */
    static String visible(String mini) {
        return mini == null ? "" : TAG.matcher(mini).replaceAll("").trim();
    }
}

package net.skybornmc.skyblock.economy.sell;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The price table from {@code sell.yml} (M3.4): base price, category and on/off per item. Vanilla items are keyed by their
 * item id ({@code coal}), custom items by {@code custom:<id>}. A custom item is <b>only</b> priced by its custom entry,
 * never by the vanilla item it is made of. Broken entries are skipped with a readable problem; the rest still loads.
 * Pure (unit-tested against the bundled file).
 */
public final class SellCatalog {

    public static final String CUSTOM_PREFIX = "custom:";
    static final String VANILLA_ROOT = "categories";
    static final String CUSTOM_ROOT = "custom-items";
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Pattern CATEGORY = Pattern.compile("[a-z0-9_-]{1,32}");

    /**
     * @param key      {@code coal} or {@code custom:depths_coal}
     * @param sellable false = priced but switched off ({@code sellable: false})
     */
    public record Entry(String key, String category, double base, boolean sellable) {

        public boolean custom() {
            return key.startsWith(CUSTOM_PREFIX);
        }
    }

    private final Map<String, Entry> entries;
    private final List<String> categories;

    private SellCatalog(Map<String, Entry> entries) {
        this.entries = Collections.unmodifiableMap(entries);
        List<String> cats = new ArrayList<>();
        entries.values().forEach(e -> {
            if (!cats.contains(e.category())) {
                cats.add(e.category());
            }
        });
        this.categories = List.copyOf(cats);
    }

    public static SellCatalog empty() {
        return new SellCatalog(new LinkedHashMap<>());
    }

    public static String customKey(String id) {
        return CUSTOM_PREFIX + id;
    }

    public Optional<Entry> get(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(entries.get(key));
    }

    /** Every entry in file order. */
    public List<Entry> all() {
        return List.copyOf(entries.values());
    }

    /** Keys of entries that can be sold, in file order. */
    public List<String> sellableKeys() {
        return entries.values().stream().filter(Entry::sellable).map(Entry::key).toList();
    }

    public List<String> categories() {
        return categories;
    }

    public int size() {
        return entries.size();
    }

    /**
     * @param file          the whole {@code sell.yml}
     * @param validMaterial whether a vanilla id is a real item ({@code Material.matchMaterial} on the server)
     * @param problems      one line per problem ("sell.yml → categories.ores.coal: ...")
     */
    public static SellCatalog load(ConfigurationSection file, Predicate<String> validMaterial, Consumer<String> problems) {
        return load(file, validMaterial, 1.0, problems);
    }

    /**
     * ECON-SELL-SHOP-AH A1: every listed price (categories, custom items, roots) is multiplied by {@code multiplier}
     * ({@code economy.yml sell.price-multiplier}) and kept to two decimals. Prices may be decimals (at least 0.01).
     */
    public static SellCatalog load(ConfigurationSection file, Predicate<String> validMaterial, double multiplier, Consumer<String> problems) {
        Map<String, Entry> out = new LinkedHashMap<>();
        double knob = multiplier > 0 && Double.isFinite(multiplier) ? multiplier : 1.0;
        if (file == null) {
            problems.accept("sell.yml: missing; nothing can be sold");
            return new SellCatalog(out);
        }
        ConfigurationSection vanilla = file.getConfigurationSection(VANILLA_ROOT);
        if (vanilla == null) {
            problems.accept("sell.yml: section '" + VANILLA_ROOT + "' is missing");
        } else {
            read(vanilla, VANILLA_ROOT, false, validMaterial, knob, out, problems);
        }
        ConfigurationSection custom = file.getConfigurationSection(CUSTOM_ROOT);
        if (custom != null) {
            read(custom, CUSTOM_ROOT, true, validMaterial, knob, out, problems);
        }
        // A2: roots (uncraftable blocks and plants, id: price), category "blocks"; a listed item keeps its listed price.
        ConfigurationSection roots = file.getConfigurationSection(ROOTS_ROOT);
        if (roots != null) {
            for (String id : roots.getKeys(false)) {
                String at = "sell.yml → " + ROOTS_ROOT + "." + id;
                double price = number(roots.get(id));
                if (!ID.matcher(id).matches() || !validMaterial.test(id)) {
                    problems.accept(at + ": not a vanilla item id (skipped)");
                } else if (!(price >= 0.01)) {
                    problems.accept(at + ": price must be at least 0.01 (skipped)");
                } else if (!out.containsKey(id)) {
                    out.put(id, new Entry(id, ROOTS_CATEGORY, round2(price * knob), true));
                }
            }
        }
        return new SellCatalog(out);
    }

    private static void read(ConfigurationSection root, String rootName, boolean custom, Predicate<String> validMaterial,
                             double knob, Map<String, Entry> out, Consumer<String> problems) {
        for (String category : root.getKeys(false)) {
            String where = "sell.yml → " + rootName + "." + category;
            ConfigurationSection items = root.getConfigurationSection(category);
            if (items == null) {
                problems.accept(where + ": not a section of items (skipped)");
                continue;
            }
            if (!CATEGORY.matcher(category).matches()) {
                problems.accept(where + ": category names use a-z, 0-9, - and _ (skipped)");
                continue;
            }
            for (String id : items.getKeys(false)) {
                String at = where + "." + id;
                if (!ID.matcher(id).matches()) {
                    problems.accept(at + ": item ids are lowercase a-z, 0-9 and _ (skipped)");
                    continue;
                }
                if (!custom && !validMaterial.test(id)) {
                    problems.accept(at + ": '" + id + "' is not a vanilla item id (custom items go under " + CUSTOM_ROOT + ") (skipped)");
                    continue;
                }
                double price;
                boolean sellable = true;
                if (items.isConfigurationSection(id)) {
                    ConfigurationSection s = items.getConfigurationSection(id);
                    price = number(s.get("price"));
                    sellable = s.getBoolean("sellable", true);
                } else if (items.get(id) instanceof Map<?, ?> map) {
                    price = number(map.get("price"));
                    sellable = !(map.get("sellable") instanceof Boolean b) || b;
                } else {
                    price = number(items.get(id));
                }
                if (!(price >= 0.01)) {
                    problems.accept(at + ": price must be a number of at least 0.01 (skipped)");
                    continue;
                }
                price = round2(price * knob);
                String key = custom ? customKey(id) : id;
                if (out.containsKey(key)) {
                    problems.accept(at + ": listed twice (first one kept: " + out.get(key).category() + ")");
                    continue;
                }
                out.put(key, new Entry(key, category, price, sellable));
            }
        }
    }

    /** A2: the section of uncraftable blocks and plants. */
    public static final String ROOTS_ROOT = "roots";
    /** A2: category of roots and derived prices. */
    public static final String ROOTS_CATEGORY = "blocks";

    /** A number from YAML (int, long, double or a numeric string), NaN otherwise. */
    static double number(Object raw) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        if (raw instanceof String str) {
            try {
                return Double.parseDouble(str.trim().replace("_", ""));
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    /** Two decimals (half up), at least 0.01. */
    static double round2(double v) {
        return Math.max(0.01, Math.round(v * 100.0) / 100.0);
    }

    /**
     * A2: this catalog plus derived prices (category {@link #ROOTS_CATEGORY}); a derived price for a listed item replaces
     * it only when lower (the listed entry keeps its category and sellable flag).
     */
    public SellCatalog withDerived(Map<String, Double> derived) {
        Map<String, Entry> out = new LinkedHashMap<>(entries);
        derived.forEach((key, price) -> {
            Entry now = out.get(key);
            if (now == null) {
                out.put(key, new Entry(key, ROOTS_CATEGORY, price, true));
            } else if (price < now.base()) {
                out.put(key, new Entry(key, now.category(), price, now.sellable()));
            }
        });
        return new SellCatalog(out);
    }
}

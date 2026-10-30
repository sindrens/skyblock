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
    public record Entry(String key, String category, long base, boolean sellable) {

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
        Map<String, Entry> out = new LinkedHashMap<>();
        if (file == null) {
            problems.accept("sell.yml: missing; nothing can be sold");
            return new SellCatalog(out);
        }
        ConfigurationSection vanilla = file.getConfigurationSection(VANILLA_ROOT);
        if (vanilla == null) {
            problems.accept("sell.yml: section '" + VANILLA_ROOT + "' is missing");
        } else {
            read(vanilla, VANILLA_ROOT, false, validMaterial, out, problems);
        }
        ConfigurationSection custom = file.getConfigurationSection(CUSTOM_ROOT);
        if (custom != null) {
            read(custom, CUSTOM_ROOT, true, validMaterial, out, problems);
        }
        return new SellCatalog(out);
    }

    private static void read(ConfigurationSection root, String rootName, boolean custom, Predicate<String> validMaterial,
                             Map<String, Entry> out, Consumer<String> problems) {
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
                long price;
                boolean sellable = true;
                if (items.isConfigurationSection(id)) {
                    ConfigurationSection s = items.getConfigurationSection(id);
                    price = s.isInt("price") || s.isLong("price") ? s.getLong("price") : -1;
                    sellable = s.getBoolean("sellable", true);
                } else if (items.get(id) instanceof Map<?, ?> map) {
                    Object raw = map.get("price");
                    price = raw instanceof Integer || raw instanceof Long ? ((Number) raw).longValue() : -1;
                    sellable = !(map.get("sellable") instanceof Boolean b) || b;
                } else if (items.isInt(id) || items.isLong(id)) {
                    price = items.getLong(id);
                } else {
                    price = -1;
                }
                if (price < 1) {
                    problems.accept(at + ": price must be a whole number of at least 1 (skipped)");
                    continue;
                }
                String key = custom ? customKey(id) : id;
                if (out.containsKey(key)) {
                    problems.accept(at + ": listed twice (first one kept: " + out.get(key).category() + ")");
                    continue;
                }
                out.put(key, new Entry(key, category, price, sellable));
            }
        }
    }
}

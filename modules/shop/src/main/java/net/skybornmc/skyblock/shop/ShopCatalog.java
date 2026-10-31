package net.skybornmc.skyblock.shop;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * What the builder shop sells ({@code shop.yml}, Shop v2 / GUI-3): up to 9 tabs in file order (slots 0-8 of the top row),
 * each with an icon and its items (vanilla item id, price per item, what it turns back into, skill requirements, on/off).
 * A tab with {@code kind: spawners} sells stacked spawners: its keys are mob types and its items get the id
 * {@code spawner_<type>}. The {@code worth-only} section keeps prices of items the shop no longer sells, for the island
 * level's block worth. Broken entries are skipped with a readable problem; the rest still loads. Pure (unit-tested
 * against the bundled file).
 */
public final class ShopCatalog {

    static final String ROOT = "categories";
    static final String WORTH_ONLY = "worth-only";
    /** Tabs fit in the top row (slots 0-8). */
    static final int MAX_TABS = 9;
    /** Item id prefix of a spawner sold in a Spawners tab ({@code spawner_pig}). */
    static final String SPAWNER_PREFIX = "spawner_";
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Pattern CATEGORY = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Set<String> SKILLS = Set.of("mining", "foraging", "combat", "farming", "fishing");

    /** What an entry hands out. */
    public enum Kind {
        /** A plain vanilla item. */
        ITEM,
        /** A stacked spawner item of the stacking module (the id is {@code spawner_<mob type>}). */
        SPAWNER
    }

    /**
     * One item for sale.
     *
     * @param id       vanilla item id ({@code oak_log}), or {@code spawner_<type>} for a spawner
     * @param price    money per item
     * @param contains what one item turns back into (crafting it back, breaking it): item id → amount
     * @param requires skill → level needed to buy it ({@code farming → 10})
     */
    public record Item(String id, String category, long price, Map<String, Integer> contains, Map<String, Integer> requires,
                       boolean enabled, Kind kind) {

        public Item {
            contains = Map.copyOf(contains);
            requires = Map.copyOf(requires);
            kind = kind == null ? Kind.ITEM : kind;
        }

        public Item(String id, String category, long price, Map<String, Integer> contains, Map<String, Integer> requires,
                    boolean enabled) {
            this(id, category, price, contains, requires, enabled, Kind.ITEM);
        }

        public boolean spawner() {
            return kind == Kind.SPAWNER;
        }

        /** The mob type of a spawner ({@code pig}), or {@code null} for a plain item. */
        public String mob() {
            return spawner() ? id.substring(SPAWNER_PREFIX.length()) : null;
        }
    }

    /** A tab: id, icon item id, tab slot (0-8), kind, items in file order (disabled ones included). */
    public record Category(String id, String icon, int slot, Kind kind, List<Item> items) {

        public Category {
            items = List.copyOf(items);
        }

        public List<Item> enabledItems() {
            return items.stream().filter(Item::enabled).toList();
        }
    }

    private final List<Category> categories;
    private final Map<String, Item> items;
    private final Map<String, Long> worthOnly;

    private ShopCatalog(List<Category> categories, Map<String, Long> worthOnly) {
        this.categories = List.copyOf(categories);
        Map<String, Item> all = new LinkedHashMap<>();
        for (Category c : categories) {
            for (Item i : c.items()) {
                all.put(i.id(), i);
            }
        }
        this.items = Collections.unmodifiableMap(all);
        this.worthOnly = Map.copyOf(worthOnly);
    }

    public static ShopCatalog empty() {
        return new ShopCatalog(List.of(), Map.of());
    }

    public List<Category> categories() {
        return categories;
    }

    public Optional<Category> category(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.toLowerCase(Locale.ROOT);
        return categories.stream().filter(c -> c.id().equals(key)).findFirst();
    }

    public Optional<Item> item(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(items.get(id));
    }

    /** Every item (enabled or not) in file order. */
    public List<Item> items() {
        return List.copyOf(items.values());
    }

    public int size() {
        return items.size();
    }

    /**
     * The block worth price of an item id: its shop price (sold or switched off), else its {@code worth-only} price
     * (island level, trade hints).
     */
    public OptionalLong worth(String id) {
        Item item = id == null ? null : items.get(id);
        if (item != null && !item.spawner()) {
            return OptionalLong.of(item.price());
        }
        Long old = id == null ? null : worthOnly.get(id);
        return old == null ? OptionalLong.empty() : OptionalLong.of(old);
    }

    Map<String, Long> worthOnly() {
        return worthOnly;
    }

    /** {@link #load(ConfigurationSection, Predicate, Predicate, Consumer)} without a Spawners tab (spawner types rejected). */
    public static ShopCatalog load(ConfigurationSection file, Predicate<String> validMaterial, Consumer<String> problems) {
        return load(file, validMaterial, type -> false, problems);
    }

    /**
     * @param file          the whole {@code shop.yml}
     * @param validMaterial whether an id is a real vanilla item ({@code Material.matchMaterial} on the server)
     * @param validSpawner  whether a mob type can be a shop spawner (the stacking module knows it)
     * @param problems      one line per problem ("shop.yml → categories.wood.items.oak_log: ...")
     */
    public static ShopCatalog load(ConfigurationSection file, Predicate<String> validMaterial, Predicate<String> validSpawner,
                                   Consumer<String> problems) {
        List<Category> out = new ArrayList<>();
        if (file == null) {
            problems.accept("shop.yml: missing; the shop is empty");
            return new ShopCatalog(out, Map.of());
        }
        ConfigurationSection root = file.getConfigurationSection(ROOT);
        if (root == null) {
            problems.accept("shop.yml: section '" + ROOT + "' is missing; the shop is empty");
            return new ShopCatalog(out, Map.of());
        }
        Set<String> seen = new HashSet<>();
        for (String id : root.getKeys(false)) {
            String where = "shop.yml → " + ROOT + "." + id;
            ConfigurationSection cat = root.getConfigurationSection(id);
            if (cat == null) {
                problems.accept(where + ": not a section (skipped)");
                continue;
            }
            if (!CATEGORY.matcher(id).matches()) {
                problems.accept(where + ": tab names use a-z, 0-9, - and _ (skipped)");
                continue;
            }
            if (out.size() >= MAX_TABS) {
                problems.accept(where + ": only " + MAX_TABS + " tabs fit in the top row (skipped)");
                continue;
            }
            String icon = cat.getString("icon", "chest").toLowerCase(Locale.ROOT);
            if (!validMaterial.test(icon)) {
                problems.accept(where + ".icon: '" + icon + "' is not an item id (using chest)");
                icon = "chest";
            }
            String kindRaw = cat.getString("kind", "items").toLowerCase(Locale.ROOT);
            Kind kind = switch (kindRaw) {
                case "spawners" -> Kind.SPAWNER;
                case "items" -> Kind.ITEM;
                default -> {
                    problems.accept(where + ".kind: '" + kindRaw + "' is not 'items' or 'spawners' (using items)");
                    yield Kind.ITEM;
                }
            };
            List<Item> items = readItems(cat.getConfigurationSection("items"), id, kind, where + ".items",
                    kind == Kind.SPAWNER ? validSpawner : validMaterial, seen, problems);
            out.add(new Category(id, icon, out.size(), kind, items));
        }
        Map<String, Long> worth = new LinkedHashMap<>();
        ConfigurationSection old = file.getConfigurationSection(WORTH_ONLY);
        if (old != null) {
            for (String id : old.getKeys(false)) {
                long price = whole(old.get(id));
                if (!ID.matcher(id).matches() || !validMaterial.test(id) || price < 1) {
                    problems.accept("shop.yml → " + WORTH_ONLY + "." + id + ": needs a vanilla item id and a whole price from 1 (skipped)");
                } else if (!seen.contains(id)) {
                    worth.put(id, price);
                }
            }
        }
        return new ShopCatalog(out, worth);
    }

    private static List<Item> readItems(ConfigurationSection items, String category, Kind kind, String where,
                                        Predicate<String> valid, Set<String> seen, Consumer<String> problems) {
        List<Item> out = new ArrayList<>();
        if (items == null) {
            problems.accept(where + ": missing (the tab is empty)");
            return out;
        }
        for (String key : items.getKeys(false)) {
            String at = where + "." + key;
            if (!ID.matcher(key).matches()) {
                problems.accept(at + ": ids are lowercase a-z, 0-9 and _ (skipped)");
                continue;
            }
            if (!valid.test(key)) {
                problems.accept(at + ": '" + key + "' is not " + (kind == Kind.SPAWNER
                        ? "a spawner type of stacking.yml (skipped)" : "a vanilla item id (skipped)"));
                continue;
            }
            String id = kind == Kind.SPAWNER ? SPAWNER_PREFIX + key : key;
            if (!seen.add(id)) {
                problems.accept(at + ": listed twice (first one kept)");
                continue;
            }
            long price;
            boolean enabled = true;
            Map<String, Integer> contains = new LinkedHashMap<>();
            Map<String, Integer> requires = new LinkedHashMap<>();
            Map<?, ?> map = items.isConfigurationSection(key) ? items.getConfigurationSection(key).getValues(false)
                    : items.get(key) instanceof Map<?, ?> m ? m : null;
            if (map != null) {
                price = whole(map.get("price"));
                enabled = !(map.get("enabled") instanceof Boolean b) || b;
                if (!readCounts(section(map.get("contains")), at + ".contains", contains, problems, k -> ID.matcher(k).matches())) {
                    continue;
                }
                if (!readCounts(section(map.get("requires")), at + ".requires", requires, problems, SKILLS::contains)) {
                    continue;
                }
            } else {
                price = whole(items.get(key));
            }
            if (price < 1) {
                problems.accept(at + ": price must be a whole number of at least 1 (skipped)");
                continue;
            }
            out.add(new Item(id, category, price, contains, requires, enabled, kind));
        }
        return out;
    }

    /** A nested map from YAML (a section or a plain map), or null. */
    static Map<?, ?> section(Object raw) {
        if (raw instanceof ConfigurationSection s) {
            return s.getValues(false);
        }
        return raw instanceof Map<?, ?> m ? m : null;
    }

    /** Reads {@code key: whole number >= 1} pairs; false (and a problem) if anything is wrong, so the item is skipped. */
    private static boolean readCounts(Map<?, ?> raw, String at, Map<String, Integer> out, Consumer<String> problems,
                                      Predicate<String> validKey) {
        if (raw == null) {
            return true;
        }
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            String key = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
            long value = whole(e.getValue());
            if (!validKey.test(key) || value < 1 || value > 1_000_000) {
                problems.accept(at + "." + key + ": needs a known name and a whole number from 1 (item skipped, to be safe)");
                return false;
            }
            out.put(key, (int) value);
        }
        return true;
    }

    static long whole(Object raw) {
        return raw instanceof Integer || raw instanceof Long ? ((Number) raw).longValue() : -1;
    }

    /** The price of a raw YAML item entry (a number or a map with {@code price}); -1 if there is none. */
    static long priceOf(Object raw) {
        Map<?, ?> map = section(raw);
        return map != null ? whole(map.get("price")) : whole(raw);
    }
}

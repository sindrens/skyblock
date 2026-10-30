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
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * What the builder shop sells ({@code shop.yml}, M3.5): categories in file order, each with an icon, a menu slot and its
 * items (vanilla item id, price per item, what it turns back into, skill requirements, on/off). Broken entries are skipped
 * with a readable problem; the rest still loads. Pure (unit-tested against the bundled file).
 */
public final class ShopCatalog {

    static final String ROOT = "categories";
    /** Inner slots of the 6-row menu a category may use. */
    static final Set<Integer> INNER_SLOTS = innerSlots();
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Pattern CATEGORY = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Set<String> SKILLS = Set.of("mining", "foraging", "combat", "farming", "fishing");

    /**
     * One item for sale.
     *
     * @param id       vanilla item id ({@code oak_log})
     * @param price    money per item
     * @param contains what one item turns back into (crafting it back, breaking it): item id → amount
     * @param requires skill → level needed to buy it ({@code farming → 10})
     */
    public record Item(String id, String category, long price, Map<String, Integer> contains, Map<String, Integer> requires,
                       boolean enabled) {

        public Item {
            contains = Map.copyOf(contains);
            requires = Map.copyOf(requires);
        }
    }

    /** A category: id, icon item id, menu slot (resolved), items in file order (disabled ones included). */
    public record Category(String id, String icon, int slot, List<Item> items) {

        public Category {
            items = List.copyOf(items);
        }

        public List<Item> enabledItems() {
            return items.stream().filter(Item::enabled).toList();
        }
    }

    private final List<Category> categories;
    private final Map<String, Item> items;

    private ShopCatalog(List<Category> categories) {
        this.categories = List.copyOf(categories);
        Map<String, Item> all = new LinkedHashMap<>();
        for (Category c : categories) {
            for (Item i : c.items()) {
                all.put(i.id(), i);
            }
        }
        this.items = Collections.unmodifiableMap(all);
    }

    public static ShopCatalog empty() {
        return new ShopCatalog(List.of());
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
     * @param file          the whole {@code shop.yml}
     * @param validMaterial whether an id is a real vanilla item ({@code Material.matchMaterial} on the server)
     * @param problems      one line per problem ("shop.yml → categories.wood.items.oak_log: ...")
     */
    public static ShopCatalog load(ConfigurationSection file, Predicate<String> validMaterial, Consumer<String> problems) {
        List<Category> out = new ArrayList<>();
        if (file == null) {
            problems.accept("shop.yml: missing; the shop is empty");
            return new ShopCatalog(out);
        }
        ConfigurationSection root = file.getConfigurationSection(ROOT);
        if (root == null) {
            problems.accept("shop.yml: section '" + ROOT + "' is missing; the shop is empty");
            return new ShopCatalog(out);
        }
        Set<String> seen = new HashSet<>();
        Set<Integer> usedSlots = new HashSet<>();
        Map<String, List<Item>> itemsByCategory = new LinkedHashMap<>();
        Map<String, Integer> slots = new LinkedHashMap<>();
        Map<String, String> icons = new LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            String where = "shop.yml → " + ROOT + "." + id;
            ConfigurationSection cat = root.getConfigurationSection(id);
            if (cat == null) {
                problems.accept(where + ": not a section (skipped)");
                continue;
            }
            if (!CATEGORY.matcher(id).matches()) {
                problems.accept(where + ": category names use a-z, 0-9, - and _ (skipped)");
                continue;
            }
            String icon = cat.getString("icon", "chest").toLowerCase(Locale.ROOT);
            if (!validMaterial.test(icon)) {
                problems.accept(where + ".icon: '" + icon + "' is not an item id (using chest)");
                icon = "chest";
            }
            icons.put(id, icon);
            int slot = cat.getInt("slot", -1);
            if (cat.contains("slot") && (!INNER_SLOTS.contains(slot) || usedSlots.contains(slot))) {
                problems.accept(where + ".slot: " + cat.get("slot") + " is not a free inner slot (10-16, 19-25, 28-34, 37-43); "
                        + "using the next free one");
                slot = -1;
            }
            if (slot >= 0) {
                usedSlots.add(slot);
            }
            slots.put(id, slot);
            itemsByCategory.put(id, readItems(cat.getConfigurationSection("items"), id, where + ".items", validMaterial, seen, problems));
        }
        for (Map.Entry<String, List<Item>> e : itemsByCategory.entrySet()) {
            int slot = slots.get(e.getKey());
            if (slot < 0) {
                slot = nextFree(usedSlots);
                if (slot < 0) {
                    problems.accept("shop.yml → " + ROOT + "." + e.getKey() + ": no free slot left in the menu (skipped)");
                    continue;
                }
                usedSlots.add(slot);
            }
            out.add(new Category(e.getKey(), icons.get(e.getKey()), slot, e.getValue()));
        }
        return new ShopCatalog(out);
    }

    private static List<Item> readItems(ConfigurationSection items, String category, String where, Predicate<String> validMaterial,
                                        Set<String> seen, Consumer<String> problems) {
        List<Item> out = new ArrayList<>();
        if (items == null) {
            problems.accept(where + ": missing (the category is empty)");
            return out;
        }
        for (String id : items.getKeys(false)) {
            String at = where + "." + id;
            if (!ID.matcher(id).matches()) {
                problems.accept(at + ": item ids are lowercase a-z, 0-9 and _ (skipped)");
                continue;
            }
            if (!validMaterial.test(id)) {
                problems.accept(at + ": '" + id + "' is not a vanilla item id (skipped)");
                continue;
            }
            if (!seen.add(id)) {
                problems.accept(at + ": listed twice (first one kept)");
                continue;
            }
            long price;
            boolean enabled = true;
            Map<String, Integer> contains = new LinkedHashMap<>();
            Map<String, Integer> requires = new LinkedHashMap<>();
            Map<?, ?> map = items.isConfigurationSection(id) ? items.getConfigurationSection(id).getValues(false)
                    : items.get(id) instanceof Map<?, ?> m ? m : null;
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
                price = whole(items.get(id));
            }
            if (price < 1) {
                problems.accept(at + ": price must be a whole number of at least 1 (skipped)");
                continue;
            }
            out.add(new Item(id, category, price, contains, requires, enabled));
        }
        return out;
    }

    /** A nested map from YAML (a section or a plain map), or null. */
    private static Map<?, ?> section(Object raw) {
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

    private static long whole(Object raw) {
        return raw instanceof Integer || raw instanceof Long ? ((Number) raw).longValue() : -1;
    }

    private static int nextFree(Set<Integer> used) {
        for (int slot : INNER_SLOTS.stream().sorted().toList()) {
            if (!used.contains(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private static Set<Integer> innerSlots() {
        Set<Integer> out = new HashSet<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                out.add(row * 9 + col);
            }
        }
        return Set.copyOf(out);
    }
}

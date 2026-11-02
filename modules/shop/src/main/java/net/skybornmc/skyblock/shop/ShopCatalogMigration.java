package net.skybornmc.skyblock.shop;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The one-time catalog migrations of the live {@code shop.yml}. Pure (unit-tested).
 *
 * <p><b>v1 &rarr; v2</b> (GUI-3, docs/design/SHOP_DESIGN.md §2, {@link #migrate}): config-version 1 (8 categories, 332
 * items) becomes the 9 tabs and 253 items of the frozen {@code migrations/shop-v2.yml}.
 *
 * <ul>
 *   <li>The result starts from the bundled v2 file (tabs, order, comments).</li>
 *   <li>Every item that stays keeps the live entry (owner price, {@code enabled}, {@code requires}); bundled extras such
 *       as {@code contains} are kept too, so the buy &gt; sell check still knows what an item breaks into.</li>
 *   <li>A price the design changes (hopper, bone meal) only moves when the live value still equals the old default;
 *       an owner-edited price is kept. Each change is reported.</li>
 *   <li>Old default items the design removes leave the shop but keep their live price under {@code worth-only} (island
 *       level block worth). Items the owner added himself are kept, in the tab their old category maps to.</li>
 * </ul>
 *
 * <p><b>v2 &rarr; v3</b> (MENUS-V3 6.3, {@link #migrateV3}): data-driven, it moves the live file to whatever the bundled
 * v3 file says (tab order, {@code groups:}, items), so it needs no item list of its own. A v1 file runs both, in order.
 */
final class ShopCatalogMigration {

    /** Shop v2 (GUI-3): 9 tabs. */
    static final int V2 = 2;
    /** MENUS-V3: tab order 5 + 4, {@code groups:}, the item lists of the bundled file. */
    static final int V3 = 3;
    /** ECON-SELL-SHOP-AH B1: the shop lists of ECON-TABLES D (remove 53, add 46 with {@code price: auto}). */
    static final int V4 = 4;
    /** Owner 2 Nov 2026: tabs Building Blocks, Nature, Farming, Mob Drops, Colors, Decoration, Essentials, Redstone, Spawners. */
    static final int V5 = 5;
    /** The current version (what a fresh install writes). */
    static final int VERSION = V5;

    /** {@code /shop <old tab>} still finds the tab its items moved to. */
    static final Map<String, String> TAB_ALIASES = Map.of(
            "wood", "building",
            "stone-earth", "building",
            "special", "building",
            "building-blocks", "building",
            "stone-bricks", "building",
            "glass-colors", "colors",
            "utility", "essentials",
            "mobdrops", "mob-drops",
            "plants", "nature");

    /** Old category → new tab, for items the owner added himself. */
    static final Map<String, String> TAB_OF_OLD_CATEGORY = Map.of(
            "building-blocks", "stone-earth",
            "stone-bricks", "stone-earth",
            "glass-colors", "colors",
            "utility", "essentials",
            "wood", "wood",
            "decoration", "decoration",
            "redstone", "redstone",
            "farming", "farming");

    /** The migrated file and one readable line per change. */
    record Result(YamlConfiguration yaml, List<String> changes) {
    }

    private record Entry(String category, Object raw) {
    }

    private ShopCatalogMigration() {
    }

    static int version(ConfigurationSection yaml) {
        return yaml == null ? VERSION : yaml.getInt("config-version", 1);
    }

    /**
     * @param live    the live {@code shop.yml}
     * @param bundled the v2 target: the frozen {@code migrations/shop-v2.yml}
     * @param v1      the frozen v1 defaults ({@code migrations/shop-v1.yml})
     * @return the migrated file, or empty when {@code live} is already version 2 or newer
     */
    static Optional<Result> migrate(YamlConfiguration live, YamlConfiguration bundled, YamlConfiguration v1) {
        if (version(live) >= V2) {
            return Optional.empty();
        }
        YamlConfiguration out = new YamlConfiguration();
        try {
            out.loadFromString(bundled.saveToString());
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException("bundled shop.yml can't be copied", e);
        }
        List<String> changes = new ArrayList<>();
        Map<String, Entry> left = items(live);
        Map<String, Entry> defaults = items(v1);
        int kept = 0;
        ConfigurationSection tabs = out.getConfigurationSection(ShopCatalog.ROOT);
        if (tabs != null) {
            for (String tab : tabs.getKeys(false)) {
                boolean spawners = "spawners".equalsIgnoreCase(tabs.getString(tab + ".kind", "items"));
                ConfigurationSection items = tabs.getConfigurationSection(tab + ".items");
                if (items == null) {
                    continue;
                }
                for (String id : items.getKeys(false)) {
                    String path = ShopCatalog.ROOT + "." + tab + ".items." + id;
                    Object bundledRaw = plain(items.get(id));
                    long newPrice = ShopCatalog.priceOf(bundledRaw);
                    Entry liveEntry = spawners ? null : left.remove(id);
                    if (liveEntry == null) {
                        changes.add("added " + (spawners ? "spawner " : "") + id + " to " + tab + " at $" + newPrice);
                        continue;
                    }
                    long livePrice = ShopCatalog.priceOf(liveEntry.raw());
                    Entry old = defaults.get(id);
                    long oldPrice = old == null ? -1 : ShopCatalog.priceOf(old.raw());
                    boolean designChanged = oldPrice > 0 && oldPrice != newPrice;
                    long price;
                    if (livePrice < 1) {
                        price = newPrice;
                        changes.add(id + ": live entry had no valid price, using $" + newPrice);
                    } else if (designChanged && livePrice == oldPrice) {
                        price = newPrice;
                        changes.add(id + ": price $" + oldPrice + " -> $" + newPrice + " (Shop v2)");
                    } else if (designChanged) {
                        price = livePrice;
                        changes.add(id + ": kept your price $" + livePrice + " (Shop v2 default would be $" + newPrice + ")");
                    } else {
                        price = livePrice;
                        if (livePrice != newPrice) {
                            kept++;
                        }
                    }
                    out.set(path, merge(bundledRaw, liveEntry.raw(), price));
                }
            }
        }
        for (Map.Entry<String, Entry> e : left.entrySet()) {
            String id = e.getKey();
            Entry entry = e.getValue();
            long price = ShopCatalog.priceOf(entry.raw());
            if (defaults.containsKey(id)) {
                if (price >= 1) {
                    out.set(ShopCatalog.WORTH_ONLY + "." + id, price);
                }
                changes.add("removed " + id + " (was $" + price + " in " + entry.category() + "); its price stays only as block worth");
            } else {
                String tab = TAB_OF_OLD_CATEGORY.getOrDefault(entry.category(), entry.category());
                if (tabs == null || !tabs.contains(tab)) {
                    tab = "decoration";
                }
                out.set(ShopCatalog.ROOT + "." + tab + ".items." + id, plain(entry.raw()));
                // An item listed under worth-only too would be both sold and hidden; the shop entry wins.
                out.set(ShopCatalog.WORTH_ONLY + "." + id, null);
                changes.add("kept your own item " + id + " ($" + price + "), now in tab " + tab);
            }
        }
        if (kept > 0) {
            changes.add("kept " + kept + " other price(s) you had changed");
        }
        out.set("config-version", V2);
        return Optional.of(new Result(out, changes));
    }

    /**
     * v2 &rarr; v3 (MENUS-V3 6.3). Starts from the bundled v3 file (tab order, {@code groups:}, comments) and:
     * <ul>
     *   <li>an item the live file has keeps the live entry ({@code enabled}, {@code requires}, own {@code contains}) and
     *       its live price; only a price that still equals its v2 default moves to the v3 default (copied verbatim, so
     *       a {@code price: auto} stays {@code auto});</li>
     *   <li>an item of {@code shop-v2.yml} that the bundled v3 file lacks goes to {@code worth-only} at its live price;</li>
     *   <li>an item new in v3 is added exactly as the bundled entry and leaves the live {@code worth-only};</li>
     *   <li>an item the owner added himself stays in its tab (or Decoration when that tab is gone);</li>
     *   <li>a Spawners tab ({@code kind: spawners}) becomes the bundled one (mobs and prices of the economy design);</li>
     *   <li>live {@code worth-only} entries are copied unless the item is sold again; a tab keeps the owner's icon.</li>
     * </ul>
     *
     * @param live       the live {@code shop.yml} (version 2, or the result of {@link #migrate})
     * @param bundledV3  the bundled {@code shop.yml} (version 3)
     * @param v2Defaults the frozen v2 defaults ({@code migrations/shop-v2.yml})
     * @return the migrated file, or empty when {@code live} is already version 3 or newer
     */
    static Optional<Result> migrateV3(YamlConfiguration live, YamlConfiguration bundledV3, YamlConfiguration v2Defaults) {
        return migrateTo(live, bundledV3, v2Defaults, V3, "Shop v3");
    }

    /**
     * v3 &rarr; v4 (ECON-SELL-SHOP-AH B1): the same data-driven move as {@link #migrateV3}, against the frozen v3 defaults:
     * default items the bundled file drops (planks, furnace, chest ...) go to {@code worth-only} at their live price, the
     * new {@code price: auto} items are added, owner prices and owner items stay.
     */
    static Optional<Result> migrateV4(YamlConfiguration live, YamlConfiguration bundled, YamlConfiguration v3Defaults) {
        return migrateTo(live, bundled, v3Defaults, V4, "Shop v4");
    }

    /**
     * v4 &rarr; v5 (owner 2 Nov 2026): the tabs are regrouped (wood, stone-earth and special become Building Blocks; leaves,
     * flowers and plants move to Nature; a Mob Drops tab with {@code price: auto}). Data-driven like v4: items keep their
     * live entry and move to their new tab, owner items in a tab that is gone go to Decoration.
     */
    static Optional<Result> migrateV5(YamlConfiguration live, YamlConfiguration bundled, YamlConfiguration v4Defaults) {
        return migrateTo(live, bundled, v4Defaults, V5, "Shop v5");
    }

    private static Optional<Result> migrateTo(YamlConfiguration live, YamlConfiguration bundledV3, YamlConfiguration v2Defaults,
                                              int target, String label) {
        if (version(live) >= target) {
            return Optional.empty();
        }
        YamlConfiguration out = new YamlConfiguration();
        try {
            out.loadFromString(bundledV3.saveToString());
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException("bundled shop.yml can't be copied", e);
        }
        List<String> changes = new ArrayList<>();
        Map<String, Entry> left = items(live, false);
        Map<String, Entry> liveSpawners = items(live, true);
        Map<String, Entry> defaults = items(v2Defaults, false);
        ConfigurationSection liveTabs = live.getConfigurationSection(ShopCatalog.ROOT);
        ConfigurationSection tabs = out.getConfigurationSection(ShopCatalog.ROOT);
        int kept = 0;
        if (tabs != null) {
            for (String tab : tabs.getKeys(false)) {
                String liveIcon = liveTabs == null ? null : liveTabs.getString(tab + ".icon");
                if (liveIcon != null && !liveIcon.equalsIgnoreCase(tabs.getString(tab + ".icon", ""))) {
                    out.set(ShopCatalog.ROOT + "." + tab + ".icon", liveIcon);
                }
                boolean spawners = "spawners".equalsIgnoreCase(tabs.getString(tab + ".kind", "items"));
                ConfigurationSection items = tabs.getConfigurationSection(tab + ".items");
                if (items == null) {
                    continue;
                }
                for (String id : items.getKeys(false)) {
                    Object bundledRaw = plain(items.get(id));
                    if (spawners) {
                        Entry old = liveSpawners.remove(id);
                        long oldPrice = old == null ? -1 : ShopCatalog.priceOf(old.raw());
                        if (old == null) {
                            changes.add("added spawner " + id + " to " + tab + " at " + priceText(bundledRaw));
                        } else if (oldPrice != ShopCatalog.priceOf(bundledRaw)) {
                            changes.add("spawner " + id + ": price $" + oldPrice + " -> " + priceText(bundledRaw));
                        }
                        continue;
                    }
                    String path = ShopCatalog.ROOT + "." + tab + ".items." + id;
                    Entry liveEntry = left.remove(id);
                    if (liveEntry == null) {
                        if (live.contains(ShopCatalog.WORTH_ONLY + "." + id)) {
                            changes.add("added " + id + " to " + tab + " at " + priceText(bundledRaw) + " (it left worth-only)");
                        } else {
                            changes.add("added " + id + " to " + tab + " at " + priceText(bundledRaw));
                        }
                        continue;
                    }
                    long livePrice = ShopCatalog.priceOf(liveEntry.raw());
                    Entry old = defaults.get(id);
                    long oldPrice = old == null ? -1 : ShopCatalog.priceOf(old.raw());
                    Object bundledPrice = priceValue(bundledRaw);
                    boolean designChanged = oldPrice > 0 && !Long.valueOf(oldPrice).equals(bundledPrice);
                    Object price;
                    if (ShopCatalog.isAuto(priceValue(liveEntry.raw()))) {
                        price = ShopCatalog.AUTO; // the owner's (or an earlier default's) auto price stays auto
                    } else if (livePrice < 1) {
                        price = bundledPrice;
                        changes.add(id + ": live entry had no valid price, using " + priceText(bundledRaw));
                    } else if (designChanged && livePrice == oldPrice) {
                        price = bundledPrice;
                        changes.add(id + ": price $" + oldPrice + " -> " + priceText(bundledRaw) + " (" + label + ")");
                    } else if (designChanged) {
                        price = livePrice;
                        changes.add(id + ": kept your price $" + livePrice + " (" + label + " default would be " + priceText(bundledRaw) + ")");
                    } else {
                        price = livePrice;
                        if (!Long.valueOf(livePrice).equals(bundledPrice)) {
                            kept++;
                        }
                    }
                    if (!liveEntry.category().equals(tab)) {
                        changes.add(id + ": moved from " + liveEntry.category() + " to " + tab);
                    }
                    out.set(path, mergeRaw(bundledRaw, liveEntry.raw(), price));
                }
            }
        }
        for (String id : liveSpawners.keySet()) {
            changes.add("removed spawner " + id + " (not sold in " + label + ")");
        }
        // Live worth-only first (owner edits win over the bundled values), then the items that leave the shop now.
        ConfigurationSection liveWorth = live.getConfigurationSection(ShopCatalog.WORTH_ONLY);
        if (liveWorth != null) {
            for (String id : liveWorth.getKeys(false)) {
                out.set(ShopCatalog.WORTH_ONLY + "." + id, plain(liveWorth.get(id)));
            }
        }
        for (Map.Entry<String, Entry> e : left.entrySet()) {
            String id = e.getKey();
            Entry entry = e.getValue();
            long price = ShopCatalog.priceOf(entry.raw());
            if (defaults.containsKey(id)) {
                if (price >= 1) {
                    out.set(ShopCatalog.WORTH_ONLY + "." + id, price);
                }
                changes.add("removed " + id + " (was $" + price + " in " + entry.category() + "); its price stays only as block worth");
            } else {
                String tab = entry.category();
                if (tabs == null || !tabs.contains(tab + ".items") || isSpawnerTab(tabs, tab)) {
                    tab = "decoration";
                }
                out.set(ShopCatalog.ROOT + "." + tab + ".items." + id, plain(entry.raw()));
                changes.add("kept your own item " + id + " ($" + price + "), in tab " + tab);
            }
        }
        // Nothing may be both sold and parked: the shop entry wins.
        ConfigurationSection worth = out.getConfigurationSection(ShopCatalog.WORTH_ONLY);
        if (worth != null) {
            Map<String, Entry> sold = items(out, false);
            for (String id : List.copyOf(worth.getKeys(false))) {
                if (sold.containsKey(id)) {
                    out.set(ShopCatalog.WORTH_ONLY + "." + id, null);
                }
            }
        }
        if (kept > 0) {
            changes.add("kept " + kept + " other price(s) you had changed");
        }
        out.set("config-version", target);
        return Optional.of(new Result(out, changes));
    }

    private static boolean isSpawnerTab(ConfigurationSection tabs, String tab) {
        return "spawners".equalsIgnoreCase(tabs.getString(tab + ".kind", "items"));
    }

    /** The raw price of a bundled entry: a whole number, or a word such as {@code auto} (never compared as a number). */
    static Object priceValue(Object raw) {
        Map<?, ?> map = ShopCatalog.section(raw);
        Object value = map != null ? map.get("price") : raw;
        return value instanceof Integer i ? Long.valueOf(i) : value;
    }

    private static String priceText(Object raw) {
        Object value = priceValue(raw);
        return value instanceof Number ? "$" + value : "price " + value;
    }

    /** Every item of every non-spawner tab ({@code spawners == false}) or of every Spawners tab: id → (tab, raw). */
    private static Map<String, Entry> items(ConfigurationSection yaml, boolean spawners) {
        Map<String, Entry> out = new LinkedHashMap<>();
        ConfigurationSection root = yaml == null ? null : yaml.getConfigurationSection(ShopCatalog.ROOT);
        if (root == null) {
            return out;
        }
        for (String category : root.getKeys(false)) {
            if (isSpawnerTab(root, category) != spawners) {
                continue;
            }
            ConfigurationSection items = root.getConfigurationSection(category + ".items");
            if (items == null) {
                continue;
            }
            for (String id : items.getKeys(false)) {
                out.putIfAbsent(id, new Entry(category, plain(items.get(id))));
            }
        }
        return out;
    }

    /** Every item of every category: id → (category, raw value); the first listing wins. */
    private static Map<String, Entry> items(ConfigurationSection yaml) {
        Map<String, Entry> out = new LinkedHashMap<>();
        ConfigurationSection root = yaml == null ? null : yaml.getConfigurationSection(ShopCatalog.ROOT);
        if (root == null) {
            return out;
        }
        for (String category : root.getKeys(false)) {
            ConfigurationSection items = root.getConfigurationSection(category + ".items");
            if (items == null) {
                continue;
            }
            for (String id : items.getKeys(false)) {
                out.putIfAbsent(id, new Entry(category, plain(items.get(id))));
            }
        }
        return out;
    }

    /**
     * The entry to write: the bundled entry's extras ({@code contains}), overlaid by the live entry's ({@code enabled},
     * {@code requires}, own {@code contains}), with {@code price}. A plain number when nothing else is left.
     */
    static Object merge(Object bundledRaw, Object liveRaw, long price) {
        return mergeRaw(bundledRaw, liveRaw, price);
    }

    /** {@link #merge} with a raw price (a whole number or a word such as {@code auto}). */
    static Object mergeRaw(Object bundledRaw, Object liveRaw, Object price) {
        Map<?, ?> bundled = ShopCatalog.section(bundledRaw);
        Map<?, ?> live = ShopCatalog.section(liveRaw);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("price", price);
        if (bundled != null) {
            bundled.forEach((k, v) -> map.put(String.valueOf(k), plain(v)));
        }
        if (live != null) {
            live.forEach((k, v) -> map.put(String.valueOf(k), plain(v)));
        }
        map.put("price", price);
        return map.size() == 1 ? price : map;
    }

    /** Sections become plain maps (so they can be written anywhere). */
    static Object plain(Object raw) {
        if (raw instanceof ConfigurationSection s) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (String key : s.getKeys(false)) {
                out.put(key, plain(s.get(key)));
            }
            return out;
        }
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), plain(v)));
            return out;
        }
        return raw;
    }
}

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
 * The one-time Shop v2 catalog migration (GUI-3, docs/design/SHOP_DESIGN.md §2): a live {@code shop.yml} of
 * config-version 1 (8 categories, 332 items) becomes the new 9 tabs and ~253 items. Pure (unit-tested).
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
 */
final class ShopCatalogMigration {

    static final int VERSION = 2;

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
     * @param bundled the bundled v2 {@code shop.yml}
     * @param v1      the frozen v1 defaults ({@code migrations/shop-v1.yml})
     * @return the migrated file, or empty when {@code live} is already version 2 or newer
     */
    static Optional<Result> migrate(YamlConfiguration live, YamlConfiguration bundled, YamlConfiguration v1) {
        if (version(live) >= VERSION) {
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
        out.set("config-version", VERSION);
        return Optional.of(new Result(out, changes));
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
        return map.size() == 1 ? (Object) price : map;
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

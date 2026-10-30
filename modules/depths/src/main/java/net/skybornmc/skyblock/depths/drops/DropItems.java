package net.skybornmc.skyblock.depths.drops;

import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.item.ItemType;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.item.Rarity;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The Depths drop items (M4.9) from {@code depths.yml → drop-items}: custom items of type material (so {@code sell.yml}
 * prices them as {@code custom:<id>} and the Forge uses them), registered in the item registry. Names come from
 * {@code messages/depths.yml → drop-items.names.<id>}.
 */
public final class DropItems {

    /** One drop item: id, look and footer rarity. */
    public record Def(String id, Material material, Rarity rarity) {
    }

    private final ItemRegistry registry;
    private final Function<String, String> raw;
    private final List<String> registered = new ArrayList<>();
    private Map<String, Def> defs = Map.of();

    /** @param raw raw MiniMessage of a key in messages/depths.yml (null when missing) */
    public DropItems(ItemRegistry registry, Function<String, String> raw) {
        this.registry = registry;
        this.raw = raw;
    }

    /** Every drop item in file order. */
    public Map<String, Def> defs() {
        return defs;
    }

    public Def def(String id) {
        return defs.get(id);
    }

    /** Ids in file order (menus sort by it: tier order). */
    public List<String> order() {
        return new ArrayList<>(defs.keySet());
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(defs.keySet());
    }

    /** Reads the section and (re-)registers the items. Broken entries are reported and skipped. */
    public void load(ConfigurationSection section, Consumer<String> problem) {
        unregister();
        Map<String, Def> out = new LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String id = key.trim().toLowerCase(Locale.ROOT);
                String where = "drop-items." + key;
                ConfigurationSection s = section.getConfigurationSection(key);
                if (s == null || !ItemDefinition.isValidId(id)) {
                    problem.accept(where + ": must be a section {material, rarity} with an id of a-z, 0-9 and _, skipped");
                    continue;
                }
                Material material = Material.matchMaterial(s.getString("material", ""));
                if (material == null || !material.isItem() || material.isAir()) {
                    problem.accept(where + ".material: '" + s.getString("material") + "' is not an item, skipped");
                    continue;
                }
                Rarity rarity = Rarity.COMMON;
                String r = s.getString("rarity", "common").trim().toUpperCase(Locale.ROOT);
                try {
                    rarity = Rarity.valueOf(r);
                } catch (IllegalArgumentException e) {
                    problem.accept(where + ".rarity: '" + r.toLowerCase(Locale.ROOT) + "' is unknown, using common");
                }
                out.put(id, new Def(id, material, rarity));
            }
        }
        defs = Collections.unmodifiableMap(out);
        for (Def def : out.values()) {
            if (registry.get(def.id()).isPresent()) {
                problem.accept("drop-items." + def.id() + ": the id is already used by another module, skipped");
                continue;
            }
            registry.register(ItemDefinition.builder(def.id(), def.material(), ItemType.MATERIAL, def.rarity())
                    .name(name(def))
                    .subtitle(text("drop-items.subtitle"))
                    .description(descriptionOf(def.id()))
                    .build());
            registered.add(def.id());
        }
    }

    /** The item name (MiniMessage, rarity colored). */
    public String name(Def def) {
        return LoreBuilder.fill(text("drop-items.name"), Map.of("rarity", def.rarity().id(), "name", plainName(def.id())));
    }

    /** The plain name without color ("Coal"), for chat lists. */
    public String plainName(String id) {
        String name = raw.apply("drop-items.names." + id);
        return name == null ? id : name;
    }

    private String descriptionOf(String id) {
        String own = raw.apply("drop-items.descriptions." + id);
        return own != null ? own : text("drop-items.description");
    }

    private String text(String key) {
        String value = raw.apply(key);
        return value == null ? "<red>[missing: depths:" + key + "]</red>" : value;
    }

    public void unregister() {
        registered.forEach(registry::unregister);
        registered.clear();
    }
}

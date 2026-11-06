package net.skybornmc.skyblock.stacking;

import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.item.ItemType;
import net.skybornmc.skyblock.common.item.Rarity;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * ECON-SPAWNERS step 8: the custom drop items of spawner mobs ({@code spawners.drop-items}: {@code id=material:rarity}),
 * registered as {@link ItemType#MATERIAL} custom items (footer {@code <RARITY> MATERIAL}). Names come from
 * {@code messages/stacking.yml drops.<id>}; prices from {@code sell.yml custom-items.spawner-drops}. The base materials are
 * inert placeholders (the resource pack gives each drop its own icon later); {@link MobDropGuard} keeps them out of
 * vanilla recipes, furnaces, brewing, composters, food and feeding.
 */
final class SpawnerDropItems {

    /** Prefix of every spawner drop id (the guard keys on it). */
    static final String PREFIX = "mobdrop_";

    record Def(String id, Material material, Rarity rarity) {
    }

    private final ItemRegistry registry;
    private final Function<String, String> text;
    private final List<String> registered = new ArrayList<>();
    private Map<String, Def> defs = Map.of();

    SpawnerDropItems(ItemRegistry registry, Function<String, String> text) {
        this.registry = registry;
        this.text = text;
    }

    void load(List<String> lines, Consumer<String> problems) {
        unregister();
        Map<String, Def> out = new LinkedHashMap<>();
        for (String line : lines) {
            String where = "stacking.yml → spawners.drop-items: '" + line + "'";
            int eq = line == null ? -1 : line.indexOf('=');
            if (eq <= 0) {
                problems.accept(where + " needs the form id=material:rarity, skipped");
                continue;
            }
            String id = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String[] rest = line.substring(eq + 1).split(":");
            Material material = Material.matchMaterial(rest[0].trim());
            Rarity rarity = Rarity.COMMON;
            if (rest.length > 1) {
                try {
                    rarity = Rarity.valueOf(rest[1].trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    problems.accept(where + " rarity '" + rest[1].trim() + "' is unknown, using common");
                }
            }
            if (!id.startsWith(PREFIX) || !ItemDefinition.isValidId(id)) {
                problems.accept(where + " the id must start with " + PREFIX + " and use a-z, 0-9 and _, skipped");
            } else if (material == null || !material.isItem() || material.isAir()) {
                problems.accept(where + " '" + rest[0].trim() + "' is not an item, skipped");
            } else if (out.containsKey(id)) {
                problems.accept(where + " is defined twice, skipped");
            } else {
                out.put(id, new Def(id, material, rarity));
            }
        }
        defs = Map.copyOf(out);
        for (Def def : out.values()) {
            if (registry.get(def.id()).isPresent()) {
                problems.accept("stacking.yml → spawners.drop-items: '" + def.id() + "' is already used by another module, skipped");
                continue;
            }
            registry.register(ItemDefinition.builder(def.id(), def.material(), ItemType.MATERIAL, def.rarity())
                    .name(name(def))
                    .subtitle(text.apply("drops.subtitle"))
                    .description(text.apply("drops.description"))
                    .build());
            registered.add(def.id());
        }
    }

    /** The item name (MiniMessage, rarity coloured); a missing name shows the id in words. */
    String name(Def def) {
        String raw = text.apply("drops." + def.id());
        if (raw == null || raw.isBlank() || raw.startsWith("[missing")) {
            raw = def.id().substring(PREFIX.length()).replace('_', ' ');
        }
        return "<rarity:" + def.rarity().name().toLowerCase(Locale.ROOT) + ">" + raw;
    }

    Set<String> ids() {
        return defs.keySet();
    }

    Def def(String id) {
        return defs.get(id);
    }

    void unregister() {
        registered.forEach(registry::unregister);
        registered.clear();
    }
}

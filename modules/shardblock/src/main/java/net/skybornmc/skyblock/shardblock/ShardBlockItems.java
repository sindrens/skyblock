package net.skybornmc.skyblock.shardblock;

import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.item.ItemType;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.item.Rarity;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.function.Function;

/**
 * The {@code shard_block} custom item (ECON-SHARDS step 6): {@link ItemType#MATERIAL} (footer RARE MATERIAL; the credit
 * shop only sells MATERIAL, COLLECTOR, CONSUMABLE or COSMETIC items). Blocks at level 0/0 stack; a picked-up block keeps its
 * levels in the item ({@code shard_block_resp}, {@code shard_block_dbl}), so placing it again restores them.
 */
final class ShardBlockItems {

    static final String ID = "shard_block";

    private final ItemRegistry registry;
    private final Function<String, String> text;
    private final NamespacedKey respawnKey;
    private final NamespacedKey doubleKey;
    private boolean registered;

    ShardBlockItems(Plugin plugin, ItemRegistry registry, Function<String, String> text) {
        this.registry = registry;
        this.text = text;
        this.respawnKey = new NamespacedKey(plugin, "shard_block_resp");
        this.doubleKey = new NamespacedKey(plugin, "shard_block_dbl");
    }

    /** Registers the item with the active material (re-registered on reload). Returns false if another module owns the id. */
    boolean register(Material material) {
        unregister();
        if (registry.get(ID).isPresent()) {
            return false;
        }
        registry.register(ItemDefinition.builder(ID, material, ItemType.MATERIAL, Rarity.RARE)
                .name(text.apply("item.name"))
                .subtitle(text.apply("item.subtitle"))
                .description(text.apply("item.description"))
                .action("CLICK", text.apply("item.action"))
                .renderer(new ItemDefinition.Renderer() {
                    @Override
                    public void lore(ItemStack stack, ItemDefinition definition, LoreBuilder lore) {
                        int resp = level(stack, respawnKey);
                        int dbl = level(stack, doubleKey);
                        if (resp > 0) {
                            lore.line(text.apply("item.respawn-level").replace("<level>", String.valueOf(resp)));
                        }
                        if (dbl > 0) {
                            lore.line(text.apply("item.double-level").replace("<level>", String.valueOf(dbl)));
                        }
                    }
                })
                .build());
        registered = true;
        return true;
    }

    void unregister() {
        if (registered) {
            registry.unregister(ID);
            registered = false;
        }
    }

    /** New shard blocks; levels above 0 are written into the item (a picked-up block). */
    ItemStack create(int amount, String source, int respawnLevel, int doubleLevel) {
        return registry.create(ID, amount, source, pdc -> {
            if (respawnLevel > 0) {
                pdc.set(respawnKey, PersistentDataType.INTEGER, respawnLevel);
            }
            if (doubleLevel > 0) {
                pdc.set(doubleKey, PersistentDataType.INTEGER, doubleLevel);
            }
        });
    }

    boolean is(ItemStack stack) {
        return ItemRegistry.is(stack, ID);
    }

    int respawnLevel(ItemStack stack) {
        return level(stack, respawnKey);
    }

    int doubleLevel(ItemStack stack) {
        return level(stack, doubleKey);
    }

    private static int level(ItemStack stack, NamespacedKey key) {
        if (stack == null || !stack.hasItemMeta()) {
            return 0;
        }
        Integer v = stack.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return v == null ? 0 : Math.max(0, v);
    }
}

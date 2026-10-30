package net.skybornmc.skyblock.boosters;

import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.item.BoundItems;
import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.item.ItemType;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.item.Rarity;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Booster items: one unique custom item per type, personal ({@code booster_<type>}) and global
 * ({@code global_booster_<type>}). The strength and length are stored on the item; the unique id lets the boosters module
 * refuse an item that was started before (a copy or a crash rollback) and the dupe scanner watch them.
 */
final class BoosterItems {

    static final String PERSONAL_PREFIX = "booster_";
    static final String GLOBAL_PREFIX = "global_booster_";
    static final NamespacedKey PERCENT = new NamespacedKey(ItemKeys.NAMESPACE, "booster_percent");
    static final NamespacedKey MINUTES = new NamespacedKey(ItemKeys.NAMESPACE, "booster_minutes");

    /** What a booster item holds. */
    record Spec(BoosterType type, int percent, int minutes, boolean global, UUID itemUuid) {

        long millis() {
            return minutes * BoosterRules.MINUTE;
        }
    }

    private final BoostersModule m;
    private final List<String> registered = new ArrayList<>();

    BoosterItems(BoostersModule module) {
        this.m = module;
    }

    static String itemId(BoosterType type, boolean global) {
        return (global ? GLOBAL_PREFIX : PERSONAL_PREFIX) + type.id();
    }

    /** Registers the 8 definitions (materials from {@code items.*}, read at start). */
    void register(Map<BoosterType, Material> materials, Material globalMaterial) {
        for (BoosterType type : BoosterType.values()) {
            define(type, false, materials.getOrDefault(type, Material.GOLD_INGOT));
            define(type, true, globalMaterial);
        }
    }

    void unregister() {
        registered.forEach(m.core.items()::unregister);
        registered.clear();
    }

    private void define(BoosterType type, boolean global, Material material) {
        String id = itemId(type, global);
        if (m.core.items().get(id).isPresent()) {
            return;
        }
        String base = global ? "item.global." : "item.personal.";
        m.core.items().register(ItemDefinition.builder(id, material, ItemType.CONSUMABLE, global ? Rarity.LEGENDARY : Rarity.RARE)
                .name(m.raw(base + "name").replace("{type}", m.typeName(type)).replace("{percent}", "?"))
                .description(m.raw(base + "description"))
                .action(m.raw("item.action-input"), m.raw("item.action-text"))
                .unique()
                .glint()
                .renderer(new ItemDefinition.Renderer() {
                    @Override
                    public String name(ItemStack stack, ItemDefinition definition) {
                        Optional<Spec> spec = read(stack);
                        if (spec.isEmpty()) {
                            return definition.name();
                        }
                        return "<rarity:" + BoosterItems.rarity(spec.get()).id() + ">" + LoreBuilder.fill(m.raw(base + "name"),
                                Map.of("type", m.typeName(type), "percent", String.valueOf(spec.get().percent())));
                    }

                    @Override
                    public void lore(ItemStack stack, ItemDefinition definition, LoreBuilder lore) {
                        read(stack).ifPresent(spec -> {
                            Map<String, String> tokens = Map.of("type", m.typeName(type), "percent", String.valueOf(spec.percent()),
                                    "time", BoosterRules.time(spec.millis()));
                            for (String line : m.rawList(base + "lore")) {
                                lore.line(LoreBuilder.fill(line, tokens));
                            }
                            if (BoundItems.isBound(stack)) {
                                lore.line(m.raw("item.bound"));
                            }
                        });
                    }

                    @Override
                    public Rarity rarity(ItemStack stack, ItemDefinition definition) {
                        return read(stack).map(BoosterItems::rarity).orElse(definition.rarity());
                    }
                })
                .build());
        registered.add(id);
    }

    /** Rarity by strength: global = legendary, +100% and up = epic, +50% = rare, less = uncommon. */
    static Rarity rarity(Spec spec) {
        if (spec.global()) {
            return Rarity.LEGENDARY;
        }
        return spec.percent() >= 100 ? Rarity.EPIC : spec.percent() >= 50 ? Rarity.RARE : Rarity.UNCOMMON;
    }

    /** The booster an item holds (empty for anything else, or a broken item). */
    static Optional<Spec> read(ItemStack stack) {
        Optional<String> id = ItemRegistry.idOf(stack);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        boolean global = id.get().startsWith(GLOBAL_PREFIX);
        String typeId = global ? id.get().substring(GLOBAL_PREFIX.length())
                : id.get().startsWith(PERSONAL_PREFIX) ? id.get().substring(PERSONAL_PREFIX.length()) : null;
        if (typeId == null) {
            return Optional.empty();
        }
        Optional<BoosterType> type = BoosterType.byId(typeId);
        Optional<UUID> uuid = ItemRegistry.uuidOf(stack);
        if (type.isEmpty() || uuid.isEmpty()) {
            return Optional.empty();
        }
        var pdc = stack.getPersistentDataContainer();
        Integer percent = pdc.get(PERCENT, PersistentDataType.INTEGER);
        Integer minutes = pdc.get(MINUTES, PersistentDataType.INTEGER);
        if (percent == null || minutes == null) {
            return Optional.empty();
        }
        return Optional.of(new Spec(type.get(), percent, minutes, global, uuid.get()));
    }

    static boolean isBooster(ItemStack stack) {
        return ItemRegistry.idOf(stack).filter(id -> id.startsWith(PERSONAL_PREFIX) || id.startsWith(GLOBAL_PREFIX)).isPresent();
    }

    /** A new booster item; bound to {@code profile} when {@code bind}. */
    ItemStack create(BoosterType type, int percent, int minutes, boolean global, UUID profile, boolean bind, String origin) {
        ItemStack stack = m.core.items().create(itemId(type, global), 1, origin, pdc -> {
            pdc.set(PERCENT, PersistentDataType.INTEGER, percent);
            pdc.set(MINUTES, PersistentDataType.INTEGER, minutes);
        });
        if (bind && profile != null) {
            BoundItems.mark(stack, profile);
            m.core.items().render(stack);
        }
        return stack;
    }
}

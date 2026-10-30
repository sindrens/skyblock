package net.skybornmc.skyblock.common.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.skybornmc.skyblock.common.text.Messages;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All custom item definitions by id. Modules register their items in {@code enable()}; everything else
 * (creating, identifying, re-rendering lore) goes through here.
 *
 * <p>Every custom item carries its id in the PDC ({@link ItemKeys#ID}); unique items also carry a UUID, creation time
 * and source, which the {@link DupeDetector} uses.</p>
 */
public final class ItemRegistry {

    static final String MESSAGES = "items";

    private final Messages messages;
    private final Map<String, ItemDefinition> definitions = new ConcurrentHashMap<>();

    public ItemRegistry(Messages messages) {
        this.messages = messages;
    }

    /** Registers a definition. Throws if the id is already taken (two modules must never share an id). */
    public void register(ItemDefinition definition) {
        if (definitions.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Duplicate custom item id: " + definition.id());
        }
    }

    /** Removes a module's definition (on module disable). Existing items keep their data. */
    public void unregister(String id) {
        definitions.remove(id);
    }

    public Optional<ItemDefinition> get(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Collection<String> ids() {
        List<String> ids = new ArrayList<>(definitions.keySet());
        Collections.sort(ids);
        return ids;
    }

    /**
     * Creates a new item.
     *
     * @param source where it came from, stored on unique items (e.g. {@code admin:SlickyMatt}, {@code crate:rare})
     * @param data   extra PDC data written before the lore is rendered (e.g. a note's amount); may be null
     * @throws IllegalArgumentException for an unknown id
     */
    public ItemStack create(String id, int amount, String source, java.util.function.Consumer<PersistentDataContainer> data) {
        ItemDefinition def = definitions.get(id);
        if (def == null) {
            throw new IllegalArgumentException("Unknown custom item '" + id + "'");
        }
        ItemStack stack = ItemStack.of(def.material(), def.unique() ? 1 : Math.max(1, amount));
        stack.editPersistentDataContainer(pdc -> {
            pdc.set(ItemKeys.ID, PersistentDataType.STRING, def.id());
            if (def.unique()) {
                pdc.set(ItemKeys.UUID, PersistentDataType.STRING, UUID.randomUUID().toString());
                pdc.set(ItemKeys.CREATED, PersistentDataType.LONG, System.currentTimeMillis());
                pdc.set(ItemKeys.SOURCE, PersistentDataType.STRING, source == null ? "unknown" : source);
            }
            if (data != null) {
                data.accept(pdc);
            }
        });
        if (def.maxStack() > 0) {
            stack.setData(DataComponentTypes.MAX_STACK_SIZE, def.maxStack());
        }
        if (def.unbreakable()) {
            stack.setData(DataComponentTypes.UNBREAKABLE);
        }
        if (def.itemModel() != null) {
            stack.setData(DataComponentTypes.ITEM_MODEL, def.itemModel());
        }
        stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().addHiddenComponents(
                DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.ENCHANTMENTS, DataComponentTypes.STORED_ENCHANTMENTS,
                DataComponentTypes.POTION_CONTENTS, DataComponentTypes.UNBREAKABLE, DataComponentTypes.DYED_COLOR,
                DataComponentTypes.TRIM, DataComponentTypes.BANNER_PATTERNS, DataComponentTypes.JUKEBOX_PLAYABLE,
                DataComponentTypes.CAN_BREAK, DataComponentTypes.CAN_PLACE_ON, DataComponentTypes.PROVIDES_TRIM_MATERIAL));
        if (def.glint()) {
            stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        render(stack);
        return stack;
    }

    public ItemStack create(String id, String source) {
        return create(id, 1, source, null);
    }

    /**
     * Regenerates the name and lore of a custom item from its definition and data (call after changing its data,
     * e.g. a tool levelled up). Returns false for non-custom or unknown items.
     */
    public boolean render(ItemStack stack) {
        Optional<ItemDefinition> found = definitionOf(stack);
        if (found.isEmpty()) {
            return false;
        }
        ItemDefinition def = found.get();
        LoreBuilder lore = new LoreBuilder(this::label)
                .subtitle(def.subtitle())
                .description(def.description());
        def.renderer().lore(stack, def, lore);
        def.actions().forEach(a -> lore.action(a.input(), a.text()));
        lore.footer(java.util.Objects.requireNonNullElse(def.renderer().rarity(stack, def), def.rarity()), def.type());

        Audience viewer = Audience.empty(); // items are shared between viewers: hex colors, Geyser rounds for Bedrock
        Component name = messages.parse(viewer, def.renderer().name(stack, def))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        List<Component> lines = new ArrayList<>();
        for (String line : lore.build()) {
            lines.add(messages.parse(viewer, line).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        stack.setData(DataComponentTypes.ITEM_NAME, name);
        stack.unsetData(DataComponentTypes.CUSTOM_NAME);
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(lines));
        return true;
    }

    /** The label/format text for the lore builder; missing keys show {@code [missing: items:key]} (logged once). */
    String label(String key) {
        return LoreBuilder.labels(messages).apply(key);
    }

    // ------------------------------------------------------------------ identification

    /** The custom item id, if this is a custom item. */
    public static Optional<String> idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.getPersistentDataContainer().get(ItemKeys.ID, PersistentDataType.STRING));
    }

    /** The unique instance id, if this is a unique custom item. */
    public static Optional<UUID> uuidOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        String raw = stack.getPersistentDataContainer().get(ItemKeys.UUID, PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static boolean isCustom(ItemStack stack) {
        return idOf(stack).isPresent();
    }

    public static boolean is(ItemStack stack, String id) {
        return idOf(stack).filter(id::equals).isPresent();
    }

    public Optional<ItemDefinition> definitionOf(ItemStack stack) {
        return idOf(stack).map(definitions::get);
    }
}

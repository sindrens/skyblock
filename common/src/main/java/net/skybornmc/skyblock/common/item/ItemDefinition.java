package net.skybornmc.skyblock.common.item;

import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A custom item type registered by a module. Texts are MiniMessage strings (with style tags) that modules take from
 * their own messages file. Lore is always generated from this definition plus the item's data
 * ({@link Renderer}); it is never edited by hand.
 *
 * @param id        lowercase id ({@code [a-z0-9_]+}), stored in the item's PDC
 * @param unique    unique items get a UUID, stack to 1 and are checked by the dupe scanner
 * @param itemModel resource-pack model ({@code item_model} component); {@code null} = vanilla look until a pack exists
 */
public record ItemDefinition(
        String id,
        Material material,
        ItemType type,
        Rarity rarity,
        String name,
        String subtitle,
        String description,
        List<Action> actions,
        boolean unique,
        boolean glint,
        boolean unbreakable,
        Key itemModel,
        int maxStack,
        Renderer renderer) {

    static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");

    /** An action line: {@code ▶ Right-click to open upgrades}. */
    public record Action(String input, String text) {
    }

    /** Adds item-specific data to the name and lore (level, stats, amount …). Both methods are optional. */
    public interface Renderer {
        Renderer NONE = new Renderer() {
        };

        /** The display name for this stack (MiniMessage). Default: the definition's name. */
        default String name(ItemStack stack, ItemDefinition definition) {
            return definition.name();
        }

        /** Adds lines/stats/enchants/actions for this stack. */
        default void lore(ItemStack stack, ItemDefinition definition, LoreBuilder lore) {
        }

        /** The rarity shown in the footer for this stack (e.g. a tool's tier). Default: the definition's rarity. */
        default Rarity rarity(ItemStack stack, ItemDefinition definition) {
            return definition.rarity();
        }
    }

    public ItemDefinition {
        Objects.requireNonNull(id, "id");
        if (!isValidId(id)) {
            throw new IllegalArgumentException("Invalid item id '" + id + "' (use a-z, 0-9 and _)");
        }
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(rarity, "rarity");
        name = Objects.requireNonNullElse(name, id);
        subtitle = Objects.requireNonNullElse(subtitle, "");
        description = Objects.requireNonNullElse(description, "");
        actions = List.copyOf(actions == null ? List.of() : actions);
        renderer = Objects.requireNonNullElse(renderer, Renderer.NONE);
        if (unique) {
            maxStack = 1;
        }
        if (maxStack < 0 || maxStack > 99) {
            throw new IllegalArgumentException("maxStack must be 0-99 for item '" + id + "'");
        }
    }

    public static boolean isValidId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    public static Builder builder(String id, Material material, ItemType type, Rarity rarity) {
        return new Builder(id, material, type, rarity);
    }

    /** Fluent builder. */
    public static final class Builder {
        private final String id;
        private final Material material;
        private final ItemType type;
        private final Rarity rarity;
        private String name;
        private String subtitle = "";
        private String description = "";
        private final List<Action> actions = new ArrayList<>();
        private boolean unique;
        private boolean glint;
        private boolean unbreakable;
        private Key itemModel;
        private int maxStack;
        private Renderer renderer = Renderer.NONE;

        private Builder(String id, Material material, ItemType type, Rarity rarity) {
            this.id = id;
            this.material = material;
            this.type = type;
            this.rarity = rarity;
        }

        public Builder name(String miniMessage) {
            this.name = miniMessage;
            return this;
        }

        public Builder subtitle(String miniMessage) {
            this.subtitle = miniMessage;
            return this;
        }

        /** Plain sentence(s); wrapped to ~32 characters and max 2 lines by the lore builder. */
        public Builder description(String text) {
            this.description = text;
            return this;
        }

        public Builder action(String input, String text) {
            actions.add(new Action(input, text));
            return this;
        }

        public Builder unique() {
            this.unique = true;
            return this;
        }

        public Builder glint() {
            this.glint = true;
            return this;
        }

        /** No durability (owner decision for custom tools). */
        public Builder unbreakable() {
            this.unbreakable = true;
            return this;
        }

        public Builder itemModel(Key key) {
            this.itemModel = key;
            return this;
        }

        /** 1-99; 0 = vanilla default. Unique items are always 1. */
        public Builder maxStack(int maxStack) {
            this.maxStack = maxStack;
            return this;
        }

        public Builder renderer(Renderer renderer) {
            this.renderer = renderer;
            return this;
        }

        public ItemDefinition build() {
            return new ItemDefinition(id, material, type, rarity, name, subtitle, description, actions, unique, glint,
                    unbreakable, itemModel, maxStack, renderer);
        }
    }
}

package net.skybornmc.skyblock.auction;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Browse categories, picked automatically from the item: the custom item type first (tool, cosmetic …), then the
 * vanilla material. {@link #categorize} is pure so it can be unit-tested without a server.
 */
enum Category {
    TOOLS,
    ARMOR,
    BLOCKS,
    MATERIALS,
    CONSUMABLES,
    COSMETICS,
    SPECIAL;

    private static final Set<String> TOOL_SUFFIXES = Set.of("_PICKAXE", "_AXE", "_SHOVEL", "_HOE", "_SWORD", "_SPEAR");
    private static final Set<String> TOOL_NAMES = Set.of("BOW", "CROSSBOW", "TRIDENT", "MACE", "FISHING_ROD", "SHEARS",
            "FLINT_AND_STEEL", "BRUSH", "SPYGLASS", "CARROT_ON_A_STICK", "WARPED_FUNGUS_ON_A_STICK");
    private static final Set<String> ARMOR_SUFFIXES = Set.of("_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS", "_HORSE_ARMOR");
    private static final Set<String> ARMOR_NAMES = Set.of("ELYTRA", "SHIELD", "TURTLE_HELMET", "WOLF_ARMOR", "CARVED_PUMPKIN_HEAD");
    private static final Set<String> CONSUMABLE_NAMES = Set.of("POTION", "SPLASH_POTION", "LINGERING_POTION", "TIPPED_ARROW",
            "EXPERIENCE_BOTTLE", "ENCHANTED_BOOK", "FIREWORK_ROCKET", "TOTEM_OF_UNDYING", "OMINOUS_BOTTLE");

    String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<Category> byKey(String key) {
        for (Category c : values()) {
            if (c.key().equalsIgnoreCase(key)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    /**
     * @param material   the vanilla material name ({@code DIAMOND_PICKAXE})
     * @param customType the custom item type id ({@code tool}, {@code cosmetic} …, see ItemType#id) or {@code null}
     * @param block      whether the material is a placeable block
     * @param edible     whether the material can be eaten
     */
    static Category categorize(String material, String customType, boolean block, boolean edible) {
        if (customType != null) {
            switch (customType) {
                case "tool" -> {
                    return TOOLS;
                }
                case "armor", "set-piece" -> {
                    return ARMOR;
                }
                case "cosmetic" -> {
                    return COSMETICS;
                }
                case "consumable" -> {
                    return CONSUMABLES;
                }
                case "material" -> {
                    return MATERIALS;
                }
                default -> {
                    return SPECIAL; // crate keys, pets, collectors, blueprints, notes …
                }
            }
        }
        String m = material == null ? "" : material.toUpperCase(Locale.ROOT);
        if (TOOL_NAMES.contains(m) || TOOL_SUFFIXES.stream().anyMatch(m::endsWith)) {
            return TOOLS;
        }
        if (ARMOR_NAMES.contains(m) || ARMOR_SUFFIXES.stream().anyMatch(m::endsWith)) {
            return ARMOR;
        }
        if (edible || CONSUMABLE_NAMES.contains(m)) {
            return CONSUMABLES;
        }
        if (block) {
            return BLOCKS;
        }
        return MATERIALS;
    }
}

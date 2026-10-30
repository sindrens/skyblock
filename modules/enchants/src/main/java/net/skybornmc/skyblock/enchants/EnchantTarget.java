package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.tools.ToolKind;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** What an enchant can go on (ENCHANTS §2.1): the five custom tools and the four wardrobe set pieces. */
enum EnchantTarget {
    PICKAXE(ToolKind.PICKAXE),
    AXE(ToolKind.AXE),
    HOE(ToolKind.HOE),
    SWORD(ToolKind.SWORD),
    ROD(ToolKind.ROD),
    HELMET(null),
    CHESTPLATE(null),
    LEGGINGS(null),
    BOOTS(null);

    private final ToolKind kind;

    EnchantTarget(ToolKind kind) {
        this.kind = kind;
    }

    /** The tool kind, or null for an armor piece. */
    ToolKind kind() {
        return kind;
    }

    boolean armor() {
        return kind == null;
    }

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<EnchantTarget> of(ToolKind kind) {
        for (EnchantTarget t : values()) {
            if (t.kind == kind) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    /**
     * Parses one entry of {@code tools:}: a target id, {@code tools} (all five tools) or {@code armor} (all four pieces).
     * Empty for an unknown word.
     */
    static Optional<Set<EnchantTarget>> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        switch (key) {
            case "tools" -> {
                return Optional.of(EnumSet.of(PICKAXE, AXE, HOE, SWORD, ROD));
            }
            case "armor" -> {
                return Optional.of(EnumSet.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS));
            }
            default -> {
                for (EnchantTarget t : values()) {
                    if (t.id().equals(key)) {
                        return Optional.of(EnumSet.of(t));
                    }
                }
                return Optional.empty();
            }
        }
    }
}

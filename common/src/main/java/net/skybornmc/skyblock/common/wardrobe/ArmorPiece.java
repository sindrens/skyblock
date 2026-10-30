package net.skybornmc.skyblock.common.wardrobe;

import org.bukkit.inventory.EquipmentSlot;

import java.util.Locale;
import java.util.Optional;

/** The four armor slots a wardrobe set has a piece for (WARDROBE §4.2), in display order. */
public enum ArmorPiece {
    HEAD(EquipmentSlot.HEAD),
    CHEST(EquipmentSlot.CHEST),
    LEGS(EquipmentSlot.LEGS),
    FEET(EquipmentSlot.FEET);

    private final EquipmentSlot slot;

    ArmorPiece(EquipmentSlot slot) {
        this.slot = slot;
    }

    /** The vanilla equipment slot. */
    public EquipmentSlot slot() {
        return slot;
    }

    /** Lowercase key used in the database, configs and messages ({@code head}, {@code chest} …). */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ArmorPiece> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        for (ArmorPiece p : values()) {
            if (p.key().equalsIgnoreCase(key.trim())) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public static Optional<ArmorPiece> of(EquipmentSlot slot) {
        for (ArmorPiece p : values()) {
            if (p.slot == slot) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}

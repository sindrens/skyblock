package net.skybornmc.skyblock.common.item;

import java.util.Locale;

/** Item rarity (UI_STYLE §1.4). {@link #id()} matches the {@code rarity:} keys in style.yml and messages/items.yml. */
public enum Rarity {
    COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC, SPECIAL;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}

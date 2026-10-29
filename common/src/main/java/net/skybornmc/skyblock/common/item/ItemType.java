package net.skybornmc.skyblock.common.item;

import java.util.Locale;

/** Item types and their footer (UI_STYLE §4.2). {@link #MENU} items have no footer. */
public enum ItemType {
    TOOL, ARMOR, SET_PIECE, COLLECTOR, CRATE_KEY, PET_EGG, PET, CONSUMABLE, MATERIAL, NOTE, COSMETIC, MENU;

    /** Key under {@code type.} in messages/items.yml, e.g. {@code set-piece}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public boolean hasFooter() {
        return this != MENU;
    }
}

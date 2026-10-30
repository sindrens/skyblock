package net.skybornmc.skyblock.box;

import java.util.UUID;

/** A stored box row (oldest first by {@code id}). */
record BoxEntry(long id, UUID profileKey, byte[] bytes, String material, int amount, String name, String source, long createdAt) {

    /** The row as a new delivery (give-back keeps the original source and arrival time). */
    BoxItem asItem() {
        return new BoxItem(bytes, material, amount, name, source, createdAt, null);
    }
}

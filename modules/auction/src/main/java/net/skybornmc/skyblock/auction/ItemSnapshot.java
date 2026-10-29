package net.skybornmc.skyblock.auction;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A listed item as the database keeps it: the serialized stack ({@code ItemStack#serializeAsBytes}) plus the plain
 * facts the browse menu, the relist limit and the audit log need without deserializing it.
 *
 * @param bytes    the serialized stack (the only thing ever turned back into an item)
 * @param itemKey  custom item id, or the material key for vanilla items (relist limit)
 * @param itemUuid unique item id (tools, tag items …) or {@code null}
 * @param name     plain display name (search)
 */
record ItemSnapshot(byte[] bytes, String itemKey, String itemUuid, String material, int amount, String name, Category category) {

    static final int MAX_NAME = 128;

    ItemSnapshot {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(itemKey, "itemKey");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(category, "category");
        name = name == null ? material : name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
        itemKey = itemKey.length() > 80 ? itemKey.substring(0, 80) : itemKey;
    }

    /** Full item summary for the audit log ({@code /logs}); {@code data} is the exact stack, so staff can restore it. */
    Map<String, Object> audit() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("material", material);
        m.put("amount", amount);
        m.put("name", name);
        m.put("id", itemKey);
        if (itemUuid != null) {
            m.put("uuid", itemUuid);
        }
        m.put("category", category.key());
        putData(m, bytes);
        return m;
    }

    /** Largest serialized item copied into an audit row (the audit details column is a TEXT); bigger ones stay in the database. */
    static final int AUDIT_DATA_MAX_BYTES = 16 * 1024;

    /** Adds {@code data} (Base64 of the exact stack), or {@code data_bytes} only when it is too big for an audit row. */
    static void putData(Map<String, Object> details, byte[] bytes) {
        if (bytes.length <= AUDIT_DATA_MAX_BYTES) {
            details.put("data", Base64.getEncoder().encodeToString(bytes));
        } else {
            details.put("data_bytes", bytes.length);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ItemSnapshot s && java.util.Arrays.equals(bytes, s.bytes) && itemKey.equals(s.itemKey)
                && Objects.equals(itemUuid, s.itemUuid) && material.equals(s.material) && amount == s.amount
                && name.equals(s.name) && category == s.category;
    }

    @Override
    public int hashCode() {
        return Objects.hash(java.util.Arrays.hashCode(bytes), itemKey, itemUuid, material, amount, name, category);
    }

    @Override
    public String toString() {
        return amount + "x " + itemKey + " (" + name + ")";
    }
}

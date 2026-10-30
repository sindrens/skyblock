package net.skybornmc.skyblock.trade;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One offered item stack as the escrow keeps it: the exact serialized stack ({@code ItemStack#serializeAsBytes}, so unique
 * item ids, enchant books, notes and tokens stay exactly the same) plus plain facts for menus and the audit log.
 *
 * @param rowId    the escrow row ({@code trade_escrow.id}); 0 while it is not stored yet
 * @param itemKey  custom item id, or the material key for vanilla items
 * @param itemUuid unique item id (tools, notes, books …) or {@code null}
 */
record Offer(long rowId, byte[] bytes, String itemKey, String itemUuid, String material, int amount, String name) {

    static final int MAX_NAME = 128;
    /** Largest serialized item copied into an audit row; bigger ones stay in the database (escrow row). */
    static final int AUDIT_DATA_MAX_BYTES = 16 * 1024;

    Offer {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(itemKey, "itemKey");
        Objects.requireNonNull(material, "material");
        name = name == null ? material : name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
        itemKey = itemKey.length() > 80 ? itemKey.substring(0, 80) : itemKey;
    }

    Offer withRow(long id) {
        return new Offer(id, bytes, itemKey, itemUuid, material, amount, name);
    }

    /** Full item summary for the audit log; {@code data} is the exact stack, so staff can restore it. */
    Map<String, Object> audit() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("material", material);
        m.put("amount", amount);
        m.put("name", name);
        m.put("id", itemKey);
        if (itemUuid != null) {
            m.put("uuid", itemUuid);
        }
        if (rowId > 0) {
            m.put("escrow_id", rowId);
        }
        if (bytes.length <= AUDIT_DATA_MAX_BYTES) {
            m.put("data", Base64.getEncoder().encodeToString(bytes));
        } else {
            m.put("data_bytes", bytes.length);
        }
        return m;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Offer s && rowId == s.rowId && java.util.Arrays.equals(bytes, s.bytes) && itemKey.equals(s.itemKey)
                && Objects.equals(itemUuid, s.itemUuid) && material.equals(s.material) && amount == s.amount && name.equals(s.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rowId, java.util.Arrays.hashCode(bytes), itemKey, itemUuid, material, amount, name);
    }

    @Override
    public String toString() {
        return amount + "x " + itemKey + " (" + name + ", row " + rowId + ")";
    }
}

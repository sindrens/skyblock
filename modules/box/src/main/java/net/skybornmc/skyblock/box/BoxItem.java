package net.skybornmc.skyblock.box;

import java.util.Objects;

/**
 * One stack on its way into the box, already serialized ({@code ItemStack#serializeAsBytes}), Bukkit-free so the
 * database code is unit-tested without a server.
 *
 * @param ref idempotency key of a spooled delivery ({@code null} for normal deliveries)
 */
record BoxItem(byte[] bytes, String material, int amount, String name, String source, long createdAt, String ref) {

    static final int MAX_NAME = 128;
    static final int MAX_MATERIAL = 64;

    BoxItem {
        Objects.requireNonNull(bytes, "bytes");
        material = cut(material == null ? "AIR" : material, MAX_MATERIAL);
        name = cut(name == null ? "" : name, MAX_NAME);
        source = net.skybornmc.skyblock.common.box.BoxSource.clean(source);
    }

    BoxItem withRef(String newRef) {
        return new BoxItem(bytes, material, amount, name, source, createdAt, newRef);
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}

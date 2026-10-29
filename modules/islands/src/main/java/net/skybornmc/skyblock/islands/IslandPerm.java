package net.skybornmc.skyblock.islands;

import java.util.Locale;
import java.util.Optional;

/**
 * Everything a player can be allowed or denied on an island (M2.2). Each role of an island (and its visitors) has a set
 * of these: defaults from {@code islands.yml → permissions.defaults}, per-island overrides in {@code island_role_perms}
 * (edited by the M2.3 permission matrix GUI). The stored key ({@link #key()}) never changes; the enum order may.
 */
public enum IslandPerm {
    /** Break blocks, fill buckets, trample crops, harvest berries, extinguish fire. */
    BREAK("break"),
    /** Place blocks, empty buckets, edit signs, use bone meal, strip/till/wax blocks, end crystals. */
    PLACE("place"),
    /** Doors, trapdoors, fence gates, buttons, levers, pressure plates, bells, beds, workstations. */
    INTERACT("interact"),
    /** Chests, barrels, furnaces, hoppers, shulkers, brewing stands, lecterns, jukeboxes, storage minecarts … */
    CONTAINERS("containers"),
    /** Repeaters, comparators, daylight detectors, note blocks, redstone dust shape. */
    REDSTONE("redstone"),
    /** Hurt, breed, feed, shear, milk, leash, ride, bucket and name animals. */
    ANIMALS("animals"),
    /** Trade with (or hurt) villagers and wandering traders. */
    VILLAGERS("villagers"),
    /** Pick up items and arrows. */
    PICKUP("pickup"),
    /** Drop items. */
    DROP("drop"),
    /** Armor stands, item frames and paintings: place, break, change. */
    ARMOR_STANDS("armor-stands"),
    /** Boats and minecarts: place, enter, break. */
    VEHICLES("vehicles"),
    /** Flint and steel, fire charges, lighting TNT, campfires and candles. */
    FIRE("fire"),
    /** Ender pearls and chorus fruit teleports onto the island. */
    ENDER_PEARLS("ender-pearls"),
    /** Spawners and trial spawners (change, break). Custom spawners arrive later. */
    SPAWNERS("spawners");

    private static final IslandPerm[] VALUES = values();

    private final String key;

    IslandPerm(String key) {
        this.key = key;
    }

    /** Stable key used in islands.yml, the database and message keys. */
    public String key() {
        return key;
    }

    /** This permission's bit in a role mask. */
    public long bit() {
        return 1L << ordinal();
    }

    /** Every permission set. */
    public static long all() {
        return (1L << VALUES.length) - 1;
    }

    public static Optional<IslandPerm> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String k = key.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (IslandPerm perm : VALUES) {
            if (perm.key.equals(k)) {
                return Optional.of(perm);
            }
        }
        return Optional.empty();
    }

    static IslandPerm[] cached() {
        return VALUES;
    }
}

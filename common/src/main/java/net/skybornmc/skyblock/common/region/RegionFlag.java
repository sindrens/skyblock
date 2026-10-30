package net.skybornmc.skyblock.common.region;

import java.util.Locale;
import java.util.Optional;

/**
 * One rule a region (or a whole world) can deny or allow (DEPTHSPROT). Protection flags guard the build and can be
 * bypassed by builders ({@link #bypassable()}); gameplay flags (PvE, targeting, pearls, wind charges) apply to everyone.
 * Ids are the names used in {@code worlds.yml → protection.*}.
 */
public enum RegionFlag {
    /** Break or place blocks, buckets, bone meal, trampling, sign editing, placing armor stands/boats/frames. */
    BUILD("build", true),
    /** Right-click any block that isn't in {@code protection.interact-allowed} (doors, buttons, levers...). */
    INTERACT("interact", true),
    /** Open containers (chests, barrels, furnaces, hoppers...), also where {@link #INTERACT} is allowed. */
    CONTAINERS("containers", true),
    /** Use workstations (anvils, enchanting tables, grindstones, smithing tables...), also where {@link #INTERACT} is allowed. */
    WORKSTATIONS("workstations", true),
    /** Rotate/fill/empty item frames, edit armor stands, break paintings, frames and armor stands. */
    ENTITY_EDIT("entity-edit", true),
    /** Fire: lighting, spreading and burning blocks. */
    FIRE("fire", true),
    /** Explosions destroying blocks (the explosion still happens, the blocks stay). */
    EXPLOSIONS("explosions", true),
    /** Players standing here deal no damage to mobs. */
    PVE_DEALT("pve-dealt", false),
    /** Players standing here take no damage from mobs (melee or their projectiles). */
    PVE_TAKEN("pve-taken", false),
    /** Mobs never pick (or keep) a player standing here as their target. */
    MOB_TARGET("mob-target", false),
    /** No ender pearls thrown here or landing here. */
    ENDER_PEARL("ender-pearl", false),
    /** No wind charges thrown here. */
    WIND_CHARGE("wind-charge", false);

    private static final RegionFlag[] VALUES = values();

    private final String id;
    private final boolean bypassable;

    RegionFlag(String id, boolean bypassable) {
        this.id = id;
        this.bypassable = bypassable;
    }

    /** Config id ({@code build}, {@code pve-dealt}...). */
    public String id() {
        return id;
    }

    /** True for protection flags that builders/admins may bypass (creative + permission or build mode). */
    public boolean bypassable() {
        return bypassable;
    }

    /** Flag by config id (case-insensitive, {@code _} and {@code -} are the same). */
    public static Optional<RegionFlag> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (RegionFlag flag : VALUES) {
            if (flag.id.equals(key)) {
                return Optional.of(flag);
            }
        }
        return Optional.empty();
    }

    /** Number of flags (array sizes). */
    public static int count() {
        return VALUES.length;
    }

    /** All flags (shared array, do not modify). */
    static RegionFlag[] all() {
        return VALUES;
    }
}

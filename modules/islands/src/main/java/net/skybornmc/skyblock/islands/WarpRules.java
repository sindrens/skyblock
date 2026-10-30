package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Role;

import java.util.regex.Pattern;

/**
 * Island warp rules (M2.4, pure): names, slots and who may manage them. Owner decision: 3 warps per island, more with
 * upgrades later (island column {@code warp_slots}); sign warps count toward the same slots (I15).
 */
public final class WarpRules {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private WarpRules() {
    }

    /** Why a warp name is not valid, or OK. */
    public enum NameCheck {
        OK, EMPTY, TOO_LONG, CHARACTERS
    }

    /** Letters, digits, {@code _} and {@code -}, 1 to {@code maxLength} characters. */
    public static NameCheck checkName(String name, int maxLength) {
        if (name == null || name.isBlank()) {
            return NameCheck.EMPTY;
        }
        if (name.length() > maxLength) {
            return NameCheck.TOO_LONG;
        }
        return NAME.matcher(name).matches() ? NameCheck.OK : NameCheck.CHARACTERS;
    }

    /** Case-insensitive key (unique per island). */
    public static String key(String name) {
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    /** Effective warp slots: the island's own value ({@code null} = start value), between 0 and the max. */
    public static int slots(Integer islandSlots, int startSlots, int maxSlots) {
        int max = Math.max(0, maxSlots);
        int value = islandSlots == null ? startSlots : islandSlots;
        return Math.max(0, Math.min(max, value));
    }

    /** A new warp fits when the island has fewer warps than slots (moving an existing warp always fits). */
    public static boolean fits(int warps, int slots, boolean exists) {
        return exists || warps < slots;
    }

    /** Leader and mods set, move, edit and delete warps. */
    public static boolean canManage(Role actor) {
        return MemberRules.isManager(actor);
    }

    /** Private warps are for members and coop players only; public warps for everyone who may enter. */
    public static boolean canUse(boolean isPublic, Role role) {
        return isPublic || role != null;
    }
}

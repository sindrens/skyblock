package net.skybornmc.skyblock.common.profile;

import java.util.Locale;
import java.util.Optional;

/**
 * Why a profile was wiped (4 Nov 2026, "island = profile"): every way of leaving an island wipes the profile that was on
 * it. Stored on the snapshot ({@code profile_snapshots.reason}) and in the audit details; {@link #key()} is the stored
 * value and the message key suffix.
 */
public enum WipeReason {
    /** {@code /is leave} or the Leave button. */
    LEAVE,
    /** Kicked by the island ({@code /is kick}). */
    KICK,
    /** Removed by staff or moderation ({@code /isadmin kick}, a punishment). */
    REMOVED,
    /** The leader disbanded the island. */
    DISBAND,
    /** Staff deleted the island ({@code /isadmin delete}). */
    ISLAND_DELETED,
    /** Staff reset the island ({@code /isadmin reset}). */
    ISLAND_RESET,
    /** The player replaced their solo island to join another one (accepting an invite). */
    REPLACE,
    /** Staff reset the profile ({@code /profiles reset}). */
    STAFF,
    /** The profile was put aside by a staff restore of an older snapshot into its slot. */
    RESTORE_REPLACED;

    /** Lowercase stored key, e.g. {@code island_deleted}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<WipeReason> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        for (WipeReason r : values()) {
            if (r.key().equalsIgnoreCase(key.strip())) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }
}

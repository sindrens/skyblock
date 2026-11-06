package net.skybornmc.skyblock.profiles;

import java.util.UUID;

/**
 * Pure rules of the profile wipe and its snapshots (owner 4 Nov 2026, "island = profile"); unit-tested without a server.
 * See {@link net.skybornmc.skyblock.common.profile.WipeScope} for what a wipe keeps.
 */
final class WipeRules {

    static final long DAY_MILLIS = 86_400_000L;

    private WipeRules() {
    }

    // ------------------------------------------------------------------ snapshots

    /** State of a snapshot row. */
    enum SnapshotStatus {
        /** Restorable. */
        OPEN,
        /** Put back by staff (can't be restored twice). */
        RESTORED,
        /** Retention over, not purged yet (no longer restorable; the purge deletes it soon). */
        EXPIRED,
        /** Retention over and every module deleted the old key's rows. */
        PURGED;

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** When a snapshot taken at {@code createdAt} expires ({@code days} at least 1). */
    static long expiresAt(long createdAt, int days) {
        return createdAt + Math.max(1, days) * DAY_MILLIS;
    }

    static SnapshotStatus status(Long restoredAt, Long purgedAt, long expiresAt, long now) {
        if (purgedAt != null) {
            return SnapshotStatus.PURGED;
        }
        if (restoredAt != null) {
            return SnapshotStatus.RESTORED;
        }
        return now >= expiresAt ? SnapshotStatus.EXPIRED : SnapshotStatus.OPEN;
    }

    static boolean restorable(Long restoredAt, Long purgedAt, long expiresAt, long now) {
        return status(restoredAt, purgedAt, expiresAt, now) == SnapshotStatus.OPEN;
    }

    /** Due for the purge: expired, never restored, not purged yet. */
    static boolean purgeDue(Long restoredAt, Long purgedAt, long expiresAt, long now) {
        return status(restoredAt, purgedAt, expiresAt, now) == SnapshotStatus.EXPIRED;
    }

    // ------------------------------------------------------------------ the player file (crash safety)

    /** What the join has to do with the player file before the player may play. */
    enum JoinStep {
        /** The file holds the active profile's state: nothing to do. */
        NOTHING,
        /** A switch/wipe/restore finished in the database but not in game: apply the active profile's saved state. */
        APPLY_PENDING,
        /** The file holds another (retired or inactive) profile's items: save them to that profile first, then apply. */
        CAPTURE_THEN_APPLY,
        /** A stale capture row that points at the active profile: drop it, the file already is the active profile. */
        DROP_CAPTURE,
        /** A stale capture row plus a pending state: drop the row and trust the database (never a duplicate). */
        DROP_CAPTURE_THEN_APPLY
    }

    /**
     * @param active     the active profile in the database
     * @param capture    the profile whose items are in the player file ({@code profile_captures}), or null
     * @param pendingRow a saved state row exists for the active profile
     */
    static JoinStep joinStep(UUID active, UUID capture, boolean pendingRow) {
        if (capture == null) {
            return pendingRow ? JoinStep.APPLY_PENDING : JoinStep.NOTHING;
        }
        if (capture.equals(active)) {
            return pendingRow ? JoinStep.DROP_CAPTURE_THEN_APPLY : JoinStep.DROP_CAPTURE;
        }
        return JoinStep.CAPTURE_THEN_APPLY;
    }

    /**
     * Does the wipe (or restore) of the active profile of an <b>offline</b> player need a capture row? Only when the
     * player file really holds that profile's items: there is no saved state row for it (a row means the file is a stale
     * copy that the next join overwrites) and no other capture is waiting (then the file belongs to that one).
     */
    static boolean needsCapture(boolean wasActive, boolean online, boolean hasStateRow, boolean captureExists) {
        return wasActive && !online && !hasStateRow && !captureExists;
    }

    // ------------------------------------------------------------------ intents

    /** What to do with a wipe intent left by a crash. */
    enum IntentAction {
        /** Too young: the removal may still be saving. */
        WAIT,
        /** The profile left the island: finish the wipe. */
        WIPE,
        /** The removal never happened (still on the island) or the profile is gone already: forget it. */
        DROP
    }

    /**
     * @param live           the profile key is still live
     * @param stillOnIsland  the profile is still a member of the intent's island
     * @param ageMillis      how long ago the intent was written
     * @param graceMillis    minimum age before a leftover counts as a crash
     */
    static IntentAction intent(boolean live, boolean stillOnIsland, long ageMillis, long graceMillis) {
        if (!live) {
            return IntentAction.DROP;
        }
        if (ageMillis < graceMillis) {
            return IntentAction.WAIT;
        }
        return stillOnIsland ? IntentAction.DROP : IntentAction.WIPE;
    }

    // ------------------------------------------------------------------ summary

    /** Items in a list of stacks (null and air count 0). */
    static long countItems(org.bukkit.inventory.ItemStack[] stacks) {
        long n = 0;
        if (stacks == null) {
            return 0;
        }
        for (org.bukkit.inventory.ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                n += stack.getAmount();
            }
        }
        return n;
    }

    /** An item count as int for the snapshot row (capped). */
    static int clampItems(long items) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, items));
    }
}

package net.skybornmc.skyblock.common.profile;

import java.util.UUID;

/**
 * Outcome of {@link ProfileService#wipe}.
 *
 * @param status     what happened
 * @param gone       the retired profile key (the snapshot), or {@code null}
 * @param fresh      the new empty profile key in the same slot, or {@code null}
 * @param snapshotId the snapshot row ({@code /profiles restore}), or 0
 */
public record ProfileWipeResult(Status status, UUID gone, UUID fresh, long snapshotId) {

    public enum Status {
        /** Wiped; the player was online and plays on the fresh profile now (or the profile was not the active one). */
        WIPED,
        /** Wiped in the database; the items in the (offline) player's file are taken on their next join. */
        WIPED_ON_JOIN,
        /** Not a live profile (already wiped or deleted): nothing to do. */
        NOT_FOUND,
        /** The player is switching right now; the wipe stays pending and is retried by the profiles module. */
        BUSY,
        /** No profiles module, or the wipe failed (logged); the pending wipe is retried. */
        UNAVAILABLE;

        public boolean done() {
            return this == WIPED || this == WIPED_ON_JOIN;
        }
    }

    public static ProfileWipeResult of(Status status) {
        return new ProfileWipeResult(status, null, null, 0);
    }

    public boolean done() {
        return status.done();
    }
}

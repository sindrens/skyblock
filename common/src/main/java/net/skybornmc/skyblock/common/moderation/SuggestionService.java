package net.skybornmc.skyblock.common.moderation;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Punishment suggestions from detectors (Phase P spec 12.8), provided by the moderation module. A chat filter or a later
 * anti-spam/anticheat module calls {@link #suggest}; staff get the same {@code [Apply] [Dismiss] [Open /p]} line as for
 * the warn rules. A suggestion <b>never</b> applies by itself. One pending suggestion per target + reason: a second call
 * refreshes the waiting one instead of adding another.
 */
public interface SuggestionService {

    enum Status {
        /** A new pending suggestion was created and staff were told. */
        CREATED,
        /** The waiting suggestion for this target + reason was refreshed (details, expiry); staff were not spammed. */
        REFRESHED,
        /** The reason id is not in punishments.yml. */
        UNKNOWN_REASON,
        /** No player with this uuid ever joined. */
        UNKNOWN_PLAYER
    }

    /** {@code id} = the suggestion id, or 0 when nothing was stored. */
    record Result(Status status, long id) {
    }

    /**
     * Creates (or refreshes) a pending suggestion. Thread-safe; completes off the main thread.
     *
     * @param target   the player
     * @param reasonId a reason id of punishments.yml (its ladder decides the suggested punishment)
     * @param source   who suggests it, short (e.g. "chat filter"), shown to staff
     * @param details  what was detected, short (e.g. "3 spam hits in 10s"), shown to staff; may be empty
     */
    CompletableFuture<Result> suggest(UUID target, String reasonId, String source, String details);
}

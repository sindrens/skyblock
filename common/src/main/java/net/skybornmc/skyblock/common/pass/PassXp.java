package net.skybornmc.skyblock.common.pass;

import net.skybornmc.skyblock.common.service.ServiceRegistry;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Battle pass XP as seen by other modules (PHASE-10 M10.8). Published by the pass module once it exists:
 * {@code core.services().optional(PassXp.class)}. Until then sources (playtime, votes, quests) simply give no pass XP.
 */
public interface PassXp {

    /** The published service, if the battle pass module runs. */
    static Optional<PassXp> of(ServiceRegistry services) {
        return services.optional(PassXp.class);
    }

    /**
     * Adds pass XP to a profile (per profile, P10-28) without an idempotency key: every call counts. Prefer
     * {@link #addXp(UUID, int, String, String)} for anything that may be retried.
     *
     * @param source short snake_case source, e.g. {@code playtime}
     */
    default CompletableFuture<Boolean> addXp(UUID profileKey, int xp, String source) {
        return addXp(profileKey, xp, source, null);
    }

    /**
     * Adds pass XP to a profile (per profile, P10-28). Completes with false if it could not be stored; the caller may
     * try again. With an {@code eventKey} the XP is stored at most once per key: a retry of a call that did commit (lost
     * answer, crash right after) completes with true and adds nothing, so hand-out rows can safely be sent again.
     *
     * @param source   short snake_case source, e.g. {@code playtime}
     * @param eventKey unique id of the event that earned the XP (at most 96 characters, e.g. {@code quests:<grant id>});
     *                 null = no idempotency
     */
    CompletableFuture<Boolean> addXp(UUID profileKey, int xp, String source, String eventKey);
}

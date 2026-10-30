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
     * Adds pass XP to a profile (per profile, P10-28). Completes with false if it could not be stored; the caller may
     * try again.
     *
     * @param source short snake_case source, e.g. {@code playtime}
     */
    CompletableFuture<Boolean> addXp(UUID profileKey, int xp, String source);
}

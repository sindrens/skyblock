package net.skybornmc.skyblock.common.cosmetics;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Titles and tags (M1.11). Published as a service by the cosmetics module:
 * {@code core.services().optional(Cosmetics.class)}.
 *
 * <p><b>Achievements hook:</b> any module grants a title when something cool happens, e.g.
 * {@code cosmetics.grantTitle(player.getUniqueId(), "first_island", "island:create")}. Granting is idempotent (an
 * owned title is never granted twice), works for offline players, notifies online players and is audited.</p>
 */
public interface Cosmetics {

    enum Kind { TITLE, TAG }

    /**
     * Gives a title or tag. Completes with {@code true} if it was new, {@code false} if already owned.
     * Fails with {@link IllegalArgumentException} for an unknown id.
     *
     * @param reason short machine-readable source for the audit log, e.g. {@code achievement:first_boss}
     */
    CompletableFuture<Boolean> grant(UUID player, Kind kind, String id, String reason);

    default CompletableFuture<Boolean> grantTitle(UUID player, String id, String reason) {
        return grant(player, Kind.TITLE, id, reason);
    }

    default CompletableFuture<Boolean> grantTag(UUID player, String id, String reason) {
        return grant(player, Kind.TAG, id, reason);
    }

    /** Removes one (staff action). Completes with {@code true} if it was owned. */
    CompletableFuture<Boolean> revoke(UUID player, Kind kind, String id, String reason);

    /** Whether an online player owns it (cached; offline players: false). */
    boolean owns(UUID player, Kind kind, String id);

    /** Registered ids of a kind. */
    Set<String> ids(Kind kind);

    /** How a title or tag looks for {@code viewer} (its configured colors); the plain id when unknown. */
    default Component display(Audience viewer, Kind kind, String id) {
        return Component.text(String.valueOf(id));
    }
}

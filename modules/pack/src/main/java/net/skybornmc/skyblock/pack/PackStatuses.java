package net.skybornmc.skyblock.pack;

import net.kyori.adventure.resource.ResourcePackStatus;
import net.skybornmc.skyblock.common.pack.PackService.Status;

import java.util.Optional;

/** Pure mapping from the client's resource pack answers to ours, and the kick rule (GUI-P1-PACK 5.3). */
final class PackStatuses {

    private PackStatuses() {
    }

    /** The final status for a client report, or empty while the client is still working (accepted, downloaded). */
    static Optional<Status> terminal(ResourcePackStatus s) {
        if (s == null) {
            return Optional.empty();
        }
        return switch (s) {
            case SUCCESSFULLY_LOADED -> Optional.of(Status.LOADED);
            case DECLINED -> Optional.of(Status.DECLINED);
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> Optional.of(Status.FAILED);
            case ACCEPTED, DOWNLOADED -> Optional.empty();
        };
    }

    /** Same, for Bukkit's {@code PlayerResourcePackStatusEvent.Status} (identical constant names), by name. */
    static Optional<Status> terminalByName(String name) {
        try {
            return terminal(ResourcePackStatus.valueOf(name));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    /** A player already in the game: a stray DISCARDED (the old copy replaced by a resend) is no failure. */
    static Optional<Status> terminalWhilePlaying(ResourcePackStatus s) {
        return s == ResourcePackStatus.DISCARDED ? Optional.empty() : terminal(s);
    }

    /**
     * The kick message key for a final status: {@code kick.declined} / {@code kick.failed} only when the pack is
     * required, this offer is enforced (the first offer at join, never a late resend) and the host is healthy;
     * everything else (LOADED, BEDROCK, ...) never kicks.
     */
    static Optional<String> kickKey(Status status, boolean required, boolean enforce, boolean healthy) {
        if (status == null || !required || !enforce || !healthy) {
            return Optional.empty();
        }
        return switch (status) {
            case DECLINED -> Optional.of("kick.declined");
            case FAILED -> Optional.of("kick.failed");
            default -> Optional.empty();
        };
    }
}

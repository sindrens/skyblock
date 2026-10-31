package net.skybornmc.skyblock.pack;

import net.kyori.adventure.resource.ResourcePackStatus;
import net.skybornmc.skyblock.common.pack.PackService.Status;

import java.util.Optional;

/** Pure mapping from the client's resource pack status to ours. */
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

    /** What a player who is already in the game gets from a resend: a stray DISCARDED (the old copy replaced) is no failure. */
    static Optional<Status> terminalWhilePlaying(ResourcePackStatus s) {
        return s == ResourcePackStatus.DISCARDED ? Optional.empty() : terminal(s);
    }

    /** Whether this final status ends the connection under the given settings, and with which message key. */
    static Optional<String> kickKey(Status result, boolean required, boolean kickOnFail) {
        return switch (result) {
            case DECLINED -> required ? Optional.of("kick.declined") : Optional.empty();
            case FAILED -> kickOnFail ? Optional.of("kick.failed") : Optional.empty();
            default -> Optional.empty();
        };
    }
}

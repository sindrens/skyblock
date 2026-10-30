package net.skybornmc.skyblock.common.audit;

import org.bukkit.command.CommandSender;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * One audit entry by its id, as the staff log viewer ({@code /logs}) shows it (Phase P spec 13.11: punish from logs).
 * Provided by the logs module, used by moderation for {@code /p <player> --evidence <log-entry-id>}, so neither module
 * touches the other's classes. The logs module owns who may see what ({@code skyblock.staff.logs.category.<category>}),
 * which is why the lookup lives there and not in {@link AuditLog}.
 *
 * <pre>{@code
 * core.services().optional(LogLookup.class).ifPresent(l -> l.entry(staff, 1234).thenAccept(...));
 * }</pre>
 */
public interface LogLookup {

    /**
     * The entry with {@code id} if it exists and {@code viewer} may see its category in {@code /logs} ({@code null} or the
     * console: every category). Call on the main thread (permissions are read there); completes off the main thread.
     */
    CompletableFuture<Optional<AuditEntry>> entry(CommandSender viewer, long id);
}

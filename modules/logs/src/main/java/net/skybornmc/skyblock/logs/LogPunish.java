package net.skybornmc.skyblock.logs;

import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditEntry;
import net.skybornmc.skyblock.common.audit.AuditLog;
import net.skybornmc.skyblock.common.audit.AuditQuery;
import net.skybornmc.skyblock.common.audit.LogLookup;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Punish from logs (Phase P spec 13.11), the logs side. Two small pieces, nothing else in the logs module changes:
 * <ul>
 *   <li>{@link Lookup}: the {@link LogLookup} service (one audit entry by id, only if the viewer may see its category),
 *   used by moderation for {@code /p <player> --evidence <id>}.</li>
 *   <li>{@link #command}: a chat line in the results (menu click, or [Punish] in the chat view) runs
 *   {@code /punish <player> --evidence <id>} for the staff member: the same entry point the staff tools use (the staff
 *   module dispatches {@code punish <name>} too), so the logs module never touches moderation classes.</li>
 * </ul>
 */
final class LogPunish {

    /** The punish menu command (owned by the moderation module) and the permission it needs. */
    static final String COMMAND = "punish";
    static final String PERM_PUNISH = "skyblock.staff.punish.gui";
    /** Audit actions of a chat line (chat module). */
    static final Set<String> CHAT_ACTIONS = Set.of("chat", "chat_blocked");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.*-]{1,32}");

    private LogPunish() {
    }

    /** A chat line of a known player (the only entries that can become evidence). */
    static boolean punishable(AuditEntry e) {
        return e != null && e.category() == AuditCategory.CHAT && CHAT_ACTIONS.contains(e.action())
                && e.actor() != null && e.actor().uuid() != null && e.actor().name() != null
                && NAME.matcher(e.actor().name()).matches();
    }

    /** {@code punish Steve --evidence 1234} (no slash), or {@code null} when the entry is not a punishable chat line. */
    static String command(AuditEntry e) {
        return punishable(e) ? COMMAND + " " + e.actor().name() + " --evidence " + e.id() : null;
    }

    /** True if {@code viewer} may use it: the punish permission, and the moderation module's command is there. */
    static boolean allowed(CommandSender viewer) {
        return viewer instanceof Player && viewer.hasPermission(PERM_PUNISH) && Bukkit.getCommandMap().getCommand(COMMAND) != null;
    }

    /**
     * The {@link LogLookup} service. Finds the entry's time and category by id, then reads it through the normal audit
     * query (so the row is mapped exactly like every other {@code /logs} result) and checks the viewer's categories.
     */
    static final class Lookup implements LogLookup {

        private final Database db;
        private final AuditLog audit;
        private final Function<CommandSender, Set<AuditCategory>> allowed;

        Lookup(Database db, AuditLog audit, Function<CommandSender, Set<AuditCategory>> allowed) {
            this.db = db;
            this.audit = audit;
            this.allowed = allowed;
        }

        record Key(long time, String category) {
        }

        @Override
        public CompletableFuture<Optional<AuditEntry>> entry(CommandSender viewer, long id) {
            Set<AuditCategory> visible = viewer == null ? null : allowed.apply(viewer); // permissions: on the caller's thread
            return db.async(c -> Sql.queryOne(c, "SELECT created_at, category FROM audit_log WHERE id = ?",
                    rs -> new Key(rs.getLong("created_at"), rs.getString("category")), id)).thenCompose(key -> {
                if (key.isEmpty()) {
                    return CompletableFuture.completedFuture(Optional.<AuditEntry>empty());
                }
                Optional<AuditCategory> category = AuditCategory.byKey(key.get().category());
                if (category.isEmpty() || (visible != null && !visible.contains(category.get()))) {
                    return CompletableFuture.completedFuture(Optional.<AuditEntry>empty());
                }
                AuditQuery q = AuditQuery.create().categories(category.get()).since(Instant.ofEpochMilli(key.get().time()))
                        .until(Instant.ofEpochMilli(key.get().time() + 1)).page(0, audit.maxQueryRows());
                return audit.query(q).thenApply(rows -> rows.stream().filter(r -> r.id() == id).findFirst());
            });
        }
    }
}

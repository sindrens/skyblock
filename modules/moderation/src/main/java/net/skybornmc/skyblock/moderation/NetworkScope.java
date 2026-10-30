package net.skybornmc.skyblock.moderation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Network-ready punishments (spec 13.12). Every new row stores the server it was given on ({@code server_id}) and where it
 * counts ({@code scope}: {@code network} = on every server sharing the database, {@code server} = only where it was given).
 * With {@code enabled: false} (one server, today) nothing changes: every active ban/mute counts. With {@code enabled: true}
 * the join and chat checks (every "active punishment" query) only see rows with {@code scope = network} or
 * {@code server_id = } this server. Rows from before P6 have no scope and count as {@code network}.
 *
 * <p>Nothing here talks to other servers: no proxy, messaging or sync. The database is the sync. Pure, unit-tested.
 */
final class NetworkScope {

    static final String NETWORK = "network";
    static final String SERVER = "server";
    static final List<String> SCOPES = List.of(NETWORK, SERVER);

    private NetworkScope() {
    }

    /**
     * {@code extras.network}.
     *
     * @param enabled      filter active punishments by scope (only when several servers share the database)
     * @param serverId     this server's id, stored on every new punishment (lowercase id, max 32)
     * @param defaultScope scope of new bans and mutes ({@code network} or {@code server}); warnings and kicks are always
     *                     {@code server} (local history)
     */
    record Settings(boolean enabled, String serverId, String defaultScope) {
        static final Settings DEFAULT = new Settings(false, "phoenix", NETWORK);
    }

    /** {@code "network"} / {@code "server"} (any case), or empty. */
    static Optional<String> parseScope(String raw) {
        String v = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        return SCOPES.contains(v) ? Optional.of(v) : Optional.empty();
    }

    /** The scope stored on a new row of this type. */
    static String scopeFor(Punishment.Type type, Settings s) {
        return type == Punishment.Type.BAN || type == Punishment.Type.MUTE ? s.defaultScope() : SERVER;
    }

    /**
     * Does a row with this scope and server count on this server? Always with the filter off; with it on: network rows
     * (and rows without a scope, from before P6) everywhere, server rows only on the server that gave them.
     */
    static boolean applies(String rowScope, String rowServer, Settings s) {
        if (!s.enabled()) {
            return true;
        }
        if (rowScope == null || NETWORK.equals(rowScope)) {
            return true;
        }
        return s.serverId().equals(rowServer);
    }

    /**
     * The same rule as SQL, appended to an "active punishments" query: empty with the filter off, else
     * {@code AND (scope IS NULL OR scope = 'network' OR server_id = ?)} with {@link #parameter} as its one parameter.
     */
    static String sql(Settings s) {
        return s.enabled() ? " AND (scope IS NULL OR scope = '" + NETWORK + "' OR server_id = ?)" : "";
    }

    /** The parameter of {@link #sql}, or {@code null} when there is none. */
    static String parameter(Settings s) {
        return s.enabled() ? s.serverId() : null;
    }
}

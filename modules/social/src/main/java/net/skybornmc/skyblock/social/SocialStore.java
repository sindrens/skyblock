package net.skybornmc.skyblock.social;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Database access for the social module. Every call is async (never on the main thread). */
final class SocialStore {

    private final Database db;

    SocialStore(Database db) {
        this.db = db;
    }

    CompletableFuture<Set<UUID>> ignores(UUID owner) {
        return db.async(c -> new HashSet<>(Sql.query(c, "SELECT ignored FROM social_ignores WHERE owner = ?",
                rs -> Sql.uuid(rs, "ignored"), Sql.toBytes(owner))));
    }

    CompletableFuture<Void> addIgnore(UUID owner, UUID ignored) {
        return db.run(c -> {
            Sql.update(c, "DELETE FROM social_ignores WHERE owner = ? AND ignored = ?", Sql.toBytes(owner), Sql.toBytes(ignored));
            Sql.update(c, "INSERT INTO social_ignores (owner, ignored, created) VALUES (?, ?, ?)",
                    Sql.toBytes(owner), Sql.toBytes(ignored), System.currentTimeMillis());
        });
    }

    CompletableFuture<Void> removeIgnore(UUID owner, UUID ignored) {
        return db.run(c -> Sql.update(c, "DELETE FROM social_ignores WHERE owner = ? AND ignored = ?",
                Sql.toBytes(owner), Sql.toBytes(ignored)));
    }

    /** Records that {@code uuid} joined from {@code ip} (bump last_seen or insert; works on MariaDB and H2). */
    CompletableFuture<Void> recordIp(UUID uuid, String ip) {
        long now = System.currentTimeMillis();
        return db.run(c -> {
            int updated = Sql.update(c, "UPDATE social_player_ips SET last_seen = ? WHERE uuid = ? AND ip = ?",
                    now, Sql.toBytes(uuid), ip);
            if (updated == 0) {
                Sql.update(c, "INSERT INTO social_player_ips (uuid, ip, first_seen, last_seen) VALUES (?, ?, ?, ?)",
                        Sql.toBytes(uuid), ip, now, now);
            }
        });
    }

    record IpRecord(String ip, long lastSeen) {
    }

    record AltRecord(UUID uuid, String name, String ip, long lastSeen) {
    }

    /** Addresses used by {@code uuid}, newest first (max 10). */
    CompletableFuture<List<IpRecord>> ips(UUID uuid) {
        return db.async(c -> Sql.query(c,
                "SELECT ip, last_seen FROM social_player_ips WHERE uuid = ? ORDER BY last_seen DESC LIMIT 10",
                rs -> new IpRecord(rs.getString("ip"), rs.getLong("last_seen")), Sql.toBytes(uuid)));
    }

    /** Other accounts that joined from any address {@code uuid} used (max 20). */
    CompletableFuture<List<AltRecord>> alts(UUID uuid) {
        return db.async(c -> Sql.query(c,
                "SELECT o.uuid, p.name, o.ip, o.last_seen FROM social_player_ips o "
                        + "JOIN social_player_ips m ON m.ip = o.ip AND m.uuid = ? "
                        + "LEFT JOIN players p ON p.uuid = o.uuid "
                        + "WHERE o.uuid <> ? ORDER BY o.last_seen DESC LIMIT 20",
                rs -> new AltRecord(Sql.uuid(rs, "uuid"), rs.getString("name"), rs.getString("ip"), rs.getLong("last_seen")),
                Sql.toBytes(uuid), Sql.toBytes(uuid)));
    }
}

package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Database access for punishments. Everything async; rows are never deleted (pardons set the removed_* columns). */
final class PunishmentStore {

    private static final String COLUMNS = "id, type, target_uuid, target_name, ip, ip_wide, reason, template, ladder_step, "
            + "issuer_uuid, issuer_name, created, expires, silent, removed_at, removed_by_uuid, removed_by_name, removed_reason";

    private final Database db;

    PunishmentStore(Database db) {
        this.db = db;
    }

    private static Punishment map(ResultSet rs) throws SQLException {
        int step = rs.getInt("ladder_step");
        Integer ladderStep = rs.wasNull() ? null : step;
        return new Punishment(rs.getLong("id"), Punishment.Type.valueOf(rs.getString("type")), Sql.uuid(rs, "target_uuid"),
                rs.getString("target_name"), rs.getString("ip"), rs.getBoolean("ip_wide"), rs.getString("reason"),
                rs.getString("template"), ladderStep, Sql.uuid(rs, "issuer_uuid"), rs.getString("issuer_name"),
                rs.getLong("created"), Sql.nullableLong(rs, "expires"), rs.getBoolean("silent"),
                Sql.nullableLong(rs, "removed_at"), Sql.uuid(rs, "removed_by_uuid"), rs.getString("removed_by_name"),
                rs.getString("removed_reason"));
    }

    private static byte[] bytes(UUID uuid) {
        return uuid == null ? null : Sql.toBytes(uuid);
    }

    /** Inserts and returns the row with its id. */
    CompletableFuture<Punishment> insert(Punishment p) {
        return db.async(c -> {
            long id = Sql.insertReturningId(c, "INSERT INTO punishments (type, target_uuid, target_name, ip, ip_wide, reason, "
                            + "template, ladder_step, issuer_uuid, issuer_name, created, expires, silent) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    p.type().name(), bytes(p.target()), p.targetName(), p.ip(), p.ipWide(), p.reason(), p.template(),
                    p.ladderStep(), bytes(p.issuer()), p.issuerName(), p.created(), p.expires(), p.silent());
            return new Punishment(id, p.type(), p.target(), p.targetName(), p.ip(), p.ipWide(), p.reason(), p.template(),
                    p.ladderStep(), p.issuer(), p.issuerName(), p.created(), p.expires(), p.silent(), null, null, null, null);
        });
    }

    /** Pardons every active punishment of {@code type} for the target (uuid) and/or IP. Returns the pardoned rows. */
    CompletableFuture<List<Punishment>> pardon(Punishment.Type type, UUID target, String ip, UUID by, String byName,
                                               String reason, boolean latestOnly) {
        return db.transaction(c -> {
            long now = System.currentTimeMillis();
            List<Punishment> active = new ArrayList<>(activeOf(c, type, target, ip, now));
            if (latestOnly && active.size() > 1) {
                active = new ArrayList<>(active.subList(0, 1));
            }
            for (Punishment p : active) {
                Sql.update(c, "UPDATE punishments SET removed_at = ?, removed_by_uuid = ?, removed_by_name = ?, removed_reason = ? "
                        + "WHERE id = ? AND removed_at IS NULL", now, bytes(by), byName, reason, p.id());
            }
            return active;
        });
    }

    private static List<Punishment> activeOf(Connection c, Punishment.Type type, UUID target, String ip, long now)
            throws SQLException {
        List<Punishment> out = new ArrayList<>();
        if (target != null) {
            out.addAll(Sql.query(c, "SELECT " + COLUMNS + " FROM punishments WHERE target_uuid = ? AND type = ? "
                            + "AND removed_at IS NULL AND (expires IS NULL OR expires > ?) ORDER BY created DESC",
                    PunishmentStore::map, Sql.toBytes(target), type.name(), now));
        }
        if (ip != null) {
            for (Punishment p : Sql.query(c, "SELECT " + COLUMNS + " FROM punishments WHERE ip = ? AND ip_wide = TRUE AND type = ? "
                            + "AND removed_at IS NULL AND (expires IS NULL OR expires > ?) ORDER BY created DESC",
                    PunishmentStore::map, ip, type.name(), now)) {
                if (out.stream().noneMatch(o -> o.id() == p.id())) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /** The active ban for this account or an IP-wide ban on this address (login check). */
    CompletableFuture<Optional<Punishment>> activeBan(UUID target, String ip) {
        return db.async(c -> activeOf(c, Punishment.Type.BAN, target, ip, System.currentTimeMillis()).stream().findFirst());
    }

    CompletableFuture<Optional<Punishment>> activeMute(UUID target, String ip) {
        return db.async(c -> activeOf(c, Punishment.Type.MUTE, target, ip, System.currentTimeMillis()).stream().findFirst());
    }

    /** Blocking variant for AsyncPlayerPreLoginEvent (already off the main thread). */
    Optional<Punishment> activeBlocking(Punishment.Type type, UUID target, String ip) {
        return db.blocking(c -> activeOf(c, type, target, ip, System.currentTimeMillis()).stream().findFirst());
    }

    /** All punishments of a player, newest first. */
    CompletableFuture<List<Punishment>> history(UUID target) {
        return db.async(c -> Sql.query(c, "SELECT " + COLUMNS + " FROM punishments WHERE target_uuid = ? ORDER BY created DESC",
                PunishmentStore::map, Sql.toBytes(target)));
    }

    /** How many earlier (not pardoned) punishments of this template the player has: decides the ladder step. */
    CompletableFuture<Integer> templateCount(UUID target, String template) {
        return db.async(c -> Sql.queryOne(c, "SELECT COUNT(*) AS n FROM punishments WHERE target_uuid = ? AND template = ? "
                        + "AND removed_at IS NULL", rs -> rs.getInt("n"), Sql.toBytes(target), template).orElse(0));
    }

    /** Newest known IP of a player (from the social module's join history), for -ip and /ipban on offline players. */
    CompletableFuture<Optional<String>> lastIp(UUID target) {
        return db.async(c -> {
            try {
                return Sql.queryOne(c, "SELECT ip FROM social_player_ips WHERE uuid = ? ORDER BY last_seen DESC LIMIT 1",
                        rs -> rs.getString("ip"), Sql.toBytes(target));
            } catch (SQLException e) {
                return Optional.empty(); // social module not installed
            }
        });
    }

    /** An account that shares an address with the given one, with its active ban/mute (if any). */
    record Alt(UUID uuid, String name, String ip, long lastSeen, Punishment ban, Punishment mute) {
    }

    /** Accounts sharing any address with {@code uuid} or with {@code ip} (if given), max 30, with active bans/mutes. */
    CompletableFuture<List<Alt>> alts(UUID uuid, String ip) {
        return db.async(c -> altsBlocking(c, uuid, ip));
    }

    List<Alt> altsBlocking(UUID uuid, String ip) {
        return db.blocking(c -> altsBlocking(c, uuid, ip));
    }

    private static List<Alt> altsBlocking(Connection c, UUID uuid, String ip) throws SQLException {
        record Row(UUID uuid, String name, String ip, long lastSeen) {
        }
        List<Row> rows;
        try {
            rows = Sql.query(c, "SELECT o.uuid, p.name, o.ip, o.last_seen FROM social_player_ips o "
                            + "LEFT JOIN players p ON p.uuid = o.uuid "
                            + "WHERE o.uuid <> ? AND (o.ip = ? OR o.ip IN (SELECT m.ip FROM social_player_ips m WHERE m.uuid = ?)) "
                            + "ORDER BY o.last_seen DESC LIMIT 30",
                    rs -> new Row(Sql.uuid(rs, "uuid"), rs.getString("name"), rs.getString("ip"), rs.getLong("last_seen")),
                    Sql.toBytes(uuid), ip == null ? "" : ip, Sql.toBytes(uuid));
        } catch (SQLException e) {
            return List.of(); // social module not installed
        }
        long now = System.currentTimeMillis();
        List<Alt> out = new ArrayList<>();
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (Row r : rows) {
            if (!seen.add(r.uuid())) {
                continue;
            }
            Punishment ban = activeOf(c, Punishment.Type.BAN, r.uuid(), null, now).stream().findFirst().orElse(null);
            Punishment mute = activeOf(c, Punishment.Type.MUTE, r.uuid(), null, now).stream().findFirst().orElse(null);
            out.add(new Alt(r.uuid(), r.name() == null ? r.uuid().toString() : r.name(), r.ip(), r.lastSeen(), ban, mute));
        }
        return out;
    }

    /** Names of players with an active ban (tab completion for /unban), max 200. */
    CompletableFuture<List<String>> bannedNames() {
        return db.async(c -> Sql.query(c, "SELECT DISTINCT target_name FROM punishments WHERE type = 'BAN' AND removed_at IS NULL "
                + "AND (expires IS NULL OR expires > ?) ORDER BY target_name LIMIT 200", rs -> rs.getString("target_name"),
                System.currentTimeMillis()));
    }
}

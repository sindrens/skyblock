package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.island.IslandService.Role;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Database side of M2.3 (tables from {@code db/islands/V003}): members, invites, coops, join history and join blocks.
 * Everything valuable is one transaction that re-checks the rules against the database (the island row is locked with
 * {@code SELECT … FOR UPDATE} so two accepts can never overfill an island); the cache in {@link IslandManager} is only
 * updated after a commit. Static SQL methods are package-private for tests.
 */
final class MemberStore {

    /** The join limits handed to invite/accept (from islands.yml). */
    record Limits(int seasonId, int maxJoins, long cooldownMillis, int startSlots, int maxSlots) {
    }

    /** Result of sending an invite. */
    sealed interface InviteResult {
        /** Saved. {@code converted} = the island just became a team island. */
        record Created(Invites.Invite invite, boolean converted) implements InviteResult {
        }

        record IslandGone() implements InviteResult {
        }

        record AlreadyMember() implements InviteResult {
        }

        record HasIsland() implements InviteResult {
        }

        record Full(int slots) implements InviteResult {
        }

        record Limited(JoinRules.Check check) implements InviteResult {
        }
    }

    /** Result of accepting an invite. */
    sealed interface AcceptResult {
        record Joined(UUID islandId, long joinedAt, UUID inviter) implements AcceptResult {
        }

        record NoInvite() implements AcceptResult {
        }

        record IslandGone() implements AcceptResult {
        }

        record HasIsland() implements AcceptResult {
        }

        record Full(int slots) implements AcceptResult {
        }

        record Limited(JoinRules.Check check) implements AcceptResult {
        }
    }

    static final String LEFT_LEAVE = "leave";
    static final String LEFT_KICK = "kick";
    static final String LEFT_REMOVED = "removed";

    private final Database db;

    MemberStore(Database db) {
        this.db = db;
    }

    // ------------------------------------------------------------------ async API

    CompletableFuture<InviteResult> invite(UUID islandId, UUID inviter, UUID target, long expiryMillis, Limits limits) {
        long now = System.currentTimeMillis();
        return db.transaction(c -> invite(c, islandId, inviter, target, now, now + expiryMillis, limits));
    }

    CompletableFuture<AcceptResult> accept(UUID islandId, UUID target, Limits limits) {
        long now = System.currentTimeMillis();
        return db.async(c -> {
            try {
                return inTransaction(c, () -> accept(c, islandId, target, now, limits));
            } catch (SQLException e) {
                if (IslandStore.isConstraintViolation(e)) {
                    return new AcceptResult.HasIsland(); // joined another island at the same moment
                }
                throw e;
            }
        });
    }

    CompletableFuture<Boolean> deleteInvite(UUID islandId, UUID target) {
        return db.async(c -> Sql.update(c, "DELETE FROM island_invites WHERE island_id = ? AND target_uuid = ?", islandId, target) > 0);
    }

    CompletableFuture<Integer> deleteExpiredInvites(long now) {
        return db.async(c -> Sql.update(c, "DELETE FROM island_invites WHERE expires_at <= ?", now));
    }

    /** Removes a stored member (never the leader); returns their old role, or empty if they weren't a member. */
    CompletableFuture<Optional<Role>> removeMember(UUID islandId, UUID uuid, String reason, int seasonId) {
        long now = System.currentTimeMillis();
        return db.transaction(c -> removeMember(c, islandId, uuid, reason, seasonId, now));
    }

    CompletableFuture<Boolean> setRole(UUID islandId, UUID uuid, Role expected, Role next) {
        return db.async(c -> setRole(c, islandId, uuid, expected, next));
    }

    CompletableFuture<Boolean> transfer(UUID islandId, UUID oldLeader, UUID newLeader) {
        return db.transaction(c -> transfer(c, islandId, oldLeader, newLeader));
    }

    CompletableFuture<Void> addCoop(UUID islandId, UUID uuid, UUID grantedBy) {
        long now = System.currentTimeMillis();
        return db.run(c -> addCoop(c, islandId, uuid, grantedBy, now));
    }

    CompletableFuture<Boolean> removeCoop(UUID islandId, UUID uuid) {
        return db.async(c -> Sql.update(c, "DELETE FROM island_coops WHERE island_id = ? AND uuid = ?", islandId, uuid) > 0);
    }

    CompletableFuture<Integer> clearCoops(UUID islandId) {
        return db.async(c -> Sql.update(c, "DELETE FROM island_coops WHERE island_id = ?", islandId));
    }

    CompletableFuture<Boolean> setMemberSlots(UUID islandId, Integer slots) {
        return db.async(c -> Sql.update(c, "UPDATE islands SET member_slots = ? WHERE id = ? AND deleted_at IS NULL", slots, islandId) == 1);
    }

    CompletableFuture<Void> blockJoining(UUID uuid, long until, String reason, String actor) {
        long now = System.currentTimeMillis();
        return db.transaction(c -> {
            blockJoining(c, uuid, until, reason, actor, now);
            return null;
        });
    }

    CompletableFuture<Optional<Long>> blockedUntil(UUID uuid) {
        long now = System.currentTimeMillis();
        return db.async(c -> blockedUntil(c, uuid, now));
    }

    CompletableFuture<JoinRules.History> history(UUID uuid, int seasonId) {
        return db.async(c -> history(c, uuid, seasonId));
    }

    /** Pending (not expired) invites, blocking: enable only. */
    List<Invites.Invite> loadInvitesBlocking(long now) {
        return db.blocking(c -> loadInvites(c, now));
    }

    // ------------------------------------------------------------------ SQL

    static List<Invites.Invite> loadInvites(Connection c, long now) throws SQLException {
        return Sql.query(c, "SELECT island_id, target_uuid, inviter_uuid, created_at, expires_at FROM island_invites WHERE expires_at > ?",
                rs -> new Invites.Invite(Sql.uuid(rs, "island_id"), Sql.uuid(rs, "target_uuid"), Sql.uuid(rs, "inviter_uuid"),
                        rs.getLong("created_at"), rs.getLong("expires_at")), now);
    }

    /** Coops of every loaded island (rows of unknown islands are ignored). */
    static void loadCoops(Connection c, Map<UUID, Island> islands) throws SQLException {
        Sql.query(c, "SELECT island_id, uuid FROM island_coops", rs -> {
            Island island = islands.get(Sql.uuid(rs, "island_id"));
            UUID uuid = Sql.uuid(rs, "uuid");
            if (island != null && island.role(uuid).isEmpty()) {
                island.addCoop(uuid);
            }
            return null;
        });
    }

    /** The locked island row: {type, member_slots}; empty if gone. */
    private record IslandRow(boolean team, Integer memberSlots) {
    }

    private static Optional<IslandRow> lockIsland(Connection c, UUID islandId) throws SQLException {
        return Sql.queryOne(c, "SELECT type, member_slots FROM islands WHERE id = ? AND deleted_at IS NULL FOR UPDATE", rs -> {
            boolean team = "team".equals(rs.getString("type"));
            int slots = rs.getInt("member_slots");
            return new IslandRow(team, rs.wasNull() ? null : slots);
        }, islandId);
    }

    private static int memberCount(Connection c, UUID islandId) throws SQLException {
        return Sql.queryOne(c, "SELECT COUNT(*) FROM island_members WHERE island_id = ?", rs -> rs.getInt(1), islandId).orElse(0);
    }

    private static Optional<UUID> islandOf(Connection c, UUID player) throws SQLException {
        return Sql.queryOne(c, "SELECT island_id FROM island_members WHERE uuid = ?", rs -> Sql.uuid(rs, "island_id"), player);
    }

    static JoinRules.History history(Connection c, UUID uuid, int seasonId) throws SQLException {
        int joins = Sql.queryOne(c, "SELECT COUNT(*) FROM player_island_joins WHERE uuid = ? AND season_id = ? AND counted = ?",
                rs -> rs.getInt(1), uuid, seasonId, true).orElse(0);
        Long[] last = Sql.queryOne(c, "SELECT MAX(CASE WHEN counted = ? THEN joined_at END), MAX(left_at) FROM player_island_joins WHERE uuid = ?",
                rs -> {
                    long join = rs.getLong(1);
                    Long j = rs.wasNull() ? null : join;
                    long left = rs.getLong(2);
                    Long l = rs.wasNull() ? null : left;
                    return new Long[]{j, l};
                }, true, uuid).orElse(new Long[]{null, null});
        return new JoinRules.History(joins, last[0], last[1]);
    }

    static Optional<Long> blockedUntil(Connection c, UUID uuid, long now) throws SQLException {
        return Sql.queryOne(c, "SELECT until_at FROM island_join_blocks WHERE uuid = ? AND until_at > ?", rs -> rs.getLong(1), uuid, now);
    }

    static JoinRules.Check joinCheck(Connection c, UUID target, long now, Limits limits) throws SQLException {
        return JoinRules.check(history(c, target, limits.seasonId()), blockedUntil(c, target, now).orElse(null), now,
                limits.maxJoins(), limits.cooldownMillis());
    }

    static InviteResult invite(Connection c, UUID islandId, UUID inviter, UUID target, long now, long expiresAt, Limits limits)
            throws SQLException {
        Optional<IslandRow> island = lockIsland(c, islandId);
        if (island.isEmpty()) {
            return new InviteResult.IslandGone();
        }
        Optional<UUID> current = islandOf(c, target);
        if (current.isPresent()) {
            return current.get().equals(islandId) ? new InviteResult.AlreadyMember() : new InviteResult.HasIsland();
        }
        int slots = MemberRules.slots(island.get().memberSlots(), limits.startSlots(), limits.maxSlots());
        if (memberCount(c, islandId) >= slots) {
            return new InviteResult.Full(slots);
        }
        JoinRules.Check check = joinCheck(c, target, now, limits);
        if (!(check instanceof JoinRules.Check.Ok)) {
            return new InviteResult.Limited(check);
        }
        boolean converted = false;
        if (!island.get().team()) {
            converted = Sql.update(c, "UPDATE islands SET type = ? WHERE id = ? AND type = ?", "team", islandId, "solo") == 1;
        }
        Sql.update(c, "DELETE FROM island_invites WHERE island_id = ? AND target_uuid = ?", islandId, target);
        Sql.update(c, "INSERT INTO island_invites (island_id, target_uuid, inviter_uuid, created_at, expires_at) VALUES (?, ?, ?, ?, ?)",
                islandId, target, inviter, now, expiresAt);
        return new InviteResult.Created(new Invites.Invite(islandId, target, inviter, now, expiresAt), converted);
    }

    static AcceptResult accept(Connection c, UUID islandId, UUID target, long now, Limits limits) throws SQLException {
        Optional<UUID> inviter = Sql.queryOne(c, "SELECT inviter_uuid FROM island_invites WHERE island_id = ? AND target_uuid = ? AND expires_at > ?",
                rs -> Sql.uuid(rs, "inviter_uuid"), islandId, target, now);
        if (inviter.isEmpty()) {
            return new AcceptResult.NoInvite();
        }
        Optional<IslandRow> island = lockIsland(c, islandId);
        if (island.isEmpty()) {
            Sql.update(c, "DELETE FROM island_invites WHERE island_id = ?", islandId);
            return new AcceptResult.IslandGone();
        }
        if (islandOf(c, target).isPresent()) {
            return new AcceptResult.HasIsland();
        }
        int slots = MemberRules.slots(island.get().memberSlots(), limits.startSlots(), limits.maxSlots());
        if (memberCount(c, islandId) >= slots) {
            return new AcceptResult.Full(slots);
        }
        JoinRules.Check check = joinCheck(c, target, now, limits);
        if (!(check instanceof JoinRules.Check.Ok)) {
            return new AcceptResult.Limited(check);
        }
        Sql.update(c, "INSERT INTO island_members (island_id, uuid, role, joined_at, invited_by) VALUES (?, ?, ?, ?, ?)",
                islandId, target, "member", now, inviter.get());
        Sql.update(c, "INSERT INTO player_island_joins (uuid, island_id, season_id, joined_at, counted) VALUES (?, ?, ?, ?, ?)",
                target, islandId, limits.seasonId(), now, true);
        Sql.update(c, "DELETE FROM island_invites WHERE target_uuid = ?", target);
        Sql.update(c, "DELETE FROM island_coops WHERE island_id = ? AND uuid = ?", islandId, target);
        return new AcceptResult.Joined(islandId, now, inviter.get());
    }

    static Optional<Role> removeMember(Connection c, UUID islandId, UUID uuid, String reason, int seasonId, long now) throws SQLException {
        Optional<Role> role = Sql.queryOne(c, "SELECT role FROM island_members WHERE island_id = ? AND uuid = ?",
                rs -> IslandStore.role(rs.getString("role")), islandId, uuid);
        if (role.isEmpty() || role.get() == Role.LEADER) {
            return Optional.empty();
        }
        Sql.update(c, "DELETE FROM island_members WHERE island_id = ? AND uuid = ? AND role <> ?", islandId, uuid, "leader");
        int closed = Sql.update(c, "UPDATE player_island_joins SET left_at = ?, left_reason = ? WHERE uuid = ? AND island_id = ? AND left_at IS NULL",
                now, reason, uuid, islandId);
        if (closed == 0) {
            // A member without a join row (e.g. the founder after a transfer): record the leave for the cooldown only.
            Sql.update(c, "INSERT INTO player_island_joins (uuid, island_id, season_id, joined_at, left_at, left_reason, counted) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)", uuid, islandId, seasonId, now, now, reason, false);
        }
        return role;
    }

    static boolean setRole(Connection c, UUID islandId, UUID uuid, Role expected, Role next) throws SQLException {
        return Sql.update(c, "UPDATE island_members SET role = ? WHERE island_id = ? AND uuid = ? AND role = ?",
                key(next), islandId, uuid, key(expected)) == 1;
    }

    static boolean transfer(Connection c, UUID islandId, UUID oldLeader, UUID newLeader) throws SQLException {
        if (lockIsland(c, islandId).isEmpty()) {
            return false;
        }
        Optional<Role> target = Sql.queryOne(c, "SELECT role FROM island_members WHERE island_id = ? AND uuid = ?",
                rs -> IslandStore.role(rs.getString("role")), islandId, newLeader);
        if (target.isEmpty() || target.get() == Role.LEADER) {
            return false;
        }
        if (Sql.update(c, "UPDATE islands SET owner_uuid = ? WHERE id = ? AND owner_uuid = ?", newLeader, islandId, oldLeader) != 1) {
            return false;
        }
        if (Sql.update(c, "UPDATE island_members SET role = ? WHERE island_id = ? AND uuid = ? AND role = ?",
                "mod", islandId, oldLeader, "leader") != 1) {
            throw new SQLException("Transfer of " + islandId + ": the old leader row is missing"); // rolls back
        }
        Sql.update(c, "UPDATE island_members SET role = ? WHERE island_id = ? AND uuid = ?", "leader", islandId, newLeader);
        return true;
    }

    static void addCoop(Connection c, UUID islandId, UUID uuid, UUID grantedBy, long now) throws SQLException {
        inTransaction(c, () -> {
            Sql.update(c, "DELETE FROM island_coops WHERE island_id = ? AND uuid = ?", islandId, uuid);
            Sql.update(c, "INSERT INTO island_coops (island_id, uuid, granted_by, created_at) VALUES (?, ?, ?, ?)",
                    islandId, uuid, grantedBy, now);
            return null;
        });
    }

    static void blockJoining(Connection c, UUID uuid, long until, String reason, String actor, long now) throws SQLException {
        Sql.update(c, "DELETE FROM island_join_blocks WHERE uuid = ?", uuid);
        if (until > now) {
            Sql.update(c, "INSERT INTO island_join_blocks (uuid, until_at, reason, actor, created_at) VALUES (?, ?, ?, ?, ?)",
                    uuid, until, reason == null ? null : truncate(reason, 255), actor == null ? null : truncate(actor, 64), now);
        }
    }

    static String key(Role role) {
        return role.name().toLowerCase(Locale.ROOT);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    @FunctionalInterface
    private interface Work<T> {
        T run() throws SQLException;
    }

    private static <T> T inTransaction(Connection c, Work<T> work) throws SQLException {
        boolean auto = c.getAutoCommit();
        if (!auto) {
            return work.run(); // already inside a transaction
        }
        c.setAutoCommit(false);
        try {
            T result = work.run();
            c.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            try {
                c.rollback();
            } catch (SQLException rollback) {
                e.addSuppressed(rollback);
            }
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }
}

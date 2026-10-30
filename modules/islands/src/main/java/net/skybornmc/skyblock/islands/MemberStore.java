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

        /** Owner P3: another profile of this player is or was on the island. */
        record OwnProfile() implements InviteResult {
        }
    }

    /** Result of accepting an invite. */
    sealed interface AcceptResult {
        record Joined(UUID islandId, long joinedAt, UUID inviter, boolean converted) implements AcceptResult {
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

        /** Owner P3: another profile of this player is or was on the island. */
        record OwnProfile() implements AcceptResult {
        }
    }

    /** Result of a staff {@code /isadmin addmember}. */
    sealed interface AddResult {
        record Added(long joinedAt, boolean converted) implements AddResult {
        }

        record IslandGone() implements AddResult {
        }

        record HasIsland() implements AddResult {
        }

        record Full(int slots) implements AddResult {
        }

        /** Owner P3: another profile of this player is or was on the island. */
        record OwnProfile() implements AddResult {
        }
    }

    static final String LEFT_LEAVE = "leave";
    static final String LEFT_KICK = "kick";
    static final String LEFT_REMOVED = "removed";
    /** The island was removed (disband, staff delete or reset, M2.7): recorded for history, never starts the join cooldown. */
    static final String LEFT_DISBAND = "disband";

    private final Database db;

    MemberStore(Database db) {
        this.db = db;
    }

    // ------------------------------------------------------------------ async API

    /**
     * @param target       the invited profile key
     * @param targetPlayer the invited player (join limits are per player, owner P11)
     */
    CompletableFuture<InviteResult> invite(UUID islandId, UUID inviter, UUID target, UUID targetPlayer, long expiryMillis, Limits limits) {
        long now = System.currentTimeMillis();
        return db.transaction(c -> invite(c, islandId, inviter, target, targetPlayer, now, now + expiryMillis, limits));
    }

    CompletableFuture<AcceptResult> accept(UUID islandId, UUID target, UUID targetPlayer, Limits limits) {
        long now = System.currentTimeMillis();
        return db.async(c -> {
            try {
                return inTransaction(c, () -> accept(c, islandId, target, targetPlayer, now, limits));
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
    CompletableFuture<Optional<Role>> removeMember(UUID islandId, UUID uuid, UUID player, String reason, int seasonId) {
        long now = System.currentTimeMillis();
        return db.transaction(c -> removeMember(c, islandId, uuid, player, reason, seasonId, now));
    }

    /**
     * Staff: adds {@code uuid} as a member without an invite (M2.7 {@code /isadmin addmember}). Skips the season join
     * limit and the join cooldown (the row is not counted) but keeps one island per player and the member slots.
     */
    CompletableFuture<AddResult> addMember(UUID islandId, UUID uuid, UUID player, UUID addedBy, Limits limits) {
        long now = System.currentTimeMillis();
        return db.async(c -> {
            try {
                return inTransaction(c, () -> addMember(c, islandId, uuid, player, addedBy, now, limits));
            } catch (SQLException e) {
                if (IslandStore.isConstraintViolation(e)) {
                    return new AddResult.HasIsland(); // joined another island at the same moment
                }
                throw e;
            }
        });
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
        // ISL2 (owner #20): only the last counted join starts the cooldown; the last leave is history only (a leave
        // because the island was removed, M2.7, is not even shown as one).
        Long[] last = Sql.queryOne(c, "SELECT MAX(CASE WHEN counted = ? THEN joined_at END), "
                        + "MAX(CASE WHEN left_reason IS NULL OR left_reason <> ? THEN left_at END) FROM player_island_joins WHERE uuid = ?",
                rs -> {
                    long join = rs.getLong(1);
                    Long j = rs.wasNull() ? null : join;
                    long left = rs.getLong(2);
                    Long l = rs.wasNull() ? null : left;
                    return new Long[]{j, l};
                }, true, LEFT_DISBAND, uuid).orElse(new Long[]{null, null});
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
        return invite(c, islandId, inviter, target, target, now, expiresAt, limits);
    }

    /**
     * Owner P3 in the database: true if {@code player} has join history on the island with ANOTHER profile than
     * {@code key}, or their profile-1 key (= the player UUID) is a member or coop of it. (Other current members are
     * checked from the cache before; their joins are in the history anyway.) Rows from before profiles have no
     * profile id and belong to profile 1.
     */
    static boolean otherOwnProfileOn(Connection c, UUID islandId, UUID key, UUID player) throws SQLException {
        if (Sql.queryOne(c, "SELECT 1 FROM player_island_joins WHERE uuid = ? AND island_id = ? AND COALESCE(profile_id, uuid) <> ?",
                rs -> 1, player, islandId, key).isPresent()) {
            return true;
        }
        return !player.equals(key)
                && (Sql.queryOne(c, "SELECT 1 FROM island_members WHERE island_id = ? AND uuid = ?", rs -> 1, islandId, player).isPresent()
                || Sql.queryOne(c, "SELECT 1 FROM island_coops WHERE island_id = ? AND uuid = ?", rs -> 1, islandId, player).isPresent());
    }

    static InviteResult invite(Connection c, UUID islandId, UUID inviter, UUID target, UUID targetPlayer, long now, long expiresAt,
                               Limits limits) throws SQLException {
        Optional<IslandRow> island = lockIsland(c, islandId);
        if (island.isEmpty()) {
            return new InviteResult.IslandGone();
        }
        Optional<UUID> current = islandOf(c, target);
        if (current.isPresent()) {
            return current.get().equals(islandId) ? new InviteResult.AlreadyMember() : new InviteResult.HasIsland();
        }
        if (otherOwnProfileOn(c, islandId, target, targetPlayer)) {
            return new InviteResult.OwnProfile();
        }
        int slots = MemberRules.slots(island.get().memberSlots(), limits.startSlots(), limits.maxSlots());
        if (memberCount(c, islandId) >= slots) {
            return new InviteResult.Full(slots);
        }
        JoinRules.Check check = joinCheck(c, targetPlayer, now, limits);
        if (!(check instanceof JoinRules.Check.Ok)) {
            return new InviteResult.Limited(check);
        }
        // Owner 2026-10-30: the island only becomes a team island when the invite is ACCEPTED (see accept()).
        boolean converted = false;
        Sql.update(c, "DELETE FROM island_invites WHERE island_id = ? AND target_uuid = ?", islandId, target);
        Sql.update(c, "INSERT INTO island_invites (island_id, target_uuid, inviter_uuid, created_at, expires_at) VALUES (?, ?, ?, ?, ?)",
                islandId, target, inviter, now, expiresAt);
        return new InviteResult.Created(new Invites.Invite(islandId, target, inviter, now, expiresAt), converted);
    }

    static AcceptResult accept(Connection c, UUID islandId, UUID target, long now, Limits limits) throws SQLException {
        return accept(c, islandId, target, target, now, limits);
    }

    /** @param target the joining profile key; {@code targetPlayer} its player (join history and limits, owner P11) */
    static AcceptResult accept(Connection c, UUID islandId, UUID target, UUID targetPlayer, long now, Limits limits) throws SQLException {
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
        if (otherOwnProfileOn(c, islandId, target, targetPlayer)) {
            return new AcceptResult.OwnProfile();
        }
        int slots = MemberRules.slots(island.get().memberSlots(), limits.startSlots(), limits.maxSlots());
        if (memberCount(c, islandId) >= slots) {
            return new AcceptResult.Full(slots);
        }
        JoinRules.Check check = joinCheck(c, targetPlayer, now, limits);
        if (!(check instanceof JoinRules.Check.Ok)) {
            return new AcceptResult.Limited(check);
        }
        // A second member makes a solo island a team island forever (DESIGN 3.2), in the same transaction as the join.
        boolean converted = !island.get().team()
                && Sql.update(c, "UPDATE islands SET type = ? WHERE id = ? AND type = ?", "team", islandId, "solo") == 1;
        Sql.update(c, "INSERT INTO island_members (island_id, uuid, role, joined_at, invited_by) VALUES (?, ?, ?, ?, ?)",
                islandId, target, "member", now, inviter.get());
        Sql.update(c, "INSERT INTO player_island_joins (uuid, island_id, season_id, joined_at, counted, profile_id) VALUES (?, ?, ?, ?, ?, ?)",
                targetPlayer, islandId, limits.seasonId(), now, true, target);
        Sql.update(c, "DELETE FROM island_invites WHERE target_uuid = ?", target);
        Sql.update(c, "DELETE FROM island_coops WHERE island_id = ? AND uuid = ?", islandId, target);
        return new AcceptResult.Joined(islandId, now, inviter.get(), converted);
    }

    static AddResult addMember(Connection c, UUID islandId, UUID uuid, UUID addedBy, long now, Limits limits) throws SQLException {
        return addMember(c, islandId, uuid, uuid, addedBy, now, limits);
    }

    static AddResult addMember(Connection c, UUID islandId, UUID uuid, UUID player, UUID addedBy, long now, Limits limits)
            throws SQLException {
        Optional<IslandRow> island = lockIsland(c, islandId);
        if (island.isEmpty()) {
            return new AddResult.IslandGone();
        }
        if (islandOf(c, uuid).isPresent()) {
            return new AddResult.HasIsland();
        }
        if (otherOwnProfileOn(c, islandId, uuid, player)) {
            return new AddResult.OwnProfile();
        }
        int slots = MemberRules.slots(island.get().memberSlots(), limits.startSlots(), limits.maxSlots());
        if (memberCount(c, islandId) >= slots) {
            return new AddResult.Full(slots);
        }
        // A second member makes a solo island a team island forever (DESIGN 3.2), also when staff adds them.
        boolean converted = !island.get().team()
                && Sql.update(c, "UPDATE islands SET type = ? WHERE id = ? AND type = ?", "team", islandId, "solo") == 1;
        Sql.update(c, "INSERT INTO island_members (island_id, uuid, role, joined_at, invited_by) VALUES (?, ?, ?, ?, ?)",
                islandId, uuid, "member", now, addedBy);
        Sql.update(c, "INSERT INTO player_island_joins (uuid, island_id, season_id, joined_at, counted, profile_id) VALUES (?, ?, ?, ?, ?, ?)",
                player, islandId, limits.seasonId(), now, false, uuid);
        Sql.update(c, "DELETE FROM island_invites WHERE target_uuid = ?", uuid);
        Sql.update(c, "DELETE FROM island_coops WHERE island_id = ? AND uuid = ?", islandId, uuid);
        return new AddResult.Added(now, converted);
    }

    static Optional<Role> removeMember(Connection c, UUID islandId, UUID uuid, String reason, int seasonId, long now) throws SQLException {
        return removeMember(c, islandId, uuid, uuid, reason, seasonId, now);
    }

    /** @param uuid the member's profile key; {@code player} their player (join history rows are per player) */
    static Optional<Role> removeMember(Connection c, UUID islandId, UUID uuid, UUID player, String reason, int seasonId, long now)
            throws SQLException {
        Optional<Role> role = Sql.queryOne(c, "SELECT role FROM island_members WHERE island_id = ? AND uuid = ?",
                rs -> IslandStore.role(rs.getString("role")), islandId, uuid);
        if (role.isEmpty() || role.get() == Role.LEADER) {
            return Optional.empty();
        }
        Sql.update(c, "DELETE FROM island_members WHERE island_id = ? AND uuid = ? AND role <> ?", islandId, uuid, "leader");
        MemberHomeStore.deleteMember(c, islandId, uuid); // ISL2: personal homes end with the membership
        int closed = Sql.update(c, "UPDATE player_island_joins SET left_at = ?, left_reason = ? WHERE uuid = ? AND island_id = ? "
                        + "AND COALESCE(profile_id, uuid) = ? AND left_at IS NULL", now, reason, player, islandId, uuid);
        if (closed == 0) {
            // A member without a join row (e.g. the founder after a transfer): record the leave for history only
            // (counted = false: never a join, and since ISL2 a leave never starts the join cooldown).
            Sql.update(c, "INSERT INTO player_island_joins (uuid, island_id, season_id, joined_at, left_at, left_reason, counted, profile_id) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", player, islandId, seasonId, now, now, reason, false, uuid);
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
                key(MemberRules.AFTER_TRANSFER), islandId, oldLeader, "leader") != 1) {
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

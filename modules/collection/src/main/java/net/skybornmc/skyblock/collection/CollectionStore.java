package net.skybornmc.skyblock.collection;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** SQL of the collection log (no Bukkit; tested on H2 in MySQL mode). Every method uses only the connection it gets. */
final class CollectionStore {

    static final String PENDING = "pending";
    static final String SENDING = "sending";
    static final String DELIVERED = "delivered";
    static final String FAILED = "failed";
    static final String REVIEW = "review";

    /** One find to save. */
    record EntryRow(UUID profile, String entry, long at, String source) {
    }

    /** One hand-out row ({@code collection_grants}). */
    record GrantRow(UUID id, UUID profile, UUID player, String milestone, String kind, String ref, long amount, String state,
                    long createdAt) {
    }

    /** What a milestone transaction wrote: the milestones that were new and their hand-outs. */
    record Committed(List<String> milestones, List<GrantRow> grants) {
    }

    private CollectionStore() {
    }

    // ================================================================== entries and milestones

    static Map<String, ProfileLog.Found> entries(Connection c, UUID profile) throws SQLException {
        Map<String, ProfileLog.Found> out = new HashMap<>();
        for (Object[] row : Sql.query(c, "SELECT entry_id, first_at, found_source FROM collection_entries WHERE profile_key = ?",
                rs -> new Object[]{rs.getString(1), rs.getLong(2), rs.getString(3)}, profile)) {
            out.put((String) row[0], new ProfileLog.Found((Long) row[1], (String) row[2]));
        }
        return out;
    }

    static Set<String> milestones(Connection c, UUID profile) throws SQLException {
        return new HashSet<>(Sql.query(c, "SELECT milestone_id FROM collection_milestones WHERE profile_key = ?", rs -> rs.getString(1),
                profile));
    }

    /** Saves finds; an entry the profile already has keeps its first time (no error, nothing changes). */
    static void insertEntries(Connection c, List<EntryRow> rows) throws SQLException {
        if (rows.isEmpty()) {
            return;
        }
        List<Object[]> params = new ArrayList<>(rows.size());
        for (EntryRow r : rows) {
            params.add(new Object[]{r.profile(), cut(r.entry(), 64), r.at(), cut(r.source(), 24)});
        }
        Sql.batch(c, "INSERT INTO collection_entries (profile_key, entry_id, first_at, found_source) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE first_at = first_at", params);
    }

    /** Inserts a milestone; false (nothing written) when the profile has it already. */
    static boolean insertMilestone(Connection c, UUID profile, String milestone, UUID player, long now) throws SQLException {
        if (Sql.queryOne(c, "SELECT 1 FROM collection_milestones WHERE profile_key = ? AND milestone_id = ?", rs -> 1, profile,
                milestone).isPresent()) {
            return false;
        }
        Sql.update(c, "INSERT INTO collection_milestones (profile_key, milestone_id, player_uuid, reached_at) VALUES (?, ?, ?, ?)",
                profile, milestone, player, now);
        return true;
    }

    /**
     * The milestone transaction (run inside one database transaction): saves the waiting finds of the profile, then each
     * milestone that is new together with its hand-out rows ({@code pending}). A milestone the profile already has writes
     * nothing, so it is paid exactly once, also when two servers or a retry race (the primary key stops the second one).
     */
    static Committed commit(Connection c, UUID profile, UUID player, List<EntryRow> entries, List<String> milestones,
                            Function<String, List<Milestones.Grant>> rewards, long now) throws SQLException {
        insertEntries(c, entries);
        List<String> written = new ArrayList<>();
        List<GrantRow> grants = new ArrayList<>();
        for (String id : milestones) {
            if (!insertMilestone(c, profile, id, player, now)) {
                continue;
            }
            written.add(id);
            for (Milestones.Grant g : rewards.apply(id)) {
                grants.add(new GrantRow(UUID.randomUUID(), profile, player, id, g.kind(), cut(g.ref(), 64), g.amount(), PENDING, now));
            }
        }
        insertGrants(c, grants);
        return new Committed(written, grants);
    }

    /** Page milestones per profile (for the island level; {@code all} does not count). */
    static Map<UUID, Integer> pageMilestoneCounts(Connection c) throws SQLException {
        Map<UUID, Integer> out = new HashMap<>();
        for (Object[] row : Sql.query(c, "SELECT profile_key, COUNT(*) FROM collection_milestones WHERE milestone_id <> ? "
                + "GROUP BY profile_key", rs -> new Object[]{Sql.uuid(rs, "profile_key"), rs.getInt(2)}, Milestones.ALL)) {
            out.put((UUID) row[0], (Integer) row[1]);
        }
        return out;
    }

    // ================================================================== grants

    static void insertGrants(Connection c, List<GrantRow> rows) throws SQLException {
        if (rows.isEmpty()) {
            return;
        }
        List<Object[]> params = new ArrayList<>(rows.size());
        for (GrantRow r : rows) {
            params.add(new Object[]{r.id(), r.profile(), r.player(), r.milestone(), r.kind(), r.ref(), r.amount(), r.state(),
                    r.createdAt(), r.createdAt()});
        }
        Sql.batch(c, "INSERT INTO collection_grants (grant_id, profile_key, player_uuid, milestone_id, grant_kind, grant_ref, amount, "
                + "grant_state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", params);
    }

    /** Moves one grant from {@code from} to {@code to}; false when it is not in {@code from} anymore. */
    static boolean move(Connection c, UUID grantId, String from, String to, long now) throws SQLException {
        return Sql.update(c, "UPDATE collection_grants SET grant_state = ?, updated_at = ? WHERE grant_id = ? AND grant_state = ?",
                to, now, grantId, from) == 1;
    }

    static List<GrantRow> grants(Connection c, String state, int limit) throws SQLException {
        return Sql.query(c, "SELECT grant_id, profile_key, player_uuid, milestone_id, grant_kind, grant_ref, amount, grant_state, "
                        + "created_at FROM collection_grants WHERE grant_state = ? ORDER BY created_at LIMIT " + Math.max(1, limit),
                rs -> new GrantRow(Sql.uuid(rs, "grant_id"), Sql.uuid(rs, "profile_key"), Sql.uuid(rs, "player_uuid"),
                        rs.getString("milestone_id"), rs.getString("grant_kind"), rs.getString("grant_ref"), rs.getLong("amount"),
                        rs.getString("grant_state"), rs.getLong("created_at")), state);
    }

    static Optional<String> grantState(Connection c, UUID grantId) throws SQLException {
        return Sql.queryOne(c, "SELECT grant_state FROM collection_grants WHERE grant_id = ?", rs -> rs.getString(1), grantId);
    }

    /** Drops delivered grants older than {@code before} (history is in the audit log). */
    static int pruneDelivered(Connection c, long before) throws SQLException {
        return Sql.update(c, "DELETE FROM collection_grants WHERE grant_state = ? AND updated_at < ?", DELIVERED, before);
    }

    // ================================================================== profiles

    /** A deleted profile's log goes with it (owner principle 2). Grants still waiting are dropped too. */
    static void deleteProfile(Connection c, UUID profile) throws SQLException {
        Sql.update(c, "DELETE FROM collection_entries WHERE profile_key = ?", profile);
        Sql.update(c, "DELETE FROM collection_milestones WHERE profile_key = ?", profile);
        Sql.update(c, "DELETE FROM collection_grants WHERE profile_key = ? AND grant_state = ?", profile, PENDING);
    }

    static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}

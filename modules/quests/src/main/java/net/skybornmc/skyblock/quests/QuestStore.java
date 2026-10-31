package net.skybornmc.skyblock.quests;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** SQL of the quests module (no Bukkit; tested on H2 in MySQL mode). Every method uses only the connection it gets. */
final class QuestStore {

    static final String PENDING = "pending";
    static final String SENDING = "sending";
    static final String DELIVERED = "delivered";
    static final String REVIEW = "review";

    static final String KEY = "key";
    static final String PASS_XP = "pass_xp";

    /** One hand-out row ({@code quest_grants}). */
    record GrantRow(UUID id, UUID profile, UUID player, String sourceRef, String kind, String ref, long amount, String state,
                    long createdAt) {
    }

    private QuestStore() {
    }

    // ================================================================== sets

    /** The set of a board in a window with its quests (and island helpers), or empty when it was not rolled yet. */
    static Optional<QuestSet> load(Connection c, QuestKind kind, UUID owner, int period) throws SQLException {
        Optional<QuestSet> set = Sql.queryOne(c, "SELECT rerolls_used, bonus_paid FROM quest_sets WHERE owner_key = ? AND quest_kind = ? "
                        + "AND period_start = ?", rs -> new QuestSet(kind, owner, period, rs.getInt(1), rs.getInt(2) != 0), owner,
                kind.id(), period);
        if (set.isEmpty()) {
            return set;
        }
        QuestSet s = set.get();
        List<Quest> quests = Sql.query(c, "SELECT slot_index, quest_uid, quest_type, tier_from, target, progress, done_at, reward_money, "
                        + "reward_crystals, reward_xp FROM quest_progress WHERE owner_key = ? AND quest_kind = ? AND period_start = ? "
                        + "ORDER BY slot_index",
                rs -> {
                    QuestType t = QuestType.byId(rs.getString("quest_type")).orElse(null);
                    return t == null ? null : new Quest(Sql.uuid(rs, "quest_uid"), rs.getInt("slot_index"), t, rs.getInt("tier_from"),
                            rs.getLong("target"), rs.getLong("reward_money"), rs.getLong("reward_crystals"), rs.getInt("reward_xp"),
                            rs.getLong("progress"), rs.getLong("done_at"));
                }, owner, kind.id(), period);
        for (Quest q : quests) {
            if (q != null) {
                s.quests.add(q);
            }
        }
        if (kind == QuestKind.ISLAND) {
            for (Quest q : s.quests) {
                List<Object[]> rows = Sql.query(c, "SELECT profile_key, player_uuid, contributed FROM quest_helpers WHERE quest_uid = ?",
                        rs -> new Object[]{Sql.uuid(rs, "profile_key"), Sql.uuid(rs, "player_uuid"), rs.getLong("contributed")}, q.uid);
                for (Object[] r : rows) {
                    UUID profile = (UUID) r[0];
                    long amount = (Long) r[2];
                    s.contributions.computeIfAbsent(q.uid, k -> new HashMap<>()).put(profile, amount);
                    s.savedContributions.computeIfAbsent(q.uid, k -> new HashMap<>()).put(profile, amount);
                    s.helperPlayers.put(profile, (UUID) r[1]);
                }
            }
        }
        return set;
    }

    /**
     * Stores a freshly rolled set, unless another roll stored one first (then that one is returned). Run in a transaction.
     */
    static QuestSet insertOrLoad(Connection c, QuestSet rolled, int rollTier, long now) throws SQLException {
        Optional<QuestSet> existing = load(c, rolled.kind, rolled.owner, rolled.period);
        if (existing.isPresent()) {
            return existing.get();
        }
        Sql.update(c, "INSERT INTO quest_sets (owner_key, quest_kind, period_start, rerolls_used, bonus_paid, roll_tier, created_at) "
                + "VALUES (?, ?, ?, 0, 0, ?, ?)", rolled.owner, rolled.kind.id(), rolled.period, rollTier, now);
        for (Quest q : rolled.quests) {
            insertQuest(c, rolled, q, now);
        }
        return rolled;
    }

    static void insertQuest(Connection c, QuestSet set, Quest q, long now) throws SQLException {
        Sql.update(c, "INSERT INTO quest_progress (owner_key, quest_kind, period_start, slot_index, quest_uid, quest_type, tier_from, "
                        + "target, progress, done_at, reward_money, reward_crystals, reward_xp, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                set.owner, set.kind.id(), set.period, q.slot, q.uid, q.type.id(), q.tierFrom, q.target, q.progress, q.doneAt,
                q.rewardMoney, q.rewardCrystals, q.rewardXp, now);
    }

    /** Writes progress of an open quest (never lowers it, never touches a finished or swapped quest). */
    static int saveProgress(Connection c, UUID questUid, long progress, long now) throws SQLException {
        return Sql.update(c, "UPDATE quest_progress SET progress = GREATEST(progress, ?), updated_at = ? WHERE quest_uid = ? AND done_at = 0",
                progress, now, questUid);
    }

    /** Raises a helper's stored contribution to {@code amount} (inserts the row the first time). */
    static void saveContribution(Connection c, UUID questUid, UUID profile, UUID player, long amount) throws SQLException {
        int n = Sql.update(c, "UPDATE quest_helpers SET contributed = GREATEST(contributed, ?) WHERE quest_uid = ? AND profile_key = ?",
                amount, questUid, profile);
        if (n == 0) {
            Sql.update(c, "INSERT INTO quest_helpers (quest_uid, profile_key, player_uuid, contributed, paid_at) VALUES (?, ?, ?, ?, 0)",
                    questUid, profile, player, amount);
        }
    }

    // ================================================================== payouts (inside the payout transaction)

    /** Marks a quest done; false when it was done (or swapped) already: the payout must not happen. */
    static boolean markDone(Connection c, UUID questUid, long now) throws SQLException {
        return Sql.update(c, "UPDATE quest_progress SET progress = target, done_at = ?, updated_at = ? WHERE quest_uid = ? AND done_at = 0",
                now, now, questUid) == 1;
    }

    /**
     * Locks the set row until the transaction ends, so two quests of the same set that finish at the same moment are paid
     * one after the other (the second one then sees the first as done and the all-done bonus is paid exactly once).
     * False when the set does not exist.
     */
    static boolean lockSet(Connection c, QuestKind kind, UUID owner, int period) throws SQLException {
        return Sql.queryOne(c, "SELECT bonus_paid FROM quest_sets WHERE owner_key = ? AND quest_kind = ? AND period_start = ? FOR UPDATE",
                rs -> rs.getInt(1), owner, kind.id(), period).isPresent();
    }

    /** Open quests left in a set (a locking read: it sees what other transactions committed). */
    static int openCount(Connection c, QuestKind kind, UUID owner, int period) throws SQLException {
        return Sql.query(c, "SELECT slot_index FROM quest_progress WHERE owner_key = ? AND quest_kind = ? AND period_start = ? AND done_at = 0 "
                + "FOR UPDATE", rs -> rs.getInt(1), owner, kind.id(), period).size();
    }

    /** Claims the all-done bonus of a set; false when it was claimed already. */
    static boolean claimBonus(Connection c, QuestKind kind, UUID owner, int period) throws SQLException {
        return Sql.update(c, "UPDATE quest_sets SET bonus_paid = 1 WHERE owner_key = ? AND quest_kind = ? AND period_start = ? AND bonus_paid = 0",
                owner, kind.id(), period) == 1;
    }

    /** Marks a helper's share paid; false when it was paid already. */
    static boolean markHelperPaid(Connection c, UUID questUid, UUID profile, long now) throws SQLException {
        return Sql.update(c, "UPDATE quest_helpers SET paid_at = ? WHERE quest_uid = ? AND profile_key = ? AND paid_at = 0",
                now, questUid, profile) == 1;
    }

    // ================================================================== swaps

    /**
     * Swaps an open quest for a new one and counts the swap; false (nothing written) when the swaps used or the old
     * quest changed meanwhile.
     */
    static boolean swap(Connection c, QuestSet set, int rerollsBefore, Quest old, Quest fresh, long now) throws SQLException {
        if (Sql.update(c, "UPDATE quest_sets SET rerolls_used = ? WHERE owner_key = ? AND quest_kind = ? AND period_start = ? "
                + "AND rerolls_used = ?", rerollsBefore + 1, set.owner, set.kind.id(), set.period, rerollsBefore) != 1) {
            return false;
        }
        return Sql.update(c, "UPDATE quest_progress SET quest_uid = ?, quest_type = ?, tier_from = ?, target = ?, progress = 0, "
                        + "reward_money = ?, reward_crystals = ?, reward_xp = ?, updated_at = ? WHERE owner_key = ? AND quest_kind = ? "
                        + "AND period_start = ? AND slot_index = ? AND quest_uid = ? AND done_at = 0",
                fresh.uid, fresh.type.id(), fresh.tierFrom, fresh.target, fresh.rewardMoney, fresh.rewardCrystals, fresh.rewardXp, now,
                set.owner, set.kind.id(), set.period, old.slot, old.uid) == 1;
    }

    // ================================================================== staff

    /** Removes a set and its quests and helpers (staff reset: the next load rolls a new one). */
    static int deleteSet(Connection c, QuestKind kind, UUID owner, int period) throws SQLException {
        Sql.update(c, "DELETE FROM quest_helpers WHERE quest_uid IN (SELECT quest_uid FROM quest_progress WHERE owner_key = ? "
                + "AND quest_kind = ? AND period_start = ?)", owner, kind.id(), period);
        Sql.update(c, "DELETE FROM quest_progress WHERE owner_key = ? AND quest_kind = ? AND period_start = ?", owner, kind.id(), period);
        return Sql.update(c, "DELETE FROM quest_sets WHERE owner_key = ? AND quest_kind = ? AND period_start = ?", owner, kind.id(), period);
    }

    // ================================================================== grants

    static void insertGrants(Connection c, List<GrantRow> rows) throws SQLException {
        if (rows.isEmpty()) {
            return;
        }
        List<Object[]> params = new ArrayList<>(rows.size());
        for (GrantRow r : rows) {
            params.add(new Object[]{r.id(), r.profile(), r.player(), r.sourceRef(), r.kind(), r.ref(), r.amount(), r.state(),
                    r.createdAt(), r.createdAt()});
        }
        Sql.batch(c, "INSERT INTO quest_grants (grant_id, profile_key, player_uuid, source_ref, grant_kind, grant_ref, amount, grant_state, "
                + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", params);
    }

    static boolean move(Connection c, UUID grantId, String from, String to, long now) throws SQLException {
        return Sql.update(c, "UPDATE quest_grants SET grant_state = ?, updated_at = ? WHERE grant_id = ? AND grant_state = ?",
                to, now, grantId, from) == 1;
    }

    static List<GrantRow> grants(Connection c, String state, int limit) throws SQLException {
        return Sql.query(c, "SELECT grant_id, profile_key, player_uuid, source_ref, grant_kind, grant_ref, amount, grant_state, created_at "
                        + "FROM quest_grants WHERE grant_state = ? ORDER BY created_at LIMIT " + Math.max(1, limit),
                rs -> new GrantRow(Sql.uuid(rs, "grant_id"), Sql.uuid(rs, "profile_key"), Sql.uuid(rs, "player_uuid"),
                        rs.getString("source_ref"), rs.getString("grant_kind"), rs.getString("grant_ref"), rs.getLong("amount"),
                        rs.getString("grant_state"), rs.getLong("created_at")), state);
    }

    // ================================================================== housekeeping

    /** Drops windows older than {@code beforeDay} (epoch day) and delivered grants older than {@code grantsBefore}. */
    static void prune(Connection c, int beforeDay, long grantsBefore) throws SQLException {
        Sql.update(c, "DELETE FROM quest_helpers WHERE quest_uid IN (SELECT quest_uid FROM quest_progress WHERE period_start < ?)", beforeDay);
        Sql.update(c, "DELETE FROM quest_progress WHERE period_start < ?", beforeDay);
        Sql.update(c, "DELETE FROM quest_sets WHERE period_start < ?", beforeDay);
        Sql.update(c, "DELETE FROM quest_grants WHERE grant_state = ? AND updated_at < ?", DELIVERED, grantsBefore);
    }

    /** A deleted profile or island loses its quests (P4: nothing moves to another profile). Open grants stay for review. */
    static void deleteOwner(Connection c, UUID owner) throws SQLException {
        Sql.update(c, "DELETE FROM quest_helpers WHERE quest_uid IN (SELECT quest_uid FROM quest_progress WHERE owner_key = ?)", owner);
        Sql.update(c, "DELETE FROM quest_helpers WHERE profile_key = ? AND paid_at = 0", owner);
        Sql.update(c, "DELETE FROM quest_progress WHERE owner_key = ?", owner);
        Sql.update(c, "DELETE FROM quest_sets WHERE owner_key = ?", owner);
        Sql.update(c, "DELETE FROM quest_grants WHERE profile_key = ? AND grant_state IN (?, ?)", owner, PENDING, DELIVERED);
    }

    /** For tests and staff views: stored helper rows of an island mission (profile → [contributed, paid_at]). */
    static Map<UUID, long[]> helpers(Connection c, UUID questUid) throws SQLException {
        Map<UUID, long[]> out = new HashMap<>();
        for (Object[] r : Sql.query(c, "SELECT profile_key, contributed, paid_at FROM quest_helpers WHERE quest_uid = ?",
                rs -> new Object[]{Sql.uuid(rs, "profile_key"), rs.getLong("contributed"), rs.getLong("paid_at")}, questUid)) {
            out.put((UUID) r[0], new long[]{(Long) r[1], (Long) r[2]});
        }
        return out;
    }
}

package net.skybornmc.skyblock.boosters;

import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * SQL of the boosters tables (same statements on MariaDB and H2 in MySQL mode). Callers run it off the main thread, the
 * changing methods inside one transaction ({@code Database#transaction}).
 */
final class BoosterStore {

    static final String KIND_PERSONAL = "personal";
    static final String KIND_GLOBAL = "global";
    static final String STATE_ACTIVE = "active";
    static final String STATE_QUEUED = "queued";
    static final String STATE_DONE = "done";
    static final int GEM_PENDING = 0;
    static final int GEM_PAID = 1;
    static final int GEM_NONE = 2;

    /** Banked time of one type. */
    record Bank(int percent, long banked, String source) {
    }

    /** Result of starting banked time: how much started, at which strength, and the run afterwards. */
    record BankStart(long moved, int percent, long remainingAfter, long bankLeft) {
    }

    /** A stored global booster. */
    record GlobalRow(long id, BoosterType type, int percent, long duration, long remaining, UUID starter, UUID starterProfile,
                     String starterName, String state) {
    }

    /** Result of starting a global booster item. */
    record GlobalStart(BoosterRules.Outcome outcome, GlobalRow row, int queuedBefore) {
    }

    /** A thanks whose credit (column gem_state, kept from before the rename) is not paid yet. */
    record UnpaidThanks(long globalId, UUID thanker, UUID starterProfile) {
    }

    private BoosterStore() {
    }

    private static boolean duplicate(SQLException e) {
        return e.getErrorCode() == 1062 || "23505".equals(e.getSQLState());
    }

    // ================================================================== personal

    static Map<BoosterType, BoosterRules.Run> runs(Connection c, UUID profile) throws SQLException {
        Map<BoosterType, BoosterRules.Run> out = new EnumMap<>(BoosterType.class);
        for (Object[] row : Sql.query(c, "SELECT booster_type, bonus_percent, remaining_ms FROM booster_active WHERE profile_key = ?",
                rs -> new Object[]{rs.getString(1), rs.getInt(2), rs.getLong(3)}, profile)) {
            BoosterType.byId((String) row[0]).ifPresent(t -> {
                BoosterRules.Run run = new BoosterRules.Run((Integer) row[1], (Long) row[2]);
                if (run.running()) {
                    out.put(t, run);
                }
            });
        }
        return out;
    }

    static Map<BoosterType, Bank> banks(Connection c, UUID profile) throws SQLException {
        Map<BoosterType, Bank> out = new EnumMap<>(BoosterType.class);
        for (Object[] row : Sql.query(c, "SELECT booster_type, bonus_percent, banked_ms, bank_source FROM booster_bank WHERE profile_key = ?",
                rs -> new Object[]{rs.getString(1), rs.getInt(2), rs.getLong(3), rs.getString(4)}, profile)) {
            BoosterType.byId((String) row[0]).ifPresent(t -> {
                if ((Long) row[2] > 0) {
                    out.put(t, new Bank((Integer) row[1], (Long) row[2], (String) row[3]));
                }
            });
        }
        return out;
    }

    /** Subtracts time used since the last save (relative, so it never undoes time added meanwhile); removes finished runs. */
    static void applyUse(Connection c, UUID profile, Map<BoosterType, Long> use, long now) throws SQLException {
        if (use == null || use.isEmpty()) {
            return;
        }
        boolean any = false;
        for (Map.Entry<BoosterType, Long> e : use.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                Sql.update(c, "UPDATE booster_active SET remaining_ms = remaining_ms - ?, updated_at = ? WHERE profile_key = ? AND booster_type = ?",
                        e.getValue(), now, profile, e.getKey().id());
                any = true;
            }
        }
        if (any) {
            Sql.update(c, "DELETE FROM booster_active WHERE profile_key = ? AND remaining_ms <= 0", profile);
        }
    }

    private static Optional<BoosterRules.Run> run(Connection c, UUID profile, BoosterType type) throws SQLException {
        return Sql.queryOne(c, "SELECT bonus_percent, remaining_ms FROM booster_active WHERE profile_key = ? AND booster_type = ? FOR UPDATE",
                rs -> new BoosterRules.Run(rs.getInt(1), rs.getLong(2)), profile, type.id());
    }

    private static void writeRun(Connection c, UUID profile, BoosterType type, boolean existed, int percent, long remaining,
                                 boolean fresh, long now) throws SQLException {
        if (existed) {
            if (fresh) {
                Sql.update(c, "UPDATE booster_active SET bonus_percent = ?, remaining_ms = ?, started_at = ?, updated_at = ? "
                        + "WHERE profile_key = ? AND booster_type = ?", percent, remaining, now, now, profile, type.id());
            } else {
                Sql.update(c, "UPDATE booster_active SET remaining_ms = ?, updated_at = ? WHERE profile_key = ? AND booster_type = ?",
                        remaining, now, profile, type.id());
            }
        } else {
            Sql.update(c, "INSERT INTO booster_active (profile_key, booster_type, bonus_percent, remaining_ms, started_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?)", profile, type.id(), percent, remaining, now, now);
        }
    }

    static boolean used(Connection c, UUID itemUuid) throws SQLException {
        return Sql.queryOne(c, "SELECT item_kind FROM booster_used_items WHERE item_uuid = ?", rs -> rs.getString(1), itemUuid).isPresent();
    }

    /** Marks an item as started. False if it was started before. */
    private static boolean markUsed(Connection c, UUID itemUuid, UUID profile, String kind, long now) throws SQLException {
        if (used(c, itemUuid)) {
            return false;
        }
        try {
            Sql.update(c, "INSERT INTO booster_used_items (item_uuid, profile_key, item_kind, used_at) VALUES (?, ?, ?, ?)",
                    itemUuid, profile, kind, now);
            return true;
        } catch (SQLException e) {
            if (duplicate(e)) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Starts a personal booster item: first the time used since the last save is subtracted, then the rules decide; on
     * success the item's id is marked used and the run is written, all in the caller's transaction.
     */
    static BoosterRules.Decision activate(Connection c, UUID profile, UUID itemUuid, BoosterType type, int percent, long add,
                                          long max, Map<BoosterType, Long> use, long now) throws SQLException {
        applyUse(c, profile, use, now);
        if (used(c, itemUuid)) {
            return new BoosterRules.Decision(BoosterRules.Outcome.ALREADY_USED, 0);
        }
        Optional<BoosterRules.Run> current = run(c, profile, type);
        BoosterRules.Decision d = BoosterRules.activate(current.orElse(null), percent, add, max);
        if (!d.ok()) {
            return d;
        }
        if (!markUsed(c, itemUuid, profile, KIND_PERSONAL, now)) {
            return new BoosterRules.Decision(BoosterRules.Outcome.ALREADY_USED, 0);
        }
        writeRun(c, profile, type, current.isPresent(), percent, d.remaining(), d.outcome() == BoosterRules.Outcome.STARTED, now);
        return d;
    }

    /** Banks time of a type (the newest strength applies). Returns the bank afterwards. */
    static long bank(Connection c, UUID profile, BoosterType type, int percent, long add, long max, String source, long now)
            throws SQLException {
        Optional<Long> cur = Sql.queryOne(c, "SELECT banked_ms FROM booster_bank WHERE profile_key = ? AND booster_type = ? FOR UPDATE",
                rs -> rs.getLong(1), profile, type.id());
        long banked = BoosterRules.bankAdd(cur.orElse(0L), add, max);
        if (cur.isPresent()) {
            Sql.update(c, "UPDATE booster_bank SET bonus_percent = ?, banked_ms = ?, bank_source = ?, updated_at = ? WHERE profile_key = ? AND booster_type = ?",
                    percent, banked, source, now, profile, type.id());
        } else {
            Sql.update(c, "INSERT INTO booster_bank (profile_key, booster_type, bonus_percent, banked_ms, bank_source, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?)", profile, type.id(), percent, banked, source, now);
        }
        return banked;
    }

    /** Starts as much banked time of a type as the rules allow (nothing while another strength runs). */
    static BankStart startBank(Connection c, UUID profile, BoosterType type, long max, Map<BoosterType, Long> use, long now)
            throws SQLException {
        applyUse(c, profile, use, now);
        Optional<Bank> bank = Sql.queryOne(c, "SELECT bonus_percent, banked_ms, bank_source FROM booster_bank WHERE profile_key = ? "
                + "AND booster_type = ? FOR UPDATE", rs -> new Bank(rs.getInt(1), rs.getLong(2), rs.getString(3)), profile, type.id());
        if (bank.isEmpty() || bank.get().banked() <= 0) {
            return new BankStart(0, 0, 0, 0);
        }
        Optional<BoosterRules.Run> current = run(c, profile, type);
        BoosterRules.Run running = current.filter(BoosterRules.Run::running).orElse(null);
        long move = BoosterRules.bankMove(running, bank.get().percent(), bank.get().banked(), max);
        if (move <= 0) {
            return new BankStart(0, bank.get().percent(), running == null ? 0 : running.remaining(), bank.get().banked());
        }
        long after = (running == null ? 0 : running.remaining()) + move;
        writeRun(c, profile, type, current.isPresent(), bank.get().percent(), after, running == null, now);
        long left = bank.get().banked() - move;
        if (left <= 0) {
            Sql.update(c, "DELETE FROM booster_bank WHERE profile_key = ? AND booster_type = ?", profile, type.id());
        } else {
            Sql.update(c, "UPDATE booster_bank SET banked_ms = ?, updated_at = ? WHERE profile_key = ? AND booster_type = ?",
                    left, now, profile, type.id());
        }
        return new BankStart(move, bank.get().percent(), after, Math.max(0, left));
    }

    /** Removes the boosters of a deleted profile. */
    static void deleteProfile(Connection c, UUID profile) throws SQLException {
        Sql.update(c, "DELETE FROM booster_active WHERE profile_key = ?", profile);
        Sql.update(c, "DELETE FROM booster_bank WHERE profile_key = ?", profile);
    }

    // ================================================================== global

    private static GlobalRow globalRow(java.sql.ResultSet rs) throws SQLException {
        return new GlobalRow(rs.getLong("global_id"), BoosterType.byId(rs.getString("booster_type")).orElse(null),
                rs.getInt("bonus_percent"), rs.getLong("duration_ms"), rs.getLong("remaining_ms"), Sql.uuid(rs, "starter_uuid"),
                Sql.uuid(rs, "starter_profile"), rs.getString("starter_name"), rs.getString("global_state"));
    }

    private static final String GLOBAL_COLUMNS = "global_id, booster_type, bonus_percent, duration_ms, remaining_ms, starter_uuid, "
            + "starter_profile, starter_name, global_state";

    /** Active and queued global boosters, oldest first (unknown types are left out). */
    static List<GlobalRow> globals(Connection c) throws SQLException {
        return Sql.query(c, "SELECT " + GLOBAL_COLUMNS + " FROM booster_global WHERE global_state IN (?, ?) ORDER BY global_id",
                BoosterStore::globalRow, STATE_ACTIVE, STATE_QUEUED).stream().filter(r -> r.type() != null).toList();
    }

    /**
     * Starts a global booster item: active when no booster of the type runs, otherwise queued (refused when
     * {@code maxQueue} are already waiting). The item's id is marked used in the same transaction.
     */
    static GlobalStart startGlobal(Connection c, UUID itemUuid, BoosterType type, int percent, long duration, UUID starter,
                                   UUID starterProfile, String starterName, int maxQueue, long now) throws SQLException {
        if (used(c, itemUuid)) {
            return new GlobalStart(BoosterRules.Outcome.ALREADY_USED, null, 0);
        }
        int active = Sql.queryOne(c, "SELECT COUNT(*) FROM booster_global WHERE global_state = ? AND booster_type = ?",
                rs -> rs.getInt(1), STATE_ACTIVE, type.id()).orElse(0);
        int queued = Sql.queryOne(c, "SELECT COUNT(*) FROM booster_global WHERE global_state = ? AND booster_type = ?",
                rs -> rs.getInt(1), STATE_QUEUED, type.id()).orElse(0);
        if (active > 0 && queued >= maxQueue) {
            return new GlobalStart(BoosterRules.Outcome.FULL, null, queued);
        }
        if (!markUsed(c, itemUuid, starterProfile, KIND_GLOBAL, now)) {
            return new GlobalStart(BoosterRules.Outcome.ALREADY_USED, null, 0);
        }
        String state = active > 0 ? STATE_QUEUED : STATE_ACTIVE;
        String name = starterName == null ? "?" : starterName.length() > 16 ? starterName.substring(0, 16) : starterName;
        long id = Sql.insertReturningId(c, "INSERT INTO booster_global (booster_type, bonus_percent, duration_ms, remaining_ms, starter_uuid, "
                        + "starter_profile, starter_name, global_state, created_at, started_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                type.id(), percent, duration, duration, starter, starterProfile, name, state, now, active > 0 ? null : now);
        return new GlobalStart(active > 0 ? BoosterRules.Outcome.EXTENDED : BoosterRules.Outcome.STARTED,
                new GlobalRow(id, type, percent, duration, duration, starter, starterProfile, name, state), queued);
    }

    /** Saves the time left of an active global booster. */
    static void saveGlobal(Connection c, long id, long remaining) throws SQLException {
        Sql.update(c, "UPDATE booster_global SET remaining_ms = ? WHERE global_id = ? AND global_state = ?", Math.max(0, remaining), id, STATE_ACTIVE);
    }

    static void endGlobal(Connection c, long id, long now) throws SQLException {
        Sql.update(c, "UPDATE booster_global SET remaining_ms = 0, global_state = ?, ended_at = ? WHERE global_id = ?", STATE_DONE, now, id);
    }

    static void activateGlobal(Connection c, long id, long now) throws SQLException {
        Sql.update(c, "UPDATE booster_global SET global_state = ?, started_at = ? WHERE global_id = ? AND global_state = ?",
                STATE_ACTIVE, now, id, STATE_QUEUED);
    }

    // ================================================================== thanks

    /** Players who thanked a global booster. */
    static Set<UUID> thanked(Connection c, long globalId) throws SQLException {
        return new HashSet<>(Sql.query(c, "SELECT thanker_uuid FROM booster_thanks WHERE global_id = ?", rs -> Sql.uuid(rs, "thanker_uuid"), globalId));
    }

    /**
     * Stores a thanks. Returns the gem state ({@link #GEM_PENDING} = pay the gem now, {@link #GEM_NONE} = no gem), or -1
     * when this player already thanked this booster.
     *
     * @param maxGems most credits one booster pays its starter (0 = no limit)
     */
    static int thank(Connection c, long globalId, UUID thanker, boolean gem, int maxGems, long now) throws SQLException {
        if (Sql.queryOne(c, "SELECT gem_state FROM booster_thanks WHERE global_id = ? AND thanker_uuid = ?", rs -> rs.getInt(1),
                globalId, thanker).isPresent()) {
            return -1;
        }
        int state = gem ? GEM_PENDING : GEM_NONE;
        if (gem && maxGems > 0) {
            int paying = Sql.queryOne(c, "SELECT COUNT(*) FROM booster_thanks WHERE global_id = ? AND gem_state IN (?, ?)",
                    rs -> rs.getInt(1), globalId, GEM_PENDING, GEM_PAID).orElse(0);
            if (paying >= maxGems) {
                state = GEM_NONE;
            }
        }
        try {
            Sql.update(c, "INSERT INTO booster_thanks (global_id, thanker_uuid, gem_state, thanked_at) VALUES (?, ?, ?, ?)",
                    globalId, thanker, state, now);
        } catch (SQLException e) {
            if (duplicate(e)) {
                return -1;
            }
            throw e;
        }
        return state;
    }

    static void markPaid(Connection c, long globalId, UUID thanker) throws SQLException {
        Sql.update(c, "UPDATE booster_thanks SET gem_state = ? WHERE global_id = ? AND thanker_uuid = ?", GEM_PAID, globalId, thanker);
    }

    /** Thanks whose credit was not paid (a crash or an economy error); paid again at start-up with the same idempotency key. */
    static List<UnpaidThanks> unpaid(Connection c, int limit) throws SQLException {
        return Sql.query(c, "SELECT t.global_id, t.thanker_uuid, g.starter_profile FROM booster_thanks t JOIN booster_global g "
                        + "ON g.global_id = t.global_id WHERE t.gem_state = ? ORDER BY t.thanked_at LIMIT " + Math.max(1, limit),
                rs -> new UnpaidThanks(rs.getLong(1), Sql.uuid(rs, "thanker_uuid"), Sql.uuid(rs, "starter_profile")), GEM_PENDING);
    }
}

package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.island.IslandService.Role;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kick backups ({@code island_kick_backups}, owner answer 251): what a kicked member had on their island profile, so an
 * admin can restore it with {@code /isadmin restore}. SECFIX1 F-02: a backup restores only what was really taken, once
 * (kick: the membership; disband/reset: {@code money_taken} and the confirmed items). Bukkit-free (item data are plain bytes), so it runs against H2 in
 * unit tests.
 *
 * <p>Restoring is one transaction: the backup is claimed ({@code restored_at IS NULL → now}) and the member is added back
 * with their old role (slots and one island per profile still apply). If adding fails nothing is claimed, so the same
 * backup can be tried again later. Items are restored separately, also exactly once ({@code items_restored_at}).</p>
 */
final class KickBackupStore {

    /**
     * One backup row. {@code money} = the balance when the backup was made (shown to staff; NULL for kicks and when the
     * economy was not available). {@code moneyTaken} = what a disband/reset really withdrew (SECFIX1 F-02: the only
     * money a restore gives back).
     */
    record Backup(long id, UUID islandId, String islandName, UUID profileId, UUID playerId, String playerName, Role role, Long money,
                  boolean itemsCaptured, byte[] inventory, byte[] enderChest, String removal, String kickedBy, long createdAt,
                  Long restoredAt, String restoredBy, Long itemsRestoredAt, Long bankMoney, Long moneyTaken) {

        /** Owner answer 591: a backup taken when the island was disbanded or reset (the island is gone, no membership). */
        boolean islandRemoval() {
            return REMOVAL_DISBAND.equals(removal) || REMOVAL_RESET.equals(removal);
        }

        /** Membership (and money) not restored yet. */
        boolean open() {
            return restoredAt == null;
        }

        /**
         * Items that were really taken (disband/reset, confirmed after the player file was saved) and not given back yet.
         * SECFIX1 F-02: a kick never takes items, so a kick backup never gives any (also old rows from before the fix).
         */
        boolean itemsOpen() {
            return islandRemoval() && itemsCaptured && itemsRestoredAt == null;
        }

        /**
         * A reset saved the items but the server stopped before it confirmed they left the player (inventory emptied and
         * player file saved). Never restored automatically (that could double them): staff check by hand.
         */
        boolean itemsPending() {
            return islandRemoval() && !itemsCaptured && inventory != null && itemsRestoredAt == null;
        }

        /** SECFIX1 F-02: the money a restore gives back: exactly what the disband/reset withdrew, 0 for kicks. Pure. */
        long restoreMoney() {
            return islandRemoval() && moneyTaken != null ? Math.max(0, moneyTaken) : 0;
        }
    }

    /** A backup to save (the id and restore columns are set by the database). */
    record NewBackup(UUID islandId, String islandName, UUID profileId, UUID playerId, String playerName, Role role, Long money,
                     byte[] inventory, byte[] enderChest, String removal, String kickedBy, long createdAt, Long bankMoney) {

        NewBackup(UUID islandId, String islandName, UUID profileId, UUID playerId, String playerName, Role role, Long money,
                  byte[] inventory, byte[] enderChest, String removal, String kickedBy, long createdAt) {
            this(islandId, islandName, profileId, playerId, playerName, role, money, inventory, enderChest, removal, kickedBy, createdAt,
                    null);
        }
    }

    /** Owner answer 591: removal value of the backups taken by a disband / a staff reset. */
    static final String REMOVAL_DISBAND = "island_disband";
    static final String REMOVAL_RESET = "island_reset";

    /** Result of {@link #restoreMembership}. */
    sealed interface RestoreResult {
        /** Claimed and added back. */
        record Restored(Backup backup, long joinedAt, boolean converted) implements RestoreResult {
        }

        /** Already restored (or no such backup). */
        record AlreadyRestored() implements RestoreResult {
        }

        /** Adding the member back was refused; nothing changed (the backup stays open). */
        record Refused(MemberStore.AddResult reason) implements RestoreResult {
        }
    }

    private static final String COLUMNS = "id, island_id, island_name, profile_id, player_id, player_name, member_role, money, "
            + "items_captured, inventory, ender_chest, removal, kicked_by, created_at, restored_at, restored_by, items_restored_at, bank_money, "
            + "money_taken";

    private final Database db;

    KickBackupStore(Database db) {
        this.db = db;
    }

    CompletableFuture<Long> save(NewBackup b) {
        return db.async(c -> save(c, b));
    }

    /** Every backup of a player (all their profiles), newest first. */
    CompletableFuture<List<Backup>> ofPlayer(UUID playerId, int limit) {
        return db.async(c -> ofPlayer(c, playerId, limit));
    }

    /** Players with at least one backup not restored yet (tab completion), most recent first. */
    CompletableFuture<List<UUID>> playersWithOpenBackups(int limit) {
        return db.async(c -> Sql.query(c, "SELECT player_id, MAX(created_at) AS latest FROM island_kick_backups WHERE restored_at IS NULL "
                + "GROUP BY player_id ORDER BY latest DESC LIMIT " + Math.max(1, limit), rs -> Sql.uuid(rs, "player_id")));
    }

    CompletableFuture<RestoreResult> restoreMembership(long id, String by, long now, MemberStore.Limits limits) {
        return db.async(c -> restoreMembership(c, id, by, now, limits));
    }

    /** Marks the items of a backup as given back; true only for the one caller that claimed them. */
    CompletableFuture<Boolean> claimItems(long id, long now) {
        return db.async(c -> claimItems(c, id, now));
    }

    /** Owner answer 591: claims an island-removal backup (no membership to add back); true only for the one caller. */
    CompletableFuture<Boolean> claimRemoval(long id, String by, long now) {
        return db.async(c -> claimRemoval(c, id, by, now));
    }

    /**
     * The items of a backup can't be restored (they were not taken from the player after all, so giving them back would
     * duplicate them). Completes with true when the backup no longer offers items.
     */
    CompletableFuture<Boolean> dropItems(long id) {
        return db.async(c -> dropItems(c, id));
    }

    /**
     * SECFIX1 F-02: the reset emptied the inventory and the player file was saved; only now may the items be restored.
     * Completes with true when the backup offers them.
     */
    CompletableFuture<Boolean> confirmItems(long id) {
        return db.async(c -> confirmItems(c, id));
    }

    /** SECFIX1 F-02: records what the reset really withdrew (once); a restore gives back exactly this. */
    CompletableFuture<Boolean> recordMoneyTaken(long id, long amount) {
        return db.async(c -> recordMoneyTaken(c, id, amount));
    }

    /** Undoes {@link #claimItems} when the items could not be given after all (player left meanwhile). */
    CompletableFuture<Void> releaseItems(long id) {
        return db.run(c -> Sql.update(c, "UPDATE island_kick_backups SET items_restored_at = NULL WHERE id = ?", id));
    }

    // ------------------------------------------------------------------ SQL (package-private for tests)

    static long save(Connection c, NewBackup b) throws SQLException {
        return Sql.insertReturningId(c, "INSERT INTO island_kick_backups (island_id, island_name, profile_id, player_id, player_name, "
                        + "member_role, money, items_captured, inventory, ender_chest, removal, kicked_by, created_at, bank_money) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                b.islandId(), truncate(b.islandName(), 64), b.profileId(), b.playerId(), truncate(b.playerName(), 16),
                // SECFIX1 F-02: items start PENDING (not restorable) until confirmItems() after the inventory was emptied
                MemberStore.key(b.role()), b.money(), false, b.inventory(), b.enderChest(), truncate(b.removal(), 16),
                truncate(b.kickedBy(), 32), b.createdAt(), b.bankMoney());
    }

    static List<Backup> ofPlayer(Connection c, UUID playerId, int limit) throws SQLException {
        return Sql.query(c, "SELECT " + COLUMNS + " FROM island_kick_backups WHERE player_id = ? ORDER BY id DESC LIMIT "
                + Math.max(1, limit), KickBackupStore::backup, playerId);
    }

    static java.util.Optional<Backup> get(Connection c, long id) throws SQLException {
        return Sql.queryOne(c, "SELECT " + COLUMNS + " FROM island_kick_backups WHERE id = ?", KickBackupStore::backup, id);
    }

    static RestoreResult restoreMembership(Connection c, long id, String by, long now, MemberStore.Limits limits) throws SQLException {
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            java.util.Optional<Backup> found = get(c, id);
            if (found.isEmpty() || !found.get().open()
                    || Sql.update(c, "UPDATE island_kick_backups SET restored_at = ?, restored_by = ? WHERE id = ? AND restored_at IS NULL",
                    now, truncate(by, 32), id) != 1) {
                c.rollback();
                return new RestoreResult.AlreadyRestored();
            }
            Backup b = found.get();
            MemberStore.AddResult added = MemberStore.addMember(c, b.islandId(), b.profileId(), b.playerId(), null, now, limits, b.role());
            if (!(added instanceof MemberStore.AddResult.Added ok)) {
                c.rollback(); // the backup stays open: try again once there is room
                return new RestoreResult.Refused(added);
            }
            c.commit();
            return new RestoreResult.Restored(get(c, id).orElse(b), ok.joinedAt(), ok.converted());
        } catch (SQLException | RuntimeException e) {
            try {
                c.rollback();
            } catch (SQLException rollback) {
                e.addSuppressed(rollback);
            }
            if (IslandStore.isConstraintViolation(e)) {
                return new RestoreResult.Refused(new MemberStore.AddResult.HasIsland()); // joined an island at the same moment
            }
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    static boolean claimRemoval(Connection c, long id, String by, long now) throws SQLException {
        return Sql.update(c, "UPDATE island_kick_backups SET restored_at = ?, restored_by = ? WHERE id = ? AND restored_at IS NULL "
                + "AND removal IN (?, ?)", now, truncate(by, 32), id, REMOVAL_DISBAND, REMOVAL_RESET) == 1;
    }

    static boolean dropItems(Connection c, long id) throws SQLException {
        Sql.update(c, "UPDATE island_kick_backups SET items_captured = ?, inventory = NULL, ender_chest = NULL WHERE id = ? "
                + "AND items_restored_at IS NULL", false, id);
        return Sql.queryOne(c, "SELECT items_captured FROM island_kick_backups WHERE id = ?", rs -> !rs.getBoolean(1), id).orElse(true);
    }

    static boolean claimItems(Connection c, long id, long now) throws SQLException {
        return Sql.update(c, "UPDATE island_kick_backups SET items_restored_at = ? WHERE id = ? AND items_captured = ? "
                + "AND items_restored_at IS NULL AND removal IN (?, ?)", now, id, true, REMOVAL_DISBAND, REMOVAL_RESET) == 1;
    }

    static boolean confirmItems(Connection c, long id) throws SQLException {
        return Sql.update(c, "UPDATE island_kick_backups SET items_captured = ? WHERE id = ? AND items_captured = ? "
                + "AND inventory IS NOT NULL AND items_restored_at IS NULL AND removal IN (?, ?)", true, id, false,
                REMOVAL_DISBAND, REMOVAL_RESET) == 1;
    }

    static boolean recordMoneyTaken(Connection c, long id, long amount) throws SQLException {
        if (amount <= 0) {
            return false;
        }
        return Sql.update(c, "UPDATE island_kick_backups SET money_taken = ? WHERE id = ? AND money_taken IS NULL AND removal IN (?, ?)",
                amount, id, REMOVAL_DISBAND, REMOVAL_RESET) == 1;
    }

    static Backup backup(ResultSet rs) throws SQLException {
        long money = rs.getLong("money");
        Long m = rs.wasNull() ? null : money;
        return new Backup(rs.getLong("id"), Sql.uuid(rs, "island_id"), rs.getString("island_name"), Sql.uuid(rs, "profile_id"),
                Sql.uuid(rs, "player_id"), rs.getString("player_name"), role(rs.getString("member_role")), m,
                rs.getBoolean("items_captured"), rs.getBytes("inventory"), rs.getBytes("ender_chest"), rs.getString("removal"),
                rs.getString("kicked_by"), rs.getLong("created_at"), Sql.nullableLong(rs, "restored_at"), rs.getString("restored_by"),
                Sql.nullableLong(rs, "items_restored_at"), Sql.nullableLong(rs, "bank_money"), Sql.nullableLong(rs, "money_taken"));
    }

    private static Role role(String key) {
        try {
            return Role.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return Role.MEMBER;
        }
    }

    private static String truncate(String text, int max) {
        String t = text == null ? "" : text;
        return t.length() <= max ? t : t.substring(0, max);
    }
}

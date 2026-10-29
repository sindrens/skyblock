package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.island.IslandService.Role;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Database access of the islands module (tables from {@code db/islands/V001}). Every method runs off the main thread
 * through {@link Database}; creation is one transaction (slot + island + owner membership, all or nothing).
 */
public final class IslandStore {

    /** Result of a creation attempt. */
    public sealed interface CreateResult {
        record Created(Island island) implements CreateResult {
        }

        record AlreadyHasIsland() implements CreateResult {
        }

        record Cooldown(long remainingMillis) implements CreateResult {
        }
    }

    /** One slot row (admin overview, M2.7). */
    public record SlotRow(int id, int gridX, int gridZ, String state, UUID islandId, long updatedAt) {
    }

    static final String STATE_USED = "used";
    static final String STATE_FREE = "free";
    static final String STATE_CLEARING = "clearing";
    private static final int MAX_ATTEMPTS = 5;

    private final Database db;

    public IslandStore(Database db) {
        this.db = db;
    }

    // ------------------------------------------------------------------ async API

    /** All active (not disbanded) islands with their members. Blocking: call during enable only. */
    public List<Island> loadAllBlocking(String world) {
        return db.blocking(c -> loadAll(c, world));
    }

    /** Last time {@code owner} disbanded an island (epoch millis), if ever. */
    public CompletableFuture<Optional<Long>> lastDisband(UUID owner) {
        return db.async(c -> lastDisband(c, owner));
    }

    /**
     * Creates an island for {@code owner} in one transaction: checks one-island-per-player and the disband cooldown,
     * takes the first free slot (or adds the next spiral slot), inserts the island and the owner as leader. Retries
     * when another server/thread took the same new slot at the same time.
     */
    public CompletableFuture<CreateResult> create(UUID owner, int seasonId, int sizeChunks, long cooldownMillis, String world) {
        return db.async(c -> createWithRetry(c, owner, seasonId, sizeChunks, cooldownMillis, world, System.currentTimeMillis()));
    }

    public CompletableFuture<Void> saveHome(UUID islandId, Island.Home home) {
        return db.run(c -> saveHome(c, islandId, home));
    }

    public CompletableFuture<Boolean> saveSize(UUID islandId, int sizeChunks) {
        return db.async(c -> Sql.update(c, "UPDATE islands SET size_chunks = ? WHERE id = ? AND deleted_at IS NULL",
                sizeChunks, islandId) == 1);
    }

    /**
     * Saves one role permission override of an island ({@code null} = back to the default: the row is removed).
     * Delete + insert in one transaction, so a row is never half-written.
     */
    public CompletableFuture<Void> savePerm(UUID islandId, PermRole role, IslandPerm perm, Boolean allowed) {
        return db.run(c -> savePerm(c, islandId, role, perm, allowed, System.currentTimeMillis()));
    }

    /** Every slot ever handed out, in spiral order (admin overview). */
    public CompletableFuture<List<SlotRow>> slots() {
        return db.async(IslandStore::slots);
    }

    // ------------------------------------------------------------------ SQL (package-private for tests)

    static List<Island> loadAll(Connection c, String world) throws SQLException {
        Map<UUID, Island> islands = new HashMap<>();
        List<Island> ordered = new ArrayList<>();
        for (Island island : Sql.query(c, "SELECT i.id, i.season_id, i.owner_uuid, i.type, i.slot_id, i.size_chunks, i.home_x, i.home_y, "
                + "i.home_z, i.home_yaw, i.home_pitch, i.name, i.created_at, s.grid_x, s.grid_z FROM islands i "
                + "JOIN island_slots s ON s.id = i.slot_id WHERE i.deleted_at IS NULL ORDER BY i.created_at", rs -> island(rs, world))) {
            islands.put(island.id(), island);
            ordered.add(island);
        }
        Sql.query(c, "SELECT island_id, uuid, role FROM island_members", rs -> {
            Island island = islands.get(Sql.uuid(rs, "island_id"));
            if (island != null) {
                island.putMember(Sql.uuid(rs, "uuid"), role(rs.getString("role")));
            }
            return null;
        });
        loadPerms(c, islands);
        return ordered;
    }

    /** Role permission overrides (rows with an unknown role/permission key, e.g. from a newer version, are skipped). */
    static void loadPerms(Connection c, Map<UUID, Island> islands) throws SQLException {
        Sql.query(c, "SELECT island_id, role, perm, allowed FROM island_role_perms", rs -> {
            Island island = islands.get(Sql.uuid(rs, "island_id"));
            Optional<PermRole> role = PermRole.byKey(rs.getString("role"));
            Optional<IslandPerm> perm = IslandPerm.byKey(rs.getString("perm"));
            if (island != null && role.isPresent() && perm.isPresent()) {
                island.perms().set(role.get(), perm.get(), rs.getBoolean("allowed"));
            }
            return null;
        });
    }

    static void savePerm(Connection c, UUID islandId, PermRole role, IslandPerm perm, Boolean allowed, long now) throws SQLException {
        inTransaction(c, () -> {
            Sql.update(c, "DELETE FROM island_role_perms WHERE island_id = ? AND role = ? AND perm = ?", islandId, role.key(), perm.key());
            if (allowed != null) {
                Sql.update(c, "INSERT INTO island_role_perms (island_id, role, perm, allowed, updated_at) VALUES (?, ?, ?, ?, ?)",
                        islandId, role.key(), perm.key(), allowed, now);
            }
            return null;
        });
    }

    private static Island island(ResultSet rs, String world) throws SQLException {
        double hx = rs.getDouble("home_x");
        Island.Home home = rs.wasNull() ? null : new Island.Home(hx, rs.getDouble("home_y"), rs.getDouble("home_z"),
                rs.getFloat("home_yaw"), rs.getFloat("home_pitch"));
        return new Island(Sql.uuid(rs, "id"), rs.getInt("season_id"), Sql.uuid(rs, "owner_uuid"), "team".equals(rs.getString("type")),
                rs.getInt("slot_id"), rs.getInt("grid_x"), rs.getInt("grid_z"), rs.getInt("size_chunks"), home, rs.getString("name"),
                rs.getLong("created_at"), world);
    }

    static Role role(String stored) {
        try {
            return Role.valueOf(stored.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Role.MEMBER;
        }
    }

    static Optional<Long> lastDisband(Connection c, UUID owner) throws SQLException {
        return Sql.queryOne(c, "SELECT MAX(deleted_at) FROM islands WHERE owner_uuid = ? AND deleted_at IS NOT NULL",
                IslandStore::nullableFirst, owner);
    }

    private static Long nullableFirst(ResultSet rs) throws SQLException {
        long value = rs.getLong(1);
        return rs.wasNull() ? null : value;
    }

    static boolean hasIsland(Connection c, UUID player) throws SQLException {
        return Sql.queryOne(c, "SELECT 1 FROM island_members WHERE uuid = ?", rs -> Boolean.TRUE, player).isPresent();
    }

    static CreateResult createWithRetry(Connection c, UUID owner, int seasonId, int sizeChunks, long cooldownMillis, String world,
                                        long now) throws SQLException {
        SQLException last = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return inTransaction(c, () -> createOnce(c, owner, seasonId, sizeChunks, cooldownMillis, world, now));
            } catch (SQLException e) {
                if (!isConstraintViolation(e)) {
                    throw e;
                }
                last = e;
                if (hasIsland(c, owner)) {
                    return new CreateResult.AlreadyHasIsland(); // a parallel creation for the same player won
                }
                // someone else took the same new slot: try the next one
            }
        }
        throw last;
    }

    private static CreateResult createOnce(Connection c, UUID owner, int seasonId, int sizeChunks, long cooldownMillis, String world,
                                           long now) throws SQLException {
        if (hasIsland(c, owner)) {
            return new CreateResult.AlreadyHasIsland();
        }
        Optional<Long> disbanded = lastDisband(c, owner);
        if (disbanded.isPresent() && cooldownMillis > 0) {
            long remaining = disbanded.get() + cooldownMillis - now;
            if (remaining > 0) {
                return new CreateResult.Cooldown(remaining);
            }
        }
        UUID islandId = UUID.randomUUID();
        int[] slot = takeSlot(c, islandId, now);
        Sql.update(c, "INSERT INTO islands (id, season_id, owner_uuid, type, slot_id, size_chunks, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                islandId, seasonId, owner, "solo", slot[0], sizeChunks, now);
        Sql.update(c, "INSERT INTO island_members (island_id, uuid, role, joined_at) VALUES (?, ?, ?, ?)",
                islandId, owner, "leader", now);
        Island island = new Island(islandId, seasonId, owner, false, slot[0], slot[1], slot[2], sizeChunks, null, null, now, world);
        island.putMember(owner, Role.LEADER);
        return new CreateResult.Created(island);
    }

    /** Reuses the lowest free slot, or appends the next spiral slot. Returns {id, gridX, gridZ}. */
    static int[] takeSlot(Connection c, UUID islandId, long now) throws SQLException {
        Optional<int[]> free = Sql.queryOne(c, "SELECT id, grid_x, grid_z FROM island_slots WHERE state = ? ORDER BY id",
                rs -> new int[]{rs.getInt("id"), rs.getInt("grid_x"), rs.getInt("grid_z")}, STATE_FREE);
        if (free.isPresent()) {
            int[] slot = free.get();
            int changed = Sql.update(c, "UPDATE island_slots SET state = ?, island_id = ?, updated_at = ? WHERE id = ? AND state = ?",
                    STATE_USED, islandId, now, slot[0], STATE_FREE);
            if (changed == 1) {
                return slot;
            }
            throw new SQLException("Slot " + slot[0] + " was taken meanwhile", "23000"); // retried by createWithRetry
        }
        int next = Sql.queryOne(c, "SELECT MAX(id) FROM island_slots", rs -> {
            int max = rs.getInt(1);
            return rs.wasNull() ? 0 : max + 1;
        }).orElse(0);
        int[] grid = IslandGrid.spiral(next);
        Sql.update(c, "INSERT INTO island_slots (id, grid_x, grid_z, state, island_id, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                next, grid[0], grid[1], STATE_USED, islandId, now);
        return new int[]{next, grid[0], grid[1]};
    }

    static void saveHome(Connection c, UUID islandId, Island.Home home) throws SQLException {
        Sql.update(c, "UPDATE islands SET home_x = ?, home_y = ?, home_z = ?, home_yaw = ?, home_pitch = ? WHERE id = ?",
                home.x(), home.y(), home.z(), home.yaw(), home.pitch(), islandId);
    }

    static List<SlotRow> slots(Connection c) throws SQLException {
        return Sql.query(c, "SELECT id, grid_x, grid_z, state, island_id, updated_at FROM island_slots ORDER BY id",
                rs -> new SlotRow(rs.getInt("id"), rs.getInt("grid_x"), rs.getInt("grid_z"), rs.getString("state"),
                        Sql.uuid(rs, "island_id"), rs.getLong("updated_at")));
    }

    // ------------------------------------------------------------------ helpers

    @FunctionalInterface
    private interface Work<T> {
        T run() throws SQLException;
    }

    private static <T> T inTransaction(Connection c, Work<T> work) throws SQLException {
        boolean auto = c.getAutoCommit();
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
            c.setAutoCommit(auto);
        }
    }

    /** Duplicate key / unique index (SQLState class 23). */
    static boolean isConstraintViolation(Throwable error) {
        Throwable t = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        while (t != null) {
            if (t instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("23")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}

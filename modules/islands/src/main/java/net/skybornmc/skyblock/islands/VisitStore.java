package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Database side of M2.4 (tables from {@code db/islands/V005}): lock, bans, warps, visit counters, ratings and likes.
 * Anything with a limit (bans, warp slots) is one transaction that locks the island row and re-checks against the
 * database; the caches in {@link VisitManager} and {@link WarpManager} are only updated after a commit. Static SQL
 * methods are package-private for tests.
 */
final class VisitStore {

    /** Result of a ban. */
    enum BanResult {
        BANNED, ALREADY, MEMBER, LIMIT, GONE
    }

    /** Result of saving a warp ({@code /is setwarp} or a [warp] sign). */
    sealed interface WarpResult {
        /** A new warp. */
        record Created(IslandWarp warp) implements WarpResult {
        }

        /** An existing warp (same name) moved here; {@code category} = its category now. */
        record Moved(long warpId, Island.Home location, IslandWarp.SignPos sign, WarpCategory category) implements WarpResult {
        }

        record Full(int slots) implements WarpResult {
        }

        /** A sign can't take over a name that is already used. */
        record NameTaken() implements WarpResult {
        }

        record Gone() implements WarpResult {
        }
    }

    /** What a rating/like check needs from the database. */
    record VoteContext(boolean visited, Integer rating, boolean likedToday) {
    }

    /** Warp slot settings (islands.yml warps.start-slots / warps.max-slots). */
    record WarpLimits(int startSlots, int maxSlots) {
    }

    private final Database db;

    VisitStore(Database db) {
        this.db = db;
    }

    // ------------------------------------------------------------------ async API

    Map<UUID, Set<UUID>> loadBansBlocking() {
        return db.blocking(VisitStore::loadBans);
    }

    List<IslandWarp> loadWarpsBlocking() {
        return db.blocking(VisitStore::loadWarps);
    }

    CompletableFuture<Boolean> setLocked(UUID islandId, boolean locked) {
        return db.async(c -> setLocked(c, islandId, locked));
    }

    CompletableFuture<BanResult> ban(UUID islandId, UUID target, UUID by, int maxBans) {
        long now = System.currentTimeMillis();
        return duplicateAs(db.transaction(c -> ban(c, islandId, target, by, now, maxBans)), BanResult.ALREADY);
    }

    CompletableFuture<Boolean> unban(UUID islandId, UUID target) {
        return db.async(c -> Sql.update(c, "DELETE FROM island_bans WHERE island_id = ? AND uuid = ?", islandId, target) > 0);
    }

    CompletableFuture<WarpResult> saveWarp(UUID islandId, String name, WarpCategory category, String icon, Island.Home at,
                                           IslandWarp.SignPos sign, UUID by, WarpLimits limits) {
        long now = System.currentTimeMillis();
        return duplicateAs(db.transaction(c -> saveWarp(c, islandId, name, category, icon, at, sign, by, now, limits)),
                new WarpResult.NameTaken());
    }

    CompletableFuture<Boolean> deleteWarp(long warpId) {
        return db.transaction(c -> deleteWarp(c, warpId));
    }

    CompletableFuture<Boolean> setWarpIcon(long warpId, String icon) {
        return db.async(c -> Sql.update(c, "UPDATE island_warps SET icon = ? WHERE id = ?", icon, warpId) == 1);
    }

    CompletableFuture<Boolean> setWarpPublic(long warpId, boolean isPublic) {
        return db.async(c -> Sql.update(c, "UPDATE island_warps SET is_public = ? WHERE id = ?", isPublic, warpId) == 1);
    }

    CompletableFuture<Boolean> setWarpCategory(long warpId, WarpCategory category) {
        return db.async(c -> Sql.update(c, "UPDATE island_warps SET category = ? WHERE id = ?", category.key(), warpId) == 1);
    }

    /** Records a visitor for today; true if it is their first visit to this island today. */
    CompletableFuture<Boolean> recordVisit(UUID islandId, UUID visitor, int day, int seasonId) {
        return duplicateAs(db.async(c -> recordVisit(c, islandId, visitor, day, seasonId)), false);
    }

    /** Counts a warp visit; true if this visitor's first use of the warp today (the counter went up). */
    CompletableFuture<Boolean> recordWarpVisit(long warpId, UUID visitor, int day) {
        return duplicateAs(db.transaction(c -> recordWarpVisit(c, warpId, visitor, day)), false);
    }

    CompletableFuture<VoteContext> voteContext(UUID islandId, UUID player, int seasonId, int day) {
        return db.async(c -> voteContext(c, islandId, player, seasonId, day));
    }

    /** Sets a rating; returns the old stars (empty = first rating this season). */
    CompletableFuture<Optional<Integer>> rate(UUID islandId, UUID player, int seasonId, int stars) {
        long now = System.currentTimeMillis();
        return db.transaction(c -> rate(c, islandId, player, seasonId, stars, now));
    }

    /** Adds today's like; false if the player already liked this island today. */
    CompletableFuture<Boolean> like(UUID islandId, UUID player, int day, int seasonId) {
        long now = System.currentTimeMillis();
        return duplicateAs(db.async(c -> like(c, islandId, player, day, seasonId, now)), false);
    }

    CompletableFuture<Map<UUID, Showcase.Stats>> stats(int fromDay, int seasonId) {
        return db.async(c -> stats(c, fromDay, seasonId));
    }

    /** A unique-key race (two clicks, two servers) means "already done", never an error. */
    private static <T> CompletableFuture<T> duplicateAs(CompletableFuture<T> future, T fallback) {
        return future.handle((value, error) -> {
            if (error == null) {
                return value;
            }
            if (IslandStore.isConstraintViolation(error)) {
                return fallback;
            }
            throw error instanceof CompletionException ce ? ce : new CompletionException(error);
        });
    }

    // ------------------------------------------------------------------ SQL

    static Map<UUID, Set<UUID>> loadBans(Connection c) throws SQLException {
        Map<UUID, Set<UUID>> bans = new HashMap<>();
        Sql.query(c, "SELECT island_id, uuid FROM island_bans", rs -> {
            bans.computeIfAbsent(Sql.uuid(rs, "island_id"), id -> new HashSet<>()).add(Sql.uuid(rs, "uuid"));
            return null;
        });
        return bans;
    }

    static List<IslandWarp> loadWarps(Connection c) throws SQLException {
        return Sql.query(c, "SELECT id, island_id, name, category, icon, is_public, x, y, z, yaw, pitch, sign_x, sign_y, sign_z, visits, "
                + "created_by, created_at FROM island_warps ORDER BY created_at, id", rs -> {
            int sx = rs.getInt("sign_x");
            IslandWarp.SignPos sign = rs.wasNull() ? null : new IslandWarp.SignPos(sx, rs.getInt("sign_y"), rs.getInt("sign_z"));
            return new IslandWarp(rs.getLong("id"), Sql.uuid(rs, "island_id"), rs.getString("name"),
                    WarpCategory.byKeyOrOther(rs.getString("category")), rs.getString("icon"), rs.getBoolean("is_public"),
                    new Island.Home(rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch")),
                    sign, rs.getInt("visits"), Sql.uuid(rs, "created_by"), rs.getLong("created_at"));
        });
    }

    static boolean setLocked(Connection c, UUID islandId, boolean locked) throws SQLException {
        return Sql.update(c, "UPDATE islands SET locked = ? WHERE id = ? AND deleted_at IS NULL", locked, islandId) == 1;
    }

    /** The locked island row's warp slots ({@code null} column = start value); empty if the island is gone. */
    private static Optional<Optional<Integer>> lockIsland(Connection c, UUID islandId) throws SQLException {
        return Sql.queryOne(c, "SELECT warp_slots FROM islands WHERE id = ? AND deleted_at IS NULL FOR UPDATE", rs -> {
            int slots = rs.getInt("warp_slots");
            return rs.wasNull() ? Optional.<Integer>empty() : Optional.of(slots);
        }, islandId);
    }

    static BanResult ban(Connection c, UUID islandId, UUID target, UUID by, long now, int maxBans) throws SQLException {
        if (lockIsland(c, islandId).isEmpty()) {
            return BanResult.GONE;
        }
        if (Sql.queryOne(c, "SELECT 1 FROM island_members WHERE island_id = ? AND uuid = ?", rs -> 1, islandId, target).isPresent()) {
            return BanResult.MEMBER;
        }
        if (Sql.queryOne(c, "SELECT 1 FROM island_bans WHERE island_id = ? AND uuid = ?", rs -> 1, islandId, target).isPresent()) {
            return BanResult.ALREADY;
        }
        int bans = Sql.queryOne(c, "SELECT COUNT(*) FROM island_bans WHERE island_id = ?", rs -> rs.getInt(1), islandId).orElse(0);
        if (bans >= maxBans) {
            return BanResult.LIMIT;
        }
        Sql.update(c, "INSERT INTO island_bans (island_id, uuid, banned_by, created_at) VALUES (?, ?, ?, ?)", islandId, target, by, now);
        // A banned player can't stay coop (bans of coop players are refused, this only covers a race).
        Sql.update(c, "DELETE FROM island_coops WHERE island_id = ? AND uuid = ?", islandId, target);
        return BanResult.BANNED;
    }

    static WarpResult saveWarp(Connection c, UUID islandId, String name, WarpCategory category, String icon, Island.Home at,
                               IslandWarp.SignPos sign, UUID by, long now, WarpLimits limits) throws SQLException {
        Optional<Optional<Integer>> island = lockIsland(c, islandId);
        if (island.isEmpty()) {
            return new WarpResult.Gone();
        }
        String key = WarpRules.key(name);
        Optional<Object[]> existing = Sql.queryOne(c, "SELECT id, category FROM island_warps WHERE island_id = ? AND name_key = ?",
                rs -> new Object[]{rs.getLong("id"), rs.getString("category")}, islandId, key);
        if (existing.isPresent()) {
            if (sign != null) {
                return new WarpResult.NameTaken();
            }
            long id = (Long) existing.get()[0];
            WarpCategory now2 = category != null ? category : WarpCategory.byKeyOrOther((String) existing.get()[1]);
            // Moving with the command ends the sign link (the old sign is just a sign again).
            Sql.update(c, "UPDATE island_warps SET x = ?, y = ?, z = ?, yaw = ?, pitch = ?, category = ?, sign_x = NULL, sign_y = NULL, "
                    + "sign_z = NULL WHERE id = ?", at.x(), at.y(), at.z(), at.yaw(), at.pitch(), now2.key(), id);
            return new WarpResult.Moved(id, at, null, now2);
        }
        int slots = WarpRules.slots(island.get().orElse(null), limits.startSlots(), limits.maxSlots());
        int count = Sql.queryOne(c, "SELECT COUNT(*) FROM island_warps WHERE island_id = ?", rs -> rs.getInt(1), islandId).orElse(0);
        if (!WarpRules.fits(count, slots, false)) {
            return new WarpResult.Full(slots);
        }
        WarpCategory cat = category == null ? WarpCategory.OTHER : category;
        long id = Sql.insertReturningId(c, "INSERT INTO island_warps (island_id, name, name_key, category, icon, is_public, x, y, z, yaw, "
                        + "pitch, sign_x, sign_y, sign_z, visits, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                islandId, name, key, cat.key(), icon, true, at.x(), at.y(), at.z(), at.yaw(), at.pitch(),
                sign == null ? null : sign.x(), sign == null ? null : sign.y(), sign == null ? null : sign.z(), 0, by, now);
        return new WarpResult.Created(new IslandWarp(id, islandId, name, cat, icon, true, at, sign, 0, by, now));
    }

    static boolean deleteWarp(Connection c, long warpId) throws SQLException {
        Sql.update(c, "DELETE FROM island_warp_visits WHERE warp_id = ?", warpId);
        return Sql.update(c, "DELETE FROM island_warps WHERE id = ?", warpId) == 1;
    }

    static boolean recordVisit(Connection c, UUID islandId, UUID visitor, int day, int seasonId) throws SQLException {
        return Sql.update(c, "INSERT INTO island_visits (island_id, uuid, visit_day, season_id) SELECT ?, ?, ?, ? FROM DUAL "
                + "WHERE NOT EXISTS (SELECT 1 FROM island_visits WHERE island_id = ? AND uuid = ? AND visit_day = ?)",
                islandId, visitor, day, seasonId, islandId, visitor, day) == 1;
    }

    static boolean recordWarpVisit(Connection c, long warpId, UUID visitor, int day) throws SQLException {
        int added = Sql.update(c, "INSERT INTO island_warp_visits (warp_id, uuid, visit_day) SELECT ?, ?, ? FROM DUAL "
                + "WHERE EXISTS (SELECT 1 FROM island_warps WHERE id = ?) "
                + "AND NOT EXISTS (SELECT 1 FROM island_warp_visits WHERE warp_id = ? AND uuid = ? AND visit_day = ?)",
                warpId, visitor, day, warpId, warpId, visitor, day);
        if (added != 1) {
            return false;
        }
        Sql.update(c, "UPDATE island_warps SET visits = visits + 1 WHERE id = ?", warpId);
        return true;
    }

    static VoteContext voteContext(Connection c, UUID islandId, UUID player, int seasonId, int day) throws SQLException {
        boolean visited = Sql.queryOne(c, "SELECT 1 FROM island_visits WHERE island_id = ? AND uuid = ? LIMIT 1", rs -> 1, islandId, player)
                .isPresent();
        Integer rating = Sql.queryOne(c, "SELECT stars FROM island_ratings WHERE island_id = ? AND uuid = ? AND season_id = ?",
                rs -> rs.getInt(1), islandId, player, seasonId).orElse(null);
        boolean liked = Sql.queryOne(c, "SELECT 1 FROM island_likes WHERE island_id = ? AND uuid = ? AND visit_day = ?",
                rs -> 1, islandId, player, day).isPresent();
        return new VoteContext(visited, rating, liked);
    }

    static Optional<Integer> rate(Connection c, UUID islandId, UUID player, int seasonId, int stars, long now) throws SQLException {
        Optional<Integer> old = Sql.queryOne(c, "SELECT stars FROM island_ratings WHERE island_id = ? AND uuid = ? AND season_id = ? FOR UPDATE",
                rs -> rs.getInt(1), islandId, player, seasonId);
        if (old.isPresent()) {
            Sql.update(c, "UPDATE island_ratings SET stars = ?, updated_at = ? WHERE island_id = ? AND uuid = ? AND season_id = ?",
                    stars, now, islandId, player, seasonId);
        } else {
            Sql.update(c, "INSERT INTO island_ratings (island_id, uuid, season_id, stars, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                    islandId, player, seasonId, stars, now, now);
        }
        return old;
    }

    static boolean like(Connection c, UUID islandId, UUID player, int day, int seasonId, long now) throws SQLException {
        return Sql.update(c, "INSERT INTO island_likes (island_id, uuid, visit_day, season_id, created_at) SELECT ?, ?, ?, ?, ? FROM DUAL "
                + "WHERE NOT EXISTS (SELECT 1 FROM island_likes WHERE island_id = ? AND uuid = ? AND visit_day = ?)",
                islandId, player, day, seasonId, now, islandId, player, day) == 1;
    }

    /** Unique visits since {@code fromDay} (inclusive), ratings and likes of {@code seasonId}, per island. */
    static Map<UUID, Showcase.Stats> stats(Connection c, int fromDay, int seasonId) throws SQLException {
        Map<UUID, int[]> visits = new HashMap<>();
        Map<UUID, long[]> ratings = new HashMap<>();
        Map<UUID, int[]> likes = new HashMap<>();
        Sql.query(c, "SELECT island_id, COUNT(*) FROM island_visits WHERE visit_day >= ? GROUP BY island_id", rs -> {
            visits.put(Sql.uuid(rs, "island_id"), new int[]{rs.getInt(2)});
            return null;
        }, fromDay);
        Sql.query(c, "SELECT island_id, COUNT(*), SUM(stars) FROM island_ratings WHERE season_id = ? GROUP BY island_id", rs -> {
            ratings.put(Sql.uuid(rs, "island_id"), new long[]{rs.getInt(2), rs.getLong(3)});
            return null;
        }, seasonId);
        Sql.query(c, "SELECT island_id, COUNT(*) FROM island_likes WHERE season_id = ? GROUP BY island_id", rs -> {
            likes.put(Sql.uuid(rs, "island_id"), new int[]{rs.getInt(2)});
            return null;
        }, seasonId);
        Set<UUID> ids = new HashSet<>(visits.keySet());
        ids.addAll(ratings.keySet());
        ids.addAll(likes.keySet());
        Map<UUID, Showcase.Stats> out = new HashMap<>();
        for (UUID id : ids) {
            int v = visits.containsKey(id) ? visits.get(id)[0] : 0;
            long[] r = ratings.getOrDefault(id, new long[]{0, 0});
            int l = likes.containsKey(id) ? likes.get(id)[0] : 0;
            out.put(id, new Showcase.Stats(v, r[1], (int) r[0], l));
        }
        return out;
    }

    /** Removes the bans and warps of an island (inside the M2.7 removal transaction). Visits, ratings and likes stay as history. */
    static void deleteIsland(Connection c, UUID islandId) throws SQLException {
        Sql.update(c, "DELETE FROM island_warp_visits WHERE warp_id IN (SELECT id FROM island_warps WHERE island_id = ?)", islandId);
        Sql.update(c, "DELETE FROM island_warps WHERE island_id = ?", islandId);
        Sql.update(c, "DELETE FROM island_bans WHERE island_id = ?", islandId);
    }
}

package net.skybornmc.skyblock.guide;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Moved guide spots ({@code guide_spots}). All async. */
final class GuideStore {

    /** A spot in a world (feet, facing). */
    record Spot(String world, double x, double y, double z, float yaw) {
    }

    private final Database db;

    GuideStore(Database db) {
        this.db = db;
    }

    CompletableFuture<Map<UUID, Spot>> all() {
        return db.async(c -> {
            Map<UUID, Spot> out = new HashMap<>();
            for (Map.Entry<UUID, Spot> e : Sql.query(c, "SELECT island_id, world, x, y, z, yaw FROM guide_spots",
                    rs -> Map.entry(Sql.uuid(rs, "island_id"), new Spot(rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"),
                            rs.getDouble("z"), rs.getFloat("yaw"))))) {
                out.put(e.getKey(), e.getValue());
            }
            return out;
        });
    }

    CompletableFuture<Void> save(UUID island, Spot s, UUID by) {
        return db.run(c -> {
            Sql.update(c, "DELETE FROM guide_spots WHERE island_id = ?", Sql.toBytes(island));
            Sql.update(c, "INSERT INTO guide_spots (island_id, world, x, y, z, yaw, moved_by, moved_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Sql.toBytes(island), s.world(), s.x(), s.y(), s.z(), s.yaw(), by == null ? null : Sql.toBytes(by),
                    System.currentTimeMillis());
        });
    }

    CompletableFuture<Void> delete(UUID island) {
        return db.run(c -> {
            Sql.update(c, "DELETE FROM guide_spots WHERE island_id = ?", Sql.toBytes(island));
            Sql.update(c, "DELETE FROM guide_done WHERE scope_id = ? AND period = ?", Sql.toBytes(island), STORY);
        });
    }

    static final String STORY = "story";

    /** Done entries of a scope in one period ({@code story} or a rotation number). */
    CompletableFuture<java.util.Set<String>> done(UUID scope, GuideCatalog.Kind kind, String period) {
        return db.async(c -> new java.util.HashSet<>(Sql.query(c, "SELECT entry_id FROM guide_done WHERE scope_id = ? AND kind = ? AND period = ?",
                rs -> rs.getString(1), Sql.toBytes(scope), kind.id(), period)));
    }

    /** Guard SQL: records a hand-in; false when it was recorded already (another click, another member). */
    static boolean record(java.sql.Connection c, UUID scope, GuideCatalog.Kind kind, String period, String entry, UUID by, long now)
            throws java.sql.SQLException {
        boolean exists = Sql.queryOne(c, "SELECT 1 FROM guide_done WHERE scope_id = ? AND kind = ? AND period = ? AND entry_id = ?",
                rs -> 1, Sql.toBytes(scope), kind.id(), period, entry).isPresent();
        if (exists) {
            return false;
        }
        Sql.update(c, "INSERT INTO guide_done (scope_id, kind, period, entry_id, done_by, done_at) VALUES (?, ?, ?, ?, ?, ?)",
                Sql.toBytes(scope), kind.id(), period, entry, Sql.toBytes(by), now);
        return true;
    }

    /** Old rotations are only history: rows older than {@code before} (not story) are deleted. */
    CompletableFuture<Void> purge(long before) {
        return db.run(c -> Sql.update(c, "DELETE FROM guide_done WHERE period <> ? AND done_at < ?", STORY, before));
    }
}

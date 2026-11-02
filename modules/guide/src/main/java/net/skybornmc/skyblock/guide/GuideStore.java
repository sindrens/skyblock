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
        return db.run(c -> Sql.update(c, "DELETE FROM guide_spots WHERE island_id = ?", Sql.toBytes(island)));
    }
}

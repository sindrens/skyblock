package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Database of the island tools and the hoe storage. All async; the guard methods run inside a caller's transaction. */
final class ToolStore {

    /** One tool of one profile. */
    record Rec(UUID profile, Kind kind, int tier, String upgrades, String itemId, int slot, boolean stored) {

        Map<String, Integer> levels() {
            return Upgrades.decode(upgrades);
        }

        int level(String upgrade) {
            return levels().getOrDefault(upgrade, 0);
        }

        Rec with(int newTier, String newUpgrades) {
            return new Rec(profile, kind, newTier, newUpgrades, itemId, slot, stored);
        }

        Rec withItem(String newItem) {
            return new Rec(profile, kind, tier, upgrades, newItem, slot, stored);
        }

        Rec withStored(boolean s, int newSlot) {
            return new Rec(profile, kind, tier, upgrades, itemId, newSlot, s);
        }
    }

    private final Database db;

    ToolStore(Database db) {
        this.db = db;
    }

    CompletableFuture<Map<Kind, Rec>> load(UUID profile) {
        return db.async(c -> {
            Map<Kind, Rec> out = new EnumMap<>(Kind.class);
            for (Rec r : Sql.query(c, "SELECT kind, tier, upgrades, item_id, slot, stored FROM island_tools WHERE profile_key = ?",
                    rs -> {
                        Kind k = Kind.of(rs.getString("kind")).orElse(null);
                        return k == null ? null : new Rec(profile, k, rs.getInt("tier"), rs.getString("upgrades"),
                                rs.getString("item_id"), rs.getInt("slot"), rs.getBoolean("stored"));
                    }, Sql.toBytes(profile))) {
                if (r != null) {
                    out.put(r.kind(), r);
                }
            }
            return out;
        });
    }

    /** Creates the missing tools (first join, D12). Existing rows are never changed. */
    CompletableFuture<Void> createMissing(Map<Kind, Rec> fresh) {
        return db.transaction(c -> {
            long now = System.currentTimeMillis();
            for (Rec r : fresh.values()) {
                boolean exists = Sql.queryOne(c, "SELECT 1 FROM island_tools WHERE profile_key = ? AND kind = ?", rs -> 1,
                        Sql.toBytes(r.profile()), r.kind().id()).isPresent();
                if (!exists) {
                    Sql.update(c, "INSERT INTO island_tools (profile_key, kind, tier, upgrades, item_id, slot, stored, updated_at) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", Sql.toBytes(r.profile()), r.kind().id(), r.tier(), r.upgrades(),
                            r.itemId(), r.slot(), r.stored(), now);
                }
            }
            return null;
        });
    }

    /** Guard: changes tier and upgrades only while they are still what the menu showed (no double purchase). */
    static boolean change(Connection c, Rec before, Rec after) throws SQLException {
        return Sql.update(c, "UPDATE island_tools SET tier = ?, upgrades = ?, updated_at = ? WHERE profile_key = ? AND kind = ? "
                        + "AND tier = ? AND upgrades = ?", after.tier(), after.upgrades(), System.currentTimeMillis(),
                Sql.toBytes(before.profile()), before.kind().id(), before.tier(), before.upgrades()) == 1;
    }

    CompletableFuture<Void> saveItem(Rec r) {
        return db.run(c -> Sql.update(c, "UPDATE island_tools SET item_id = ?, updated_at = ? WHERE profile_key = ? AND kind = ?",
                r.itemId(), System.currentTimeMillis(), Sql.toBytes(r.profile()), r.kind().id()));
    }

    CompletableFuture<Void> saveStored(Rec r) {
        return db.run(c -> Sql.update(c, "UPDATE island_tools SET stored = ?, slot = ?, updated_at = ? WHERE profile_key = ? AND kind = ?",
                r.stored(), r.slot(), System.currentTimeMillis(), Sql.toBytes(r.profile()), r.kind().id()));
    }

    CompletableFuture<Void> reset(UUID profile) {
        return db.run(c -> {
            Sql.update(c, "DELETE FROM island_tools WHERE profile_key = ?", Sql.toBytes(profile));
            Sql.update(c, "DELETE FROM island_tool_storage WHERE profile_key = ?", Sql.toBytes(profile));
        });
    }

    // ------------------------------------------------------------------ hoe storage

    CompletableFuture<Map<String, Long>> storage(UUID profile) {
        return db.async(c -> {
            Map<String, Long> out = new HashMap<>();
            for (Map.Entry<String, Long> e : Sql.query(c, "SELECT item, amount FROM island_tool_storage WHERE profile_key = ? AND amount > 0",
                    rs -> Map.entry(rs.getString(1), rs.getLong(2)), Sql.toBytes(profile))) {
                out.put(e.getKey(), e.getValue());
            }
            return out;
        });
    }

    /** Adds harvested amounts (write-behind of the in-memory storage). */
    CompletableFuture<Void> add(UUID profile, Map<String, Long> deltas) {
        return db.transaction(c -> {
            for (Map.Entry<String, Long> e : deltas.entrySet()) {
                if (e.getValue() <= 0) {
                    continue;
                }
                int n = Sql.update(c, "UPDATE island_tool_storage SET amount = amount + ? WHERE profile_key = ? AND item = ?",
                        e.getValue(), Sql.toBytes(profile), e.getKey());
                if (n == 0) {
                    Sql.update(c, "INSERT INTO island_tool_storage (profile_key, item, amount) VALUES (?, ?, ?)",
                            Sql.toBytes(profile), e.getKey(), e.getValue());
                }
            }
            return null;
        });
    }

    /** Guard: takes {@code amount} of an item out (false when there is less: another withdraw or sale was first). */
    static boolean take(Connection c, UUID profile, String item, long amount) throws SQLException {
        return Sql.update(c, "UPDATE island_tool_storage SET amount = amount - ? WHERE profile_key = ? AND item = ? AND amount >= ?",
                amount, Sql.toBytes(profile), item, amount) == 1;
    }
}

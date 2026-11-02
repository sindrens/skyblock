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

    // ------------------------------------------------------------------ shared island levels (owner 3 Nov 2026)

    /** An island's tier and upgrades of one tool kind. */
    record Levels(int tier, String upgrades) {
    }

    /**
     * Pure: the higher of two level sets (the tier and every upgrade level, never down). {@code a} may be null.
     */
    static Levels max(Levels a, Levels b) {
        if (a == null) {
            return b;
        }
        Map<String, Integer> out = new java.util.TreeMap<>(Upgrades.decode(a.upgrades()));
        Upgrades.decode(b.upgrades()).forEach((k, v) -> out.merge(k, v, Math::max));
        return new Levels(Math.max(a.tier(), b.tier()), Upgrades.encode(out));
    }

    /**
     * Merges this profile's own tools into the island's shared levels (inserts missing rows, raises lower ones) and
     * returns the island's levels and task progress per kind. One transaction.
     */
    CompletableFuture<Map<Kind, Object[]>> mergeIsland(UUID island, Map<Kind, Rec> own) {
        return db.transaction(c -> {
            Map<Kind, Object[]> out = new EnumMap<>(Kind.class);
            long now = System.currentTimeMillis();
            for (Kind kind : Kind.values()) {
                Rec r = own.get(kind);
                java.util.Optional<Levels> stored = Sql.queryOne(c, "SELECT tier, upgrades FROM island_tool_levels WHERE island_id = ? "
                        + "AND kind = ? FOR UPDATE", rs -> new Levels(rs.getInt(1), rs.getString(2)), Sql.toBytes(island), kind.id());
                Levels mine = r == null ? null : new Levels(r.tier(), r.upgrades());
                Levels merged = stored.orElse(null);
                if (mine != null) {
                    merged = max(merged, mine);
                }
                if (merged == null) {
                    continue;
                }
                if (stored.isEmpty()) {
                    Sql.update(c, "INSERT INTO island_tool_levels (island_id, kind, tier, upgrades, updated_at) VALUES (?, ?, ?, ?, ?)",
                            Sql.toBytes(island), kind.id(), merged.tier(), merged.upgrades(), now);
                } else if (!merged.equals(stored.get())) {
                    Sql.update(c, "UPDATE island_tool_levels SET tier = ?, upgrades = ?, updated_at = ? WHERE island_id = ? AND kind = ?",
                            merged.tier(), merged.upgrades(), now, Sql.toBytes(island), kind.id());
                }
                long progress = Sql.queryOne(c, "SELECT progress FROM island_tool_tasks WHERE island_id = ? AND kind = ?",
                        rs -> rs.getLong(1), Sql.toBytes(island), kind.id()).orElse(0L);
                out.put(kind, new Object[]{merged, progress});
            }
            return out;
        });
    }

    /**
     * Guard of a shared purchase: the island's levels are still {@code before} (else another member was faster) and the
     * task has at least {@code task} progress, which is used up. Changes the island row to {@code after}.
     */
    static boolean changeIsland(Connection c, UUID island, Kind kind, Levels before, Levels after, long task) throws SQLException {
        if (Sql.update(c, "UPDATE island_tool_levels SET tier = ?, upgrades = ?, updated_at = ? WHERE island_id = ? AND kind = ? "
                        + "AND tier = ? AND upgrades = ?", after.tier(), after.upgrades(), System.currentTimeMillis(), Sql.toBytes(island),
                kind.id(), before.tier(), before.upgrades()) != 1) {
            return false;
        }
        if (task <= 0) {
            return true;
        }
        return Sql.update(c, "UPDATE island_tool_tasks SET progress = progress - ? WHERE island_id = ? AND kind = ? AND progress >= ?",
                task, Sql.toBytes(island), kind.id(), task) == 1;
    }

    /** Adds task progress (write-behind of the in-memory counters). */
    CompletableFuture<Void> addTasks(Map<UUID, Map<Kind, Long>> deltas) {
        return db.transaction(c -> {
            for (Map.Entry<UUID, Map<Kind, Long>> island : deltas.entrySet()) {
                for (Map.Entry<Kind, Long> e : island.getValue().entrySet()) {
                    if (e.getValue() <= 0) {
                        continue;
                    }
                    if (Sql.update(c, "UPDATE island_tool_tasks SET progress = progress + ? WHERE island_id = ? AND kind = ?",
                            e.getValue(), Sql.toBytes(island.getKey()), e.getKey().id()) == 0) {
                        Sql.update(c, "INSERT INTO island_tool_tasks (island_id, kind, progress) VALUES (?, ?, ?)",
                                Sql.toBytes(island.getKey()), e.getKey().id(), e.getValue());
                    }
                }
            }
            return null;
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

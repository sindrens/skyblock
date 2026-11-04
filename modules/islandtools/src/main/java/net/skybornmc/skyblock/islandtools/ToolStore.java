package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Database of the island tools (owner 4 Nov 2026: the tools belong to the island) and the hoe storage. All async; the
 * guard methods run inside a caller's transaction.
 */
final class ToolStore {

    /** One island tool: XP in this prestige, prestige, perk levels and the purchase guard version. */
    record Prog(long xp, int prestige, String upgrades, long version) {

        static final Prog EMPTY = new Prog(0, 0, "", 0);

        Map<String, Integer> levels() {
            return Upgrades.decode(upgrades);
        }

        int level(String upgrade) {
            return levels().getOrDefault(upgrade, 0);
        }

        Prog withXp(long newXp) {
            return new Prog(newXp, prestige, upgrades, version);
        }

        Prog withUpgrades(String newUpgrades) {
            return new Prog(xp, prestige, newUpgrades, version + 1);
        }

        Prog prestiged() {
            return new Prog(0, prestige + 1, "", version + 1);
        }
    }

    private final Database db;

    ToolStore(Database db) {
        this.db = db;
    }

    /**
     * The island's tools, created on first use. A new row starts from the island's old shared levels (V002
     * {@code island_tool_levels}) through {@code migrate} (kind, old upgrades → new upgrades); the old tier is dropped.
     */
    CompletableFuture<Map<Kind, Prog>> loadIsland(UUID island, BiFunction<Kind, String, String> migrate) {
        return db.transaction(c -> {
            Map<Kind, Prog> out = new EnumMap<>(Kind.class);
            long now = System.currentTimeMillis();
            for (Kind kind : Kind.values()) {
                Optional<Prog> row = Sql.queryOne(c, "SELECT xp, prestige, upgrades, version FROM island_tool_progress WHERE island_id = ? "
                                + "AND kind = ? FOR UPDATE", rs -> new Prog(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getLong(4)),
                        Sql.toBytes(island), kind.id());
                if (row.isPresent()) {
                    out.put(kind, row.get());
                    continue;
                }
                String old = Sql.queryOne(c, "SELECT upgrades FROM island_tool_levels WHERE island_id = ? AND kind = ?",
                        rs -> rs.getString(1), Sql.toBytes(island), kind.id()).orElse("");
                String seeded = old.isEmpty() ? "" : migrate.apply(kind, old);
                Sql.update(c, "INSERT INTO island_tool_progress (island_id, kind, xp, prestige, upgrades, version, updated_at) "
                        + "VALUES (?, ?, 0, 0, ?, 0, ?)", Sql.toBytes(island), kind.id(), seeded, now);
                out.put(kind, new Prog(0, 0, seeded, 0));
            }
            return out;
        });
    }

    /** Adds tool XP (write-behind of the in-memory counters; never touches the version). */
    CompletableFuture<Void> addXp(Map<UUID, Map<Kind, Long>> deltas) {
        return db.transaction(c -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Map<Kind, Long>> island : deltas.entrySet()) {
                for (Map.Entry<Kind, Long> e : island.getValue().entrySet()) {
                    if (e.getValue() > 0) {
                        Sql.update(c, "UPDATE island_tool_progress SET xp = xp + ?, updated_at = ? WHERE island_id = ? AND kind = ?",
                                e.getValue(), now, Sql.toBytes(island.getKey()), e.getKey().id());
                    }
                }
            }
            return null;
        });
    }

    /** Guard of a perk purchase: the tool is still at {@code version} (else another member was faster). */
    static boolean buy(Connection c, UUID island, Kind kind, long version, String upgrades) throws SQLException {
        return Sql.update(c, "UPDATE island_tool_progress SET upgrades = ?, version = version + 1, updated_at = ? WHERE island_id = ? "
                + "AND kind = ? AND version = ?", upgrades, System.currentTimeMillis(), Sql.toBytes(island), kind.id(), version) == 1;
    }

    /**
     * Guard of a prestige: the tool is unchanged since the menu ({@code version}, {@code prestige}) and its XP (plus the
     * {@code pending} XP not written yet) reaches the max level ({@code xpToMax}). Resets XP and perks, prestige + 1.
     */
    static boolean prestige(Connection c, UUID island, Kind kind, Prog before, long pending, long xpToMax) throws SQLException {
        Optional<Prog> now = Sql.queryOne(c, "SELECT xp, prestige, upgrades, version FROM island_tool_progress WHERE island_id = ? AND kind = ? "
                + "FOR UPDATE", rs -> new Prog(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getLong(4)), Sql.toBytes(island), kind.id());
        if (now.isEmpty() || now.get().version() != before.version() || now.get().prestige() != before.prestige()
                || now.get().xp() + Math.max(0, pending) < xpToMax) {
            return false;
        }
        return Sql.update(c, "UPDATE island_tool_progress SET xp = 0, prestige = prestige + 1, upgrades = '', version = version + 1, "
                + "updated_at = ? WHERE island_id = ? AND kind = ? AND version = ?", System.currentTimeMillis(), Sql.toBytes(island),
                kind.id(), before.version()) == 1;
    }

    /** Staff: the island's tools back to level 1, prestige 0, no perks. */
    CompletableFuture<Void> resetIsland(UUID island) {
        return db.run(c -> Sql.update(c, "UPDATE island_tool_progress SET xp = 0, prestige = 0, upgrades = '', version = version + 1, "
                + "updated_at = ? WHERE island_id = ?", System.currentTimeMillis(), Sql.toBytes(island)));
    }

    // ------------------------------------------------------------------ handles given (V001 island_tools: item_id = the island)

    /** The kinds whose handle this profile already got for {@code island} (the first handle comes by itself, once). */
    CompletableFuture<Set<Kind>> given(UUID profile, UUID island) {
        return db.async(c -> {
            Set<Kind> out = EnumSet.noneOf(Kind.class);
            for (String k : Sql.query(c, "SELECT kind FROM island_tools WHERE profile_key = ? AND item_id = ?", rs -> rs.getString(1),
                    Sql.toBytes(profile), island.toString())) {
                Kind.of(k).ifPresent(out::add);
            }
            return out;
        });
    }

    /** Remembers that the profile got the handles of {@code kinds} for {@code island}. */
    CompletableFuture<Void> markGiven(UUID profile, UUID island, Set<Kind> kinds) {
        return db.transaction(c -> {
            long now = System.currentTimeMillis();
            for (Kind kind : kinds) {
                if (Sql.update(c, "UPDATE island_tools SET item_id = ?, tier = 4, updated_at = ? WHERE profile_key = ? AND kind = ?",
                        island.toString(), now, Sql.toBytes(profile), kind.id()) == 0) {
                    Sql.update(c, "INSERT INTO island_tools (profile_key, kind, tier, upgrades, item_id, slot, stored, updated_at) "
                            + "VALUES (?, ?, 4, '', ?, -1, FALSE, ?)", Sql.toBytes(profile), kind.id(), island.toString(), now);
                }
            }
            return null;
        });
    }

    /** Staff reset of a profile: the handle markers and the hoe storage. */
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

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

    /**
     * One island tool: XP in this prestige, prestige, perk levels and the purchase guard version. Owner 6-7 Nov 2026: the
     * module shows a "view" of it (IslandToolsModule#view) with the player's own XP, prestige and {@code rebirth}.
     */
    record Prog(long xp, int prestige, String upgrades, long version, int rebirth) {

        static final Prog EMPTY = new Prog(0, 0, "", 0);

        Prog(long xp, int prestige, String upgrades, long version) {
            this(xp, prestige, upgrades, version, 0);
        }

        Map<String, Integer> levels() {
            return Upgrades.decode(upgrades);
        }

        int level(String upgrade) {
            return levels().getOrDefault(upgrade, 0);
        }

        Prog withXp(long newXp) {
            return new Prog(newXp, prestige, upgrades, version, rebirth);
        }

        Prog withUpgrades(String newUpgrades) {
            return new Prog(xp, prestige, newUpgrades, version + 1, rebirth);
        }

        Prog prestiged() {
            return new Prog(0, prestige + 1, "", version + 1, rebirth);
        }
    }

    /** Owner 6-7 Nov 2026: a player's own tool: XP in this prestige, prestige (0-50), rebirths and the guard version. */
    record Personal(long xp, int prestige, int rebirth, long version) {

        static final Personal EMPTY = new Personal(0, 0, 0, 0);

        Personal withXp(long newXp) {
            return new Personal(newXp, prestige, rebirth, version);
        }

        /** After a prestige ({@code rebirth} false) or a rebirth: XP 0, the next prestige or prestige 0 and one more rebirth. */
        Personal next(boolean rebirth) {
            return rebirth ? new Personal(0, 0, this.rebirth + 1, version + 1) : new Personal(0, prestige + 1, this.rebirth, version + 1);
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

    /**
     * A profile's own tools, created on first use from the island tool of {@code island} (its XP and prestige; null = from
     * nothing), so the move to personal tools keeps every level.
     */
    CompletableFuture<Map<Kind, Personal>> loadPersonal(UUID profile, UUID island) {
        return db.transaction(c -> {
            Map<Kind, Personal> out = new EnumMap<>(Kind.class);
            long now = System.currentTimeMillis();
            for (Kind kind : Kind.values()) {
                Optional<Personal> row = Sql.queryOne(c, "SELECT xp, prestige, rebirth, version FROM island_tool_personal WHERE profile_id = ? "
                        + "AND kind = ?", rs -> new Personal(rs.getLong(1), rs.getInt(2), rs.getInt(3), rs.getLong(4)), profile, kind.id());
                if (row.isPresent()) {
                    out.put(kind, row.get());
                    continue;
                }
                Personal seed = island == null ? Personal.EMPTY : Sql.queryOne(c, "SELECT xp, prestige FROM island_tool_progress WHERE "
                                + "island_id = ? AND kind = ?", rs -> new Personal(rs.getLong(1), rs.getInt(2), 0, 0), Sql.toBytes(island), kind.id())
                        .orElse(Personal.EMPTY);
                Sql.update(c, "INSERT INTO island_tool_personal (profile_id, kind, xp, prestige, rebirth, version, updated_at) "
                        + "VALUES (?, ?, ?, ?, 0, 0, ?)", profile, kind.id(), seed.xp(), seed.prestige(), now);
                out.put(kind, seed);
            }
            return out;
        });
    }

    /** Adds personal tool XP (write-behind; never touches the version). */
    CompletableFuture<Void> addPersonalXp(Map<UUID, Map<Kind, Long>> deltas) {
        return db.transaction(c -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Map<Kind, Long>> profile : deltas.entrySet()) {
                for (Map.Entry<Kind, Long> e : profile.getValue().entrySet()) {
                    if (e.getValue() > 0) {
                        Sql.update(c, "UPDATE island_tool_personal SET xp = xp + ?, updated_at = ? WHERE profile_id = ? AND kind = ?",
                                e.getValue(), now, profile.getKey(), e.getKey().id());
                    }
                }
            }
            return null;
        });
    }

    /**
     * Guard of a personal prestige or rebirth: the tool is still at {@code before.version()} and its XP (plus the
     * {@code pending} XP not written yet) reaches the max level ({@code xpToMax}). Prestige + 1 (or prestige 0 and
     * rebirth + 1), XP 0. Enchants and shards are kept (plans/TOOLS_V3.md section 5).
     */
    static boolean prestigePersonal(Connection c, UUID profile, Kind kind, Personal before, long pending, long xpToMax, boolean rebirth)
            throws SQLException {
        String set = rebirth ? "prestige = 0, rebirth = rebirth + 1" : "prestige = prestige + 1";
        return Sql.update(c, "UPDATE island_tool_personal SET xp = 0, " + set + ", version = version + 1, updated_at = ? WHERE profile_id = ? "
                        + "AND kind = ? AND version = ? AND prestige = ? AND rebirth = ? AND xp + ? >= ?", System.currentTimeMillis(), profile,
                kind.id(), before.version(), before.prestige(), before.rebirth(), pending, xpToMax) == 1;
    }

    /** Staff: a profile's own tools back to level 1, prestige 0, no rebirth. */
    CompletableFuture<Void> resetPersonal(UUID profile) {
        return db.transaction(c -> {
            Sql.update(c, "UPDATE island_tool_personal SET xp = 0, prestige = 0, rebirth = 0, version = version + 1, updated_at = ? "
                    + "WHERE profile_id = ?", System.currentTimeMillis(), profile);
            return null;
        });
    }

    /** A deleted profile's rows go with it. */
    CompletableFuture<Void> deletePersonal(UUID profile) {
        return db.transaction(c -> {
            Sql.update(c, "DELETE FROM island_tool_personal WHERE profile_id = ?", profile);
            return null;
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

    /**
     * Owner 7 Nov 2026 (Top Tools board): players by tool prestiges ({@code kind} or all four), a rebirth counting
     * {@code rebirthWeight}; the best profile of each player, names from {@code players}. Database thread.
     */
    static java.util.List<net.skybornmc.skyblock.common.leaderboard.TopLine> top(Connection c, String kind, int limit, int rebirthWeight,
            java.util.function.Function<Map<UUID, Long>, java.util.List<net.skybornmc.skyblock.common.profile.BestProfile.Entry>> best)
            throws SQLException {
        if (limit <= 0) {
            return java.util.List.of();
        }
        boolean all = kind == null || kind.equals("all");
        String sql = "SELECT profile_id, SUM(prestige + rebirth * ?) AS score FROM island_tool_personal" + (all ? "" : " WHERE kind = ?")
                + " GROUP BY profile_id HAVING SUM(prestige + rebirth * ?) > 0 ORDER BY score DESC LIMIT ?";
        Object[] args = all ? new Object[]{rebirthWeight, rebirthWeight, limit * 4 + 10} : new Object[]{rebirthWeight, kind, rebirthWeight, limit * 4 + 10};
        Map<UUID, Long> values = new java.util.LinkedHashMap<>();
        for (Object[] row : Sql.query(c, sql, rs -> new Object[]{Sql.uuid(rs, "profile_id"), rs.getLong("score")}, args)) {
            values.put((UUID) row[0], (Long) row[1]);
        }
        java.util.List<net.skybornmc.skyblock.common.profile.BestProfile.Entry> ranked = best.apply(values);
        java.util.List<net.skybornmc.skyblock.common.leaderboard.TopLine> out = new java.util.ArrayList<>();
        for (net.skybornmc.skyblock.common.profile.BestProfile.Entry e : ranked.subList(0, Math.min(limit, ranked.size()))) {
            String name = Sql.queryOne(c, "SELECT name FROM players WHERE uuid = ?", rs -> rs.getString(1), e.owner()).orElse(null);
            out.add(new net.skybornmc.skyblock.common.leaderboard.TopLine(e.owner(), name, e.value()));
        }
        return out;
    }
}

package net.skybornmc.skyblock.stacking;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Spawners v2 (owner plan 5 Nov 2026): kills and prestige per island and mob type ({@code stacking_spawner_levels}).
 * Memory is the truth (main thread); every row is loaded at enable and dirty rows are written as absolute values in a
 * batch, so a repeated write is harmless.
 */
final class SpawnerLevels {

    /** One island's mob type: kills in this prestige cycle and the prestige. */
    static final class State {
        long kills;
        int prestige;
    }

    private static final String UPSERT = "INSERT INTO stacking_spawner_levels (island_id, mob, kills, prestige, updated_at) "
            + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE kills = VALUES(kills), prestige = VALUES(prestige), updated_at = VALUES(updated_at)";

    private final Database db;
    private final Logger log;
    private final Map<UUID, Map<String, State>> states = new HashMap<>();
    private final Set<String> dirty = new HashSet<>();

    SpawnerLevels(Database db, Logger log) {
        this.db = db;
        this.log = log;
    }

    /** Enable: every row (blocking, like the spawner blocks). */
    void load() {
        states.clear();
        List<Object[]> rows = db.blocking(c -> Sql.query(c, "SELECT island_id, mob, kills, prestige FROM stacking_spawner_levels",
                rs -> new Object[]{Sql.uuid(rs, "island_id"), rs.getString(2), rs.getLong(3), rs.getInt(4)}));
        for (Object[] r : rows) {
            State s = state((UUID) r[0], (String) r[1]);
            s.kills = (Long) r[2];
            s.prestige = (Integer) r[3];
        }
    }

    State state(UUID island, String mob) {
        return states.computeIfAbsent(island, k -> new HashMap<>()).computeIfAbsent(mob, k -> new State());
    }

    /** Read only: a fresh state when there is none (never stored). */
    State peek(UUID island, String mob) {
        Map<String, State> m = island == null ? null : states.get(island);
        State s = m == null ? null : m.get(mob);
        return s == null ? new State() : s;
    }

    /** Adds kills; returns the state after. */
    State add(UUID island, String mob, long kills) {
        State s = state(island, mob);
        s.kills = Math.max(0, s.kills + kills);
        synchronized (dirty) {
            dirty.add(island + "|" + mob);
        }
        return s;
    }

    /** A prestige: kills back to 0, prestige + 1. */
    void prestige(UUID island, String mob) {
        State s = state(island, mob);
        s.kills = 0;
        s.prestige++;
        synchronized (dirty) {
            dirty.add(island + "|" + mob);
        }
    }

    /** Writes the dirty rows (async). */
    void flush() {
        List<Object[]> rows = rows();
        if (rows.isEmpty()) {
            return;
        }
        db.run(c -> Sql.batch(c, UPSERT, rows)).whenComplete((v, error) -> {
            if (error != null) {
                log.log(Level.WARNING, "Stacking: writing " + rows.size() + " spawner level(s) failed; they are tried again",
                        Database.cause(error));
                synchronized (dirty) {
                    rows.forEach(r -> dirty.add(r[0] + "|" + r[1])); // values are read from memory again next time
                }
            }
        });
    }

    /** Shutdown: writes the dirty rows, blocking. */
    void flushBlocking() {
        List<Object[]> rows = rows();
        if (rows.isEmpty()) {
            return;
        }
        try {
            db.blocking(c -> Sql.batch(c, UPSERT, rows));
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Stacking: spawner levels could not be saved at shutdown", e);
        }
    }

    private List<Object[]> rows() {
        long now = System.currentTimeMillis();
        List<Object[]> rows = new ArrayList<>();
        List<String> keys;
        synchronized (dirty) {
            keys = new ArrayList<>(dirty);
            dirty.clear();
        }
        for (String key : keys) {
            int bar = key.indexOf('|');
            UUID island = UUID.fromString(key.substring(0, bar));
            String mob = key.substring(bar + 1);
            State s = peek(island, mob);
            rows.add(new Object[]{island, mob, s.kills, s.prestige, now});
        }
        return rows;
    }

    /** Island disbanded or reset: its levels go. */
    void clear(UUID island) {
        states.remove(island);
        synchronized (dirty) {
            dirty.removeIf(k -> k.startsWith(island + "|"));
        }
        db.async(c -> Sql.update(c, "DELETE FROM stacking_spawner_levels WHERE island_id = ?", island));
    }
}

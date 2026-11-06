package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tutorial progress per account ({@code db/tutorial/V001__tutorial.sql}). Futures complete on a database thread. Saves of
 * one player run one after another (a newer snapshot is never overwritten by an older one).
 */
final class TutorialStore {

    /** A saved state, also the snapshot a save writes. */
    record Saved(Set<String> done, Map<String, Long> counters, Set<String> chapters, long skippedAt, boolean hidden) {
    }

    /** {@code /tutorial admin stats}: players who started, skipped, and finished each chapter (without skipping). */
    record Stats(long started, long skipped, Map<String, Long> finished) {
    }

    private final Database db;
    private final Map<UUID, CompletableFuture<Void>> chains = new ConcurrentHashMap<>();

    TutorialStore(Database db) {
        this.db = db;
    }

    CompletableFuture<Saved> load(UUID player) {
        return db.async(c -> load(c, player));
    }

    /** Queues a save of {@code s} after the player's earlier saves. */
    CompletableFuture<Void> save(UUID player, Saved s) {
        CompletableFuture<Void> next = chains.compute(player, (k, prev) -> {
            CompletableFuture<Void> base = prev == null ? CompletableFuture.completedFuture(null) : prev.exceptionally(e -> null);
            return base.thenCompose(v -> db.<Void>transaction(c -> {
                save(c, player, s, System.currentTimeMillis());
                return null;
            }));
        });
        next.whenComplete((v, e) -> chains.remove(player, next));
        return next;
    }

    /** Blocking save (module disable: the database closes right after). */
    void saveNow(UUID player, Saved s) {
        db.blockingTransaction(c -> {
            save(c, player, s, System.currentTimeMillis());
            return null;
        });
    }

    /** Records that a chapter's reward is paid; false when it was paid before (never pay twice). */
    CompletableFuture<Boolean> claim(UUID player, String chapter) {
        return db.transaction(c -> claim(c, player, chapter, System.currentTimeMillis()));
    }

    /** Staff reset: progress and finished chapters go; paid rewards stay recorded. */
    CompletableFuture<Void> reset(UUID player) {
        CompletableFuture<Void> next = chains.compute(player, (k, prev) -> {
            CompletableFuture<Void> base = prev == null ? CompletableFuture.completedFuture(null) : prev.exceptionally(e -> null);
            return base.thenCompose(v -> db.<Void>transaction(c -> {
                reset(c, player);
                return null;
            }));
        });
        next.whenComplete((v, e) -> chains.remove(player, next));
        return next;
    }

    CompletableFuture<Stats> stats() {
        return db.async(TutorialStore::stats);
    }

    // ------------------------------------------------------------------ SQL (static for tests)

    static Saved load(Connection c, UUID player) throws SQLException {
        byte[] id = Sql.toBytes(player);
        long[] head = Sql.queryOne(c, "SELECT skipped_at, bar_hidden FROM tutorial_players WHERE player_id = ?",
                rs -> new long[]{rs.getLong(1), rs.getInt(2)}, id).orElse(new long[]{0, 0});
        Set<String> done = new HashSet<>();
        Map<String, Long> counters = new HashMap<>();
        for (Object[] row : Sql.query(c, "SELECT step_key, progress, done_at FROM tutorial_steps WHERE player_id = ?",
                rs -> new Object[]{rs.getString(1), rs.getLong(2), rs.getLong(3)}, id)) {
            if ((long) row[2] > 0) {
                done.add((String) row[0]);
            } else if ((long) row[1] > 0) {
                counters.put((String) row[0], (long) row[1]);
            }
        }
        Set<String> chapters = new HashSet<>(Sql.query(c, "SELECT chapter_id FROM tutorial_chapters WHERE player_id = ?",
                rs -> rs.getString(1), id));
        return new Saved(done, counters, chapters, head[0], head[1] != 0);
    }

    static void save(Connection c, UUID player, Saved s, long now) throws SQLException {
        byte[] id = Sql.toBytes(player);
        int updated = Sql.update(c, "UPDATE tutorial_players SET skipped_at = ?, bar_hidden = ?, updated_at = ? WHERE player_id = ?",
                s.skippedAt(), s.hidden() ? 1 : 0, now, id);
        if (updated == 0) {
            Sql.update(c, "INSERT INTO tutorial_players (player_id, skipped_at, bar_hidden, started_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                    id, s.skippedAt(), s.hidden() ? 1 : 0, now, now);
        }
        Map<String, Long> doneAt = new LinkedHashMap<>();
        for (Object[] row : Sql.query(c, "SELECT step_key, done_at FROM tutorial_steps WHERE player_id = ?",
                rs -> new Object[]{rs.getString(1), rs.getLong(2)}, id)) {
            doneAt.put((String) row[0], (long) row[1]);
        }
        Sql.update(c, "DELETE FROM tutorial_steps WHERE player_id = ?", id);
        for (String key : s.done()) {
            Long at = doneAt.get(key);
            Sql.update(c, "INSERT INTO tutorial_steps (player_id, step_key, progress, done_at) VALUES (?, ?, ?, ?)",
                    id, key, 0L, at != null && at > 0 ? at : now);
        }
        for (Map.Entry<String, Long> e : s.counters().entrySet()) {
            if (!s.done().contains(e.getKey()) && e.getValue() > 0) {
                Sql.update(c, "INSERT INTO tutorial_steps (player_id, step_key, progress, done_at) VALUES (?, ?, ?, ?)",
                        id, e.getKey(), e.getValue(), 0L);
            }
        }
        Set<String> have = new HashSet<>(Sql.query(c, "SELECT chapter_id FROM tutorial_chapters WHERE player_id = ?",
                rs -> rs.getString(1), id));
        for (String chapter : s.chapters()) {
            if (have.add(chapter)) {
                Sql.update(c, "INSERT INTO tutorial_chapters (player_id, chapter_id, done_at, was_skipped) VALUES (?, ?, ?, ?)",
                        id, chapter, now, s.skippedAt() > 0 ? 1 : 0);
            }
        }
    }

    static boolean claim(Connection c, UUID player, String chapter, long now) throws SQLException {
        byte[] id = Sql.toBytes(player);
        if (Sql.queryOne(c, "SELECT 1 FROM tutorial_rewards WHERE player_id = ? AND chapter_id = ?", rs -> 1, id, chapter).isPresent()) {
            return false;
        }
        Sql.update(c, "INSERT INTO tutorial_rewards (player_id, chapter_id, paid_at) VALUES (?, ?, ?)", id, chapter, now);
        return true;
    }

    static void reset(Connection c, UUID player) throws SQLException {
        byte[] id = Sql.toBytes(player);
        Sql.update(c, "DELETE FROM tutorial_steps WHERE player_id = ?", id);
        Sql.update(c, "DELETE FROM tutorial_chapters WHERE player_id = ?", id);
        Sql.update(c, "DELETE FROM tutorial_players WHERE player_id = ?", id);
    }

    static Stats stats(Connection c) throws SQLException {
        long started = Sql.queryOne(c, "SELECT COUNT(*) FROM tutorial_players", rs -> rs.getLong(1)).orElse(0L);
        long skipped = Sql.queryOne(c, "SELECT COUNT(*) FROM tutorial_players WHERE skipped_at > 0", rs -> rs.getLong(1)).orElse(0L);
        Map<String, Long> finished = new LinkedHashMap<>();
        for (Object[] row : Sql.query(c, "SELECT chapter_id, COUNT(*) FROM tutorial_chapters WHERE was_skipped = 0 GROUP BY chapter_id",
                rs -> new Object[]{rs.getString(1), rs.getLong(2)})) {
            finished.put((String) row[0], (long) row[1]);
        }
        return new Stats(started, skipped, finished);
    }
}

package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import net.skybornmc.skyblock.common.wardrobe.PieceEnchants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E5 on H2: an armor enchant apply / remove / shatter on a wardrobe set piece is ONE transaction with the book rows and
 * the crystal payment; a piece that changed meanwhile (or another profile's, P4) refuses everything; undo restores.
 */
class ArmorOpRunnerTest {

    @TempDir
    Path dir;

    private Database db;
    private final BookStore store = new BookStore();
    private OpRunner runner;
    private final UUID player = UUID.randomUUID();
    private final UUID profile = UUID.randomUUID();
    private final UUID entry = UUID.randomUUID();
    private final Set<String> usedKeys = new HashSet<>();

    private static final class Refused extends RuntimeException {
        final TxResult.Status status;

        Refused(TxResult.Status status) {
            super(status.name(), null, false, false);
            this.status = status;
        }
    }

    private CompletableFuture<TxResult> pay(Transaction tx, TxGuard guard) {
        String key = tx.idempotencyKey();
        if (key != null && usedKeys.contains(key)) {
            return CompletableFuture.completedFuture(TxResult.of(TxResult.Status.DUPLICATE));
        }
        return db.transaction(c -> {
            if (!guard.holds(c)) {
                throw new Refused(TxResult.Status.DUPLICATE);
            }
            UUID who = (tx.from() != null ? tx.from() : tx.to()).owner();
            long balance = Sql.queryOne(c, "SELECT balance FROM test_balance WHERE player = ?", rs -> rs.getLong(1), who).orElse(0L);
            long next = tx.from() != null ? balance - tx.amount() : balance + tx.amount();
            if (next < 0) {
                throw new Refused(TxResult.Status.INSUFFICIENT_FUNDS);
            }
            Sql.update(c, "UPDATE test_balance SET balance = ? WHERE player = ?", next, who);
            return new TxResult(TxResult.Status.SUCCESS, 1, tx.from() != null ? next : null, tx.to() != null ? next : null);
        }).thenApply(r -> {
            if (key != null) {
                usedKeys.add(key);
            }
            return r;
        }).exceptionally(e -> Database.cause(e) instanceof Refused r ? TxResult.of(r.status) : TxResult.of(TxResult.Status.ERROR));
    }

    /** Stands in for WardrobeService#lockPieceEnchants / #savePieceEnchants. */
    private final OpRunner.PieceStore pieces = new OpRunner.PieceStore() {
        @Override
        public Optional<List<ToolPerk>> lock(Connection c, UUID who, UUID entryId, ArmorPiece piece) throws SQLException {
            Optional<Object[]> row = Sql.queryOne(c, "SELECT profile_key, active FROM test_entries WHERE entry_id = ? FOR UPDATE",
                    rs -> new Object[]{Sql.uuid(rs, "profile_key"), rs.getBoolean("active")}, entryId);
            if (row.isEmpty() || !(Boolean) row.get()[1] || !who.equals(row.get()[0])) {
                return Optional.empty();
            }
            return Optional.of(Sql.queryOne(c, "SELECT enchants FROM test_pieces WHERE entry_id = ? AND piece = ?",
                    rs -> PieceEnchants.decode(rs.getString(1)), entryId, piece.key()).orElse(List.of()));
        }

        @Override
        public void save(Connection c, UUID entryId, ArmorPiece piece, List<ToolPerk> enchants) throws SQLException {
            Sql.update(c, "DELETE FROM test_pieces WHERE entry_id = ? AND piece = ?", entryId, piece.key());
            Sql.update(c, "INSERT INTO test_pieces (entry_id, piece, enchants) VALUES (?, ?, ?)", entryId, piece.key(), PieceEnchants.encode(enchants));
        }
    };

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "armor-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("armor-test"));
        db.start();
        new Migrations(db, Logger.getLogger("armor-test")).migrate("enchants", getClass().getClassLoader());
        db.blocking(c -> {
            Sql.update(c, "CREATE TABLE test_balance (player BINARY(16) PRIMARY KEY, balance BIGINT NOT NULL)");
            Sql.update(c, "CREATE TABLE test_entries (entry_id BINARY(16) PRIMARY KEY, profile_key BINARY(16) NOT NULL, active BOOLEAN NOT NULL)");
            Sql.update(c, "CREATE TABLE test_pieces (entry_id BINARY(16) NOT NULL, piece VARCHAR(8) NOT NULL, enchants VARCHAR(2048) NOT NULL, "
                    + "PRIMARY KEY (entry_id, piece))");
            Sql.update(c, "INSERT INTO test_balance (player, balance) VALUES (?, ?)", profile, 1_000L);
            Sql.update(c, "INSERT INTO test_entries (entry_id, profile_key, active) VALUES (?, ?, ?)", entry, profile, true);
            return null;
        });
        runner = new OpRunner(db, store, this::pay, (c, t, p) -> {
            throw new SQLException("no tools here");
        }, pieces, () -> 1_000L, Logger.getLogger("armor-test"));
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private UUID book(String enchant, int level) {
        UUID id = UUID.randomUUID();
        db.blocking(c -> {
            store.insert(c, id, false, enchant, level, 0, "test", player, profile, 1L, "test");
            return null;
        });
        return id;
    }

    private long balance() {
        return db.blocking(c -> Sql.queryOne(c, "SELECT balance FROM test_balance WHERE player = ?", rs -> rs.getLong(1), profile)).orElseThrow();
    }

    private List<ToolPerk> chest() {
        return db.blocking(c -> pieces.lock(c, profile, entry, ArmorPiece.CHEST)).orElseThrow();
    }

    private OpPlan apply(UUID who, UUID bookId, List<ToolPerk> before, long fee) {
        List<ToolPerk> after = PieceEnchants.sorted(new java.util.ArrayList<>(before) {{
            add(new ToolPerk(1, "vitality", 2, 1, fee));
        }});
        return new OpPlan(UUID.randomUUID(), "armor_apply", player, who, "Steve", 0, null, before, after,
                List.of(OpPlan.Book.book(bookId, "vitality", 2, null)), List.of(), fee, 0, null, "enchant_apply", Map.of(),
                new OpPlan.Piece(entry, ArmorPiece.CHEST));
    }

    @Test
    void applyIsOneTransactionWithTheBookAndTheCrystals() {
        UUID b = book("vitality", 2);
        OpRunner.Result r = runner.run(apply(profile, b, List.of(), 81)).join();
        assertEquals(OpRunner.Status.OK, r.status());
        assertEquals(919, balance());
        assertEquals(BookStore.State.CONSUMED, db.blocking(c -> store.find(c, b)).orElseThrow().state());
        assertEquals(List.of(new ToolPerk(1, "vitality", 2, 1, 81)), chest());
    }

    @Test
    void notEnoughCrystalsChangesNothing() {
        UUID b = book("vitality", 2);
        assertEquals(OpRunner.Status.NOT_ENOUGH, runner.run(apply(profile, b, List.of(), 5_000)).join().status());
        assertEquals(BookStore.State.ACTIVE, db.blocking(c -> store.find(c, b)).orElseThrow().state());
        assertEquals(List.of(), chest());
        assertEquals(1_000, balance());
    }

    @Test
    void aPieceThatChangedOrBelongsToAnotherProfileRefusesEverything() {
        UUID b = book("vitality", 2);
        OpPlan stale = apply(profile, b, List.of(new ToolPerk(2, "fortitude", 1)), 81);
        assertEquals(OpRunner.Status.PIECE_CHANGED, runner.run(stale).join().status(), "the menu saw other enchants");
        UUID other = UUID.randomUUID();
        db.blocking(c -> Sql.update(c, "INSERT INTO test_balance (player, balance) VALUES (?, ?)", other, 1_000L));
        assertEquals(OpRunner.Status.PIECE_CHANGED, runner.run(apply(other, b, List.of(), 81)).join().status(), "P4");
        db.blocking(c -> Sql.update(c, "UPDATE test_entries SET active = FALSE WHERE entry_id = ?", entry));
        assertEquals(OpRunner.Status.PIECE_CHANGED, runner.run(apply(profile, b, List.of(), 81)).join().status(), "merged away");
        assertEquals(BookStore.State.ACTIVE, db.blocking(c -> store.find(c, b)).orElseThrow().state());
        assertEquals(1_000, balance());
    }

    @Test
    void removeWithAScrollGivesTheBookAndShatterRefunds() {
        UUID b = book("vitality", 2);
        assertTrue(runner.run(apply(profile, b, List.of(), 81)).join().ok());
        UUID scroll = UUID.randomUUID();
        db.blocking(c -> {
            store.insert(c, scroll, true, null, 0, 0, "shop", player, profile, 1L, "test");
            return null;
        });
        UUID back = UUID.randomUUID();
        List<ToolPerk> before = chest();
        OpPlan remove = new OpPlan(UUID.randomUUID(), "armor_remove", player, profile, "Steve", 0, null, before, List.of(),
                List.of(OpPlan.Book.scroll(scroll, null)), List.of(OpPlan.Book.book(back, "vitality", 2, "disenchant")), 0, 0, null,
                "enchant_remove", Map.of(), new OpPlan.Piece(entry, ArmorPiece.CHEST));
        assertEquals(OpRunner.Status.OK, runner.run(remove).join().status());
        assertEquals(List.of(), chest());
        assertEquals(BookStore.State.ACTIVE, db.blocking(c -> store.find(c, back)).orElseThrow().state());

        UUID b2 = book("vitality", 2);
        assertTrue(runner.run(apply(profile, b2, List.of(), 81)).join().ok());
        OpPlan shatter = new OpPlan(UUID.randomUUID(), "armor_shatter", player, profile, "Steve", 0, null, chest(), List.of(), List.of(),
                List.of(), 0, 16, null, "enchant_shatter", Map.of(), new OpPlan.Piece(entry, ArmorPiece.CHEST));
        long beforeBalance = balance();
        assertEquals(OpRunner.Status.OK, runner.run(shatter).join().status());
        assertEquals(beforeBalance + 16, balance());
        assertEquals(List.of(), chest());
    }

    @Test
    void undoRestoresThePieceOnlyIfItStillHoldsTheResult() {
        UUID b = book("vitality", 2);
        OpPlan plan = apply(profile, b, List.of(), 81);
        assertTrue(runner.run(plan).join().ok());
        assertTrue(runner.undo(plan).join());
        assertEquals(List.of(), chest());
        assertEquals(1_000, balance());
        assertEquals(BookStore.State.ACTIVE, db.blocking(c -> store.find(c, b)).orElseThrow().state());

        UUID b2 = book("vitality", 2);
        OpPlan second = apply(profile, b2, List.of(), 81);
        assertTrue(runner.run(second).join().ok());
        db.blocking(c -> {
            pieces.save(c, entry, ArmorPiece.CHEST, List.of(new ToolPerk(1, "vitality", 3)));
            return null;
        });
        assertFalse(runner.undo(second).join(), "the piece changed after: no book or crystals come back (no dupe)");
        assertEquals(BookStore.State.CONSUMED, db.blocking(c -> store.find(c, b2)).orElseThrow().state());
    }
}

package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Atomic Enchanter operations on H2 in MySQL mode (ENCHANTS §4.2 step 5, §9, §13.3): books consumed + daily roll + perks
 * saved + crystals paid commit together; any failure rolls everything back; copies are refused; undo restores.
 */
class OpRunnerTest {

    @TempDir
    Path dir;

    private Database db;
    private final BookStore store = new BookStore();
    private OpRunner runner;
    private final UUID player = UUID.randomUUID();
    private final UUID tool = UUID.randomUUID();
    private final Set<String> usedKeys = new HashSet<>();

    /** Balance table + guarded payment in one transaction, like EconomyService#executeGuarded. */
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

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "enchants-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("enchants-test"));
        db.start();
        new Migrations(db, Logger.getLogger("enchants-test")).migrate("enchants", getClass().getClassLoader());
        db.blocking(c -> {
            Sql.update(c, "CREATE TABLE test_balance (player BINARY(16) PRIMARY KEY, balance BIGINT NOT NULL)");
            Sql.update(c, "CREATE TABLE test_perks (tool BINARY(16) PRIMARY KEY, perks VARCHAR(512) NOT NULL)");
            Sql.update(c, "INSERT INTO test_balance (player, balance) VALUES (?, ?)", player, 1_000L);
            Sql.update(c, "INSERT INTO test_perks (tool, perks) VALUES (?, ?)", tool, "[]");
            return null;
        });
        runner = new OpRunner(db, store, this::pay,
                (c, toolId, perks) -> Sql.update(c, "UPDATE test_perks SET perks = ? WHERE tool = ?", perks.toString(), toolId),
                () -> 1_000L, Logger.getLogger("enchants-test"));
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private UUID book(String enchant, int level) {
        UUID id = UUID.randomUUID();
        db.blocking(c -> {
            store.insert(c, id, false, enchant, level, 0, "test", player, 1L, "test");
            return null;
        });
        return id;
    }

    private BookStore.State state(UUID id) {
        return db.blocking(c -> store.find(c, id)).orElseThrow().state();
    }

    private long balance() {
        return db.blocking(c -> Sql.queryOne(c, "SELECT balance FROM test_balance WHERE player = ?", rs -> rs.getLong(1), player)).orElseThrow();
    }

    private String perks() {
        return db.blocking(c -> Sql.queryOne(c, "SELECT perks FROM test_perks WHERE tool = ?", rs -> rs.getString(1), tool)).orElseThrow();
    }

    private OpPlan applyPlan(UUID bookId, int level, long cost) {
        List<ToolPerk> after = List.of(new ToolPerk(1, "vein_rend", level, 1, cost));
        return new OpPlan(UUID.randomUUID(), "apply", player, "Steve", 0, tool, List.of(), after,
                List.of(OpPlan.Book.book(bookId, "vein_rend", level, null)), List.of(), cost, 0, null, "enchant_apply", Map.of());
    }

    @Test
    void applyConsumesTheBookSavesPerksAndPays() {
        UUID b = book("vein_rend", 2);
        OpRunner.Result r = runner.run(applyPlan(b, 2, 709)).join();
        assertEquals(OpRunner.Status.OK, r.status());
        assertEquals(291L, r.balance());
        assertEquals(BookStore.State.CONSUMED, state(b));
        assertEquals(291, balance());
        assertTrue(perks().contains("vein_rend"));
        assertEquals(Set.of("vein_rend"), db.blocking(c -> store.seen(c, player, 0)), "the collection learns it");
    }

    @Test
    void notEnoughCrystalsRollsEverythingBack() {
        UUID b = book("vein_rend", 3);
        OpRunner.Result r = runner.run(applyPlan(b, 3, 7_439)).join();
        assertEquals(OpRunner.Status.NOT_ENOUGH, r.status());
        assertEquals(BookStore.State.ACTIVE, state(b), "book not consumed");
        assertEquals(1_000, balance());
        assertEquals("[]", perks(), "perks not saved");
    }

    @Test
    void aCopyOfAUsedBookIsRefused() {
        UUID b = book("vein_rend", 1);
        assertEquals(OpRunner.Status.OK, runner.run(applyPlan(b, 1, 150)).join().status());
        OpRunner.Result second = runner.run(applyPlan(b, 1, 150)).join();
        assertEquals(OpRunner.Status.BOOK_INVALID, second.status());
        assertEquals(b, second.badBook());
        assertEquals(850, balance(), "the copy cost nothing");
    }

    @Test
    void unknownOrTamperedBooksAreRefused() {
        assertEquals(OpRunner.Status.BOOK_INVALID, runner.run(applyPlan(UUID.randomUUID(), 1, 150)).join().status(), "no row");
        UUID b = book("vein_rend", 1);
        assertEquals(OpRunner.Status.BOOK_INVALID, runner.run(applyPlan(b, 3, 150)).join().status(), "item says III, row says I");
        assertEquals(BookStore.State.ACTIVE, state(b));
    }

    @Test
    void combineMintsANewBookAndConsumesBoth() {
        UUID a = book("slaughter", 4);
        UUID c = book("slaughter", 4);
        UUID made = UUID.randomUUID();
        OpPlan plan = new OpPlan(UUID.randomUUID(), "combine", player, "Steve", 0, null, null, null,
                List.of(OpPlan.Book.book(a, "slaughter", 4, null), OpPlan.Book.book(c, "slaughter", 4, null)),
                List.of(OpPlan.Book.book(made, "slaughter", 5, "combine")), 263, 0, null, "enchant_combine", Map.of());
        assertEquals(OpRunner.Status.OK, runner.run(plan).join().status());
        assertEquals(BookStore.State.CONSUMED, state(a));
        assertEquals(BookStore.State.CONSUMED, state(c));
        BookStore.Row row = db.blocking(x -> store.find(x, made)).orElseThrow();
        assertEquals(BookStore.State.ACTIVE, row.state());
        assertEquals(5, row.level());
        assertEquals(737, balance());
    }

    @Test
    void sameBookTwiceInOnePlanIsRefused() {
        UUID a = book("slaughter", 4);
        OpPlan plan = new OpPlan(UUID.randomUUID(), "combine", player, "Steve", 0, null, null, null,
                List.of(OpPlan.Book.book(a, "slaughter", 4, null), OpPlan.Book.book(a, "slaughter", 4, null)),
                List.of(OpPlan.Book.book(UUID.randomUUID(), "slaughter", 5, "combine")), 263, 0, null, "enchant_combine", Map.of());
        assertEquals(OpRunner.Status.BOOK_INVALID, runner.run(plan).join().status());
        assertEquals(BookStore.State.ACTIVE, state(a));
        assertEquals(1_000, balance());
    }

    @Test
    void dailyRollLimit() {
        for (int i = 0; i < 2; i++) {
            OpPlan roll = new OpPlan(UUID.randomUUID(), "roll", player, "Steve", 0, null, null, null, List.of(),
                    List.of(OpPlan.Book.book(UUID.randomUUID(), "swiftness", 1, "roll")), 100, 0, new OpPlan.Daily("2026-10-30", "apprentice", 2),
                    "enchant_roll", Map.of());
            assertEquals(OpRunner.Status.OK, runner.run(roll).join().status());
        }
        UUID third = UUID.randomUUID();
        OpPlan roll = new OpPlan(UUID.randomUUID(), "roll", player, "Steve", 0, null, null, null, List.of(),
                List.of(OpPlan.Book.book(third, "swiftness", 1, "roll")), 100, 0, new OpPlan.Daily("2026-10-30", "apprentice", 2),
                "enchant_roll", Map.of());
        assertEquals(OpRunner.Status.LIMIT, runner.run(roll).join().status());
        assertTrue(db.blocking(c -> store.find(c, third)).isEmpty(), "no row for the refused roll");
        assertEquals(800, balance());
        assertEquals(Map.of("apprentice", 2), db.blocking(c -> store.daily(c, player, "2026-10-30")));
    }

    @Test
    void removeWithAScrollNeedsNoCrystals() {
        UUID scroll = UUID.randomUUID();
        db.blocking(c -> {
            store.insert(c, scroll, true, null, 0, 0, "shop", player, 1L, "test");
            return null;
        });
        UUID back = UUID.randomUUID();
        OpPlan plan = new OpPlan(UUID.randomUUID(), "remove", player, "Steve", 0, tool, List.of(new ToolPerk(1, "vein_rend", 2)), List.of(),
                List.of(OpPlan.Book.scroll(scroll, null)), List.of(OpPlan.Book.book(back, "vein_rend", 2, "disenchant")), 0, 0, null,
                "enchant_remove", Map.of());
        assertEquals(OpRunner.Status.OK, runner.run(plan).join().status());
        assertEquals(BookStore.State.CONSUMED, state(scroll));
        assertEquals(BookStore.State.ACTIVE, state(back));
        assertEquals("[]", perks());
        assertEquals(1_000, balance());
        OpPlan again = new OpPlan(UUID.randomUUID(), "remove", player, "Steve", 0, tool, List.of(), List.of(),
                List.of(OpPlan.Book.scroll(scroll, null)), List.of(OpPlan.Book.book(UUID.randomUUID(), "vein_rend", 2, "disenchant")), 0, 0,
                null, "enchant_remove", Map.of());
        assertEquals(OpRunner.Status.BOOK_INVALID, runner.run(again).join().status(), "a used scroll can't work twice");
    }

    @Test
    void shatterRefundsCrystals() {
        OpPlan plan = new OpPlan(UUID.randomUUID(), "shatter", player, "Steve", 0, tool, List.of(new ToolPerk(1, "vein_rend", 2, 1, 709)),
                List.of(), List.of(), List.of(), 0, 141, null, "enchant_shatter", Map.of());
        OpRunner.Result r = runner.run(plan).join();
        assertEquals(OpRunner.Status.OK, r.status());
        assertEquals(1_141, balance());
    }

    @Test
    void undoRestoresEverything() {
        UUID b = book("vein_rend", 2);
        OpPlan plan = applyPlan(b, 2, 709);
        assertEquals(OpRunner.Status.OK, runner.run(plan).join().status());
        assertTrue(runner.undo(plan).join());
        assertEquals(BookStore.State.ACTIVE, state(b), "the book works again");
        assertEquals(1_000, balance(), "crystals back");
        assertEquals("[]", perks(), "old perks saved");
        assertFalse(runner.undo(plan).join(), "a second undo is refused (idempotency key)");
        assertEquals(1_000, balance());
    }

    @Test
    void undoOfARollVoidsTheBookAndGivesTheRollBack() {
        UUID made = UUID.randomUUID();
        OpPlan roll = new OpPlan(UUID.randomUUID(), "roll", player, "Steve", 0, null, null, null, List.of(),
                List.of(OpPlan.Book.book(made, "swiftness", 1, "roll")), 500, 0, new OpPlan.Daily("2026-10-30", "apprentice", 5),
                "enchant_roll", Map.of());
        assertEquals(OpRunner.Status.OK, runner.run(roll).join().status());
        assertTrue(runner.undo(roll).join());
        assertEquals(BookStore.State.VOID, state(made));
        assertEquals(1_000, balance());
        assertEquals(Map.of("apprentice", 0), db.blocking(c -> store.daily(c, player, "2026-10-30")));
    }
}

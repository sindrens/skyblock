package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
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
 * Every wardrobe data change on H2 in MySQL mode: purchases commit with their payment or not at all (one copy per set per
 * profile, idempotency), Sigils and claims (WD5), tokens, escrow of the Wardrobe Chest with crash recovery (§4.4), worn
 * rows, and the P4 rule (nothing reaches another profile of the same player).
 */
class WardrobeOpsTest {

    @TempDir
    Path dir;

    private Database db;
    private final WardrobeStore store = new WardrobeStore();
    private WardrobeOps ops;
    private final Set<String> usedKeys = new HashSet<>();
    private final UUID player = UUID.randomUUID();
    private final UUID profile1 = player;              // profile 1 uses the player UUID (PROF)
    private final UUID profile2 = UUID.randomUUID();
    private final WardrobeOps.Who who1 = new WardrobeOps.Who(player, "Steve", profile1);
    private final WardrobeOps.Who who2 = new WardrobeOps.Who(player, "Steve", profile2);

    private static final class Refused extends RuntimeException {
        final TxResult.Status status;

        Refused(TxResult.Status status) {
            super(status.name(), null, false, false);
            this.status = status;
        }
    }

    /** Balance table + guarded payment in one transaction, like EconomyService#executeGuarded (gems go to the owner). */
    private synchronized CompletableFuture<TxResult> pay(Transaction tx, TxGuard guard) {
        String key = tx.idempotencyKey();
        if (key != null && usedKeys.contains(key)) {
            return CompletableFuture.completedFuture(TxResult.of(TxResult.Status.DUPLICATE));
        }
        return db.transaction(c -> {
            if (!guard.holds(c)) {
                throw new Refused(TxResult.Status.DUPLICATE);
            }
            UUID who = tx.currency() == Currency.GEMS ? player : tx.from().owner();
            String cur = tx.currency().key();
            long balance = Sql.queryOne(c, "SELECT balance FROM test_balance WHERE owner = ? AND cur = ? FOR UPDATE", rs -> rs.getLong(1), who, cur)
                    .orElse(0L);
            long next = balance - tx.amount();
            if (next < 0) {
                throw new Refused(TxResult.Status.INSUFFICIENT_FUNDS);
            }
            Sql.update(c, "UPDATE test_balance SET balance = ? WHERE owner = ? AND cur = ?", next, who, cur);
            return new TxResult(TxResult.Status.SUCCESS, 1, next, null);
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
        config.h2File = "wardrobe-test";
        config.poolSize = 4;
        db = new Database(config, dir.toFile(), Logger.getLogger("wardrobe-test"));
        db.start();
        new Migrations(db, Logger.getLogger("wardrobe-test")).migrate("wardrobe", getClass().getClassLoader());
        db.blocking(c -> {
            Sql.update(c, "CREATE TABLE test_balance (owner BINARY(16) NOT NULL, cur VARCHAR(16) NOT NULL, balance BIGINT NOT NULL, PRIMARY KEY (owner, cur))");
            for (UUID p : List.of(profile1, profile2)) {
                Sql.update(c, "INSERT INTO test_balance (owner, cur, balance) VALUES (?, ?, ?)", p, "money", 50_000L);
                Sql.update(c, "INSERT INTO test_balance (owner, cur, balance) VALUES (?, ?, ?)", p, "crystals", 30_000L);
            }
            Sql.update(c, "INSERT INTO test_balance (owner, cur, balance) VALUES (?, ?, ?)", player, "gems", 1_000L);
            return null;
        });
        ops = new WardrobeOps(db, store, this::pay, System::currentTimeMillis, Logger.getLogger("wardrobe-test"));
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private long balance(UUID owner, String cur) {
        return db.blocking(c -> Sql.queryOne(c, "SELECT balance FROM test_balance WHERE owner = ? AND cur = ?", rs -> rs.getLong(1), owner, cur))
                .orElseThrow();
    }

    private List<WardrobeStore.Entry> entries(UUID profile) {
        return db.blocking(c -> store.activeEntries(c, profile));
    }

    // ------------------------------------------------------------------ buying

    @Test
    void buyWithMoneyAddsTheSetAndPaysOnce() {
        WardrobeOps.Result r = ops.buy(who1, Fixtures.def("delver"), 0).join();
        assertEquals(WardrobeOps.Status.OK, r.status());
        assertEquals(30_000, balance(profile1, "money"));
        assertEquals(30_000L, r.balance());
        assertEquals(List.of("delver"), entries(profile1).stream().map(WardrobeStore.Entry::setId).toList());
        assertEquals("money", entries(profile1).getFirst().origin());

        WardrobeOps.Result again = ops.buy(who1, Fixtures.def("delver"), 0).join();
        assertEquals(WardrobeOps.Status.OWNED, again.status(), "one copy per set per profile (WD6)");
        assertEquals(30_000, balance(profile1, "money"), "nothing paid for a refused purchase");
        assertEquals(1, entries(profile1).size());
    }

    @Test
    void notEnoughMoneyChangesNothing() {
        WardrobeOps.Result r = ops.buy(who1, Fixtures.def("coinmaster"), 0).join();
        assertEquals(WardrobeOps.Status.NOT_ENOUGH, r.status());
        assertEquals(50_000, balance(profile1, "money"));
        assertTrue(entries(profile1).isEmpty(), "the row rolled back with the failed payment");
    }

    @Test
    void doubleClickBuysAndChargesOnlyOnce() {
        List<CompletableFuture<WardrobeOps.Result>> all = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            all.add(ops.buy(who1, Fixtures.def("deepforge"), 0));
        }
        long ok = all.stream().map(CompletableFuture::join).filter(WardrobeOps.Result::ok).count();
        assertEquals(1, ok);
        assertEquals(5_000, balance(profile1, "crystals"));
        assertEquals(1, entries(profile1).size());
    }

    @Test
    void idempotencyKeyIsPerPurchase() {
        WardrobeOps.Result r = ops.buy(who1, Fixtures.def("tiller"), 0).join();
        assertTrue(r.ok());
        assertTrue(usedKeys.contains(WardrobeOps.REASON_BUY + ":" + r.entry().entryId()));
    }

    @Test
    void profilesOwnSetsSeparately() {
        assertTrue(ops.buy(who1, Fixtures.def("delver"), 0).join().ok());
        assertTrue(ops.buy(who2, Fixtures.def("delver"), 0).join().ok(), "the other profile buys its own copy (P2)");
        assertEquals(30_000, balance(profile1, "money"));
        assertEquals(30_000, balance(profile2, "money"));
        assertEquals(1, entries(profile2).size());
    }

    // ------------------------------------------------------------------ gems, Sigils, claims (WD5)

    @Test
    void gemPurchaseBuysASigilAndEachProfileClaimsOncePerSeason() {
        WardrobeOps.Result r = ops.buy(who1, Fixtures.def("lorekeeper"), 0).join();
        assertEquals(WardrobeOps.Status.OK, r.status());
        assertEquals(600, balance(player, "gems"));
        assertEquals(Set.of("lorekeeper"), db.blocking(c -> store.sigils(c, player)));
        assertEquals(Set.of("lorekeeper"), db.blocking(c -> store.claims(c, profile1, 0)), "the buying profile used its claim");

        assertEquals(WardrobeOps.Status.HAS_SIGIL, ops.buy(who2, Fixtures.def("lorekeeper"), 0).join().status(), "never pay twice");
        assertEquals(600, balance(player, "gems"));

        assertEquals(WardrobeOps.Status.OK, ops.claim(who2, "lorekeeper", 0).join().status());
        assertEquals(WardrobeOps.Status.CLAIMED, ops.claim(who2, "lorekeeper", 0).join().status());
        assertEquals(WardrobeOps.Status.CLAIMED, ops.claim(who1, "lorekeeper", 0).join().status());
        assertEquals(1, entries(profile2).size());
        assertEquals("gems_claim", entries(profile2).getFirst().origin());
        assertEquals(WardrobeOps.Status.OWNED, ops.claim(who1, "lorekeeper", 1).join().status(), "next season: still owned");
        assertTrue(db.blocking(c -> store.claims(c, profile1, 1)).isEmpty(), "the claim rolled back with it");
    }

    @Test
    void claimNeedsASigil() {
        assertEquals(WardrobeOps.Status.NO_SIGIL, ops.claim(who1, "thornward", 0).join().status());
        assertTrue(entries(profile1).isEmpty());
    }

    // ------------------------------------------------------------------ tokens

    @Test
    void tokenRedeemsOnceAndCopiesAreRefused() {
        UUID token = UUID.randomUUID();
        assertTrue(ops.mintToken(token, "phoenix", "crate:eclipse", player).join());
        assertEquals(WardrobeOps.Status.TOKEN_INVALID, ops.redeem(who1, token, "voidforged", 0).join().status(), "the row says another set");
        assertEquals(WardrobeOps.Status.OK, ops.redeem(who1, token, "phoenix", 0).join().status());
        assertEquals(WardrobeOps.Status.TOKEN_INVALID, ops.redeem(who2, token, "phoenix", 0).join().status(), "a copy of a redeemed token");
        assertEquals(WardrobeStore.TokenState.REDEEMED, db.blocking(c -> store.token(c, token, false)).orElseThrow().state());
        assertEquals("token", entries(profile1).getFirst().origin());
        assertEquals(WardrobeOps.Status.TOKEN_INVALID, ops.redeem(who1, UUID.randomUUID(), "phoenix", 0).join().status(), "unknown token");
    }

    @Test
    void redeemingAnOwnedSetKeepsTheTokenActive() {
        UUID token = UUID.randomUUID();
        ops.mintToken(token, "delver", "test", player).join();
        assertTrue(ops.buy(who1, Fixtures.def("delver"), 0).join().ok());
        assertEquals(WardrobeOps.Status.OWNED, ops.redeem(who1, token, "delver", 0).join().status());
        assertEquals(WardrobeStore.TokenState.ACTIVE, db.blocking(c -> store.token(c, token, false)).orElseThrow().state(), "rolled back");
        assertTrue(ops.voidToken(token, "test").join());
        assertEquals(WardrobeStore.TokenState.VOID, db.blocking(c -> store.token(c, token, false)).orElseThrow().state());
    }

    // ------------------------------------------------------------------ staff

    @Test
    void staffGiveAndRemove() {
        UUID staff = UUID.randomUUID();
        WardrobeOps.Result given = ops.giveSet(profile1, "dreadguard", staff, 0).join();
        assertTrue(given.ok());
        assertEquals(WardrobeOps.Status.OWNED, ops.giveSet(profile1, "dreadguard", staff, 0).join().status());
        UUID entry = given.entry().entryId();
        assertTrue(ops.equip(profile1, List.of(new WardrobeStore.Equipped(ArmorPiece.HEAD, entry, UUID.randomUUID())), List.of()).join().ok());
        assertTrue(ops.removeSet(entry, staff).join().ok());
        assertEquals(WardrobeOps.Status.GONE, ops.removeSet(entry, staff).join().status());
        assertTrue(entries(profile1).isEmpty());
        assertTrue(db.blocking(c -> store.equipped(c, profile1)).isEmpty(), "worn pieces of a removed set come off");
        assertTrue(ops.giveSet(profile1, "dreadguard", staff, 0).join().ok(), "removed sets free the slot for a new copy");
    }

    // ------------------------------------------------------------------ worn rows

    @Test
    void equipWritesNoncesAndOnlyForTheProfilesOwnSets() {
        WardrobeStore.Entry mine = ops.buy(who1, Fixtures.def("delver"), 0).join().entry();
        WardrobeStore.Entry other = ops.buy(who2, Fixtures.def("footman"), 0).join().entry();
        UUID n1 = UUID.randomUUID();
        assertTrue(ops.equip(profile1, List.of(new WardrobeStore.Equipped(ArmorPiece.CHEST, mine.entryId(), n1)), List.of()).join().ok());
        Map<ArmorPiece, WardrobeStore.Equipped> rows = db.blocking(c -> store.equipped(c, profile1));
        assertEquals(n1, rows.get(ArmorPiece.CHEST).nonce());

        assertEquals(WardrobeOps.Status.GONE, ops.equip(profile1, List.of(new WardrobeStore.Equipped(ArmorPiece.HEAD, other.entryId(),
                UUID.randomUUID())), List.of()).join().status(), "a set of another own profile can't be worn (P4)");
        assertFalse(db.blocking(c -> store.equipped(c, profile1)).containsKey(ArmorPiece.HEAD));

        UUID n2 = UUID.randomUUID();
        assertTrue(ops.equip(profile1, List.of(new WardrobeStore.Equipped(ArmorPiece.CHEST, mine.entryId(), n2)), List.of()).join().ok());
        assertEquals(n2, db.blocking(c -> store.equipped(c, profile1)).get(ArmorPiece.CHEST).nonce(), "re-equip changes the nonce");
        assertTrue(ops.equip(profile1, List.of(), List.of(ArmorPiece.CHEST)).join().ok());
        assertTrue(db.blocking(c -> store.equipped(c, profile1)).isEmpty());
    }

    // ------------------------------------------------------------------ Wardrobe Chest (escrow)

    private WardrobeStore.Stored item(UUID id, UUID profile) {
        return new WardrobeStore.Stored(id, profile, "tool", "tool_iron_pickaxe", "Iron Pickaxe", new byte[]{1, 2, 3},
                WardrobeStore.StorageState.STORED, null, 1L);
    }

    @Test
    void storeTakeAndStoreAgain() {
        UUID id = UUID.randomUUID();
        assertEquals(WardrobeOps.Status.OK, ops.store(item(id, profile1), 27).join().status());
        assertEquals(WardrobeOps.Status.STALE, ops.store(item(id, profile1), 27).join().status(), "a second copy of a stored item");
        assertEquals(1, db.blocking(c -> store.stored(c, profile1)).size());

        UUID plan = UUID.randomUUID();
        WardrobeOps.Result take = ops.take(id, profile1, plan).join();
        assertTrue(take.ok());
        assertEquals(WardrobeStore.StorageState.RELEASING, take.stored().state());
        assertEquals(WardrobeOps.Status.GONE, ops.take(id, profile1, UUID.randomUUID()).join().status(), "can't be taken twice");
        assertEquals(WardrobeOps.Status.STALE, ops.store(item(id, profile1), 27).join().status(), "still releasing: not yet storable");
        assertTrue(ops.released(id, plan).join());
        assertFalse(ops.released(id, plan).join());
        assertEquals(WardrobeOps.Status.OK, ops.store(item(id, profile1), 27).join().status(), "a released item can be stored again");
    }

    @Test
    void chestIsFullAtCapacity() {
        assertTrue(ops.store(item(UUID.randomUUID(), profile1), 2).join().ok());
        assertTrue(ops.store(item(UUID.randomUUID(), profile1), 2).join().ok());
        assertEquals(WardrobeOps.Status.FULL, ops.store(item(UUID.randomUUID(), profile1), 2).join().status());
        assertTrue(ops.store(item(UUID.randomUUID(), profile2), 2).join().ok(), "capacity is per profile");
    }

    @Test
    void anotherProfileCantTakeItems() {
        UUID id = UUID.randomUUID();
        assertTrue(ops.store(item(id, profile1), 27).join().ok());
        assertEquals(WardrobeOps.Status.GONE, ops.take(id, profile2, UUID.randomUUID()).join().status(), "P4: no moves between own profiles");
        assertTrue(db.blocking(c -> store.stored(c, profile2)).isEmpty());
    }

    @Test
    void crashRecovery() {
        UUID handedOut = UUID.randomUUID();
        UUID stored = UUID.randomUUID();
        UUID plan = UUID.randomUUID();
        assertTrue(ops.store(item(handedOut, profile1), 27).join().ok());
        assertTrue(ops.store(item(stored, profile1), 27).join().ok());
        assertTrue(ops.take(handedOut, profile1, plan).join().ok());
        // Crash: the item never reached the player. The player still has a stale copy of the stored one.
        WardrobeOps.Recovery rec = ops.recovery(profile1, List.of(stored, UUID.randomUUID())).join();
        assertEquals(List.of(handedOut), rec.releasing().stream().map(WardrobeStore.Stored::itemUuid).toList());
        assertEquals(plan, rec.releasing().getFirst().planId());
        assertEquals(Set.of(stored), rec.stale(), "the stored copy wins");
        assertTrue(ops.backToStored(handedOut, plan).join(), "no room: back in the chest");
        assertEquals(2, db.blocking(c -> store.stored(c, profile1)).size());
        assertTrue(ops.recovery(profile1, List.of()).join().releasing().isEmpty());
    }
}

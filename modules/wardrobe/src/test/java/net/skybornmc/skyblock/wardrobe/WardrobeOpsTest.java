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

    /** Balance table + guarded payment in one transaction, like EconomyService#executeGuarded (credits go to the owner). */
    private synchronized CompletableFuture<TxResult> pay(Transaction tx, TxGuard guard) {
        String key = tx.idempotencyKey();
        if (key != null && usedKeys.contains(key)) {
            return CompletableFuture.completedFuture(TxResult.of(TxResult.Status.DUPLICATE));
        }
        return db.transaction(c -> {
            if (!guard.holds(c)) {
                throw new Refused(TxResult.Status.DUPLICATE);
            }
            UUID who = tx.currency().isCredits() ? player : tx.from().owner();
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
            Sql.update(c, "INSERT INTO test_balance (owner, cur, balance) VALUES (?, ?, ?)", player, "credits", 1_000L);
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
    void creditPurchaseBuysASigilAndEachProfileClaimsOncePerSeason() {
        WardrobeOps.Result r = ops.buy(who1, Fixtures.def("lorekeeper"), 0).join();
        assertEquals(WardrobeOps.Status.OK, r.status());
        assertEquals(600, balance(player, "credits"));
        assertEquals(Set.of("lorekeeper"), db.blocking(c -> store.sigils(c, player)));
        assertEquals(Set.of("lorekeeper"), db.blocking(c -> store.claims(c, profile1, 0)), "the buying profile used its claim");

        assertEquals(WardrobeOps.Status.HAS_SIGIL, ops.buy(who2, Fixtures.def("lorekeeper"), 0).join().status(), "never pay twice");
        assertEquals(600, balance(player, "credits"));

        assertEquals(WardrobeOps.Status.OK, ops.claim(who2, "lorekeeper", 0).join().status());
        assertEquals(WardrobeOps.Status.CLAIMED, ops.claim(who2, "lorekeeper", 0).join().status());
        assertEquals(WardrobeOps.Status.CLAIMED, ops.claim(who1, "lorekeeper", 0).join().status());
        assertEquals(1, entries(profile2).size());
        assertEquals("credits_claim", entries(profile2).getFirst().origin());
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

    // ------------------------------------------------------------------ set levels (W2)

    private final SetLevels levels = new WardrobeConfig().levels();

    private WardrobeStore.Entry entry(UUID entryId) {
        return db.blocking(c -> store.entry(c, entryId)).orElseThrow();
    }

    private WardrobeStore.Entry give(UUID profile, String set, int level) {
        WardrobeStore.Entry e = ops.giveSet(profile, set, null, 0).join().entry();
        if (level > 0) {
            assertTrue(ops.setLevel(e.entryId(), profile, level, levels, null).join().ok());
        }
        return entry(e.entryId());
    }

    @Test
    void flushAddsXpAndTheLevelFollows() {
        WardrobeStore.Entry e = give(profile1, "delver", 0);
        assertTrue(ops.flushXp(Map.of(e.entryId(), 150.0), levels).join());
        assertTrue(ops.flushXp(Map.of(e.entryId(), 100.0), levels).join());
        WardrobeStore.Entry now = entry(e.entryId());
        assertEquals(250, now.xp(), 1e-9, "additive: two saves add up");
        assertEquals(1, now.level());
        assertTrue(ops.flushXp(Map.of(e.entryId(), 1e9), levels).join());
        assertEquals(levels.total(50, false), entry(e.entryId()).xp(), 1e-6, "never past the max level");
        assertEquals(50, entry(e.entryId()).level());
    }

    @Test
    void xpForARemovedSetIsDropped() {
        WardrobeStore.Entry e = give(profile1, "delver", 0);
        assertTrue(ops.removeSet(e.entryId(), null).join().ok());
        assertTrue(ops.flushXp(Map.of(e.entryId(), 500.0), levels).join());
        assertEquals(0, entry(e.entryId()).xp(), 1e-9);
    }

    @Test
    void staffSetsALevelOnlyOnTheProfilesActiveSet() {
        WardrobeStore.Entry e = give(profile1, "deepforge", 0);
        WardrobeOps.Result r = ops.setLevel(e.entryId(), profile1, 25, levels, UUID.randomUUID()).join();
        assertTrue(r.ok());
        assertEquals(25, entry(e.entryId()).level());
        assertEquals(levels.total(25, false), entry(e.entryId()).xp(), 1e-6);
        assertEquals(WardrobeOps.Status.GONE, ops.setLevel(e.entryId(), profile2, 50, levels, null).join().status(), "P4");
        assertEquals(50, ops.setLevel(e.entryId(), profile1, 99, levels, null).join().entry().level(), "clamped");
    }

    // ------------------------------------------------------------------ merging (W2)

    private WardrobeOps.MergeRequest merge(WardrobeOps.Who who, WardrobeStore.Entry a, WardrobeStore.Entry b, long fee, double pa, double pb) {
        return new WardrobeOps.MergeRequest(who, a.entryId(), Fixtures.def(a.setId()), b.entryId(), Fixtures.def(b.setId()), b.setId(),
                fee, pa, pb, levels, 0);
    }

    @Test
    void mergeMakesOneSetAtLevelZeroInOneTransaction() {
        WardrobeStore.Entry a = give(profile1, "deepforge", 50);
        WardrobeStore.Entry b = give(profile1, "coinmaster", 50);
        assertTrue(ops.equip(profile1, List.of(new WardrobeStore.Equipped(ArmorPiece.HEAD, a.entryId(), UUID.randomUUID()),
                new WardrobeStore.Equipped(ArmorPiece.CHEST, b.entryId(), UUID.randomUUID())), List.of()).join().ok());

        WardrobeOps.MergeResult r = ops.merge(merge(who1, a, b, 25_000, 0, 0)).join();
        assertEquals(WardrobeOps.Status.OK, r.status());
        assertEquals(5_000, balance(profile1, "crystals"), "fee paid once");
        assertTrue(usedKeys.contains(WardrobeOps.REASON_MERGE + ":" + r.entry().entryId()));

        List<WardrobeStore.Entry> active = entries(profile1);
        assertEquals(1, active.size());
        WardrobeStore.Entry m = active.getFirst();
        assertEquals("deepforge", m.setId());
        assertEquals("coinmaster", m.mergedSetId());
        assertEquals("coinmaster", m.look());
        assertEquals(0, m.level(), "owner rule: merging resets the level");
        assertEquals(0, m.xp(), 1e-9);
        assertEquals("merge", m.origin());
        assertEquals(WardrobeStore.EntryState.MERGED, entry(a.entryId()).state());
        assertEquals(WardrobeStore.EntryState.MERGED, entry(b.entryId()).state());
        assertTrue(db.blocking(c -> store.equipped(c, profile1)).isEmpty(), "both inputs came off");
        assertEquals(1, db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM wardrobe_merges WHERE merge_id = ?", rs -> rs.getInt(1),
                m.entryId())).orElse(0));

        assertTrue(ops.giveSet(profile1, "deepforge", null, 0).join().ok(), "the merged set is a separate entry (WD6)");
        assertEquals(WardrobeOps.Status.GONE, ops.merge(merge(who1, a, b, 25_000, 0, 0)).join().status(), "inputs can't be merged twice");
        assertEquals(5_000, balance(profile1, "crystals"));
    }

    @Test
    void refusedMergeChangesNothing() {
        WardrobeStore.Entry a = give(profile1, "deepforge", 50);
        WardrobeStore.Entry b = give(profile1, "coinmaster", 49);
        WardrobeOps.MergeResult r = ops.merge(merge(who1, a, b, 25_000, 0, 0)).join();
        assertEquals(WardrobeOps.Status.REFUSED, r.status());
        assertEquals(MergeRules.Refusal.NOT_MAX, r.refusal());
        assertEquals(30_000, balance(profile1, "crystals"), "no fee");
        assertEquals(2, entries(profile1).size());

        WardrobeStore.Entry delver = give(profile1, "delver", 50);
        assertEquals(MergeRules.Refusal.SHARED_FAMILY, ops.merge(merge(who1, delver, a, 15_000, 0, 0)).join().refusal());
        assertEquals(3, entries(profile1).size());
    }

    @Test
    void unsavedXpCountsInsideTheMerge() {
        WardrobeStore.Entry a = give(profile1, "deepforge", 50);
        WardrobeStore.Entry b = give(profile1, "coinmaster", 49);
        double missing = levels.total(50, false) - b.xp();
        WardrobeOps.MergeResult r = ops.merge(merge(who1, a, b, 25_000, 0, missing)).join();
        assertEquals(WardrobeOps.Status.OK, r.status(), "the level reached a moment ago (not saved yet) counts");
    }

    @Test
    void notEnoughCrystalsRollsEverythingBack() {
        WardrobeStore.Entry a = give(profile1, "deepforge", 50);
        WardrobeStore.Entry b = give(profile1, "coinmaster", 49);
        WardrobeOps.MergeResult r = ops.merge(merge(who1, a, b, 31_000, 0, 1e9)).join();
        assertEquals(WardrobeOps.Status.NOT_ENOUGH, r.status());
        assertEquals(49, entry(b.entryId()).level(), "the unsaved XP rolled back too (the caller keeps it)");
        assertEquals(WardrobeStore.EntryState.ACTIVE, entry(a.entryId()).state());
        assertEquals(2, entries(profile1).size());
    }

    @Test
    void cantMergeAnotherProfilesSet() {
        WardrobeStore.Entry a = give(profile1, "deepforge", 50);
        WardrobeStore.Entry b = give(profile2, "coinmaster", 50);
        assertEquals(WardrobeOps.Status.GONE, ops.merge(merge(who1, a, b, 25_000, 0, 0)).join().status(), "P4");
        assertEquals(30_000, balance(profile1, "crystals"));
    }

    @Test
    void concurrentMergesOfTheSameSetsMergeOnce() {
        WardrobeStore.Entry a = give(profile1, "deepforge", 50);
        WardrobeStore.Entry b = give(profile1, "coinmaster", 50);
        List<CompletableFuture<WardrobeOps.MergeResult>> all = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            all.add(ops.merge(merge(who1, a, b, 5_000, 0, 0)));
        }
        assertEquals(1, all.stream().map(CompletableFuture::join).filter(WardrobeOps.MergeResult::ok).count());
        assertEquals(25_000, balance(profile1, "crystals"));
        assertEquals(1, entries(profile1).size());
    }

    @Test
    void freeMergeWorksWithoutPayment() {
        WardrobeStore.Entry a = give(profile1, "dreadguard", 50);
        WardrobeStore.Entry b = give(profile1, "lorekeeper", 50);
        assertEquals(WardrobeOps.Status.OK, ops.merge(merge(who1, a, b, 0, 0, 0)).join().status());
        assertEquals(30_000, balance(profile1, "crystals"));
    }

    @Test
    void mergedSetsLevelOnTheSlowerCurve() {
        WardrobeStore.Entry a = give(profile1, "deepforge", 50);
        WardrobeStore.Entry b = give(profile1, "coinmaster", 50);
        WardrobeStore.Entry m = ops.merge(merge(who1, a, b, 25_000, 0, 0)).join().entry();
        assertTrue(ops.flushXp(Map.of(m.entryId(), levels.total(25, false)), levels).join());
        assertEquals(levels.level(levels.total(25, false), true), entry(m.entryId()).level());
        assertTrue(entry(m.entryId()).level() < 25);
    }

    // ------------------------------------------------------------------ tomes (W2)

    @Test
    void tomeAddsXpOnceAndStopsAtLevelForty() {
        WardrobeStore.Entry e = give(profile1, "delver", 39);
        UUID tome = UUID.randomUUID();
        assertTrue(ops.mintTome(tome, Tomes.GREATER, 10_000, "test", player).join());
        WardrobeOps.Result r = ops.useTome(who1, tome, e.entryId(), 0, levels, 40).join();
        assertEquals(WardrobeOps.Status.OK, r.status());
        assertEquals(40, entry(e.entryId()).level(), "capped at the start of level 40");
        assertEquals(levels.total(40, false), entry(e.entryId()).xp(), 1e-6);
        assertEquals(WardrobeStore.TokenState.REDEEMED, db.blocking(c -> store.tome(c, tome, false)).orElseThrow().state());
        assertEquals(WardrobeOps.Status.TOKEN_INVALID, ops.useTome(who1, tome, e.entryId(), 0, levels, 40).join().status(), "a copy");

        UUID second = UUID.randomUUID();
        ops.mintTome(second, Tomes.NORMAL, 2_500, "test", player).join();
        assertEquals(WardrobeOps.Status.TOME_CAPPED, ops.useTome(who1, second, e.entryId(), 0, levels, 40).join().status());
        assertEquals(WardrobeStore.TokenState.ACTIVE, db.blocking(c -> store.tome(c, second, false)).orElseThrow().state(), "kept");
        WardrobeStore.Entry other = give(profile2, "delver", 0);
        assertEquals(WardrobeOps.Status.GONE, ops.useTome(who1, second, other.entryId(), 0, levels, 40).join().status(), "P4");
        assertTrue(ops.voidTome(second, "test").join());
    }

    @Test
    void tomeOnALowSetAddsItsFullAmount() {
        WardrobeStore.Entry e = give(profile1, "woodsman", 0);
        UUID tome = UUID.randomUUID();
        ops.mintTome(tome, Tomes.NORMAL, 2_500, "test", player).join();
        assertTrue(ops.useTome(who1, tome, e.entryId(), 100, levels, 40).join().ok());
        assertEquals(2_600, entry(e.entryId()).xp(), 1e-9, "unsaved XP + the tome");
    }

    // ------------------------------------------------------------------ armor enchants (E5)

    private static List<net.skybornmc.skyblock.common.tools.ToolPerk> perks(Object... idLevel) {
        List<net.skybornmc.skyblock.common.tools.ToolPerk> out = new ArrayList<>();
        for (int i = 0; i < idLevel.length; i += 2) {
            out.add(new net.skybornmc.skyblock.common.tools.ToolPerk(out.size() + 1, (String) idLevel[i], (Integer) idLevel[i + 1]));
        }
        return out;
    }

    private void enchant(WardrobeStore.Entry e, ArmorPiece piece, List<net.skybornmc.skyblock.common.tools.ToolPerk> list) {
        db.blocking(c -> {
            store.savePiece(c, e.entryId(), piece, list);
            return null;
        });
    }

    @Test
    void pieceEnchantsAreLockedPerProfile() {
        WardrobeStore.Entry e = give(profile1, "deepforge", 30);
        enchant(e, ArmorPiece.HEAD, perks("vitality", 2));
        assertEquals(perks("vitality", 2), db.blocking(c -> store.lockPiece(c, profile1, e.entryId(), ArmorPiece.HEAD)).orElseThrow());
        assertTrue(db.blocking(c -> store.lockPiece(c, profile2, e.entryId(), ArmorPiece.HEAD)).isEmpty(), "P4: not another profile's");
        assertEquals(List.of(), db.blocking(c -> store.lockPiece(c, profile1, e.entryId(), ArmorPiece.FEET)).orElseThrow());
        assertEquals(Map.of(e.entryId(), Map.of(ArmorPiece.HEAD, perks("vitality", 2))), db.blocking(c -> store.pieceEnchants(c, profile1)));
        enchant(e, ArmorPiece.HEAD, List.of());
        assertTrue(db.blocking(c -> store.pieceEnchants(c, profile1)).isEmpty(), "no row for a piece without enchants");
    }

    @Test
    void mergeKeepsTheLooksEnchantsAndReturnsTheOthersAsBooks() {
        WardrobeStore.Entry a = give(profile1, "deepforge", 50);
        WardrobeStore.Entry b = give(profile1, "coinmaster", 50);
        enchant(a, ArmorPiece.HEAD, perks("vitality", 5, "fortitude", 4));
        enchant(a, ArmorPiece.CHEST, perks("second_wind", 3));
        enchant(b, ArmorPiece.FEET, perks("featherfall", 4));
        // Look = coinmaster (b): b's enchants stay, a's three come back as books.
        Map<ArmorPiece, List<net.skybornmc.skyblock.common.tools.ToolPerk>> expected = db.blocking(c -> store.entryEnchants(c, a.entryId()));
        List<MergeRules.Returned> returned = MergeRules.returnedBooks(expected, id -> 5);
        assertEquals(3, returned.size());
        List<WardrobeOps.ReturnedBook> books = new ArrayList<>();
        for (MergeRules.Returned r : returned) {
            books.add(new WardrobeOps.ReturnedBook(UUID.randomUUID(), r.enchant(), r.level(), r.piece(), "enchant_book", "enchant_book",
                    r.enchant(), new byte[]{1, 2, 3}));
        }
        List<UUID> minted = new ArrayList<>();
        WardrobeOps.MergeEnchants enchants = new WardrobeOps.MergeEnchants(expected, books, (c, book, who, profile, reason) -> {
            assertEquals(profile1, profile);
            assertTrue(reason.startsWith(WardrobeOps.REASON_MERGE + ":"));
            minted.add(book.id());
        });
        WardrobeOps.MergeRequest req = new WardrobeOps.MergeRequest(who1, a.entryId(), Fixtures.def("deepforge"), b.entryId(),
                Fixtures.def("coinmaster"), "coinmaster", 25_000, 0, 0, levels, 0, enchants);
        WardrobeOps.MergeResult r = ops.merge(req).join();
        assertEquals(WardrobeOps.Status.OK, r.status());
        assertEquals(Map.of(ArmorPiece.FEET, perks("featherfall", 4)), r.enchants(), "the look's enchants");
        assertEquals(Map.of(ArmorPiece.FEET, perks("featherfall", 4)), db.blocking(c -> store.entryEnchants(c, r.entry().entryId())));
        assertTrue(db.blocking(c -> store.entryEnchants(c, a.entryId())).isEmpty(), "moved off the inputs");
        assertTrue(db.blocking(c -> store.entryEnchants(c, b.entryId())).isEmpty());
        assertEquals(books.stream().map(WardrobeOps.ReturnedBook::id).toList(), minted, "one book row per returned enchant");
        assertEquals(3, db.blocking(c -> store.stored(c, profile1)).size(), "the books are in the Wardrobe Chest");
        assertEquals(0, r.entry().level(), "level 0: the kept enchants are dormant until the level catches up");
    }

    @Test
    void returnedBooksGoInEvenWhenTheChestIsFull() {
        WardrobeStore.Entry a = give(profile1, "deepforge", 50);
        WardrobeStore.Entry b = give(profile1, "coinmaster", 50);
        enchant(a, ArmorPiece.LEGS, perks("phoenix_thorns", 3));
        db.blocking(c -> {
            for (int i = 0; i < 30; i++) {
                store.put(c, new WardrobeStore.Stored(UUID.randomUUID(), profile1, "tool", "tool", "Tool", new byte[]{1},
                        WardrobeStore.StorageState.STORED, null, 1L), 30, 1L);
            }
            return null;
        });
        Map<ArmorPiece, List<net.skybornmc.skyblock.common.tools.ToolPerk>> expected = Map.of(ArmorPiece.LEGS, perks("phoenix_thorns", 3));
        WardrobeOps.ReturnedBook book = new WardrobeOps.ReturnedBook(UUID.randomUUID(), "phoenix_thorns", 3, ArmorPiece.LEGS, "enchant_book",
                "enchant_book", "Phoenix Thorns III", new byte[]{9});
        WardrobeOps.MergeRequest req = new WardrobeOps.MergeRequest(who1, a.entryId(), Fixtures.def("deepforge"), b.entryId(),
                Fixtures.def("coinmaster"), "coinmaster", 0, 0, 0, levels, 0,
                new WardrobeOps.MergeEnchants(expected, List.of(book), (c, x, w, p, why) -> { }));
        assertEquals(WardrobeOps.Status.OK, ops.merge(req).join().status());
        assertEquals(31, db.blocking(c -> store.stored(c, profile1)).size(), "nothing is lost, even past the chest size");
    }

    @Test
    void mergeRefusesWhenTheOtherSetsEnchantsChangedOrNoSinkIsThere() {
        WardrobeStore.Entry a = give(profile1, "deepforge", 50);
        WardrobeStore.Entry b = give(profile1, "coinmaster", 50);
        enchant(a, ArmorPiece.HEAD, perks("vitality", 5));
        // The menu saw no enchants on the other set: refused, nothing changes.
        WardrobeOps.MergeRequest blind = new WardrobeOps.MergeRequest(who1, a.entryId(), Fixtures.def("deepforge"), b.entryId(),
                Fixtures.def("coinmaster"), "coinmaster", 25_000, 0, 0, levels, 0);
        assertEquals(WardrobeOps.Status.GONE, ops.merge(blind).join().status());
        assertEquals(30_000, balance(profile1, "crystals"));
        assertEquals(2, entries(profile1).size());
        assertEquals(Map.of(ArmorPiece.HEAD, perks("vitality", 5)), db.blocking(c -> store.entryEnchants(c, a.entryId())));
        // Books but no way to write their rows: refused too.
        WardrobeOps.ReturnedBook book = new WardrobeOps.ReturnedBook(UUID.randomUUID(), "vitality", 5, ArmorPiece.HEAD, "enchant_book",
                "enchant_book", "Vitality V", new byte[]{1});
        WardrobeOps.MergeRequest noSink = new WardrobeOps.MergeRequest(who1, a.entryId(), Fixtures.def("deepforge"), b.entryId(),
                Fixtures.def("coinmaster"), "coinmaster", 25_000, 0, 0, levels, 0,
                new WardrobeOps.MergeEnchants(Map.of(ArmorPiece.HEAD, perks("vitality", 5)), List.of(book), null));
        assertEquals(WardrobeOps.Status.ERROR, ops.merge(noSink).join().status());
        assertEquals(30_000, balance(profile1, "crystals"));
        assertTrue(db.blocking(c -> store.stored(c, profile1)).isEmpty());
    }

    @Test
    void returnedBooksRules() {
        Map<ArmorPiece, List<net.skybornmc.skyblock.common.tools.ToolPerk>> other = Map.of(
                ArmorPiece.FEET, perks("featherfall", 4), ArmorPiece.HEAD, perks("vitality", 5, "night_eye", 1));
        List<MergeRules.Returned> books = MergeRules.returnedBooks(other, id -> id.equals("vitality") ? 3 : 4);
        assertEquals(List.of(new MergeRules.Returned(ArmorPiece.HEAD, "vitality", 3), new MergeRules.Returned(ArmorPiece.HEAD, "night_eye", 1),
                new MergeRules.Returned(ArmorPiece.FEET, "featherfall", 4)), books, "armor order, level clamped to a lowered max");
        assertEquals(List.of(new MergeRules.Returned(ArmorPiece.HEAD, "gone", 2)),
                MergeRules.returnedBooks(Map.of(ArmorPiece.HEAD, perks("gone", 2)), id -> 0), "unknown enchants are listed (the merge is refused)");
        assertTrue(MergeRules.sameEnchants(Map.of(ArmorPiece.HEAD, List.of()), Map.of()));
        assertFalse(MergeRules.sameEnchants(Map.of(ArmorPiece.HEAD, perks("vitality", 1)), Map.of()));
    }
}

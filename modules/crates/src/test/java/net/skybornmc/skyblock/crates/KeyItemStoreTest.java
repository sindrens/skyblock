package net.skybornmc.skyblock.crates;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.item.Rarity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M10.4 key items on H2 in MySQL mode (same SQL as MariaDB): withdraw, redeem once, P4, copies, bound keys. */
class KeyItemStoreTest {

    @TempDir
    Path dir;
    private Database db;
    private final UUID alice = UUID.randomUUID();
    /** Alice's first profile uses her player UUID as key (PROF); her second has its own key. */
    private final UUID aliceMain = alice;
    private final UUID aliceAlt = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "key-items-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("key-items-test"));
        db.start();
        new Migrations(db, Logger.getLogger("key-items-test")).migrate("crates", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private CrateStore.Keys keys(UUID profile) {
        return db.blocking(c -> CrateStore.keys(c, profile, "phoenix"));
    }

    private boolean withdraw(UUID profile, List<UUID> ids) {
        return db.blockingTransaction(c -> KeyItemStore.withdraw(c, profile, "phoenix", alice, "Alice", ids, 10));
    }

    private KeyItemStore.Redeem redeem(UUID item, UUID profile, UUID account) {
        return db.blockingTransaction(c -> KeyItemStore.redeem(c, item, profile, account, "x", 20, "phoenix"::equals));
    }

    @Test
    void withdrawTakesOnlyTradeableKeysAndWritesActiveRows() {
        db.blockingTransaction(c -> {
            CrateStore.addKeys(c, aliceMain, "phoenix", 5, 2, 1);
            return null;
        });
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(withdraw(aliceMain, List.of(a, b)));
        assertEquals(new CrateStore.Keys(5, 0), keys(aliceMain), "both tradeable keys left, bound keys untouched");
        KeyItemStore.Row row = db.blocking(c -> KeyItemStore.row(c, a)).orElseThrow();
        assertEquals(KeyItemStore.ACTIVE, row.state());
        assertEquals(alice, row.account());
        assertEquals(aliceMain, row.profile());
        assertEquals("phoenix", row.crate());
    }

    @Test
    void boundKeysCanNeverBeWithdrawn() {
        db.blockingTransaction(c -> {
            CrateStore.addKeys(c, aliceMain, "phoenix", 10, 0, 1);
            return null;
        });
        assertFalse(withdraw(aliceMain, List.of(UUID.randomUUID())), "only bound keys: nothing to withdraw");
        assertEquals(new CrateStore.Keys(10, 0), keys(aliceMain));
        assertEquals(0, KeyItemStore.amount(0, 5, 64), "the menu offers nothing without tradeable keys");
    }

    @Test
    void withdrawIsAllOrNothing() {
        db.blockingTransaction(c -> {
            CrateStore.addKeys(c, aliceMain, "phoenix", 3, 1, 1);
            return null;
        });
        UUID a = UUID.randomUUID();
        assertFalse(withdraw(aliceMain, List.of(a, UUID.randomUUID())), "2 asked, 1 tradeable");
        assertEquals(new CrateStore.Keys(3, 1), keys(aliceMain));
        assertTrue(db.blocking(c -> KeyItemStore.row(c, a)).isEmpty(), "no row written");
    }

    @Test
    void redeemWorksExactlyOnceAndACopyIsRefused() {
        db.blockingTransaction(c -> {
            CrateStore.addKeys(c, aliceMain, "phoenix", 0, 1, 1);
            return null;
        });
        UUID item = UUID.randomUUID();
        assertTrue(withdraw(aliceMain, List.of(item)));
        assertEquals(KeyItemStore.Outcome.REDEEMED, redeem(item, aliceMain, alice).outcome(), "back on the same profile");
        assertEquals(new CrateStore.Keys(0, 1), keys(aliceMain), "a redeemed key is tradeable again");
        KeyItemStore.Redeem again = redeem(item, aliceMain, alice);
        assertEquals(KeyItemStore.Outcome.ALREADY_USED, again.outcome(), "the same id a second time is a copy");
        assertTrue(KeyItemStore.copy(again.outcome()));
        assertEquals(KeyItemStore.REDEEMED, again.row().state());
        assertEquals(new CrateStore.Keys(0, 1), keys(aliceMain), "the copy added nothing");
        assertEquals(KeyItemStore.Outcome.ALREADY_USED, redeem(item, bob, bob).outcome(), "nor for anybody else");
        assertEquals(CrateStore.Keys.NONE, keys(bob));
    }

    @Test
    void unknownIdIsACopy() {
        KeyItemStore.Redeem r = redeem(UUID.randomUUID(), bob, bob);
        assertEquals(KeyItemStore.Outcome.UNKNOWN, r.outcome());
        assertTrue(KeyItemStore.copy(r.outcome()));
        assertEquals(CrateStore.Keys.NONE, keys(bob));
    }

    @Test
    void sameAccountOtherProfileIsRefusedButAnotherPlayerMayRedeem() {
        db.blockingTransaction(c -> {
            CrateStore.addKeys(c, aliceMain, "phoenix", 0, 1, 1);
            return null;
        });
        UUID item = UUID.randomUUID();
        assertTrue(withdraw(aliceMain, List.of(item)));
        KeyItemStore.Redeem refused = redeem(item, aliceAlt, alice);
        assertEquals(KeyItemStore.Outcome.OTHER_PROFILE, refused.outcome(), "P4: not on Alice's other profile");
        assertFalse(KeyItemStore.copy(refused.outcome()), "not a copy: the item goes back");
        assertEquals(CrateStore.Keys.NONE, keys(aliceAlt));
        assertEquals(KeyItemStore.ACTIVE, db.blocking(c -> KeyItemStore.row(c, item)).orElseThrow().state(), "still usable");

        assertEquals(KeyItemStore.Outcome.REDEEMED, redeem(item, bob, bob).outcome(), "traded to Bob: he may redeem it");
        assertEquals(new CrateStore.Keys(0, 1), keys(bob));
        KeyItemStore.Row row = db.blocking(c -> KeyItemStore.row(c, item)).orElseThrow();
        assertEquals(bob, row.redeemedBy());
        assertEquals(bob, row.redeemedProfile());
    }

    @Test
    void removedCrateChangesNothing() {
        db.blockingTransaction(c -> {
            CrateStore.addKeys(c, aliceMain, "phoenix", 0, 1, 1);
            return null;
        });
        UUID item = UUID.randomUUID();
        assertTrue(withdraw(aliceMain, List.of(item)));
        KeyItemStore.Redeem r = db.blockingTransaction(c -> KeyItemStore.redeem(c, item, bob, bob, "Bob", 20, crate -> false));
        assertEquals(KeyItemStore.Outcome.UNKNOWN_CRATE, r.outcome());
        assertEquals(KeyItemStore.ACTIVE, db.blocking(c -> KeyItemStore.row(c, item)).orElseThrow().state());
    }

    @Test
    void refundGivesKeysBackOnceAndUsesTheRowsUp() {
        db.blockingTransaction(c -> {
            CrateStore.addKeys(c, aliceMain, "phoenix", 0, 2, 1);
            return null;
        });
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(withdraw(aliceMain, List.of(a, b)));
        assertEquals(2, (int) db.blockingTransaction(c -> KeyItemStore.refund(c, aliceMain, "phoenix", List.of(a, b), 30)));
        assertEquals(new CrateStore.Keys(0, 2), keys(aliceMain));
        assertEquals(0, (int) db.blockingTransaction(c -> KeyItemStore.refund(c, aliceMain, "phoenix", List.of(a, b), 31)),
                "a second refund gives nothing");
        assertEquals(KeyItemStore.Outcome.ALREADY_USED, redeem(a, bob, bob).outcome(), "a refunded id can't be redeemed");
        assertEquals(new CrateStore.Keys(0, 2), keys(aliceMain));
    }

    @Test
    void fragmentKeysAreTradeableSoTheyCanBeWithdrawn() {
        assertFalse(new CratesSettings().bound(Fragments.SOURCE), "keys made from fragments are tradeable");
    }

    @Test
    void amountAndRarity() {
        assertEquals(1, KeyItemStore.amount(5, 1, 64));
        assertEquals(5, KeyItemStore.amount(5, 64, 64));
        assertEquals(64, KeyItemStore.amount(500, 1000, 64));
        assertEquals(0, KeyItemStore.amount(5, 0, 64));
        assertEquals(Rarity.LEGENDARY, KeyItems.rarity("legendary"));
        assertEquals(Rarity.MYTHIC, KeyItems.rarity(" Mythic "));
        assertEquals(Rarity.COMMON, KeyItems.rarity("nonsense"));
        assertEquals(Rarity.COMMON, KeyItems.rarity(null));
    }
}

package net.skybornmc.skyblock.collection;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The real SQL on H2 in MySQL mode: finds once, milestones paid once, profiles separate. */
class CollectionStoreTest {

    @TempDir
    Path dir;
    private Database db;
    private final UUID player = UUID.randomUUID();
    private final UUID profileA = UUID.randomUUID();
    private final UUID profileB = UUID.randomUUID();
    private final Milestones.Rewards rewards = new Milestones.Rewards(true, "blaze", 1, "keeper_of_ashes", "phoenix", 1, "");

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "collection-test";
        config.poolSize = 4;
        db = new Database(config, dir.toFile(), Logger.getLogger("collection-test"));
        db.start();
        new Migrations(db, Logger.getLogger("collection-test")).migrate("collection", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private CollectionStore.Committed commit(UUID profile, List<CollectionStore.EntryRow> entries, List<String> milestones) {
        return db.blockingTransaction(c -> CollectionStore.commit(c, profile, player, entries, milestones,
                id -> Milestones.grants(id, rewards), 1000));
    }

    @Test
    void aFirstFindIsStoredOnceAndKeepsItsTime() {
        db.blockingTransaction(c -> {
            CollectionStore.insertEntries(c, List.of(new CollectionStore.EntryRow(profileA, "depths_coal", 10, "depths_mine")));
            CollectionStore.insertEntries(c, List.of(new CollectionStore.EntryRow(profileA, "depths_coal", 20, "crate"),
                    new CollectionStore.EntryRow(profileA, "depths_iron", 30, "depths_mine")));
            return true;
        });
        Map<String, ProfileLog.Found> found = db.blocking(c -> CollectionStore.entries(c, profileA));
        assertEquals(2, found.size());
        assertEquals(new ProfileLog.Found(10, "depths_mine"), found.get("depths_coal"));
    }

    @Test
    void aMilestoneIsPaidOnce() {
        CollectionStore.Committed first = commit(profileA, List.of(new CollectionStore.EntryRow(profileA, "depths_coal", 10, "depths_mine")),
                List.of("mine:25", "mine:100"));
        assertEquals(List.of("mine:25", "mine:100"), first.milestones());
        assertEquals(2, first.grants().size(), "title + Blaze key for the full page");

        CollectionStore.Committed again = commit(profileA, List.of(), List.of("mine:25", "mine:100", "mine:50"));
        assertEquals(List.of("mine:50"), again.milestones(), "stored milestones write nothing again");
        assertEquals(List.of(), again.grants());

        List<CollectionStore.GrantRow> pending = db.blocking(c -> CollectionStore.grants(c, CollectionStore.PENDING, 100));
        assertEquals(2, pending.size());
        assertEquals(Set.of("mine:25", "mine:50", "mine:100"), db.blocking(c -> CollectionStore.milestones(c, profileA)));
        assertTrue(db.blocking(c -> CollectionStore.entries(c, profileA)).containsKey("depths_coal"), "the waiting finds are saved too");
    }

    @Test
    void aGrantIsTakenOnce() {
        CollectionStore.GrantRow row = commit(profileA, List.of(), List.of(Milestones.ALL)).grants().getFirst();
        Boolean taken = db.blocking(c -> CollectionStore.move(c, row.id(), CollectionStore.PENDING, CollectionStore.SENDING, 2000));
        Boolean twice = db.blocking(c -> CollectionStore.move(c, row.id(), CollectionStore.PENDING, CollectionStore.SENDING, 2000));
        Boolean delivered = db.blocking(c -> CollectionStore.move(c, row.id(), CollectionStore.SENDING, CollectionStore.DELIVERED, 3000));
        assertTrue(taken);
        assertFalse(twice);
        assertTrue(delivered);
        assertEquals("delivered", db.blocking(c -> CollectionStore.grantState(c, row.id())).orElseThrow());
        Integer pruned = db.blocking(c -> CollectionStore.pruneDelivered(c, 4000));
        assertEquals(1, pruned);
    }

    @Test
    void profilesAreSeparate() {
        commit(profileA, List.of(new CollectionStore.EntryRow(profileA, "depths_coal", 10, "depths_mine")), List.of("mine:25", "mine:100"));
        assertEquals(Map.of(), db.blocking(c -> CollectionStore.entries(c, profileB)));
        assertEquals(Set.of(), db.blocking(c -> CollectionStore.milestones(c, profileB)));
        // Profile B reaching the same milestone is paid for B too: nothing is shared between profiles of one account.
        CollectionStore.Committed b = commit(profileB, List.of(), List.of("mine:100"));
        assertEquals(List.of("mine:100"), b.milestones());
        assertEquals(2, b.grants().size());
        assertTrue(b.grants().stream().allMatch(g -> g.profile().equals(profileB)));

        Map<UUID, Integer> counts = db.blocking(CollectionStore::pageMilestoneCounts);
        assertEquals(2, counts.get(profileA));
        assertEquals(1, counts.get(profileB));

        db.blockingTransaction(c -> {
            CollectionStore.deleteProfile(c, profileA);
            return true;
        });
        assertEquals(Map.of(), db.blocking(c -> CollectionStore.entries(c, profileA)));
        assertEquals(Set.of("mine:100"), db.blocking(c -> CollectionStore.milestones(c, profileB)), "the other profile keeps its log");
    }

    @Test
    void theWholeLogIsNoIslandPoint() {
        commit(profileA, List.of(), List.of("mine:100", Milestones.ALL));
        assertEquals(1, db.blocking(CollectionStore::pageMilestoneCounts).get(profileA));
    }
}

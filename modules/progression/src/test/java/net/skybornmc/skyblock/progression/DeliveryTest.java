package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.progression.TaskCatalog.TaskDef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hand-in is crash-safe: items leave the inventory, the player file is saved, and only then the progress is written.
 * Every failure gives the items back and adds no progress; progress is added only after the write committed.
 */
class DeliveryTest {

    /** Fake inventory + player file + database that records the order of the steps. */
    static class Fake implements Delivery.Steps {
        long inventory;
        long savedInventory;
        long database;
        boolean saveWorks = true;
        boolean commitWorks = true;
        CompletableFuture<Void> pending;
        final List<String> order = new ArrayList<>();

        Fake(long inventory) {
            this.inventory = inventory;
            this.savedInventory = inventory;
        }

        @Override
        public long take(long max) {
            long n = Math.min(max, inventory);
            inventory -= n;
            order.add("take " + n);
            return n;
        }

        @Override
        public boolean save() {
            order.add("save");
            if (saveWorks) {
                savedInventory = inventory;
            }
            return saveWorks;
        }

        @Override
        public CompletableFuture<Void> commit(long taken) {
            order.add("commit " + taken);
            if (pending != null) {
                return pending.thenRun(() -> database += taken);
            }
            if (!commitWorks) {
                return CompletableFuture.failedFuture(new java.sql.SQLException("down"));
            }
            database += taken;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void giveBack(long taken) {
            order.add("give back " + taken);
            inventory += taken;
        }

        /** Items that exist after a crash right now: the saved player file + the database. */
        long afterCrash() {
            return savedInventory + database;
        }
    }

    private static TaskDef hand(TaskProgress p) {
        return p.catalog().get("hand_in").orElseThrow();
    }

    private static Delivery.Outcome run(TaskProgress p, UUID island, Fake fake) {
        return Delivery.run(p, island, hand(p), fake.inventory, fake, Runnable::run).join();
    }

    @Test
    void savesThePlayerFileBeforeWritingProgress() {
        TaskProgress p = new TaskProgress(TaskProgressTest.catalog());
        UUID island = UUID.randomUUID();
        Fake fake = new Fake(100);
        Delivery.Outcome out = run(p, island, fake);
        assertEquals(Delivery.Status.DELIVERED, out.status());
        assertEquals(64, out.taken(), "never more than the goal");
        assertEquals(List.of("take 64", "save", "commit 64"), fake.order);
        assertEquals(36, fake.inventory);
        assertEquals(64, fake.database);
        assertEquals(64, p.counter(island, "deliver:hand_in"));
        assertEquals(List.of(hand(p)), out.ready(), "goal reached: complete it");
        assertEquals(Delivery.Status.NOT_NEEDED, run(p, island, new Fake(10)).status());
    }

    @Test
    void aFailedSaveSkipsTheDatabaseAndGivesTheItemsBack() {
        TaskProgress p = new TaskProgress(TaskProgressTest.catalog());
        UUID island = UUID.randomUUID();
        Fake fake = new Fake(20);
        fake.saveWorks = false;
        Delivery.Outcome out = run(p, island, fake);
        assertEquals(Delivery.Status.FAILED, out.status());
        assertEquals(List.of("take 20", "save", "give back 20"), fake.order);
        assertEquals(20, fake.inventory);
        assertEquals(0, fake.database);
        assertEquals(0, p.counter(island, "deliver:hand_in"));
        assertEquals(64, p.room(island, hand(p)), "reservation freed");
    }

    @Test
    void aFailedWriteGivesTheItemsBackAndAddsNothing() {
        TaskProgress p = new TaskProgress(TaskProgressTest.catalog());
        UUID island = UUID.randomUUID();
        Fake fake = new Fake(30);
        fake.commitWorks = false;
        Delivery.Outcome out = run(p, island, fake);
        assertEquals(Delivery.Status.FAILED, out.status());
        assertEquals(List.of("take 30", "save", "commit 30", "give back 30"), fake.order);
        assertEquals(30, fake.inventory);
        assertEquals(0, p.counter(island, "deliver:hand_in"));
        assertEquals(64, p.room(island, hand(p)));
    }

    @Test
    void aCrashMidwayCanLoseButNeverDuplicate() {
        TaskProgress p = new TaskProgress(TaskProgressTest.catalog());
        UUID island = UUID.randomUUID();
        Fake fake = new Fake(10);
        fake.pending = new CompletableFuture<>();
        CompletableFuture<Delivery.Outcome> out = Delivery.run(p, island, hand(p), fake.inventory, fake, Runnable::run);
        // Crash while the write is running: the saved file no longer has the items, the database has nothing yet.
        assertTrue(fake.afterCrash() <= 10, "never more items than before");
        assertEquals(0, fake.afterCrash());
        assertEquals(0, p.counter(island, "deliver:hand_in"), "progress only after the commit");
        assertEquals(54, p.room(island, hand(p)), "the running hand-in holds its room");
        fake.pending.complete(null);
        assertEquals(Delivery.Status.DELIVERED, out.join().status());
        assertEquals(10, fake.afterCrash());
        assertEquals(10, p.counter(island, "deliver:hand_in"));
    }

    @Test
    void nothingToHandIn() {
        TaskProgress p = new TaskProgress(TaskProgressTest.catalog());
        UUID island = UUID.randomUUID();
        Fake fake = new Fake(0);
        assertEquals(Delivery.Status.NO_ITEMS, run(p, island, fake).status());
        assertEquals(List.of(), fake.order);
        // The inventory changed between counting and taking: nothing moves, the room is freed.
        Fake gone = new Fake(5) {
            @Override
            public long take(long max) {
                order.add("take 0");
                return 0;
            }
        };
        assertEquals(Delivery.Status.NO_ITEMS, Delivery.run(p, island, hand(p), 5, gone, Runnable::run).join().status());
        assertEquals(List.of("take 0"), gone.order);
        assertEquals(64, p.room(island, hand(p)));
    }
}

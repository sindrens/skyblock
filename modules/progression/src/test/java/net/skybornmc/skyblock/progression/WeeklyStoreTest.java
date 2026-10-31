package net.skybornmc.skyblock.progression;

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
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M5.7 leaderboard tables on H2 in MySQL mode: week bases, closing a week once, survival across a restart, hidden accounts. */
class WeeklyStoreTest {

    @TempDir
    Path dir;

    private Database db;
    private final WeeklyStore store = new WeeklyStore();
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    private Database open() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "weekly-test";
        config.poolSize = 2;
        Database d = new Database(config, dir.toFile(), Logger.getLogger("weekly-test"));
        d.start();
        new Migrations(d, Logger.getLogger("weekly-test")).migrate("progression", getClass().getClassLoader());
        return d;
    }

    @BeforeEach
    void start() throws Exception {
        db = open();
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void firstWeekHasABaseAndNoPlaces() {
        assertTrue(db.<Boolean>blockingTransaction(c -> store.rollOver(c, 1, null, List.of(), 100,
                Map.of(a, new WeeklyStore.Base(40.5, 12), b, new WeeklyStore.Base(0, 0)))));
        Map<UUID, WeeklyStore.Base> base = db.blocking(c -> store.loadBase(c, 1, 100));
        assertEquals(new WeeklyStore.Base(40.5, 12), base.get(a));
        assertEquals(2, base.size());
        assertTrue(db.blocking(c -> store.loadPlaces(c, 1, 100)).isEmpty());
        assertTrue(db.blocking(c -> store.loadBase(c, 2, 100)).isEmpty(), "bases are per season");
    }

    @Test
    void closingAWeekStoresItsPlacesAndTheNextBaseOnce() {
        db.blockingTransaction(c -> store.rollOver(c, 0, null, List.of(), 100, Map.of(a, new WeeklyStore.Base(10, 1))));
        List<WeeklyStore.Place> places = List.of(new WeeklyStore.Place("weekly_gain_solo", 1, a, 30),
                new WeeklyStore.Place("weekly_gain_solo", 2, b, 5));
        assertTrue(db.<Boolean>blockingTransaction(c -> store.rollOver(c, 0, 100, places, 107, Map.of(a, new WeeklyStore.Base(40, 6)))));
        assertFalse(db.<Boolean>blockingTransaction(c -> store.rollOver(c, 0, 100, places, 107, Map.of(a, new WeeklyStore.Base(99, 9)))),
                "a week is closed only once");
        assertEquals(places, db.blocking(c -> store.loadPlaces(c, 0, 100)));
        assertEquals(new WeeklyStore.Base(40, 6), db.blocking(c -> store.loadBase(c, 0, 107)).get(a));
        assertEquals(OptionalInt.of(107), db.blocking(c -> store.previousWeek(c, 0, 114)));
        assertEquals(OptionalInt.of(100), db.blocking(c -> store.previousWeek(c, 0, 107)));
        assertEquals(OptionalInt.of(107), db.blocking(c -> store.previousWeek(c, 0, 108)));
        assertEquals(OptionalInt.empty(), db.blocking(c -> store.previousWeek(c, 0, 100)));
    }

    @Test
    void snapshotSurvivesARestart() throws Exception {
        db.blockingTransaction(c -> store.rollOver(c, 0, null, List.of(), 200, Map.of(a, new WeeklyStore.Base(7, 3))));
        db.close();
        db = open();
        assertEquals(Map.of(a, new WeeklyStore.Base(7, 3)), db.blocking(c -> store.loadBase(c, 0, 200)));
    }

    @Test
    void emptyServerStillMarksTheWeek() {
        assertTrue(db.<Boolean>blockingTransaction(c -> store.rollOver(c, 0, null, List.of(), 300, Map.of())));
        assertTrue(db.<Boolean>blocking(c -> store.hasBase(c, 0, 300)));
        assertTrue(db.blocking(c -> store.loadBase(c, 0, 300)).isEmpty(), "the marker row is not an island");
    }

    @Test
    void failedPlacesRollBackTheBase() {
        List<WeeklyStore.Place> duplicate = List.of(new WeeklyStore.Place("weekly_gain_solo", 1, a, 3),
                new WeeklyStore.Place("weekly_gain_solo", 1, b, 2));
        try {
            db.blockingTransaction(c -> store.rollOver(c, 0, 400, duplicate, 407, Map.of(a, new WeeklyStore.Base(1, 1))));
        } catch (RuntimeException expected) {
            // duplicate place: the whole transaction rolls back
        }
        assertFalse(db.<Boolean>blocking(c -> store.hasBase(c, 0, 407)));
    }

    @Test
    void hiddenAccounts() {
        assertTrue(db.<Boolean>blocking(c -> store.setHidden(c, a, true, 1L)));
        assertFalse(db.<Boolean>blocking(c -> store.setHidden(c, a, true, 2L)));
        assertEquals(Set.of(a), db.blocking(store::loadHidden));
        assertTrue(db.<Boolean>blocking(c -> store.setHidden(c, a, false, 3L)));
        assertFalse(db.<Boolean>blocking(c -> store.setHidden(c, a, false, 3L)));
        assertTrue(db.blocking(store::loadHidden).isEmpty());
    }
}

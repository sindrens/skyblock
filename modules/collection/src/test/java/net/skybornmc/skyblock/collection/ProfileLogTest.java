package net.skybornmc.skyblock.collection;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** First finds are recorded once, also while the stored log is still loading. */
class ProfileLogTest {

    @Test
    void aFindIsNewExactlyOnce() {
        ProfileLog log = new ProfileLog(UUID.randomUUID());
        log.loaded(Map.of(), Set.of());
        assertNotNull(log.add("depths_coal", 1, "depths_mine"));
        assertNull(log.add("depths_coal", 2, "depths_mine"));
        assertNull(log.add("depths_coal", 3, "crate"));
        assertEquals(1, log.found("depths_coal").at(), "the first time is kept");
        assertEquals(Set.of("depths_coal"), log.foundIds());
    }

    @Test
    void storedFindsAreNotNewAgain() {
        ProfileLog log = new ProfileLog(UUID.randomUUID());
        log.loaded(Map.of("depths_coal", new ProfileLog.Found(5, "depths_mine")), Set.of("mine:25"));
        assertNull(log.add("depths_coal", 9, "depths_mine"));
        assertTrue(log.hasMilestone("mine:25"));
    }

    @Test
    void findsWhileLoadingWaitAndOnlyNewOnesComeBack() {
        ProfileLog log = new ProfileLog(UUID.randomUUID());
        assertFalse(log.loaded());
        assertNull(log.add("depths_coal", 1, "depths_mine"), "nothing is shown before the log is loaded");
        assertNull(log.add("depths_iron", 2, "depths_mine"));
        assertNull(log.add("depths_iron", 3, "depths_mine"));
        List<ProfileLog.NewFind> news = log.loaded(Map.of("depths_coal", new ProfileLog.Found(0, "depths_mine")), Set.of());
        assertEquals(List.of(new ProfileLog.NewFind("depths_iron", 2, "depths_mine")), news);
        assertNull(log.add("depths_iron", 4, "depths_mine"));
    }

    @Test
    void milestonesAreNotStartedTwiceWhileWriting() {
        ProfileLog log = new ProfileLog(UUID.randomUUID());
        log.loaded(Map.of(), Set.of("mine:25"));
        List<String> first = log.startWriting(List.of("mine:25", "mine:50"));
        assertEquals(List.of("mine:50"), first);
        assertEquals(List.of(), log.startWriting(List.of("mine:50")), "already being written");
        assertTrue(log.milestones().contains("mine:50"));
        log.doneWriting(first, false);
        assertFalse(log.hasMilestone("mine:50"), "a failed write can be tried again");
        assertEquals(List.of("mine:50"), log.startWriting(List.of("mine:50")));
        log.doneWriting(List.of("mine:50"), true);
        assertTrue(log.hasMilestone("mine:50"));
    }
}

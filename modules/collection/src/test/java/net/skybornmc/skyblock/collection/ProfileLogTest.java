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

    /** Batch 9: the log counts every find, also the ones made while it was loading; new only once. */
    @Test
    void amountsCountEveryFind() {
        ProfileLog log = new ProfileLog(java.util.UUID.randomUUID());
        assertNull(log.add("farm.wheat", 1, "farm", 5));
        assertNull(log.add("mine.coal", 1, "mine", 2), "mine.coal was stored before");
        assertTrue(log.waiting("farm.wheat"));
        List<ProfileLog.NewFind> news = log.loaded(Map.of("mine.coal", new ProfileLog.Found(0, "mine")), Map.of("mine.coal", 10L), Set.of());
        assertEquals(List.of("farm.wheat"), news.stream().map(ProfileLog.NewFind::entry).toList());
        assertEquals(5, log.amount("farm.wheat"));
        assertEquals(12, log.amount("mine.coal"));
        assertNull(log.add("farm.wheat", 2, "farm", 3));
        assertEquals(8, log.amount("farm.wheat"));
        assertEquals(0, log.amount("fish.cod"));
        assertNotNull(log.add("fish.cod", 3, "fish"));
        assertEquals(1, log.amount("fish.cod"));
    }

    @Test
    void grindEventsMapToEntries() {
        assertEquals("farm.wheat", CollectionModule.grindEntry("farm", org.bukkit.Material.WHEAT, org.bukkit.Material.WHEAT));
        assertEquals("mine.raw_iron", CollectionModule.grindEntry("mine", org.bukkit.Material.IRON_ORE, org.bukkit.Material.RAW_IRON));
        assertEquals("fish.cod", CollectionModule.grindEntry("fish", org.bukkit.Material.WATER, org.bukkit.Material.COD));
        assertEquals("mob.wither_skeleton", CollectionModule.grindEntry("pve", org.bukkit.Material.WITHER_SKELETON_SPAWN_EGG,
                org.bukkit.Material.BONE));
        assertNull(CollectionModule.grindEntry("pve", org.bukkit.Material.BONE, org.bukkit.Material.BONE));
        assertNull(CollectionModule.grindEntry("dungeon", org.bukkit.Material.STONE, org.bukkit.Material.STONE));
    }
}

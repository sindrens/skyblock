package net.skybornmc.skyblock.npcs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF9: the tracked sets built off the main thread match the old main-thread track() loop. */
class PlacementIndexTest {

    static Spots.Npc npc(String ref, String world, double x, double z) {
        return new Spots.Npc(ref, ref, "bank", "b", world, x, 99, z, 0f);
    }

    static Spots.Text text(String ref, String world, double x, double z) {
        return new Spots.Text(ref, ref, "b", world, x, 100, z, 0f, 3, 2, "");
    }

    private static Spots spots() {
        return new Spots(List.of(npc("bank", "spawn", 0, 81), npc("smith", "spawn", 15.5, -1)),
                List.of(text("info", "spawn", 10, -11), text("keys", "spawn", 1, 80)),
                List.of(text("mining", "spawn", -61, -62)), List.of());
    }

    @Test
    void oneSetPerPointInPlacementOrder() {
        PlacementIndex index = PlacementIndex.build(spots(), true, true, true);
        assertEquals(List.of("npc:bank", "npc:smith", "hologram:info", "hologram:keys", "board:mining"),
                index.order().stream().map(Placed::key).toList(), "NPCs, then holograms, then boards");
        assertEquals(List.copyOf(index.byKey().values()), index.order());
        assertThrows(UnsupportedOperationException.class, () -> index.order().add(index.order().getFirst()),
                "the order is handed to the spawn queue as one batch, so it must not change");
    }

    @Test
    void chunkMapGroupsSetsByWorldAndChunk() {
        PlacementIndex index = PlacementIndex.build(spots(), true, true, true);
        // bank (0, 81) and keys (1, 80) share chunk (0, 5); smith (15.5, -1) is chunk (0, -1); info (10, -11) too.
        List<Placed> chunk05 = index.byChunk().get("spawn").get(Placed.chunkKey(0, 5));
        assertEquals(List.of("npc:bank", "hologram:keys"), chunk05.stream().map(Placed::key).toList());
        List<Placed> chunk0m1 = index.byChunk().get("spawn").get(Placed.chunkKey(0, -1));
        assertEquals(List.of("npc:smith", "hologram:info"), chunk0m1.stream().map(Placed::key).toList());
        assertEquals(List.of("board:mining"),
                index.byChunk().get("spawn").get(Placed.chunkKey(-4, -4)).stream().map(Placed::key).toList());
        // the same objects in both maps (a spawn from a chunk load must find the tracked set)
        assertSame(index.byKey().get("npc:bank"), chunk05.getFirst());
    }

    @Test
    void disabledKindsAreNotTracked() {
        PlacementIndex index = PlacementIndex.build(spots(), false, true, false);
        assertEquals(List.of("hologram:info", "hologram:keys"), index.order().stream().map(Placed::key).toList());
        assertTrue(PlacementIndex.build(spots(), false, false, false).isEmpty());
    }

    @Test
    void aKeyUsedTwiceKeepsItsFirstPlaceWithTheLastSetLikeTheOldLoop() {
        Spots twice = new Spots(List.of(npc("bank", "spawn", 0, 0), npc("other", "spawn", 50, 0), npc("bank", "spawn", 100, 0)),
                List.of(), List.of(), List.of());
        PlacementIndex index = PlacementIndex.build(twice, true, true, true);
        assertEquals(List.of("npc:bank", "npc:other"), index.order().stream().map(Placed::key).toList());
        assertEquals(6, index.byKey().get("npc:bank").chunkX, "the last one wins the key");
        assertEquals(1, index.byChunk().get("spawn").get(Placed.chunkKey(0, 0)).size(), "the chunk map keeps both");
    }

    @Test
    void emptyIsMutableAndEmpty() {
        PlacementIndex empty = PlacementIndex.empty();
        assertTrue(empty.isEmpty());
        assertTrue(empty.order().isEmpty());
        assertFalse(PlacementIndex.build(spots(), true, false, false).isEmpty());
    }

    @Test
    void removalSkipsSetsThatNeverSpawned() {
        Placed never = new Placed(Placed.Kind.NPC, npc("bank", "spawn", 0, 0), null);
        assertFalse(Placer.removeSet(never), "a set without entities is skipped (does not use the removal budget)");
    }
}

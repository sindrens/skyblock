package net.skybornmc.skyblock.npcs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PERF9: the tracked sets of one placement, built off the main thread ({@link NpcPlan}) and taken over by the
 * {@link Placer} in O(1) on the main thread (building them there was {@code npcs:apply-track}). Same content and order
 * as the old main-thread {@code track()} loop: one {@link Placed} per enabled point, NPCs, then holograms, then
 * leaderboards; a key used twice keeps its first position with the last set (LinkedHashMap put) while the chunk map
 * keeps both (the spawn checks the key map, so only the last one ever spawns).
 *
 * <p>Built once per plan and used once: the sets are mutable after the hand-over and belong to the main thread from
 * then on (the scheduler's hand-over is the happens-before edge). Pure (no Bukkit calls), unit-tested.
 *
 * @param byKey   every set by {@link Placed#key()}, in placement order
 * @param byChunk world name to chunk key ({@link Placed#chunkKey}) to the sets in that chunk
 * @param order   {@code byKey}'s values in order, immutable (queued as one batch)
 */
record PlacementIndex(Map<String, Placed> byKey, Map<String, Map<Long, List<Placed>>> byChunk, List<Placed> order) {

    /** Nothing tracked (a fresh, mutable index: the placer's state between placements). */
    static PlacementIndex empty() {
        return new PlacementIndex(new LinkedHashMap<>(), new HashMap<>(), List.of());
    }

    /** One set per point of the enabled kinds. */
    static PlacementIndex build(Spots spots, boolean npcs, boolean holograms, boolean boards) {
        Map<String, Placed> byKey = new LinkedHashMap<>();
        Map<String, Map<Long, List<Placed>>> byChunk = new HashMap<>();
        if (npcs) {
            spots.npcs().forEach(n -> track(byKey, byChunk, new Placed(Placed.Kind.NPC, n, null)));
        }
        if (holograms) {
            spots.holograms().forEach(t -> track(byKey, byChunk, new Placed(Placed.Kind.HOLOGRAM, null, t)));
        }
        if (boards) {
            spots.boards().forEach(t -> track(byKey, byChunk, new Placed(Placed.Kind.BOARD, null, t)));
        }
        return new PlacementIndex(byKey, byChunk, List.copyOf(byKey.values()));
    }

    private static void track(Map<String, Placed> byKey, Map<String, Map<Long, List<Placed>>> byChunk, Placed p) {
        byKey.put(p.key(), p);
        byChunk.computeIfAbsent(p.world, w -> new HashMap<>()).computeIfAbsent(p.chunkKey(), k -> new ArrayList<>()).add(p);
    }

    boolean isEmpty() {
        return byKey.isEmpty();
    }
}

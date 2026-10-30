package net.skybornmc.skyblock.depths.drops;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * R3-22 full-satchel chain (owner: "drops go to the inventory, then /box"). Pure rules plus the per-profile buffer of
 * drops that fit neither the satchel nor the inventory. The buffer is handed to the item box in batches (one box
 * delivery per profile every few seconds, not one database transaction per block), on quit, on a profile switch and at
 * shutdown. Main thread only.
 */
public final class DropOverflow {

    /** Where the drops of one block break or mob kill go. */
    public enum Route {
        /** Into the satchel (it has room). */
        SATCHEL,
        /** Satchel full (or not usable): inventory first, the rest to /box. */
        OVERFLOW,
        /** Mining only: the satchel is still loading, the break waits (no drops lost, the block stays). */
        REFUSE_LOADING,
        /** Mining only: satchel full and /box at its hard cap (5,000 stacks): breaking stops until there is room. */
        REFUSE_BOX_FULL
    }

    /**
     * Mining (a block break can simply not happen): satchel first; full → inventory → /box; refused only while the
     * satchel loads or when the satchel is full and the /box is at its hard cap.
     */
    public static Route forBreak(boolean loaded, boolean satchelFull, boolean boxHardFull) {
        if (!loaded) {
            return Route.REFUSE_LOADING;
        }
        if (!satchelFull) {
            return Route.SATCHEL;
        }
        return boxHardFull ? Route.REFUSE_BOX_FULL : Route.OVERFLOW;
    }

    /**
     * Combat (the kill already happened, nothing can be refused): satchel when it is on, loaded and has room, otherwise
     * inventory → /box. Items that already left are always stored, even over the box hard cap (ItemBox guarantee).
     */
    public static Route forKill(boolean satchelEnabled, boolean loaded, boolean satchelFull) {
        return satchelEnabled && loaded && !satchelFull ? Route.SATCHEL : Route.OVERFLOW;
    }

    private final Map<UUID, Map<String, Long>> waiting = new HashMap<>();

    /** Remembers {@code amount} of {@code item} for the box of {@code profile}. */
    public void add(UUID profile, String item, long amount) {
        if (profile == null || item == null || amount <= 0) {
            return;
        }
        waiting.computeIfAbsent(profile, k -> new LinkedHashMap<>()).merge(item, amount, Long::sum);
    }

    /** Takes everything waiting for one profile (empty map when nothing). */
    public Map<String, Long> drain(UUID profile) {
        Map<String, Long> out = waiting.remove(profile);
        return out == null ? Map.of() : out;
    }

    /** Takes everything waiting, per profile. */
    public Map<UUID, Map<String, Long>> drainAll() {
        Map<UUID, Map<String, Long>> out = new LinkedHashMap<>(waiting);
        waiting.clear();
        return out;
    }

    /** Amount of an item waiting for a profile (tests and the satchel menu). */
    public long waiting(UUID profile, String item) {
        Map<String, Long> m = waiting.get(profile);
        return m == null ? 0 : m.getOrDefault(item, 0L);
    }

    public boolean isEmpty() {
        return waiting.isEmpty();
    }
}

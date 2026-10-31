package net.skybornmc.skyblock.perks;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Items waiting for the "Destroy N items?" confirm of {@code /trash}. Pure and generic so it is unit-tested without a
 * server. Each held batch leaves exactly once: {@link #confirm} hands it over to be destroyed, {@link #release} hands it
 * back to the player (cancel, closing the confirm, quitting, a module reload). A second call gets nothing, so items can
 * never be both destroyed and returned, or returned twice.
 */
final class TrashBin<T> {

    private final Map<UUID, List<T>> held = new ConcurrentHashMap<>();

    /**
     * Holds {@code items} for {@code player} until the confirm is answered. Returns the batch that was already held (it
     * must go back to the player first), or an empty list.
     */
    List<T> hold(UUID player, List<T> items) {
        List<T> before = held.put(player, List.copyOf(items));
        return before == null ? List.of() : before;
    }

    /** Confirmed: the held items, to destroy now (empty if they were already destroyed or returned). */
    List<T> confirm(UUID player) {
        List<T> items = held.remove(player);
        return items == null ? List.of() : items;
    }

    /** Not confirmed: the held items, to give back now (empty if they were already destroyed or returned). */
    List<T> release(UUID player) {
        List<T> items = held.remove(player);
        return items == null ? List.of() : items;
    }

    boolean holding(UUID player) {
        return held.containsKey(player);
    }

    /** Every player with held items (module disable gives them all back). */
    List<UUID> players() {
        return List.copyOf(held.keySet());
    }
}

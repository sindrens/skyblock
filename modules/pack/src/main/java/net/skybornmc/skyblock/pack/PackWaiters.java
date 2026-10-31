package net.skybornmc.skyblock.pack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure: actions waiting for a player's pack answer ({@code PackService#afterAnswer}). One chain per player with the
 * deadline of the first call; the module ticks {@link #expired} once per second (one task for everyone) and calls
 * {@link #complete} on an answer and {@link #drop} on quit. Main thread only.
 */
final class PackWaiters {

    private static final class Chain {
        final long deadline;
        final List<Runnable> actions = new ArrayList<>();

        Chain(long deadline) {
            this.deadline = deadline;
        }
    }

    private final Map<UUID, Chain> chains = new LinkedHashMap<>();

    /** Queues {@code action}; the chain's deadline is the one of its first action. */
    void add(UUID player, Runnable action, long deadlineMillis) {
        chains.computeIfAbsent(player, id -> new Chain(deadlineMillis)).actions.add(action);
    }

    /** The player answered: their actions in call order (once; a second call returns nothing). */
    List<Runnable> complete(UUID player) {
        Chain chain = chains.remove(player);
        return chain == null ? List.of() : List.copyOf(chain.actions);
    }

    /** Chains whose deadline has passed at {@code now}, removed. */
    Map<UUID, List<Runnable>> expired(long now) {
        Map<UUID, List<Runnable>> out = new LinkedHashMap<>();
        Iterator<Map.Entry<UUID, Chain>> it = chains.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Chain> e = it.next();
            if (e.getValue().deadline <= now) {
                out.put(e.getKey(), List.copyOf(e.getValue().actions));
                it.remove();
            }
        }
        return out;
    }

    /** Quit or kick: the actions never run. */
    void drop(UUID player) {
        chains.remove(player);
    }

    boolean waiting(UUID player) {
        return chains.containsKey(player);
    }

    void clear() {
        chains.clear();
    }
}

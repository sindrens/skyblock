package net.skybornmc.skyblock.progression;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Debounce of island level recalculations (PHASE-5 M5.2). A change waits {@code delay} for more changes; every new change
 * pushes it back, but never past {@code maxWait} after the first one, so constant building still recalculates regularly.
 * Main thread only (not thread-safe). Pure, unit-tested.
 */
final class LevelDebounce {

    private record Pending(long first, long due) {
    }

    private final Map<UUID, Pending> pending = new LinkedHashMap<>();

    /** Requests a recalculation of {@code island} at {@code now}. */
    void request(UUID island, long now, long delayMillis, long maxWaitMillis) {
        if (island == null) {
            return;
        }
        Pending p = pending.get(island);
        long first = p == null ? now : p.first();
        long due = Math.min(now + Math.max(0, delayMillis), first + Math.max(0, maxWaitMillis));
        pending.put(island, new Pending(first, due));
    }

    /** Removes and returns up to {@code limit} islands that are due at {@code now}, oldest request first. */
    List<UUID> drainDue(long now, int limit) {
        List<UUID> out = new ArrayList<>();
        Iterator<Map.Entry<UUID, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext() && out.size() < limit) {
            Map.Entry<UUID, Pending> e = it.next();
            if (e.getValue().due() <= now) {
                out.add(e.getKey());
                it.remove();
            }
        }
        return out;
    }

    boolean isPending(UUID island) {
        return pending.containsKey(island);
    }

    void forget(UUID island) {
        pending.remove(island);
    }

    int size() {
        return pending.size();
    }
}

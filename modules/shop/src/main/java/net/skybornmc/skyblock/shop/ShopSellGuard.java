package net.skybornmc.skyblock.shop;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * GUI-P3 section 6, guard layer 2: one shop sale in flight per player and a minimum gap between two shop sales
 * ({@code sell.min-gap-ms}). Pure; main thread only.
 */
final class ShopSellGuard {

    private final Set<UUID> inFlight = new HashSet<>();
    private final Map<UUID, Long> last = new HashMap<>();

    /** True (and the sale counts as started) when no sale is in flight and the last one is at least {@code gapMs} ago. */
    boolean tryStart(UUID player, long now, long gapMs) {
        if (inFlight.contains(player)) {
            return false;
        }
        Long before = last.get(player);
        if (before != null && gapMs > 0 && now - before < gapMs) {
            return false;
        }
        inFlight.add(player);
        last.put(player, now);
        return true;
    }

    /** The sale ended (paid, refused or nothing attempted): the next one may start after the gap. */
    void finish(UUID player, long now) {
        inFlight.remove(player);
        last.put(player, now);
    }

    boolean inFlight(UUID player) {
        return inFlight.contains(player);
    }

    void forget(UUID player) {
        inFlight.remove(player);
        last.remove(player);
    }

    void clear() {
        inFlight.clear();
        last.clear();
    }
}

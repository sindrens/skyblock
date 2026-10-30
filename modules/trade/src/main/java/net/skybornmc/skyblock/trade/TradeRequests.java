package net.skybornmc.skyblock.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Open trade requests (sender → target, with an expiry) and the request cooldown per sender. Main thread only. Pure
 * logic, unit-tested.
 */
final class TradeRequests {

    /** target → (sender → expires at), oldest first. */
    private final Map<UUID, LinkedHashMap<UUID, Long>> incoming = new HashMap<>();
    private final Map<UUID, Long> lastSent = new HashMap<>();

    /** Stores (or refreshes) a request. */
    void send(UUID from, UUID to, long now, long ttlMillis) {
        LinkedHashMap<UUID, Long> map = incoming.computeIfAbsent(to, k -> new LinkedHashMap<>());
        map.remove(from);
        map.put(from, now + ttlMillis);
        lastSent.put(from, now);
    }

    /** Milliseconds until {@code from} may send the next request. */
    long cooldownLeft(UUID from, long now, long cooldownMillis) {
        Long last = lastSent.get(from);
        return last == null || cooldownMillis <= 0 ? 0 : Math.max(0, last + cooldownMillis - now);
    }

    /** True if {@code from} has an unexpired request to {@code to}. */
    boolean has(UUID from, UUID to, long now) {
        LinkedHashMap<UUID, Long> map = incoming.get(to);
        if (map == null) {
            return false;
        }
        Long expires = map.get(from);
        return expires != null && expires > now;
    }

    /** Senders with an unexpired request to {@code to}, oldest first. */
    List<UUID> incoming(UUID to, long now) {
        prune(now);
        LinkedHashMap<UUID, Long> map = incoming.get(to);
        return map == null ? List.of() : new ArrayList<>(map.keySet());
    }

    /** Removes one request; true if it existed (and was not expired). */
    boolean remove(UUID from, UUID to, long now) {
        LinkedHashMap<UUID, Long> map = incoming.get(to);
        if (map == null) {
            return false;
        }
        Long expires = map.remove(from);
        if (map.isEmpty()) {
            incoming.remove(to);
        }
        return expires != null && expires > now;
    }

    /** Forgets every request from or to {@code player} (logout, trade started). */
    void forget(UUID player) {
        incoming.remove(player);
        for (Iterator<Map.Entry<UUID, LinkedHashMap<UUID, Long>>> it = incoming.entrySet().iterator(); it.hasNext(); ) {
            LinkedHashMap<UUID, Long> map = it.next().getValue();
            map.remove(player);
            if (map.isEmpty()) {
                it.remove();
            }
        }
    }

    /** Drops expired requests and returns them as (sender, target) pairs, so both can be told. */
    List<UUID[]> prune(long now) {
        List<UUID[]> expired = new ArrayList<>();
        for (Iterator<Map.Entry<UUID, LinkedHashMap<UUID, Long>>> it = incoming.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, LinkedHashMap<UUID, Long>> e = it.next();
            for (Iterator<Map.Entry<UUID, Long>> inner = e.getValue().entrySet().iterator(); inner.hasNext(); ) {
                Map.Entry<UUID, Long> r = inner.next();
                if (r.getValue() <= now) {
                    expired.add(new UUID[]{r.getKey(), e.getKey()});
                    inner.remove();
                }
            }
            if (e.getValue().isEmpty()) {
                it.remove();
            }
        }
        return expired;
    }

    void clear() {
        incoming.clear();
        lastSent.clear();
    }
}

package net.skybornmc.skyblock.milestones;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Counted actions waiting for the next flush (Complete Developer Spec §7.11: no database call in the event; flush every
 * 10 s, on quit, profile switch and stop). Main thread only; O(1) per action. Pure, unit-tested.
 */
final class ProgressBuffer {

    enum Scope { ISLAND, PROFILE }

    /** One pending amount: scope, chain and (for island chains) the member who did it. */
    record Key(Scope scope, UUID scopeId, String chainId, UUID actor) {
    }

    private Map<Key, Long> pending = new HashMap<>();

    void add(Scope scope, UUID scopeId, String chainId, UUID actor, long amount) {
        if (scopeId == null || chainId == null || amount <= 0) {
            return;
        }
        pending.merge(new Key(scope, scopeId, chainId, scope == Scope.ISLAND ? actor : null), amount, Long::sum);
    }

    /** What is pending for one scope and chain (shown in the menu before the flush). */
    long pending(Scope scope, UUID scopeId, String chainId) {
        long n = 0;
        for (Map.Entry<Key, Long> e : pending.entrySet()) {
            Key k = e.getKey();
            if (k.scope() == scope && k.scopeId().equals(scopeId) && k.chainId().equals(chainId)) {
                n += e.getValue();
            }
        }
        return n;
    }

    /** Takes everything pending (the flush writes it; a failed write puts it back with {@link #putBack}). */
    Map<Key, Long> drain() {
        Map<Key, Long> out = pending;
        pending = new HashMap<>();
        return out;
    }

    /** Takes what is pending for one actor's profile scope and their island contributions (quit, profile switch). */
    Map<Key, Long> drain(UUID profile) {
        Map<Key, Long> out = new HashMap<>();
        List<Key> keys = new ArrayList<>(pending.keySet());
        for (Key k : keys) {
            if (profile.equals(k.actor()) || (k.scope() == Scope.PROFILE && profile.equals(k.scopeId()))) {
                out.put(k, pending.remove(k));
            }
        }
        return out;
    }

    void putBack(Map<Key, Long> batch) {
        batch.forEach((k, v) -> pending.merge(k, v, Long::sum));
    }

    boolean isEmpty() {
        return pending.isEmpty();
    }
}

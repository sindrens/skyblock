package net.skybornmc.skyblock.common.menu;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bedrock popups waiting for a player (GUI-5): a popup never opens over another open menu, so it waits here until the
 * player's window closes. Per player, first in first out; the same id replaces the older entry (a renewed TPA request
 * shows once); the oldest entry is dropped beyond {@code max}; entries that expired or waited longer than
 * {@code maxAgeMillis} are skipped. Pure (no Bukkit), unit-tested.
 */
public final class OfferQueue<T extends OfferQueue.Queued> {

    /** What the queue needs from an entry. */
    public interface Queued {
        /** Same id = same offer. */
        String id();

        /** True when it can no longer be answered. */
        boolean expired(long now);
    }

    private record Entry<T>(T value, long queuedAt) {
    }

    private final Map<UUID, LinkedHashMap<String, Entry<T>>> byPlayer = new ConcurrentHashMap<>();
    private int max;
    private long maxAgeMillis;

    public OfferQueue(int max, long maxAgeMillis) {
        configure(max, maxAgeMillis);
    }

    public void configure(int max, long maxAgeMillis) {
        this.max = Math.max(1, max);
        this.maxAgeMillis = Math.max(0, maxAgeMillis);
    }

    /** Queues {@code value} for {@code player} at the back (an entry with the same id is replaced and moves back). */
    public synchronized void add(UUID player, T value, long now) {
        LinkedHashMap<String, Entry<T>> list = byPlayer.computeIfAbsent(player, k -> new LinkedHashMap<>());
        list.remove(value.id());
        list.put(value.id(), new Entry<>(value, now));
        while (list.size() > max) {
            Iterator<String> oldest = list.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /** Removes and returns the first entry that can still be shown; expired and too old entries are dropped on the way. */
    public synchronized Optional<T> next(UUID player, long now) {
        LinkedHashMap<String, Entry<T>> list = byPlayer.get(player);
        if (list == null) {
            return Optional.empty();
        }
        Optional<T> found = Optional.empty();
        Iterator<Entry<T>> it = list.values().iterator();
        while (it.hasNext()) {
            Entry<T> e = it.next();
            it.remove();
            if (!stale(e, now)) {
                found = Optional.of(e.value());
                break;
            }
        }
        if (list.isEmpty()) {
            byPlayer.remove(player);
        }
        return found;
    }

    /** True if anything (even stale) waits for {@code player}: a cheap check before scheduling work. */
    public boolean has(UUID player) {
        return byPlayer.containsKey(player);
    }

    public synchronized int size(UUID player) {
        LinkedHashMap<String, Entry<T>> list = byPlayer.get(player);
        return list == null ? 0 : list.size();
    }

    /** Drops everything for {@code player} (quit). */
    public void forget(UUID player) {
        byPlayer.remove(player);
    }

    public void clear() {
        byPlayer.clear();
    }

    private boolean stale(Entry<T> e, long now) {
        return e.value().expired(now) || (maxAgeMillis > 0 && now - e.queuedAt() >= maxAgeMillis);
    }
}

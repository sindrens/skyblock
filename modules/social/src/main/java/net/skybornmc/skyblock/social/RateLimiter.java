package net.skybornmc.skyblock.social;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sliding-window rate limit per player (e.g. 5 private messages per 5 seconds). Thread-safe. */
final class RateLimiter {

    private final Map<UUID, Deque<Long>> hits = new ConcurrentHashMap<>();

    /**
     * Records a hit if allowed.
     *
     * @return 0 if allowed, otherwise the milliseconds until the next hit is allowed
     */
    long tryAcquire(UUID key, long now, int max, long windowMillis) {
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() >= windowMillis) {
                deque.pollFirst();
            }
            if (deque.size() >= max) {
                return Math.max(1, windowMillis - (now - deque.peekFirst()));
            }
            deque.addLast(now);
            return 0;
        }
    }

    void forget(UUID key) {
        hits.remove(key);
    }
}

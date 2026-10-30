package net.skybornmc.skyblock.moderation;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Click again within 5s to apply anyway" (spec 12.2). When the duplicate check blocks a staff member, that
 * (staff, target, reason) is armed; the same request again within {@code duplicate-confirm} goes through. One use per
 * arming. In memory only: a restart simply asks for the second click again.
 */
final class DuplicateGuard {

    /** Upper bound on remembered armings (stale ones are dropped first). */
    private static final int MAX = 2048;

    private final Map<String, Long> armed = new ConcurrentHashMap<>();

    static String key(String actor, UUID target, String reasonKey) {
        return actor + "|" + target + "|" + reasonKey.toLowerCase(java.util.Locale.ROOT);
    }

    /** Remembers a blocked attempt at {@code now}. */
    void arm(String key, long now, Duration window) {
        if (armed.size() >= MAX) {
            long limit = window == null ? 0 : window.toMillis();
            armed.values().removeIf(t -> now - t > limit);
            if (armed.size() >= MAX) {
                armed.clear();
            }
        }
        armed.put(key, now);
    }

    /** True (and disarmed) if {@code key} was armed within {@code window} before {@code now}. */
    boolean consume(String key, long now, Duration window) {
        Long at = armed.remove(key);
        return at != null && window != null && now - at >= 0 && now - at <= window.toMillis();
    }
}

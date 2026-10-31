package net.skybornmc.skyblock.economy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owner R3-40: a soft limit of N money notes per player in any 60 seconds (replaces the old 10 s cooldown). A sliding
 * window of the times of the player's last notes; thread-safe, in memory (a restart resets it). Unit-tested.
 */
final class NoteRate {

    static final long WINDOW_MS = 60_000L;

    private final Map<UUID, Deque<Long>> made = new ConcurrentHashMap<>();

    /**
     * Records a note at {@code now} if the player made fewer than {@code max} in the last minute. Returns 0 when recorded,
     * otherwise the milliseconds until the next note is possible. {@code max <= 0} = no limit (always recorded as 0).
     */
    long tryTake(UUID player, int max, long now) {
        if (max <= 0) {
            return 0;
        }
        Deque<Long> times = made.computeIfAbsent(player, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_MS) {
                times.pollFirst();
            }
            if (times.size() >= max) {
                return Math.max(1, times.peekFirst() + WINDOW_MS - now);
            }
            times.addLast(now);
            return 0;
        }
    }

    /** Gives back the newest recorded note (the withdraw failed, nothing was made). */
    void giveBack(UUID player) {
        Deque<Long> times = made.get(player);
        if (times != null) {
            synchronized (times) {
                times.pollLast();
            }
        }
    }

    /** Forgets a player (quit). */
    void forget(UUID player) {
        made.remove(player);
    }
}

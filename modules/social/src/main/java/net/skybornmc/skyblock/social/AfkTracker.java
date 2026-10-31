package net.skybornmc.skyblock.social;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is AFK and when everyone was last active. Pure state (no Bukkit), so it is easy to test; the module feeds it
 * activity events and asks {@link #dueForAuto} once per check.
 */
final class AfkTracker {

    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();
    private final Set<UUID> afk = ConcurrentHashMap.newKeySet();

    /** Marks activity. Returns {@code true} if the player was AFK and is now back. */
    boolean active(UUID uuid, long now) {
        lastActive.put(uuid, now);
        return afk.remove(uuid);
    }

    /** Sets AFK on/off. Returns {@code true} if the state changed. */
    boolean set(UUID uuid, boolean value, long now) {
        if (value) {
            return afk.add(uuid);
        }
        lastActive.put(uuid, now);
        return afk.remove(uuid);
    }

    boolean isAfk(UUID uuid) {
        return afk.contains(uuid);
    }

    /**
     * QOL3: how long an AFK player has been idle (since their last activity), or -1 when not AFK. Shown to people who
     * message them ("Steve is AFK (12m)").
     */
    long afkFor(UUID uuid, long now) {
        if (!afk.contains(uuid)) {
            return -1;
        }
        Long at = lastActive.get(uuid);
        return at == null ? 0 : Math.max(0, now - at);
    }

    /** Players inactive for at least {@code idleMillis} who are not AFK yet. Any thread. */
    Set<UUID> dueForAuto(long now, long idleMillis) {
        Set<UUID> due = new java.util.HashSet<>();
        lastActive.forEach((uuid, at) -> {
            if (!afk.contains(uuid) && now - at >= idleMillis) {
                due.add(uuid);
            }
        });
        return due.isEmpty() ? Set.of() : due;
    }

    /**
     * PERF8: the same test as {@link #dueForAuto} for one player, repeated on the main thread for the players a scan
     * found (activity in between keeps them active). A player who left (no entry) is not due.
     */
    boolean stillDue(UUID uuid, long now, long idleMillis) {
        Long at = lastActive.get(uuid);
        return at != null && !afk.contains(uuid) && now - at >= idleMillis;
    }

    void remove(UUID uuid) {
        lastActive.remove(uuid);
        afk.remove(uuid);
    }
}

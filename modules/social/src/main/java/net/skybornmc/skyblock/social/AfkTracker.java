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

    /** Players inactive for at least {@code idleMillis} who are not AFK yet. */
    Set<UUID> dueForAuto(long now, long idleMillis) {
        Set<UUID> due = new java.util.HashSet<>();
        lastActive.forEach((uuid, at) -> {
            if (!afk.contains(uuid) && now - at >= idleMillis) {
                due.add(uuid);
            }
        });
        return due;
    }

    void remove(UUID uuid) {
        lastActive.remove(uuid);
        afk.remove(uuid);
    }
}

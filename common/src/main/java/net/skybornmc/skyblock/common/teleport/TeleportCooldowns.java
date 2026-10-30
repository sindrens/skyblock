package net.skybornmc.skyblock.common.teleport;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** In-memory cooldowns per player and {@link TeleportReason} (not persisted; survive relogs, not restarts). */
public final class TeleportCooldowns {

    private final Map<UUID, Map<TeleportReason, Long>> until = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public TeleportCooldowns(LongSupplier clock) {
        this.clock = clock;
    }

    /** Milliseconds left, 0 when ready. */
    public long left(UUID player, TeleportReason reason) {
        Map<TeleportReason, Long> map = until.get(player);
        if (map == null) {
            return 0;
        }
        synchronized (map) {
            Long end = map.get(reason);
            return end == null ? 0 : Math.max(0, end - clock.getAsLong());
        }
    }

    /** Starts a cooldown of {@code seconds} (0 or less clears it). */
    public void start(UUID player, TeleportReason reason, int seconds) {
        if (seconds <= 0) {
            clear(player, reason);
            return;
        }
        Map<TeleportReason, Long> map = until.computeIfAbsent(player, u -> new EnumMap<>(TeleportReason.class));
        synchronized (map) {
            map.put(reason, clock.getAsLong() + seconds * 1000L);
        }
    }

    public void clear(UUID player, TeleportReason reason) {
        Map<TeleportReason, Long> map = until.get(player);
        if (map != null) {
            synchronized (map) {
                map.remove(reason);
            }
        }
    }

    /** Drops this player's expired cooldowns (on quit), keeping memory flat. */
    public void prune(UUID player) {
        Map<TeleportReason, Long> map = until.get(player);
        if (map == null) {
            return;
        }
        long now = clock.getAsLong();
        synchronized (map) {
            map.values().removeIf(end -> end <= now);
            if (map.isEmpty()) {
                until.remove(player, map);
            }
        }
    }

    /** Players with at least one stored cooldown (tests). */
    int size() {
        return until.size();
    }
}

package net.skybornmc.skyblock.common.util;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory cooldowns per player and key ({@code "kit_starter"}, {@code "tpa"} …). Not persisted: anything that must
 * survive a restart (e.g. the 24 h starter kit) stores its timestamp in the database instead.
 */
public final class Cooldowns {

    private final Map<UUID, Map<String, Long>> expiries = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public Cooldowns() {
        this(System::currentTimeMillis);
    }

    Cooldowns(LongSupplier clock) {
        this.clock = clock;
    }

    /** Time left, or {@link Duration#ZERO} if ready. */
    public Duration remaining(UUID player, String key) {
        Map<String, Long> map = expiries.get(player);
        if (map == null) {
            return Duration.ZERO;
        }
        Long until = map.get(key);
        long left = until == null ? 0 : until - clock.getAsLong();
        if (left <= 0) {
            if (until != null) {
                map.remove(key, until);
            }
            return Duration.ZERO;
        }
        return Duration.ofMillis(left);
    }

    public boolean ready(UUID player, String key) {
        return remaining(player, key).isZero();
    }

    public void set(UUID player, String key, Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            clear(player, key);
            return;
        }
        expiries.computeIfAbsent(player, u -> new ConcurrentHashMap<>()).put(key, clock.getAsLong() + duration.toMillis());
    }

    /**
     * Atomically checks and starts the cooldown.
     *
     * @return true if it was ready (and is now on cooldown), false if still cooling down
     */
    public boolean tryUse(UUID player, String key, Duration duration) {
        Map<String, Long> map = expiries.computeIfAbsent(player, u -> new ConcurrentHashMap<>());
        long now = clock.getAsLong();
        boolean[] used = {false};
        map.compute(key, (k, until) -> {
            if (until != null && until > now) {
                return until;
            }
            used[0] = true;
            return now + Math.max(1, duration.toMillis());
        });
        return used[0];
    }

    public void clear(UUID player, String key) {
        Map<String, Long> map = expiries.get(player);
        if (map != null) {
            map.remove(key);
        }
    }

    /** Forget a player (on quit), keeping memory flat. */
    public void clear(UUID player) {
        expiries.remove(player);
    }
}

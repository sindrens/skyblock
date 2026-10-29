package net.skybornmc.skyblock.common.menu;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player click limiter: at most one accepted click per {@code intervalMillis}. */
public final class Debounce {

    private final Map<UUID, Long> last = new ConcurrentHashMap<>();
    private volatile long intervalMillis;

    public Debounce(long intervalMillis) {
        this.intervalMillis = Math.max(0, intervalMillis);
    }

    public void interval(long millis) {
        this.intervalMillis = Math.max(0, millis);
    }

    /** {@code true} if the click is accepted (and remembered), {@code false} if it came too fast. */
    public boolean tryClick(UUID player, long nowMillis) {
        long interval = intervalMillis;
        boolean[] accepted = {false};
        last.compute(player, (id, previous) -> {
            if (previous == null || nowMillis - previous >= interval || nowMillis < previous) {
                accepted[0] = true;
                return nowMillis;
            }
            return previous;
        });
        return accepted[0];
    }

    public void forget(UUID player) {
        last.remove(player);
    }
}

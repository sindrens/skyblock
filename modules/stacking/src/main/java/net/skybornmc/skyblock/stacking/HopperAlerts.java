package net.skybornmc.skyblock.stacking;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * When a chunk's hopper count is worth a staff alert (owner F13), free of Bukkit so it is unit-tested.
 *
 * <p>A chunk is at alert level 0 up to the threshold, level 1 above it, and one level higher for every further
 * {@code step} hoppers (step 0: only level 1). A rising level alerts, rate-limited: the same chunk at the same or a lower
 * level stays quiet for the cooldown (placing and breaking the 65th hopper does not spam), a higher level alerts at once,
 * and all chunks together alert at most {@code maxPerMinute} times a minute. Main thread only.</p>
 */
final class HopperAlerts {

    private record Ref(String world, long chunk) {
    }

    private record Sent(long at, int level) {
    }

    private static final int PRUNE_ABOVE = 2048;

    private final Map<Ref, Sent> sent = new HashMap<>();
    private final ArrayDeque<Long> recent = new ArrayDeque<>();

    static int level(int count, int threshold, int step) {
        if (count <= threshold) {
            return 0;
        }
        return step <= 0 ? 1 : 1 + (count - threshold - 1) / step;
    }

    /** True when going from {@code before} to {@code after} hoppers reaches a new alert level. */
    static boolean rises(int before, int after, int threshold, int step) {
        return level(after, threshold, step) > level(before, threshold, step);
    }

    /**
     * Decides whether the (verified) count of a chunk alerts now and, if so, records it.
     *
     * @param cooldownMs   quiet time per chunk for the same or a lower level
     * @param maxPerMinute alerts for all chunks together per minute (0 = no alerts)
     */
    boolean tryAlert(String world, long chunk, int count, int threshold, int step, long cooldownMs, int maxPerMinute, long now) {
        int level = level(count, threshold, step);
        if (level == 0) {
            return false;
        }
        Ref ref = new Ref(world, chunk);
        Sent last = sent.get(ref);
        if (last != null && level <= last.level() && now - last.at() < cooldownMs) {
            return false;
        }
        while (!recent.isEmpty() && now - recent.peekFirst() >= 60_000L) {
            recent.pollFirst();
        }
        if (recent.size() >= maxPerMinute) {
            return false; // not recorded: the next rise in this chunk may alert
        }
        recent.addLast(now);
        sent.put(ref, new Sent(now, level));
        if (sent.size() > PRUNE_ABOVE) {
            prune(cooldownMs, now);
        }
        return true;
    }

    private void prune(long cooldownMs, long now) {
        for (Iterator<Sent> it = sent.values().iterator(); it.hasNext(); ) {
            if (now - it.next().at() >= cooldownMs) {
                it.remove();
            }
        }
    }

    int remembered() {
        return sent.size();
    }

    void clear() {
        sent.clear();
        recent.clear();
    }
}

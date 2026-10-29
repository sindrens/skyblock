package net.skybornmc.skyblock.common.util;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Times named main-thread work and warns when it is slower than {@code core.yml → slow-operation-ms}.
 * Warnings are rate-limited per name (one per minute, with a count of how often it happened in between),
 * so a slow task every tick can't flood the console.
 */
public final class SlowOperationTimer {

    static final long WARN_INTERVAL_NANOS = 60_000_000_000L;

    private static final class Stats {
        long lastWarn = Long.MIN_VALUE;
        int suppressed;
        long worstNanos;
    }

    private final Logger log;
    private final IntSupplier thresholdMs;
    private final LongSupplier nanoClock;
    private final Map<String, Stats> stats = new ConcurrentHashMap<>();

    /** @param thresholdMs read on every call so a reload of core.yml applies immediately */
    public SlowOperationTimer(Logger log, IntSupplier thresholdMs) {
        this(log, thresholdMs, System::nanoTime);
    }

    SlowOperationTimer(Logger log, IntSupplier thresholdMs, LongSupplier nanoClock) {
        this.log = Objects.requireNonNull(log, "log");
        this.thresholdMs = Objects.requireNonNull(thresholdMs, "thresholdMs");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    public void time(String name, Runnable work) {
        long start = nanoClock.getAsLong();
        try {
            work.run();
        } finally {
            record(name, nanoClock.getAsLong() - start);
        }
    }

    public <T> T time(String name, Supplier<T> work) {
        long start = nanoClock.getAsLong();
        try {
            return work.get();
        } finally {
            record(name, nanoClock.getAsLong() - start);
        }
    }

    /**
     * Records a measured duration (for code that measures itself).
     *
     * @return true if it was over the threshold
     */
    public boolean record(String name, long nanos) {
        long threshold = thresholdMs.getAsInt() * 1_000_000L;
        if (nanos <= threshold) {
            return false;
        }
        Stats s = stats.computeIfAbsent(name, n -> new Stats());
        long now = nanoClock.getAsLong();
        String message = null;
        synchronized (s) {
            s.worstNanos = Math.max(s.worstNanos, nanos);
            if (s.lastWarn == Long.MIN_VALUE || now - s.lastWarn >= WARN_INTERVAL_NANOS) {
                message = "Slow main-thread operation '" + name + "': " + format(nanos) + " (limit "
                        + thresholdMs.getAsInt() + " ms)"
                        + (s.suppressed > 0 ? "; " + s.suppressed + " more slow runs in the last minute, worst "
                        + format(s.worstNanos) : "");
                s.lastWarn = now;
                s.suppressed = 0;
                s.worstNanos = 0;
            } else {
                s.suppressed++;
            }
        }
        if (message != null) {
            log.warning(message);
        }
        return true;
    }

    private static String format(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.1f ms", nanos / 1_000_000.0);
    }
}

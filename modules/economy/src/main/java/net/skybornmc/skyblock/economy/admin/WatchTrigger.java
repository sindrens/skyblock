package net.skybornmc.skyblock.economy.admin;

import java.util.concurrent.atomic.AtomicLong;

/**
 * PERF11: the decisions of the economy watch's periodic trigger, which runs off the main thread (a Bukkit async repeat):
 * whether a run starts, whether its result needs the main thread (only when it found alerts), and the rate limit of the
 * "background run failed" warning. Pure and thread-safe.
 */
final class WatchTrigger {

    private final long warnEveryMs;
    private final AtomicLong lastWarning = new AtomicLong();

    WatchTrigger(long warnEveryMs) {
        this.warnEveryMs = warnEveryMs;
    }

    /** A run starts when the watch is on, the job exists and no run is going. */
    static boolean shouldStart(boolean enabled, WatchJob job) {
        return enabled && job != null && !job.busy();
    }

    /** Only a run that found alerts has work for the main thread (chat, sounds, audit, Discord, suggestions). */
    static boolean needsMain(WatchJob.Result result) {
        return result != null && !result.alerts().isEmpty();
    }

    /** True at most once per {@code warnEveryMs} (the first failure always warns); safe from any thread. */
    boolean warnDue(long now) {
        while (true) {
            long last = lastWarning.get();
            if (now - last <= warnEveryMs) {
                return false;
            }
            if (lastWarning.compareAndSet(last, now)) {
                return true;
            }
        }
    }
}

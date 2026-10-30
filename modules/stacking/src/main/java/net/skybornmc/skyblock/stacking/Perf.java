package net.skybornmc.skyblock.stacking;

import java.util.function.DoubleSupplier;
import java.util.logging.Logger;

/**
 * Measures the main-thread time of the stacking module (every handler and task adds its nanoseconds). Once a minute
 * the average per second is compared with {@code perf.warn-ms-per-second} and logged when above it, so lag from
 * stacking shows up in the console with numbers instead of guesses.
 */
final class Perf {

    private final Logger log;
    private final DoubleSupplier warnMsPerSecond;
    private long nanos;
    private long calls;
    private long windowStart = System.nanoTime();
    private double lastMsPerSecond;

    Perf(Logger log, DoubleSupplier warnMsPerSecond) {
        this.log = log;
        this.warnMsPerSecond = warnMsPerSecond;
    }

    long start() {
        return System.nanoTime();
    }

    void end(long start) {
        nanos += System.nanoTime() - start;
        calls++;
    }

    /** Called once a minute by the module. */
    void report() {
        long now = System.nanoTime();
        double seconds = Math.max(1e-3, (now - windowStart) / 1e9);
        lastMsPerSecond = msPerSecond(nanos, seconds);
        if (lastMsPerSecond > warnMsPerSecond.getAsDouble()) {
            log.warning(String.format(java.util.Locale.ROOT, "Stacking used %.2f ms of main-thread time per second over the last %.0f s "
                    + "(%d calls; limit perf.warn-ms-per-second = %.2f).", lastMsPerSecond, seconds, calls, warnMsPerSecond.getAsDouble()));
        }
        nanos = 0;
        calls = 0;
        windowStart = now;
    }

    double lastMsPerSecond() {
        return lastMsPerSecond;
    }

    static double msPerSecond(long nanos, double seconds) {
        return seconds <= 0 ? 0 : nanos / 1e6 / seconds;
    }
}

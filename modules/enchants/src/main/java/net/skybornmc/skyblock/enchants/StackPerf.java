package net.skybornmc.skyblock.enchants;

import java.util.function.DoubleSupplier;
import java.util.logging.Logger;

/**
 * Measures the main-thread time of the stacked-mob enchants (kill counts, bonuses, sweeps, Phoenix Wrath hit checks). Once a minute
 * the average per second is compared with {@code enchanter.yml → stacks.perf-warn-ms-per-second} and logged when above it, so lag from
 * these enchants shows up in the console with numbers instead of guesses.
 */
final class StackPerf {

    private final Logger log;
    private final DoubleSupplier warnMsPerSecond;
    private final String label;
    private final String setting;
    private long nanos;
    private long calls;
    private long windowStart = System.nanoTime();
    private double lastMsPerSecond;

    StackPerf(Logger log, DoubleSupplier warnMsPerSecond) {
        this(log, warnMsPerSecond, "Stacked-mob enchants", EnchanterConfig.PERF_STACKS);
    }

    /** Same timer for another hot path (E2 gathering/sword effects), with its own log label and setting name. */
    StackPerf(Logger log, DoubleSupplier warnMsPerSecond, String label, String setting) {
        this.log = log;
        this.warnMsPerSecond = warnMsPerSecond;
        this.label = label;
        this.setting = setting;
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
            log.warning(String.format(java.util.Locale.ROOT, "%s used %.2f ms of main-thread time per second over the last %.0f s "
                    + "(%d calls; limit %s = %.2f).", label, lastMsPerSecond, seconds, calls, setting, warnMsPerSecond.getAsDouble()));
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

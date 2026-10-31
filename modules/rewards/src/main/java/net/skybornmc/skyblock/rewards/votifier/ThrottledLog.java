package net.skybornmc.skyblock.rewards.votifier;

import java.util.function.Consumer;

/**
 * Rejected connections reach the console at most once per interval (pure, clock passed in; thread-safe): the first one
 * is written at once, the ones after it are counted and the next written line says how many there were. A flood of bad
 * packets can't fill the log.
 */
public final class ThrottledLog {

    private final Consumer<String> sink;
    private volatile long intervalMillis;
    private long lastWritten = Long.MIN_VALUE;
    private int suppressed;

    public ThrottledLog(long intervalMillis, Consumer<String> sink) {
        this.intervalMillis = Math.max(0, intervalMillis);
        this.sink = sink;
    }

    public void interval(long intervalMillis) {
        this.intervalMillis = Math.max(0, intervalMillis);
    }

    /** Reports one reject; true when a line was written. */
    public boolean reject(String address, String reason, long now) {
        String line;
        synchronized (this) {
            if (lastWritten != Long.MIN_VALUE && now - lastWritten < intervalMillis && now >= lastWritten) {
                suppressed++;
                return false;
            }
            int before = suppressed;
            suppressed = 0;
            lastWritten = now;
            line = "Votifier: rejected a connection from " + address + ": " + reason
                    + (before > 0 ? " (+" + before + " more rejected since the last line)" : "");
        }
        sink.accept(line);
        return true;
    }
}

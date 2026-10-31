package net.skybornmc.skyblock.progression;

import java.util.Map;
import java.util.NavigableMap;
import java.util.OptionalLong;
import java.util.TreeMap;

/**
 * Island bank balance samples of one island (PHASE-5 M5.2 source 7, owner P5-5): the level counts the <b>lowest</b>
 * balance of the last window (24 h), so a big deposit just before a check does nothing. Pure, unit-tested.
 *
 * <p>The balance at the window start is the latest sample taken before it. While no sample is that old (a new island,
 * or the first day after this feature started) the bank counts 0: it was not observed for a whole window yet.</p>
 */
final class BankSamples {

    private final NavigableMap<Long, Long> samples = new TreeMap<>();

    /**
     * Adds a sample when the last one is older than {@code minGapMillis} or the balance went down (a drop always counts).
     *
     * @return true if the sample was kept
     */
    boolean add(long at, long balance, long minGapMillis) {
        Map.Entry<Long, Long> last = samples.lastEntry();
        if (last != null && at < last.getKey()) {
            return false; // never out of order
        }
        if (last != null && at - last.getKey() < minGapMillis && balance >= last.getValue()) {
            return false;
        }
        samples.put(at, Math.max(0, balance));
        return true;
    }

    /** Puts a stored sample back (load at start). */
    void restore(long at, long balance) {
        samples.put(at, Math.max(0, balance));
    }

    /** PERF11: puts every sample of {@code stored} back (same result as {@link #restore} for each of them, in any order). */
    void restoreAll(BankSamples stored) {
        samples.putAll(stored.samples);
    }

    /** The lowest balance in {@code [now - window, now]} including the balance at the window start; empty if not observed that long. */
    OptionalLong lowest(long now, long windowMillis) {
        long start = now - windowMillis;
        Map.Entry<Long, Long> before = samples.floorEntry(start);
        if (before == null) {
            return OptionalLong.empty();
        }
        long min = before.getValue();
        for (long v : samples.subMap(start, false, now, true).values()) {
            min = Math.min(min, v);
        }
        return OptionalLong.of(min);
    }

    /** Drops samples that can't matter any more (keeps the latest one before the window start). Returns how many. */
    int prune(long now, long windowMillis) {
        Long keep = samples.floorKey(now - windowMillis);
        if (keep == null) {
            return 0;
        }
        NavigableMap<Long, Long> old = samples.headMap(keep, false);
        int n = old.size();
        old.clear();
        return n;
    }

    /** The oldest sample time still kept, or {@code Long.MAX_VALUE}. */
    long oldest() {
        return samples.isEmpty() ? Long.MAX_VALUE : samples.firstKey();
    }

    int size() {
        return samples.size();
    }
}

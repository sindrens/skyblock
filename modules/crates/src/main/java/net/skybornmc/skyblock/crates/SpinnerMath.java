package net.skybornmc.skyblock.crates;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.random.RandomGenerator;

/**
 * The B40 spinner's math (pure, unit-tested). A strip of rewards moves one slot to the left per frame through the
 * window of slots 10-16; the frames come quickly at first and slow down, and the last frame puts the <b>already
 * decided</b> reward in the middle slot (13). The strip around it is decoration picked by chance; nothing here decides
 * what is won.
 */
final class SpinnerMath {

    /** Width of the window (slots 10-16). */
    static final int WINDOW = 7;
    /** The middle of the window (slot 13). */
    static final int CENTER = 3;
    /** PHASE-10: a Bedrock spinner updates the menu at most this often (frames + the final screen). */
    static final int BEDROCK_MAX_UPDATES = 20;

    private SpinnerMath() {
    }

    /** Frames of one spin: the configured value, Bedrock capped so frames + the final screen stay within the limit. */
    static int frames(boolean bedrock, int javaFrames, int bedrockFrames) {
        int f = bedrock ? Math.min(bedrockFrames, BEDROCK_MAX_UPDATES - 1) : javaFrames;
        return Math.max(2, f);
    }

    /**
     * The tick (after the start) of each frame: frame 0 at tick 0, the last at {@code duration}. The gaps grow (ease-out:
     * the row slows down onto the middle slot) and are at least one tick, so times strictly increase.
     */
    static int[] schedule(int frames, int duration) {
        int n = Math.max(2, frames);
        int d = Math.max(n - 1, duration);
        int[] out = new int[n];
        for (int i = 1; i < n; i++) {
            // Position follows 1-(1-t)^3, so the time of step i is the inverse: t = 1 - (1 - i/(n-1))^(1/3).
            double share = 1.0 - Math.cbrt(1.0 - (double) i / (n - 1));
            int t = (int) Math.round(share * d);
            // Leave room for the frames still to come (one tick each) and never go back in time.
            out[i] = Math.min(d - (n - 1 - i), Math.max(out[i - 1] + 1, t));
        }
        out[n - 1] = d;
        return out;
    }

    /** Index in the strip of the reward that ends in the middle slot (the decided one). */
    static int winnerIndex(int frames) {
        return Math.max(2, frames) - 1 + CENTER;
    }

    /** Length of the strip for {@code frames} frames. */
    static int stripLength(int frames) {
        return Math.max(2, frames) - 1 + WINDOW;
    }

    /**
     * The strip: random decoration from {@code pool} (by {@code weight}) with {@code winner} at {@link #winnerIndex}.
     * An empty pool fills with the winner.
     */
    static <T> List<T> strip(List<T> pool, ToDoubleFunction<T> weight, T winner, int frames, RandomGenerator random) {
        int length = stripLength(frames);
        int win = winnerIndex(frames);
        double total = 0;
        for (T t : pool) {
            total += Math.max(0, weight.applyAsDouble(t));
        }
        List<T> out = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            out.add(i == win || total <= 0 ? winner : pick(pool, weight, total, random));
        }
        return out;
    }

    private static <T> T pick(List<T> pool, ToDoubleFunction<T> weight, double total, RandomGenerator random) {
        double r = random.nextDouble() * total;
        for (T t : pool) {
            r -= Math.max(0, weight.applyAsDouble(t));
            if (r < 0) {
                return t;
            }
        }
        return pool.getLast();
    }

    /** What slots 10-16 show at {@code frame} (0-based; the last frame has the winner at {@link #CENTER}). */
    static <T> List<T> window(List<T> strip, int frame) {
        int start = Math.max(0, Math.min(frame, strip.size() - WINDOW));
        return strip.subList(start, start + WINDOW);
    }

    /** The frame that should be on screen {@code elapsed} ticks after the start (the last one once the spin is over). */
    static int frameAt(int[] schedule, long elapsed) {
        int frame = 0;
        for (int i = 0; i < schedule.length; i++) {
            if (schedule[i] <= elapsed) {
                frame = i;
            }
        }
        return frame;
    }
}

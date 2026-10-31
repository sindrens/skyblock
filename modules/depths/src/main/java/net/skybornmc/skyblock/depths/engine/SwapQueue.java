package net.skybornmc.skyblock.depths.engine;

import java.util.function.LongConsumer;

/**
 * The slot blocks of one tier swap still to be sent to a player (DV2-SPIKE). Keys are sorted nearest first; each tick
 * the engine drains at most the player's per-tick limit (Bedrock about 300: Geyser turns a multi-block change into one
 * packet per block). The block shown is decided when it is sent, so a newer swap or a respawn in between is respected.
 */
public final class SwapQueue {

    private final long[] keys;
    private int next;
    private final long startedNanos;
    private int ticks;

    public SwapQueue(long[] keys, int alreadySent, long startedNanos) {
        this.keys = keys;
        this.next = Math.max(0, Math.min(alreadySent, keys.length));
        this.startedNanos = startedNanos;
    }

    /** Hands at most {@code max} keys to {@code sink}. Returns how many. */
    public int drain(int max, LongConsumer sink) {
        int n = Math.min(Math.max(0, max), keys.length - next);
        for (int i = 0; i < n; i++) {
            sink.accept(keys[next++]);
        }
        ticks++;
        return n;
    }

    public int remaining() {
        return keys.length - next;
    }

    public boolean done() {
        return next >= keys.length;
    }

    public int total() {
        return keys.length;
    }

    /** Ticks spent draining (the first batch, sent at once, is not counted). */
    public int ticks() {
        return ticks;
    }

    public long startedNanos() {
        return startedNanos;
    }

    /** Batch sizes of a swap of {@code total} blocks at {@code perTick} per tick (first batch at once). */
    public static int[] batches(int total, int perTick) {
        if (total <= 0) {
            return new int[0];
        }
        int per = Math.max(1, perTick);
        int count = (int) (((long) total + per - 1) / per);
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = (int) Math.min(per, total - (long) i * per);
        }
        return out;
    }
}

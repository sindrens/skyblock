package net.skybornmc.skyblock.depths.engine;

import java.util.Arrays;

/**
 * Hashed timing wheel for respawn timers, one slot per server tick.
 *
 * <p>Scheduling and expiring are O(1) and allocation-free: entries live in parallel primitive arrays (a node pool with a
 * free list) that only grow when more timers are pending than ever before. Delays longer than the wheel wrap around
 * and are skipped until their tick comes. An idle wheel ({@link #size()} == 0) only increments its tick.</p>
 *
 * <p>An entry is {@code (owner, key, token)}: the engine uses the player's session handle, the packed block position
 * and a per-deplete token, so stale timers (player left, block restored early, re-depleted) are recognised by the
 * {@link Sink} and ignored instead of being searched for and removed. Not thread-safe (main thread only).</p>
 */
public final class TimingWheel {

    /** Receives every timer whose tick has come. May schedule new timers. */
    @FunctionalInterface
    public interface Sink {
        void expire(int owner, long key, int token);
    }

    private static final int NIL = -1;

    private final int mask;
    private final int[] heads;
    private int[] next;
    private int[] owners;
    private int[] tokens;
    private long[] keys;
    private long[] due;
    private int freeHead = NIL;
    private int highWater;
    private int size;
    private long tick;

    /**
     * @param slots    wheel size, rounded up to a power of two (delays up to this many ticks need no wrap-around)
     * @param capacity initial node pool size (grows by doubling when needed)
     */
    public TimingWheel(int slots, int capacity) {
        int s = Integer.highestOneBit(Math.max(2, slots - 1)) << 1;
        this.mask = s - 1;
        this.heads = new int[s];
        Arrays.fill(heads, NIL);
        int cap = Math.max(16, capacity);
        next = new int[cap];
        owners = new int[cap];
        tokens = new int[cap];
        keys = new long[cap];
        due = new long[cap];
    }

    /** Number of slots (a power of two). */
    public int slots() {
        return mask + 1;
    }

    /** Ticks advanced so far. */
    public long tick() {
        return tick;
    }

    /** Pending timers (stale ones included until their tick comes). */
    public int size() {
        return size;
    }

    /** Current node pool size (for the debug command). */
    public int capacity() {
        return next.length;
    }

    /** Fires {@code delayTicks} ticks from now (at least 1). */
    public void schedule(long delayTicks, int owner, long key, int token) {
        long at = tick + Math.max(1L, delayTicks);
        int n = allocate();
        owners[n] = owner;
        keys[n] = key;
        tokens[n] = token;
        due[n] = at;
        int slot = (int) (at & mask);
        next[n] = heads[slot];
        heads[slot] = n;
        size++;
    }

    /** Advances one tick and hands every timer due now to {@code sink}. Returns how many fired. */
    public int advance(Sink sink) {
        tick++;
        if (size == 0) {
            return 0;
        }
        int slot = (int) (tick & mask);
        // Detach due nodes into a private list first, so the sink can schedule into this same slot safely.
        int expired = NIL;
        int prev = NIL;
        int n = heads[slot];
        while (n != NIL) {
            int following = next[n];
            if (due[n] <= tick) {
                if (prev == NIL) {
                    heads[slot] = following;
                } else {
                    next[prev] = following;
                }
                next[n] = expired;
                expired = n;
            } else {
                prev = n;
            }
            n = following;
        }
        int fired = 0;
        while (expired != NIL) {
            int following = next[expired];
            int owner = owners[expired];
            long key = keys[expired];
            int token = tokens[expired];
            release(expired);
            size--;
            fired++;
            sink.expire(owner, key, token);
            expired = following;
        }
        return fired;
    }

    /** Advances until {@link #tick()} equals {@code targetTick} (catch-up after a missed tick). Returns timers fired. */
    public int advanceTo(long targetTick, Sink sink) {
        int fired = 0;
        while (tick < targetTick) {
            fired += advance(sink);
        }
        return fired;
    }

    /** Drops every timer without firing (module disable / reload). Keeps the pool. */
    public void clear() {
        Arrays.fill(heads, NIL);
        freeHead = NIL;
        highWater = 0;
        size = 0;
    }

    private int allocate() {
        if (freeHead != NIL) {
            int n = freeHead;
            freeHead = next[n];
            return n;
        }
        if (highWater == next.length) {
            int cap = next.length * 2;
            next = Arrays.copyOf(next, cap);
            owners = Arrays.copyOf(owners, cap);
            tokens = Arrays.copyOf(tokens, cap);
            keys = Arrays.copyOf(keys, cap);
            due = Arrays.copyOf(due, cap);
        }
        return highWater++;
    }

    private void release(int n) {
        next[n] = freeHead;
        freeHead = n;
    }
}

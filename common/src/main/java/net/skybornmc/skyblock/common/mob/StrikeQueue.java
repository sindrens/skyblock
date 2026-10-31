package net.skybornmc.skyblock.common.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Predicate;

/**
 * The running ability effects of all mobs (M8.1), driven by one shared scheduler. Every strike goes
 * <b>telegraph → hit pulse(s) → end</b>: the telegraph is shown the moment the strike starts, the first hit comes no
 * earlier than {@link #minTelegraphTicks} later (one second by default, so a careful player can always dodge), more
 * pulses follow every {@code interval} ticks (fire ring, leap landing) and {@code end} always runs once (restores fake
 * blocks, ends a shield), also for strikes that were cancelled (mob died) or cleared (reload, disable).
 *
 * <p>Pure: the caller passes the tick and a {@link Sink} that does the real work. Not thread-safe (main thread).
 * The queue is small (bounded by the shared effect cap), so a priority queue by next due tick is enough.</p>
 *
 * @param <T> what a strike carries (the runtime's effect state)
 */
public final class StrikeQueue<T> {

    /** Does the real work of a strike. */
    public interface Sink<T> {
        void telegraph(Strike<T> strike);

        void hit(Strike<T> strike, int pulse);

        void end(Strike<T> strike);
    }

    /** One running strike. */
    public static final class Strike<T> {
        private final T payload;
        private final long startedAt;
        private final long firstHitAt;
        private final int pulses;
        private final int interval;
        private final long endAt;
        private final long seq;
        private int done;
        private long due;
        private boolean cancelled;

        Strike(T payload, long startedAt, long firstHitAt, int pulses, int interval, long endAt, long seq) {
            this.payload = payload;
            this.startedAt = startedAt;
            this.firstHitAt = firstHitAt;
            this.pulses = pulses;
            this.interval = interval;
            this.endAt = endAt;
            this.seq = seq;
            this.due = pulses > 0 ? firstHitAt : endAt;
        }

        public T payload() {
            return payload;
        }

        public long startedAt() {
            return startedAt;
        }

        public long firstHitAt() {
            return firstHitAt;
        }

        public long endAt() {
            return endAt;
        }

        public int pulses() {
            return pulses;
        }

        public boolean cancelled() {
            return cancelled;
        }
    }

    private final Sink<T> sink;
    private final int minTelegraphTicks;
    private final PriorityQueue<Strike<T>> queue = new PriorityQueue<>((a, b) -> a.due != b.due ? Long.compare(a.due, b.due)
            : Long.compare(a.seq, b.seq));
    private long seq;

    /**
     * @param minTelegraphTicks shortest allowed time between telegraph and first hit (20 = one second)
     */
    public StrikeQueue(Sink<T> sink, int minTelegraphTicks) {
        this.sink = sink;
        this.minTelegraphTicks = Math.max(1, minTelegraphTicks);
    }

    public int minTelegraphTicks() {
        return minTelegraphTicks;
    }

    /** Running strikes (telegraphing or active). */
    public int active() {
        return queue.size();
    }

    /**
     * Starts a strike: shows its telegraph now and schedules its pulses and end.
     *
     * @param telegraphTicks ticks between telegraph and first hit (raised to {@link #minTelegraphTicks})
     * @param pulses         hit pulses (0 = telegraph only, then end)
     * @param intervalTicks  ticks between pulses
     * @param lingerTicks    ticks the strike stays after its last pulse before {@code end} (shield duration)
     */
    public Strike<T> start(long now, int telegraphTicks, int pulses, int intervalTicks, int lingerTicks, T payload) {
        long firstHit = now + Math.max(minTelegraphTicks, telegraphTicks);
        int p = Math.max(0, pulses);
        int interval = Math.max(1, intervalTicks);
        long lastPulse = p > 0 ? firstHit + (long) (p - 1) * interval : firstHit;
        Strike<T> strike = new Strike<>(payload, now, firstHit, p, interval, lastPulse + Math.max(0, lingerTicks), seq++);
        sink.telegraph(strike);
        queue.add(strike);
        return strike;
    }

    /** Runs every pulse and end that is due at {@code now}, in time order. */
    public void advance(long now) {
        while (!queue.isEmpty() && queue.peek().due <= now) {
            Strike<T> s = queue.poll();
            if (s.cancelled) {
                sink.end(s);
                continue;
            }
            if (s.done < s.pulses) {
                int pulse = s.done++;
                sink.hit(s, pulse);
                s.due = s.done < s.pulses ? s.firstHitAt + (long) s.done * s.interval : s.endAt;
                if (s.cancelled) {
                    s.due = now;
                }
                queue.add(s);
            } else {
                sink.end(s);
            }
        }
    }

    /** Cancels the strikes whose payload matches; their {@code end} runs on the next {@link #advance}. */
    public void cancel(Predicate<T> which) {
        List<Strike<T>> hit = new ArrayList<>();
        for (Strike<T> s : queue) {
            if (!s.cancelled && which.test(s.payload)) {
                hit.add(s);
            }
        }
        for (Strike<T> s : hit) {
            queue.remove(s);
            s.cancelled = true;
            s.due = Long.MIN_VALUE;
            queue.add(s);
        }
    }

    /** Ends every strike at once (reload, disable). */
    public void clear() {
        List<Strike<T>> all = new ArrayList<>(queue);
        queue.clear();
        all.sort((a, b) -> Long.compare(a.seq, b.seq));
        for (Strike<T> s : all) {
            s.cancelled = true;
            sink.end(s);
        }
    }
}

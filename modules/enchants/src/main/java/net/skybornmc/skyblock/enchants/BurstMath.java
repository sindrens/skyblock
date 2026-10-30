package net.skybornmc.skyblock.enchants;

/**
 * Speed bursts (ENCHANTS §6.1): Ember Momentum stacks and Phoenix Haste, plus the limits every burst obeys: the speed
 * family cap (the burst adds at most what the tool's own speed family leaves below the cap) and the hard cap of
 * {@code gathering.max-blocks-per-second} (6) manual breaks a second, above which no burst applies. Pure, unit-tested.
 */
final class BurstMath {

    private BurstMath() {
    }

    /** Ember Momentum: actions towards the next stack, current stacks, time of the last action. */
    record Momentum(int actions, int stacks, long last) {
        static final Momentum NONE = new Momentum(0, 0, Long.MIN_VALUE / 2);
    }

    /**
     * One more action: after an idle pause longer than {@code idleMs} everything starts over; every {@code perStack}
     * actions add a stack (up to {@code maxStacks}).
     */
    static Momentum momentum(Momentum m, long now, int perStack, long idleMs, int maxStacks) {
        Momentum cur = now - m.last() > idleMs ? Momentum.NONE : m;
        int actions = cur.actions() + 1;
        int stacks = cur.stacks();
        if (perStack > 0 && actions >= perStack) {
            actions = 0;
            stacks = Math.min(Math.max(0, maxStacks), stacks + 1);
        }
        return new Momentum(actions, stacks, now);
    }

    /** Stacks that still count at {@code now} (0 after the idle pause). */
    static int stacks(Momentum m, long now, long idleMs) {
        return now - m.last() > idleMs ? 0 : m.stacks();
    }

    /** Phoenix Haste: active until, next possible start. */
    record Haste(long activeUntil, long readyAt) {
        static final Haste NONE = new Haste(0, 0);
    }

    /**
     * One more action: when ready, {@code roll} (0..1) below the chance starts a burst of {@code durationMs}; the cooldown
     * starts when the burst ends.
     */
    static Haste haste(Haste h, long now, double roll, double chancePct, long durationMs, long cooldownMs) {
        if (now < h.readyAt() || !(chancePct > 0) || roll * 100 >= chancePct) {
            return h;
        }
        long until = now + Math.max(0, durationMs);
        return new Haste(until, until + Math.max(0, cooldownMs));
    }

    static boolean active(Haste h, long now) {
        return now < h.activeUntil();
    }

    /**
     * The burst that may apply, in percent: {@code wanted} limited to the room the tool's speed family leaves below the cap,
     * and 0 while the player breaks {@code maxPerSecond} or more blocks a second (the hard cap).
     */
    static double allowed(double wanted, double toolFamily, double cap, int breaksLastSecond, int maxPerSecond) {
        if (!(wanted > 0) || (maxPerSecond > 0 && breaksLastSecond >= maxPerSecond)) {
            return 0;
        }
        double room = Math.max(0, cap - Math.max(0, toolFamily));
        return Math.min(wanted, room);
    }

    /** Manual breaks in the last second (a small ring of timestamps). */
    static final class Rate {
        private final long[] times;
        private int next;

        Rate(int size) {
            this.times = new long[Math.max(1, size)];
            java.util.Arrays.fill(times, Long.MIN_VALUE / 2);
        }

        void add(long now) {
            times[next] = now;
            next = (next + 1) % times.length;
        }

        int lastSecond(long now) {
            int n = 0;
            for (long t : times) {
                if (now - t < 1000) {
                    n++;
                }
            }
            return n;
        }
    }
}

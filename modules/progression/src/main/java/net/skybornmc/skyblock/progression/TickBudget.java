package net.skybornmc.skyblock.progression;

/**
 * PERF5: a per-tick time budget for main-thread work that arrives in bursts (rescan chunk copies). The first piece of
 * work of a tick is always allowed, so the work always moves; after that only while the tick's spent time is below the
 * budget. Pure and main-thread only; unit-tested.
 */
final class TickBudget {

    private long tick = Long.MIN_VALUE;
    private long spentNanos;
    private int count;

    /** True when one more piece of work may run in {@code currentTick}. */
    boolean allow(long currentTick, long budgetNanos) {
        roll(currentTick);
        return count == 0 || spentNanos < budgetNanos;
    }

    /** Records one piece of work of {@code currentTick} that took {@code nanos}. */
    void spent(long currentTick, long nanos) {
        roll(currentTick);
        spentNanos += Math.max(0, nanos);
        count++;
    }

    int count(long currentTick) {
        roll(currentTick);
        return count;
    }

    private void roll(long currentTick) {
        if (currentTick != tick) {
            tick = currentTick;
            spentNanos = 0;
            count = 0;
        }
    }
}

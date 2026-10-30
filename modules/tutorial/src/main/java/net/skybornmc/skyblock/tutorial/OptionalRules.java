package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.tutorial.TutorialService.State;

/**
 * Decisions about optional steps (TUTORIAL.md §3.1, TQ8, TQ26): when they count, how progress adds up, when a tip may
 * show. Pure, unit-tested; {@link OptionalSteps} stores and shows what these return.
 */
final class OptionalRules {

    /** One account's state of one optional step. */
    static final class Entry {
        long tippedAt;
        long doneAt;
        boolean rewarded;
        /** Blocks broken, or the bit mask of visited targets. */
        int value;

        boolean done() {
            return doneAt > 0;
        }

        boolean tipped() {
            return tippedAt > 0;
        }
    }

    private OptionalRules() {
    }

    /** Optional steps count after the core tutorial (any complete state), or during it when {@code duringCore}. */
    static boolean counts(State state, boolean duringCore) {
        if (state == null) {
            return false;
        }
        return state.complete() || (duringCore && state == State.ACTIVE);
    }

    /** Adds broken blocks; returns the new value (never above {@code needed}). */
    static int addCount(int current, int amount, int needed) {
        if (amount <= 0) {
            return Math.min(current, needed);
        }
        return (int) Math.min(needed, (long) current + amount);
    }

    /** Marks target {@code index} visited. */
    static int visit(int mask, int index) {
        if (index < 0 || index >= OptionalStep.MAX_VISIT) {
            return mask;
        }
        return mask | (1 << index);
    }

    static boolean visited(int mask, int index) {
        return index >= 0 && index < OptionalStep.MAX_VISIT && (mask & (1 << index)) != 0;
    }

    /** How many of {@code total} targets are visited. */
    static int visitedCount(int mask, int total) {
        int n = 0;
        for (int i = 0; i < total && i < OptionalStep.MAX_VISIT; i++) {
            if (visited(mask, i)) {
                n++;
            }
        }
        return n;
    }

    /** Progress shown to the player: blocks broken / targets visited / 0-1. */
    static int progress(OptionalStep step, Entry e) {
        if (e == null) {
            return 0;
        }
        if (e.done()) {
            return step.needed();
        }
        return step.kind() == OptionalStep.Kind.VISIT ? visitedCount(e.value, step.args().size()) : Math.min(e.value, step.needed());
    }

    /** True when the step is finished by this progress. */
    static boolean complete(OptionalStep step, Entry e) {
        return progress(step, e) >= step.needed();
    }

    /**
     * A first-visit tip may show (TQ26): tips on, the step neither done nor tipped before, and the last tip at least
     * {@code cooldownMs} ago (one tip at a time, never a wall of tips).
     */
    static boolean tipDue(Entry e, boolean tipsOn, long lastTip, long now, long cooldownMs) {
        if (!tipsOn || (e != null && (e.done() || e.tipped()))) {
            return false;
        }
        return now - lastTip >= cooldownMs;
    }
}

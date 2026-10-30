package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.tutorial.TutorialService.State;

/**
 * How progress moves (TUTORIAL.md §3, §8): start, step done (current or early), counted steps, resume after a flow
 * change, staff skip/reset. Pure, unit-tested; the engine saves and shows what these return.
 */
final class StepRules {

    /** What a "done" signal changed. */
    enum Outcome {
        /** Nothing: not running, unknown step, already done, or an early step while early steps are off. */
        IGNORED,
        /** A later step was done early; the current step stays. */
        EARLY,
        /** The current step was done; {@link Progress#step} is the next one. */
        ADVANCED,
        /** The current step was the last open one: ready to finish (reward). */
        COMPLETE
    }

    private StepRules() {
    }

    /** Starts the tutorial at its first open step. False when it runs already or is complete. */
    static boolean start(Progress p, TutorialFlow flow, long now) {
        if (p.state != State.NEW) {
            return false;
        }
        p.state = State.ACTIVE;
        p.startedAt = now;
        p.lastActivity = now;
        String first = flow.firstOpen(p.doneSet());
        p.step = first == null ? flow.steps().getLast() : first;
        p.stepStartedAt = now;
        return true;
    }

    /** Marks {@code step} done. */
    static Outcome done(Progress p, TutorialFlow flow, String step, boolean earlyAllowed, long now) {
        if (p.state != State.ACTIVE || step == null || !flow.contains(step) || p.isDone(step)) {
            return Outcome.IGNORED;
        }
        boolean current = step.equals(p.step);
        if (!current && !earlyAllowed) {
            return Outcome.IGNORED;
        }
        p.done.put(step, !current);
        p.lastActivity = now;
        p.idleReminded = false;
        if (!current) {
            return Outcome.EARLY;
        }
        String next = flow.firstOpen(p.doneSet());
        if (next == null) {
            return Outcome.COMPLETE;
        }
        p.step = next;
        p.stepStartedAt = now;
        p.chestOpened = false;
        return Outcome.ADVANCED;
    }

    /**
     * Adds {@code amount} to a counted step; true when it reaches {@code needed} (then call {@link #done}). Counts only
     * while the tutorial runs and the step is not done (early counting follows the same rule as early steps).
     */
    static boolean count(Progress p, TutorialFlow flow, String step, int amount, int needed, boolean earlyAllowed) {
        if (p.state != State.ACTIVE || !flow.contains(step) || p.isDone(step) || amount <= 0) {
            return false;
        }
        if (!step.equals(p.step) && !earlyAllowed) {
            return false;
        }
        int now = Math.min(needed, p.count(step) + amount);
        p.counts.put(step, now);
        return now >= needed;
    }

    /**
     * After loading: a running tutorial continues at the first open step of the current flow (steps removed or added by
     * an update, TUTORIAL §8). Returns true when every step is already done (finish now).
     */
    static boolean resume(Progress p, TutorialFlow flow, int flowVersion, long now) {
        if (p.state != State.ACTIVE) {
            p.flowVersion = flowVersion;
            return false;
        }
        String next = flow.firstOpen(p.doneSet());
        p.flowVersion = flowVersion;
        if (next == null) {
            return true;
        }
        if (!next.equals(p.step)) {
            p.step = next;
            p.stepStartedAt = now;
        }
        return false;
    }

    /** The finish was saved: complete, every lock open. */
    static void finished(Progress p, long now) {
        p.state = State.FINISHED;
        p.step = null;
        p.finishedAt = now;
    }

    /** Staff skip: complete without a reward. False when it was complete already. */
    static boolean staffSkip(Progress p, long now) {
        if (p.state.complete()) {
            return false;
        }
        p.state = State.STAFF_SKIPPED;
        p.step = null;
        p.finishedAt = now;
        return true;
    }

    /**
     * Staff reset: back to the start (locks return). A player who was paid the reward before keeps the "reward paid"
     * mark, so finishing again never pays twice (the reward's idempotency key also refuses it).
     */
    static void reset(Progress p) {
        p.state = State.NEW;
        p.step = null;
        p.startedAt = null;
        p.finishedAt = null;
        p.done.clear();
        p.counts.clear();
        p.stuck.clear();
        p.chestOpened = false;
    }

    /** Staff "jump to step": everything before {@code step} counts as done, the rest is open. */
    static boolean jump(Progress p, TutorialFlow flow, String step, long now) {
        int index = flow.index(step);
        if (index < 0) {
            return false;
        }
        p.done.clear();
        for (int i = 0; i < index; i++) {
            p.done.put(flow.steps().get(i), false);
        }
        p.counts.keySet().removeIf(k -> !p.done.containsKey(k));
        p.state = State.ACTIVE;
        if (p.startedAt == null) {
            p.startedAt = now;
        }
        p.finishedAt = null;
        p.step = step;
        p.stepStartedAt = now;
        return true;
    }

    /** "I'm stuck": counts the press; true when the player may skip this step now. */
    static boolean stuck(Progress p, String step, int skipAfter, java.util.Collection<String> skippable) {
        int presses = p.stuck.merge(step, 1, Integer::sum);
        return presses >= skipAfter && skippable.contains(step);
    }
}

package net.skybornmc.skyblock.common.menu;

/**
 * The state of one confirm screen (I9 section 5). Pure, unit-tested.
 * <ul>
 *   <li>One step: Confirm tile 11 (lime concrete), details 13, Cancel tile 15 (red concrete).</li>
 *   <li>Two steps (things that can't be undone: disband, delete profile, permanent ban): the first Confirm opens a
 *       second check in place with the sides swapped, so a double click can never confirm twice by accident.</li>
 *   <li>One-shot: after the action ran or was cancelled every further click is ignored.</li>
 * </ul>
 */
public final class ConfirmFlow {

    /** What a click does. */
    public enum Result {
        /** Show the second check (two-step only). */
        NEXT_STEP,
        /** Run the action now. */
        RUN,
        /** Cancelled: go back, nothing happens. */
        CANCEL,
        /** Already done: ignore the click. */
        IGNORED
    }

    public static final int DETAILS_SLOT = 13;
    /** The two tile slots; one constant each, so wider targets are a one-line change (GUI-P2 risk table). */
    private static final int[] LEFT = {11};
    private static final int[] RIGHT = {15};

    private final boolean twoStep;
    private int step = 1;
    private boolean done;

    public ConfirmFlow(boolean twoStep) {
        this.twoStep = twoStep;
    }

    public Result confirm() {
        if (done) {
            return Result.IGNORED;
        }
        if (twoStep && step == 1) {
            step = 2;
            return Result.NEXT_STEP;
        }
        done = true;
        return Result.RUN;
    }

    public Result cancel() {
        if (done) {
            return Result.IGNORED;
        }
        done = true;
        return Result.CANCEL;
    }

    public boolean twoStep() {
        return twoStep;
    }

    /** 1 or 2. */
    public int step() {
        return step;
    }

    public int steps() {
        return twoStep ? 2 : 1;
    }

    public boolean done() {
        return done;
    }

    /** The second check swaps the sides: Cancel on the left, Confirm on the right. */
    public boolean swapped() {
        return step == 2;
    }

    public int[] confirmSlots() {
        return (swapped() ? RIGHT : LEFT).clone();
    }

    public int[] cancelSlots() {
        return (swapped() ? LEFT : RIGHT).clone();
    }
}

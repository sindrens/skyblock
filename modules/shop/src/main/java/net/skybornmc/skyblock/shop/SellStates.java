package net.skybornmc.skyblock.shop;

/** GUI-P3 5.4: what one sell GUI button can do. Pure. */
final class SellStates {

    enum State {
        /** You carry at least the amount: sells exactly it. */
        OK,
        /** You carry fewer: sells what you carry. */
        PARTIAL,
        /** You carry none. */
        NONE,
        /** The sell service does not buy this item. */
        NOT_SELLABLE,
        /** Worth less than $1 together (the sale would be refused). */
        TOO_CHEAP
    }

    private SellStates() {
    }

    /**
     * @param wanted     the button's amount; 0 = Sell all
     * @param quoteTotal what selling {@code min(wanted, carried)} pays now
     */
    static State of(boolean sellable, long carried, long wanted, long quoteTotal) {
        if (!sellable) {
            return State.NOT_SELLABLE;
        }
        if (carried <= 0) {
            return State.NONE;
        }
        if (quoteTotal < 1) {
            return State.TOO_CHEAP;
        }
        return wanted > 0 && carried < wanted ? State.PARTIAL : State.OK;
    }

    /** Clickable states. */
    static boolean clickable(State state) {
        return state == State.OK || state == State.PARTIAL;
    }
}

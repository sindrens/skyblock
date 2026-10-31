package net.skybornmc.skyblock.afk;

/**
 * Notices the moment Island Fuel switches on (PHASE-7 M7.1: the production module published {@code ProductionService}),
 * so the AFK pool hands out the fuel it banked for online players right away instead of at their next reward or join.
 * Delivery itself is once per grant (each grant row is claimed with a conditional update first).
 */
final class FuelSwitch {

    private boolean on;

    /** True exactly once per switch-on: the first call that sees {@code nowOn} after it was off (or at the start). */
    boolean turnedOn(boolean nowOn) {
        boolean flipped = nowOn && !on;
        on = nowOn;
        return flipped;
    }
}

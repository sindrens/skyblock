package net.skybornmc.skyblock.islands;

/**
 * F15: the island protection bypass follows staff mode for owner and admin. Entering {@code /s} turns it on (needs both
 * {@code skyblock.staff.island-creative} and {@code skyblock.island.bypass}); leaving turns it off, also when it was
 * switched on by hand, so no bypass outlives staff mode. Pure, unit-tested.
 */
final class StaffModeBypass {

    enum Change { TURN_ON, TURN_OFF, NONE }

    private StaffModeBypass() {
    }

    /**
     * @param entered     the player entered staff mode (false: left it)
     * @param autoAllowed has {@code skyblock.staff.island-creative}
     * @param mayBypass   has {@code skyblock.island.bypass}
     * @param bypassOn    the bypass is on now
     */
    static Change decide(boolean entered, boolean autoAllowed, boolean mayBypass, boolean bypassOn) {
        if (entered) {
            return autoAllowed && mayBypass && !bypassOn ? Change.TURN_ON : Change.NONE;
        }
        return bypassOn ? Change.TURN_OFF : Change.NONE;
    }
}

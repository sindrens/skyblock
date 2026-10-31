package net.skybornmc.skyblock.afk;

/**
 * Owner R3-35 (pure, tested): no idle kick, but a player in the pool who is not earning (daily limit reached, or paused
 * because another account on their connection earns) is sent to their island after {@code idle.send-home-seconds}.
 * The timer starts when earning stops and resets as soon as they earn again or leave.
 */
final class IdleHome {

    enum Action {
        /** Nothing to do. */
        NONE,
        /** The timer just started: tell the player once. */
        WARN,
        /** Time is up: send the player to their island. */
        SEND
    }

    /** @param since when not-earning started (0 = earning or outside) */
    record Step(long since, Action action) {
    }

    private IdleHome() {
    }

    /** States that count as "in the pool and not earning". Loading and no-permission don't (nothing the player can fix by waiting). */
    static boolean notEarning(AfkState state) {
        return state == AfkState.CAPPED || state == AfkState.IP_CAPPED || state == AfkState.IP_BLOCKED;
    }

    static Step step(boolean enabled, AfkState state, long since, long now, long delayMillis) {
        if (!enabled || !notEarning(state)) {
            return new Step(0, Action.NONE);
        }
        if (since <= 0) {
            return new Step(now, Action.WARN);
        }
        if (now - since >= delayMillis) {
            return new Step(0, Action.SEND);
        }
        return new Step(since, Action.NONE);
    }
}

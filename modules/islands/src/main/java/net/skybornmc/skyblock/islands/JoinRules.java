package net.skybornmc.skyblock.islands;

/**
 * Island-hopping limits (REVIEW A5, pure): a player may join at most {@code maxJoins} islands per season and must wait
 * {@code cooldown} after their last <b>join</b> before joining again (owner #20, ISL2: leaving or being kicked never
 * starts or extends the cooldown). A staff/moderation join block (Phase P7) wins over everything.
 */
public final class JoinRules {

    /** Outcome of a join check. */
    public sealed interface Check {
        record Ok() implements Check {
        }

        /** Blocked by staff until {@code until} (epoch millis). */
        record Blocked(long until) implements Check {
        }

        /** Already joined {@code max} islands this season. */
        record SeasonLimit(int max) implements Check {
        }

        /** Must wait {@code remainingMillis}. */
        record Cooldown(long remainingMillis) implements Check {
        }
    }

    /** A player's join history ({@code player_island_joins}); {@code lastLeave} is kept for history only. */
    public record History(int joinsThisSeason, Long lastJoin, Long lastLeave) {
        public static final History NONE = new History(0, null, null);
    }

    private static final Check OK = new Check.Ok();

    private JoinRules() {
    }

    /**
     * @param blockedUntil  staff block end (epoch millis) or {@code null}
     * @param maxJoins      joins allowed per season; 0 or less = unlimited
     * @param cooldownMillis wait after the last join; 0 or less = none
     */
    public static Check check(History history, Long blockedUntil, long now, int maxJoins, long cooldownMillis) {
        if (blockedUntil != null && blockedUntil > now) {
            return new Check.Blocked(blockedUntil);
        }
        History h = history == null ? History.NONE : history;
        if (maxJoins > 0 && h.joinsThisSeason() >= maxJoins) {
            return new Check.SeasonLimit(maxJoins);
        }
        long remaining = cooldownRemaining(h.lastJoin(), now, cooldownMillis);
        if (remaining > 0) {
            return new Check.Cooldown(remaining);
        }
        return OK;
    }

    /** Milliseconds left of the join cooldown after a join at {@code lastJoin} (0 = can join; null = never joined). */
    public static long cooldownRemaining(Long lastJoin, long now, long cooldownMillis) {
        if (cooldownMillis <= 0 || lastJoin == null) {
            return 0;
        }
        return Math.max(0, lastJoin + cooldownMillis - now);
    }
}

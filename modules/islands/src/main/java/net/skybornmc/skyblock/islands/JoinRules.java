package net.skybornmc.skyblock.islands;

/**
 * Island-hopping limits (REVIEW A5, pure): a player may join at most {@code maxJoins} islands per season. There is no
 * join cooldown (owner answers 121/161/258/259, round 1: never after leaving, a kick, a disband, a staff delete or a
 * reset). A staff/moderation join block (Phase P7) wins over everything.
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
    }

    /** A player's join history ({@code player_island_joins}); {@code lastJoin} and {@code lastLeave} are history only. */
    public record History(int joinsThisSeason, Long lastJoin, Long lastLeave) {
        public static final History NONE = new History(0, null, null);
    }

    private static final Check OK = new Check.Ok();

    private JoinRules() {
    }

    /**
     * @param blockedUntil  staff block end (epoch millis) or {@code null}
     * @param maxJoins      joins allowed per season; 0 or less = unlimited
     */
    public static Check check(History history, Long blockedUntil, long now, int maxJoins) {
        if (blockedUntil != null && blockedUntil > now) {
            return new Check.Blocked(blockedUntil);
        }
        History h = history == null ? History.NONE : history;
        if (maxJoins > 0 && h.joinsThisSeason() >= maxJoins) {
            return new Check.SeasonLimit(maxJoins);
        }
        return OK;
    }
}

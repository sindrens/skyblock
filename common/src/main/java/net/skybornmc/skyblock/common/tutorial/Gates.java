package net.skybornmc.skyblock.common.tutorial;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Ids of the features the join tutorial locks until a step is done (TUTORIAL.md §4, owner answer 131). Which step opens
 * which gate is set in {@code tutorial.yml → gates}; modules only name the gate they guard.
 */
public final class Gates {

    /** {@code /is invite}, {@code /is coop}, {@code /is visit} and island warps. */
    public static final String ISLAND_TEAM = "island_team";
    /** The Depths: the {@code to_depths} portal and {@code /dungeon} teleports. */
    public static final String DEPTHS = "depths";
    /** Auction house {@code /ah}: browse, buy and list. */
    public static final String AUCTION = "auction";
    /** {@code /pay}. */
    public static final String PAY = "pay";
    /** {@code /withdraw} (money notes). */
    public static final String WITHDRAW = "withdraw";
    /** Secure trades {@code /trade}. */
    public static final String TRADE = "trade";
    /** The warzone portal. */
    public static final String WARZONE = "warzone";
    /** Creating a 2nd or 3rd profile. */
    public static final String PROFILE_CREATE = "profile_create";
    /** Outposts portal (when built). */
    public static final String OUTPOSTS = "outposts";
    /** Events portal (when built). */
    public static final String EVENTS = "events";

    /** Every known gate, in the order of the plan's lock table. */
    public static final List<String> ALL = List.of(ISLAND_TEAM, DEPTHS, AUCTION, PAY, WITHDRAW, TRADE, WARZONE, PROFILE_CREATE,
            OUTPOSTS, EVENTS);

    private Gates() {
    }

    public static boolean known(String gate) {
        return gate != null && ALL.contains(gate.toLowerCase(Locale.ROOT));
    }

    /**
     * The gate guarding a build portal's target ({@code depths}, {@code warzone}, {@code outposts}, {@code events}), or empty
     * for targets the tutorial never locks (spawn, island...).
     */
    public static Optional<String> forPortalTarget(String target) {
        if (target == null) {
            return Optional.empty();
        }
        String t = target.toLowerCase(Locale.ROOT);
        return switch (t) {
            case DEPTHS, WARZONE, OUTPOSTS, EVENTS -> Optional.of(t);
            default -> Optional.empty();
        };
    }
}

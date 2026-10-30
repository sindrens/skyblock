package net.skybornmc.skyblock.common.progression;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Island leaderboards of the progression module (PHASE-5 M5.2 {@code /is top}, NPCHOLO island boards). */
public final class IslandTop {

    private IslandTop() {
    }

    /** Which islands a board ranks: every island, solo islands only or team islands only (separate boards, owner). */
    public enum Board {
        ALL, SOLO, TEAM;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Board> byId(String id) {
            if (id == null) {
                return Optional.empty();
            }
            for (Board b : values()) {
                if (b.id().equalsIgnoreCase(id.trim())) {
                    return Optional.of(b);
                }
            }
            return Optional.empty();
        }
    }

    /** What a board ranks by. */
    public enum Kind {
        /** Island level (tie: total points, then who reached the level first). */
        LEVEL,
        /** Block worth (money value of the placed blocks). */
        VALUE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Kind> byId(String id) {
            if (id == null) {
                return Optional.empty();
            }
            for (Kind k : values()) {
                if (k.id().equalsIgnoreCase(id.trim())) {
                    return Optional.of(k);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * One ranked island.
     *
     * @param place      1-based position on the board
     * @param ownerKey   the leader's profile key
     * @param name       island display name at the time of the ranking (may be blank: show the leader's name)
     * @param team       true for a team island
     * @param level      island level
     * @param points     total level points
     * @param blockWorth money worth of the placed blocks
     */
    public record Entry(int place, UUID islandId, UUID ownerKey, String name, boolean team, int level, double points,
                        double blockWorth) {

        public Entry {
            Objects.requireNonNull(islandId, "islandId");
        }
    }
}

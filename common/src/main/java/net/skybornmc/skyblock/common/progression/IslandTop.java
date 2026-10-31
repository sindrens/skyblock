package net.skybornmc.skyblock.common.progression;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Island leaderboards of the progression module (PHASE-5 M5.2/M5.7 {@code /is top}, NPCHOLO island boards). Weekly boards
 * count from Monday 00:00 Europe/Oslo and only earned progress (owner P5-26); per-pillar boards come from
 * {@code ProgressionService#pillarTop}.
 */
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
        VALUE,
        /** Earned level points gained this week (pillars, upgrades, masteries, achievements; M5.7). */
        WEEKLY_GAIN,
        /** Pillar levels gained this week (all pillars together; M5.7). */
        WEEKLY_PILLARS;

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
     * @param value      what the board ranks by: level, block worth, weekly points gained, pillar levels gained or the
     *                   pillar's level (M5.7)
     * @param leader     the leader's player name at the time of the ranking ({@code ""} when unknown)
     */
    public record Entry(int place, UUID islandId, UUID ownerKey, String name, boolean team, int level, double points,
                        double blockWorth, double value, String leader) {

        public Entry {
            Objects.requireNonNull(islandId, "islandId");
            leader = leader == null ? "" : leader;
        }

        /** M5.2 shape (value = level, leader unknown). */
        public Entry(int place, UUID islandId, UUID ownerKey, String name, boolean team, int level, double points,
                     double blockWorth) {
            this(place, islandId, ownerKey, name, team, level, points, blockWorth, level, "");
        }
    }

    /**
     * Which board a Hall of Heroes panel that switches between Solo and Team shows right now (owner PG28 default: every
     * {@code seconds}). Pure: the same moment gives the same board on every panel.
     */
    public static Board cycleBoard(long nowMillis, int seconds) {
        long period = Math.max(1, seconds) * 1000L;
        return Math.floorMod(Math.floorDiv(nowMillis, period), 2L) == 0 ? Board.SOLO : Board.TEAM;
    }
}

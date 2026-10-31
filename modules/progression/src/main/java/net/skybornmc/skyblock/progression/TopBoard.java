package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.IslandTop;
import net.skybornmc.skyblock.common.progression.Pillar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ranks islands for {@code /is top} and the Hall of Heroes boards (PHASE-5 M5.2, M5.7). Pure, unit-tested.
 * <ul>
 *   <li>Level boards: island level, then total points, then who reached the level first.</li>
 *   <li>Value boards: block worth (tie: the level order).</li>
 *   <li>Weekly island gain: earned points gained since Monday (tie: pillar levels gained, then the level order).</li>
 *   <li>Weekly pillar gain: pillar levels gained since Monday (tie: earned points gained, then the level order).</li>
 *   <li>Per pillar: that pillar's level, then its XP.</li>
 *   <li>Every order ends with the island id, so equal islands always come out in the same order.</li>
 *   <li>Solo and team islands are separate boards ({@link IslandTop.Board}); one island per leader account (owner P5-29,
 *   a player's best profile counts); hidden (staff) islands are left out (owner 576, like {@code /baltop}).</li>
 *   <li>Islands with nothing to show (0 points, no block worth, no gain, pillar level 0) are not ranked.</li>
 * </ul>
 */
final class TopBoard {

    private TopBoard() {
    }

    /**
     * One island that can be ranked.
     *
     * @param account     the leader's player account (profiles of one player share it)
     * @param reachedAt   when the island reached its current level (earlier wins a tie)
     * @param earnedGain  earned level points gained this week (M5.7)
     * @param pillarGain  pillar levels gained this week (M5.7)
     * @param pillarLevel level per pillar, by {@link Pillar#ordinal()} (may be shorter: missing = 0)
     * @param pillarXp    XP per pillar, by {@link Pillar#ordinal()} (may be shorter: missing = 0)
     * @param leader      the leader's player name ({@code ""} when unknown)
     * @param hidden      a staff island: never ranked
     */
    record Candidate(UUID islandId, UUID ownerKey, UUID account, String name, boolean team, int level, double points,
                     long reachedAt, double blockWorth, double earnedGain, int pillarGain, int[] pillarLevel, double[] pillarXp,
                     String leader, boolean hidden) {

        /** M5.2 shape: no weekly gains, no pillars, visible. */
        Candidate(UUID islandId, UUID ownerKey, UUID account, String name, boolean team, int level, double points,
                  long reachedAt, double blockWorth) {
            this(islandId, ownerKey, account, name, team, level, points, reachedAt, blockWorth, 0, 0, new int[0], new double[0], "",
                    false);
        }

        int pillarLevelOf(Pillar pillar) {
            return pillarLevel != null && pillar.ordinal() < pillarLevel.length ? pillarLevel[pillar.ordinal()] : 0;
        }

        double pillarXpOf(Pillar pillar) {
            return pillarXp != null && pillar.ordinal() < pillarXp.length ? pillarXp[pillar.ordinal()] : 0;
        }
    }

    static final Comparator<Candidate> BY_LEVEL = Comparator.comparingInt(Candidate::level).reversed()
            .thenComparing(Comparator.comparingDouble(Candidate::points).reversed())
            .thenComparingLong(Candidate::reachedAt)
            .thenComparing(Candidate::islandId);

    static final Comparator<Candidate> BY_VALUE = Comparator.comparingDouble(Candidate::blockWorth).reversed()
            .thenComparing(BY_LEVEL);

    static final Comparator<Candidate> BY_WEEKLY_GAIN = Comparator.comparingDouble(Candidate::earnedGain).reversed()
            .thenComparing(Comparator.comparingInt(Candidate::pillarGain).reversed())
            .thenComparing(BY_LEVEL);

    static final Comparator<Candidate> BY_WEEKLY_PILLARS = Comparator.comparingInt(Candidate::pillarGain).reversed()
            .thenComparing(Comparator.comparingDouble(Candidate::earnedGain).reversed())
            .thenComparing(BY_LEVEL);

    static Comparator<Candidate> byPillar(Pillar pillar) {
        return Comparator.<Candidate>comparingInt(c -> c.pillarLevelOf(pillar)).reversed()
                .thenComparing(Comparator.<Candidate>comparingDouble(c -> c.pillarXpOf(pillar)).reversed())
                .thenComparing(Candidate::islandId);
    }

    static List<IslandTop.Entry> rank(List<Candidate> candidates, IslandTop.Kind kind, IslandTop.Board board) {
        return switch (kind) {
            case LEVEL -> rank(candidates, board, c -> c.points() > 0, BY_LEVEL, Candidate::level);
            case VALUE -> rank(candidates, board, c -> c.blockWorth() > 0, BY_VALUE, Candidate::blockWorth);
            case WEEKLY_GAIN -> rank(candidates, board, c -> c.earnedGain() > 0, BY_WEEKLY_GAIN, Candidate::earnedGain);
            case WEEKLY_PILLARS -> rank(candidates, board, c -> c.pillarGain() > 0, BY_WEEKLY_PILLARS, Candidate::pillarGain);
        };
    }

    static List<IslandTop.Entry> rankPillar(List<Candidate> candidates, Pillar pillar, IslandTop.Board board) {
        return rank(candidates, board, c -> c.pillarLevelOf(pillar) > 0, byPillar(pillar), c -> c.pillarLevelOf(pillar));
    }

    private static List<IslandTop.Entry> rank(List<Candidate> candidates, IslandTop.Board board,
                                              java.util.function.Predicate<Candidate> shows, Comparator<Candidate> order,
                                              java.util.function.ToDoubleFunction<Candidate> value) {
        List<Candidate> list = new ArrayList<>();
        for (Candidate c : candidates) {
            if (!c.hidden() && inBoard(c, board) && shows.test(c)) {
                list.add(c);
            }
        }
        list.sort(order);
        Set<UUID> accounts = new HashSet<>();
        List<IslandTop.Entry> out = new ArrayList<>();
        for (Candidate c : list) {
            UUID account = c.account() == null ? c.ownerKey() : c.account();
            if (account != null && !accounts.add(account)) {
                continue; // that player's better island is already on the board
            }
            out.add(new IslandTop.Entry(out.size() + 1, c.islandId(), c.ownerKey(), c.name(), c.team(), c.level(), c.points(),
                    c.blockWorth(), value.applyAsDouble(c), c.leader()));
        }
        return out;
    }

    private static boolean inBoard(Candidate c, IslandTop.Board board) {
        return switch (board) {
            case ALL -> true;
            case SOLO -> !c.team();
            case TEAM -> c.team();
        };
    }

    /** Island → place on its own level board (solo islands on the solo board, team islands on the team board). */
    static Map<UUID, Integer> ownPlaces(List<IslandTop.Entry> solo, List<IslandTop.Entry> team) {
        Map<UUID, Integer> out = new HashMap<>();
        for (IslandTop.Entry e : solo) {
            out.put(e.islandId(), e.place());
        }
        for (IslandTop.Entry e : team) {
            out.put(e.islandId(), e.place());
        }
        return out;
    }

    /** Weekly gain from the week's starting values (never below 0: earned sources never go down, rounding aside). */
    static double gain(double now, double base) {
        return Math.max(0, now - base);
    }
}

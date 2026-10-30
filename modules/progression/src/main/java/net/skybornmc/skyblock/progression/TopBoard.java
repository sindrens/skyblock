package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.IslandTop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ranks islands for {@code /is top} and the Hall of Heroes boards (PHASE-5 M5.2). Pure, unit-tested.
 * <ul>
 *   <li>Level boards: island level, then total points, then who reached the level first.</li>
 *   <li>Value boards: block worth.</li>
 *   <li>Solo and team islands are separate boards ({@link IslandTop.Board}); one island per leader account (owner P5-29,
 *   a player's best profile counts).</li>
 *   <li>Islands without points (or without block worth on a value board) are not ranked.</li>
 * </ul>
 */
final class TopBoard {

    private TopBoard() {
    }

    /**
     * One island that can be ranked.
     *
     * @param account   the leader's player account (profiles of one player share it)
     * @param reachedAt when the island reached its current level (earlier wins a tie)
     */
    record Candidate(UUID islandId, UUID ownerKey, UUID account, String name, boolean team, int level, double points,
                     long reachedAt, double blockWorth) {
    }

    static final Comparator<Candidate> BY_LEVEL = Comparator.comparingInt(Candidate::level).reversed()
            .thenComparing(Comparator.comparingDouble(Candidate::points).reversed())
            .thenComparingLong(Candidate::reachedAt)
            .thenComparing(Candidate::islandId);

    static final Comparator<Candidate> BY_VALUE = Comparator.comparingDouble(Candidate::blockWorth).reversed()
            .thenComparing(BY_LEVEL);

    static List<IslandTop.Entry> rank(List<Candidate> candidates, IslandTop.Kind kind, IslandTop.Board board) {
        List<Candidate> list = new ArrayList<>();
        for (Candidate c : candidates) {
            if (!inBoard(c, board)) {
                continue;
            }
            if (kind == IslandTop.Kind.LEVEL ? !(c.points() > 0) : !(c.blockWorth() > 0)) {
                continue;
            }
            list.add(c);
        }
        list.sort(kind == IslandTop.Kind.LEVEL ? BY_LEVEL : BY_VALUE);
        Set<UUID> accounts = new HashSet<>();
        List<IslandTop.Entry> out = new ArrayList<>();
        for (Candidate c : list) {
            UUID account = c.account() == null ? c.ownerKey() : c.account();
            if (account != null && !accounts.add(account)) {
                continue; // that player's better island is already on the board
            }
            out.add(new IslandTop.Entry(out.size() + 1, c.islandId(), c.ownerKey(), c.name(), c.team(), c.level(), c.points(),
                    c.blockWorth()));
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
}

package net.skybornmc.skyblock.perks;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Owner 7 Nov 2026: /reclaim points by rank and the join-when-full limit. */
class ReclaimAndVaultsTest {

    @Test
    void highestRankCounts() {
        var lines = new PerksConfig().rankMonthlyPoints;
        assertEquals(0, RankRewardsRules.points(lines, g -> false));
        assertEquals(250, RankRewardsRules.points(lines, Set.of("hatchling", "fledgling", "firewing")::contains));
        assertEquals(1000, RankRewardsRules.points(lines, g -> true));
        assertEquals(5, RankRewardsRules.points(java.util.List.of("bad", "x:abc", "y:5"), g -> true));
    }

    @Test
    void ranksCanJoinTenPercentOver() {
        assertEquals(110, JoinFull.limit(100, 10));
        assertEquals(301, JoinFull.limit(300, 0));
        assertEquals(330, JoinFull.limit(300, 10));
    }
}

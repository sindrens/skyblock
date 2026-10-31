package net.skybornmc.skyblock.rewards;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.rewards.RewardsService;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** /vote layout (sites centred on row 2, nothing overlaps) and waiting votes in the /menu Rewards summary. */
class VoteMenuLayoutTest {

    @Test
    void fiveSitesAreCentredOnRowTwo() {
        assertEquals(List.of(20, 21, 22, 23, 24), List.of(VoteMenu.siteSlot(0, 5), VoteMenu.siteSlot(1, 5), VoteMenu.siteSlot(2, 5),
                VoteMenu.siteSlot(3, 5), VoteMenu.siteSlot(4, 5)));
        assertEquals(22, VoteMenu.siteSlot(0, 1));
    }

    @Test
    void nothingOverlaps() {
        Set<Integer> used = new HashSet<>(List.of(VoteMenu.SLOT_SUMMARY, VoteMenu.SLOT_STREAK, VoteMenu.SLOT_PAGE, VoteMenu.SLOT_PARTY));
        assertEquals(4, used.size());
        for (int i = 0; i < VoteMenu.MAX_SITES; i++) {
            int slot = VoteMenu.siteSlot(i, VoteMenu.MAX_SITES);
            assertTrue(slot >= 19 && slot <= 25, "row 2 inside the frame: " + slot);
            assertTrue(used.add(slot), "slot used twice: " + slot);
        }
    }

    @Test
    void waitingVotesJoinTheRewardsSummary() {
        RewardsService.ClaimSummary nothing = RewardsService.ClaimSummary.nothingReady();
        assertSame(nothing, RewardsServiceImpl.withVotes(nothing, 0, () -> Component.text("x")));
        RewardsService.ClaimSummary votesOnly = RewardsServiceImpl.withVotes(nothing, 2, () -> Component.text("votes"));
        assertEquals(RewardsService.Outcome.CLAIMED, votesOnly.outcome());
        assertEquals(List.of(VoteService.READY), votesOnly.claimed());
        RewardsService.ClaimSummary daily = new RewardsService.ClaimSummary(RewardsService.Outcome.CLAIMED, List.of(RewardsService.DAILY),
                Component.text("day"));
        RewardsService.ClaimSummary both = RewardsServiceImpl.withVotes(daily, 1, () -> Component.text("votes"));
        assertEquals(List.of(RewardsService.DAILY, VoteService.READY), both.claimed());
        RewardsService.ClaimSummary failed = RewardsServiceImpl.withVotes(RewardsService.ClaimSummary.failed(), 1, () -> Component.text("v"));
        assertEquals(RewardsService.Outcome.CLAIMED, failed.outcome());
    }
}

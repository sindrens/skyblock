package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.profile.WipeReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 4 Nov 2026 (island = profile): every way of leaving wipes, and joining needs an empty profile. */
class LeaveWipeTest {

    @Test
    void joiningNeedsAnEmptyProfile() {
        assertEquals(LeaveWipe.JoinNeed.JOIN, LeaveWipe.joinNeed(false, null, 0));
        assertEquals(LeaveWipe.JoinNeed.REPLACE, LeaveWipe.joinNeed(true, Role.LEADER, 1), "alone: replacing wipes, with a confirm");
        assertEquals(LeaveWipe.JoinNeed.NEED_EMPTY, LeaveWipe.joinNeed(true, Role.LEADER, 2));
        assertEquals(LeaveWipe.JoinNeed.NEED_EMPTY, LeaveWipe.joinNeed(true, Role.MEMBER, 3));
        assertEquals(LeaveWipe.JoinNeed.NEED_EMPTY, LeaveWipe.joinNeed(true, Role.ADMIN, 2));
    }

    @Test
    void everyWayOfLeavingWipes() {
        assertEquals(WipeReason.LEAVE, LeaveWipe.forRemoval(MemberStore.LEFT_LEAVE, ""));
        assertEquals(WipeReason.KICK, LeaveWipe.forRemoval(MemberStore.LEFT_KICK, ""));
        assertEquals(WipeReason.REMOVED, LeaveWipe.forRemoval(MemberStore.LEFT_REMOVED, "griefing"));
        assertNull(LeaveWipe.forRemoval(MemberStore.LEFT_REMOVED, KickBackups.NOTE_PROFILE_DELETED), "the profile is gone already");
        assertNull(LeaveWipe.forRemoval(null, ""));

        assertEquals(WipeReason.DISBAND, LeaveWipe.forIslandRemoval(IslandStore.REASON_DISBAND));
        assertEquals(WipeReason.ISLAND_DELETED, LeaveWipe.forIslandRemoval(IslandStore.REASON_DELETE));
        assertEquals(WipeReason.ISLAND_RESET, LeaveWipe.forIslandRemoval(IslandStore.REASON_RESET));
        assertNull(LeaveWipe.forIslandRemoval(IslandStore.REASON_JOIN), "the join flow wipes it itself");
    }

    @Test
    void coopGuestsAreNeverWiped() {
        assertFalse(LeaveWipe.wipesRole(Role.COOP));
        assertFalse(LeaveWipe.wipesRole(null));
        assertTrue(LeaveWipe.wipesRole(Role.MEMBER));
        assertTrue(LeaveWipe.wipesRole(Role.LEADER));
    }
}

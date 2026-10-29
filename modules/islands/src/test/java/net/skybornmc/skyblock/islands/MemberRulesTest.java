package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberRulesTest {

    @Test
    void leaderAndModsManagePeople() {
        assertTrue(MemberRules.canInvite(Role.LEADER));
        assertTrue(MemberRules.canInvite(Role.MOD));
        assertFalse(MemberRules.canInvite(Role.MEMBER));
        assertFalse(MemberRules.canInvite(Role.COOP));
        assertFalse(MemberRules.canInvite(null));
        assertTrue(MemberRules.canCoop(Role.MOD));
        assertFalse(MemberRules.canCoop(Role.MEMBER));
        assertTrue(MemberRules.canSetHome(Role.LEADER));
        assertTrue(MemberRules.canSetHome(Role.MOD), "M2.3: sethome is Leader + Mod");
        assertFalse(MemberRules.canSetHome(Role.MEMBER));
    }

    @Test
    void kickNeedsHigherRank() {
        assertTrue(MemberRules.canKick(Role.LEADER, Role.MOD));
        assertTrue(MemberRules.canKick(Role.LEADER, Role.MEMBER));
        assertTrue(MemberRules.canKick(Role.MOD, Role.MEMBER));
        assertFalse(MemberRules.canKick(Role.MOD, Role.MOD), "mods can't kick each other");
        assertFalse(MemberRules.canKick(Role.MOD, Role.LEADER));
        assertFalse(MemberRules.canKick(Role.LEADER, Role.LEADER), "nobody kicks the leader");
        assertFalse(MemberRules.canKick(Role.MEMBER, Role.MEMBER));
        assertFalse(MemberRules.canKick(Role.LEADER, Role.COOP), "coop is removed with /is uncoop");
        assertFalse(MemberRules.canKick(Role.LEADER, null));
    }

    @Test
    void onlyTheLeaderPromotesDemotesAndTransfers() {
        assertTrue(MemberRules.canPromote(Role.LEADER, Role.MEMBER));
        assertFalse(MemberRules.canPromote(Role.LEADER, Role.MOD), "mod is the highest promotion");
        assertFalse(MemberRules.canPromote(Role.MOD, Role.MEMBER));
        assertTrue(MemberRules.canDemote(Role.LEADER, Role.MOD));
        assertFalse(MemberRules.canDemote(Role.LEADER, Role.MEMBER));
        assertFalse(MemberRules.canDemote(Role.MOD, Role.MOD));
        assertTrue(MemberRules.canTransfer(Role.LEADER, Role.MOD));
        assertTrue(MemberRules.canTransfer(Role.LEADER, Role.MEMBER));
        assertFalse(MemberRules.canTransfer(Role.LEADER, Role.COOP), "coop players can't own the island");
        assertFalse(MemberRules.canTransfer(Role.LEADER, Role.LEADER));
        assertFalse(MemberRules.canTransfer(Role.MOD, Role.MEMBER));
        assertEquals(Role.MOD, MemberRules.promoted(Role.MEMBER));
        assertEquals(Role.MOD, MemberRules.promoted(Role.MOD));
        assertEquals(Role.MEMBER, MemberRules.demoted(Role.MOD));
        assertEquals(Role.MEMBER, MemberRules.demoted(Role.MEMBER));
    }

    @Test
    void leaderCantLeave() {
        assertFalse(MemberRules.canLeave(Role.LEADER));
        assertTrue(MemberRules.canLeave(Role.MOD));
        assertTrue(MemberRules.canLeave(Role.MEMBER));
        assertFalse(MemberRules.canLeave(Role.COOP));
        assertFalse(MemberRules.canLeave(null));
    }

    @Test
    void permissionMatrixEditing() {
        for (PermRole row : PermRole.values()) {
            assertEquals(row != PermRole.LEADER, MemberRules.canEditPerms(Role.LEADER, row), "leader edits " + row);
        }
        assertFalse(MemberRules.canEditPerms(Role.MOD, PermRole.LEADER));
        assertFalse(MemberRules.canEditPerms(Role.MOD, PermRole.MOD), "mods can't edit mod permissions");
        assertTrue(MemberRules.canEditPerms(Role.MOD, PermRole.MEMBER));
        assertTrue(MemberRules.canEditPerms(Role.MOD, PermRole.COOP));
        assertTrue(MemberRules.canEditPerms(Role.MOD, PermRole.VISITOR));
        for (PermRole row : PermRole.values()) {
            assertFalse(MemberRules.canEditPerms(Role.MEMBER, row));
            assertFalse(MemberRules.canEditPerms(Role.COOP, row));
            assertFalse(MemberRules.canEditPerms(null, row));
        }
        assertTrue(MemberRules.canView(Role.MEMBER));
        assertFalse(MemberRules.canView(Role.COOP));
    }

    @Test
    void toggleStoresOnlyDifferencesFromTheDefault() {
        assertEquals(Boolean.FALSE, MemberRules.toggledOverride(true, true), "deny a default-allowed permission");
        assertNull(MemberRules.toggledOverride(false, true), "allow again = back to the default");
        assertEquals(Boolean.TRUE, MemberRules.toggledOverride(false, false));
        assertNull(MemberRules.toggledOverride(true, false));
    }

    @Test
    void memberSlots() {
        assertEquals(4, MemberRules.slots(null, 4, 8));
        assertEquals(6, MemberRules.slots(6, 4, 8));
        assertEquals(8, MemberRules.slots(12, 4, 8), "never above the max");
        assertEquals(1, MemberRules.slots(0, 4, 8), "at least the leader");
        assertEquals(1, MemberRules.slots(null, 4, 0));
    }

    @Test
    void memberListOrderLeaderModsMembersThenCoops() {
        java.util.UUID leader = java.util.UUID.randomUUID();
        java.util.UUID mod = java.util.UUID.randomUUID();
        java.util.UUID early = java.util.UUID.randomUUID();
        java.util.UUID late = java.util.UUID.randomUUID();
        java.util.UUID coop = java.util.UUID.randomUUID();
        java.util.Map<java.util.UUID, Role> members = new java.util.LinkedHashMap<>();
        members.put(late, Role.MEMBER);
        members.put(mod, Role.MOD);
        members.put(early, Role.MEMBER);
        members.put(leader, Role.LEADER);
        java.util.Map<java.util.UUID, Long> joined = java.util.Map.of(leader, 1L, mod, 5L, early, 2L, late, 9L);
        var order = MembersMenu.order(members, java.util.List.of(coop, early), joined::get);
        assertEquals(java.util.List.of(leader, mod, early, late, coop), order.stream().map(MembersMenu.Entry::uuid).toList());
        assertEquals(Role.COOP, order.getLast().role());
    }

    @Test
    void actionsShownOnlyWhenTheViewerCanDoSomething() {
        assertTrue(MemberActionsMenu.hasActions(Role.LEADER, Role.MEMBER, false));
        assertTrue(MemberActionsMenu.hasActions(Role.MOD, Role.MEMBER, false), "kick");
        assertFalse(MemberActionsMenu.hasActions(Role.MOD, Role.MOD, false));
        assertFalse(MemberActionsMenu.hasActions(Role.MEMBER, Role.MEMBER, false));
        assertTrue(MemberActionsMenu.hasActions(Role.MOD, Role.COOP, false), "uncoop");
        assertTrue(MemberActionsMenu.hasActions(Role.MEMBER, Role.MEMBER, true), "leave");
        assertFalse(MemberActionsMenu.hasActions(Role.LEADER, Role.LEADER, true), "leader can't leave");
    }

    @Test
    void permissionMatrixLayoutFitsInsideTheFrame() {
        assertEquals(2, PermissionsMenu.pages());
        for (int row = 0; row < PermissionsMenu.ROWS.length; row++) {
            for (int column = 0; column < PermissionsMenu.PER_PAGE; column++) {
                int slot = PermissionsMenu.cellSlot(row, column);
                assertTrue(slot % 9 >= 1 && slot % 9 <= 7 && slot / 9 >= 1 && slot / 9 <= 4, "cell " + slot);
            }
        }
        for (IslandPerm perm : IslandPerm.values()) {
            assertTrue(PermissionsMenu.ICONS.containsKey(perm), "icon for " + perm);
        }
    }
}

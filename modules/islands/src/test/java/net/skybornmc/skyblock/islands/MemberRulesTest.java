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
        assertTrue(MemberRules.canPromote(Role.LEADER, Role.MOD), "ISL2: mod → admin");
        assertFalse(MemberRules.canPromote(Role.LEADER, Role.ADMIN), "admin is the highest promotion");
        assertFalse(MemberRules.canPromote(Role.MOD, Role.MEMBER));
        assertTrue(MemberRules.canDemote(Role.LEADER, Role.MOD));
        assertTrue(MemberRules.canDemote(Role.LEADER, Role.ADMIN));
        assertFalse(MemberRules.canDemote(Role.LEADER, Role.MEMBER));
        assertFalse(MemberRules.canDemote(Role.MOD, Role.MOD));
        assertTrue(MemberRules.canTransfer(Role.LEADER, Role.MOD));
        assertTrue(MemberRules.canTransfer(Role.LEADER, Role.MEMBER));
        assertFalse(MemberRules.canTransfer(Role.LEADER, Role.COOP), "coop players can't own the island");
        assertFalse(MemberRules.canTransfer(Role.LEADER, Role.LEADER));
        assertFalse(MemberRules.canTransfer(Role.MOD, Role.MEMBER));
        assertEquals(Role.MOD, MemberRules.promoted(Role.MEMBER));
        assertEquals(Role.ADMIN, MemberRules.promoted(Role.MOD));
        assertEquals(Role.ADMIN, MemberRules.promoted(Role.ADMIN));
        assertEquals(Role.MOD, MemberRules.demoted(Role.ADMIN));
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
        assertFalse(MemberRules.canEditPerms(Role.MOD, PermRole.ADMIN));
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
        java.util.UUID admin = java.util.UUID.randomUUID();
        java.util.UUID mod = java.util.UUID.randomUUID();
        java.util.UUID early = java.util.UUID.randomUUID();
        java.util.UUID late = java.util.UUID.randomUUID();
        java.util.UUID coop = java.util.UUID.randomUUID();
        java.util.Map<java.util.UUID, Role> members = new java.util.LinkedHashMap<>();
        members.put(late, Role.MEMBER);
        members.put(mod, Role.MOD);
        members.put(early, Role.MEMBER);
        members.put(leader, Role.LEADER);
        members.put(admin, Role.ADMIN);
        java.util.Map<java.util.UUID, Long> joined = java.util.Map.of(leader, 1L, admin, 7L, mod, 5L, early, 2L, late, 9L);
        var order = MembersMenu.order(members, java.util.List.of(coop, early), joined::get);
        assertEquals(java.util.List.of(leader, admin, mod, early, late, coop), order.stream().map(MembersMenu.Entry::uuid).toList());
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
        assertEquals((IslandPerm.values().length + 3) / 4, PermissionsMenu.pages());
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (int row = 0; row < PermissionsMenu.PER_PAGE; row++) {
            for (int column = 0; column < PermissionsMenu.COLUMNS.length; column++) {
                int slot = PermissionsMenu.cellSlot(row, column);
                assertTrue(slot % 9 >= 1 && slot % 9 <= 7 && slot / 9 >= 1 && slot / 9 <= 4, "cell " + slot);
                assertTrue(used.add(slot), "one cell per slot: " + slot);
            }
            assertTrue(used.add((row + 1) * 9 + PermissionsMenu.ICON_COLUMN), "action icon slot");
        }
        for (int column = 0; column < PermissionsMenu.COLUMNS.length; column++) {
            int slot = PermissionsMenu.headerSlot(column);
            assertTrue(slot >= 1 && slot <= 7, "header " + slot);
        }
        for (IslandPerm perm : IslandPerm.values()) {
            assertTrue(PermissionsMenu.ICONS.containsKey(perm), "icon for " + perm);
        }
        for (PermRole role : PermissionsMenu.COLUMNS) {
            assertTrue(PermissionsMenu.ROLE_ICONS.containsKey(role), "icon for " + role);
        }
    }

    // ------------------------------------------------------------------ ISL2: Admin role

    @Test
    void adminSitsBetweenLeaderAndMod() {
        assertTrue(MemberRules.rank(Role.LEADER) > MemberRules.rank(Role.ADMIN));
        assertTrue(MemberRules.rank(Role.ADMIN) > MemberRules.rank(Role.MOD));
        assertTrue(MemberRules.isManager(Role.ADMIN));
        assertTrue(MemberRules.canInvite(Role.ADMIN));
        assertTrue(MemberRules.canCoop(Role.ADMIN));
        assertTrue(MemberRules.canSetHome(Role.ADMIN));
        assertTrue(MemberRules.canLeave(Role.ADMIN));
        assertTrue(MemberRules.canView(Role.ADMIN));
        assertTrue(VisitRules.canManageVisitors(Role.ADMIN));
        assertTrue(WarpRules.canManage(Role.ADMIN));
    }

    @Test
    void adminPromotesAndDemotesBetweenMemberAndMod() {
        assertTrue(MemberRules.canPromote(Role.ADMIN, Role.MEMBER));
        assertFalse(MemberRules.canPromote(Role.ADMIN, Role.MOD), "only the leader makes admins");
        assertTrue(MemberRules.canDemote(Role.ADMIN, Role.MOD));
        assertFalse(MemberRules.canDemote(Role.ADMIN, Role.ADMIN));
        assertFalse(MemberRules.canDemote(Role.ADMIN, Role.MEMBER));
        assertFalse(MemberRules.canPromote(Role.MOD, Role.MEMBER));
    }

    @Test
    void adminKicksLowerRolesOnly() {
        assertTrue(MemberRules.canKick(Role.ADMIN, Role.MOD));
        assertTrue(MemberRules.canKick(Role.ADMIN, Role.MEMBER));
        assertFalse(MemberRules.canKick(Role.ADMIN, Role.ADMIN));
        assertFalse(MemberRules.canKick(Role.ADMIN, Role.LEADER));
        assertFalse(MemberRules.canKick(Role.MOD, Role.ADMIN));
        assertTrue(MemberRules.canKick(Role.LEADER, Role.ADMIN));
    }

    @Test
    void onlyTheLeaderTransfersAndTheOldLeaderBecomesAdmin() {
        assertFalse(MemberRules.canTransfer(Role.ADMIN, Role.MEMBER));
        assertTrue(MemberRules.canTransfer(Role.LEADER, Role.ADMIN));
        assertEquals(Role.ADMIN, MemberRules.AFTER_TRANSFER);
        assertFalse(MemberRules.canLeave(Role.LEADER));
    }

    @Test
    void adminEditsTheModRowButNotItsOwn() {
        assertTrue(MemberRules.canEditPerms(Role.ADMIN, PermRole.MOD));
        assertTrue(MemberRules.canEditPerms(Role.ADMIN, PermRole.MEMBER));
        assertTrue(MemberRules.canEditPerms(Role.ADMIN, PermRole.VISITOR));
        assertFalse(MemberRules.canEditPerms(Role.ADMIN, PermRole.ADMIN));
        assertFalse(MemberRules.canEditPerms(Role.ADMIN, PermRole.LEADER));
        assertTrue(MemberRules.canEditPerms(Role.LEADER, PermRole.ADMIN));
    }

    @Test
    void settingsRightIsOnlyHandedOutByHolders() {
        assertTrue(MemberRules.canEditCell(Role.LEADER, PermRole.MOD, IslandPerm.SETTINGS, false), "the leader always has it");
        assertTrue(MemberRules.canEditCell(Role.ADMIN, PermRole.MOD, IslandPerm.SETTINGS, true));
        assertFalse(MemberRules.canEditCell(Role.ADMIN, PermRole.MOD, IslandPerm.SETTINGS, false), "can't give what you don't have");
        assertFalse(MemberRules.canEditCell(Role.LEADER, PermRole.VISITOR, IslandPerm.SETTINGS, true), "never for visitors");
        assertFalse(MemberRules.canEditCell(Role.LEADER, PermRole.COOP, IslandPerm.SETTINGS, true), "never for coop players");
        assertTrue(MemberRules.canEditCell(Role.MOD, PermRole.VISITOR, IslandPerm.HURT_MONSTERS, false), "normal cells need no holder");
        assertFalse(MemberRules.canEditCell(Role.MOD, PermRole.MOD, IslandPerm.BREAK, true));
    }

    @Test
    void whoChangesIslandSettings() {
        assertTrue(MemberRules.canChangeSettings(Role.LEADER, false));
        assertTrue(MemberRules.canChangeSettings(Role.ADMIN, true));
        assertFalse(MemberRules.canChangeSettings(Role.ADMIN, false));
        assertTrue(MemberRules.canChangeSettings(Role.MEMBER, true), "when the leader allows members");
        assertFalse(MemberRules.canChangeSettings(Role.COOP, true));
        assertFalse(MemberRules.canChangeSettings(null, true));
    }

    @Test
    void adminActionsInTheMemberMenu() {
        assertTrue(MemberActionsMenu.hasActions(Role.ADMIN, Role.MOD, false));
        assertTrue(MemberActionsMenu.hasActions(Role.LEADER, Role.ADMIN, false));
        assertFalse(MemberActionsMenu.hasActions(Role.MOD, Role.ADMIN, false));
        assertTrue(MemberActionsMenu.hasActions(Role.ADMIN, Role.ADMIN, true), "leave");
    }
}

package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GUI-4 (owner U8): Strict / Normal / Open island permission presets. */
class PermPresetsTest {

    private final IslandsConfig config = new IslandsConfig();
    private final RolePermissions defaults = config.rolePermissions().permissions();
    private final PermPresets presets = config.permPresets().presets();

    private static boolean has(long mask, IslandPerm perm) {
        return (mask & perm.bit()) != 0;
    }

    @Test
    void shippedPresetsParseCleanly() {
        assertEquals(List.of(), config.permPresets().problems());
    }

    @Test
    void normalIsTheServerDefault() {
        for (PermRole role : PermRole.values()) {
            if (role.editable()) {
                assertEquals(defaults.defaultMask(role) & ~IslandPerm.notApplicable(role),
                        presets.target(PermPresets.Preset.NORMAL, role, defaults), role.key());
            }
        }
        assertEquals(Optional.of(PermPresets.Preset.NORMAL), presets.active(new IslandPerms(), defaults));
    }

    @Test
    void strictTakesAwayAndOpenAdds() {
        long strictMember = presets.target(PermPresets.Preset.STRICT, PermRole.MEMBER, defaults);
        assertFalse(has(strictMember, IslandPerm.CONTAINERS));
        assertTrue(has(strictMember, IslandPerm.BREAK));
        long strictCoop = presets.target(PermPresets.Preset.STRICT, PermRole.COOP, defaults);
        assertFalse(has(strictCoop, IslandPerm.BREAK));
        long openVisitor = presets.target(PermPresets.Preset.OPEN, PermRole.VISITOR, defaults);
        assertTrue(has(openVisitor, IslandPerm.INTERACT));
        assertTrue(has(openVisitor, IslandPerm.PICKUP));
        assertFalse(has(openVisitor, IslandPerm.BREAK));
        assertEquals(IslandPerm.all(), presets.target(PermPresets.Preset.STRICT, PermRole.LEADER, defaults));
    }

    @Test
    void managementRightsNeverReachCoopOrVisitors() {
        PermPresets wild = PermPresets.parse(Map.of(), Map.of(PermRole.VISITOR, List.of("settings", "interact"))).presets();
        long visitor = wild.target(PermPresets.Preset.OPEN, PermRole.VISITOR, defaults);
        assertFalse(has(visitor, IslandPerm.SETTINGS));
        assertTrue(has(visitor, IslandPerm.INTERACT));
        assertEquals(1, PermPresets.parse(Map.of(), Map.of(PermRole.VISITOR, List.of("settings"))).problems().size());
        assertEquals(1, PermPresets.parse(Map.of(PermRole.MEMBER, List.of("teleport-cats")), Map.of()).problems().size());
    }

    @Test
    void applyingAPresetMakesItActiveAndTwiceWritesNothing() {
        IslandPerms perms = new IslandPerms();
        List<PermPresets.Change> changes = presets.changes(PermPresets.Preset.STRICT, perms, defaults, (role, perm) -> true);
        assertFalse(changes.isEmpty());
        for (PermPresets.Change change : changes) {
            perms.set(change.role(), change.perm(), change.override());
        }
        assertEquals(Optional.of(PermPresets.Preset.STRICT), presets.active(perms, defaults));
        assertEquals(List.of(), presets.changes(PermPresets.Preset.STRICT, perms, defaults, (role, perm) -> true));
        // back to Normal removes every override
        for (PermPresets.Change change : presets.changes(PermPresets.Preset.NORMAL, perms, defaults, (role, perm) -> true)) {
            assertEquals(null, change.override());
            perms.set(change.role(), change.perm(), change.override());
        }
        assertTrue(perms.isEmpty());
    }

    @Test
    void aCustomSetupIsNoPreset() {
        IslandPerms perms = new IslandPerms();
        perms.set(PermRole.MEMBER, IslandPerm.SPAWNERS, true); // part of Open, but nothing else of Open
        assertEquals(Optional.empty(), presets.active(perms, defaults));
    }

    @Test
    void onlyCellsTheViewerMayChangeAreWritten() {
        // An island Admin can't touch the Admin column.
        PermPresets.CellFilter admin = (role, perm) -> MemberRules.canEditCell(Role.ADMIN, role, perm, true);
        PermPresets openAdmin = PermPresets.parse(Map.of(), Map.of(PermRole.ADMIN, List.of("upgrades"), PermRole.MEMBER, List.of("spawners")))
                .presets();
        IslandPerms perms = new IslandPerms();
        perms.set(PermRole.ADMIN, IslandPerm.UPGRADES, false);
        List<PermPresets.Change> changes = openAdmin.changes(PermPresets.Preset.OPEN, perms, defaults, admin);
        assertTrue(changes.stream().noneMatch(c -> c.role() == PermRole.ADMIN));
        assertTrue(changes.stream().anyMatch(c -> c.role() == PermRole.MEMBER && c.perm() == IslandPerm.SPAWNERS));
    }
}

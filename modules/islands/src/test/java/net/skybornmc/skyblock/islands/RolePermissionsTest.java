package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Permission resolution: config defaults per role, per-island overrides, leader always, visitors walk-and-look. */
class RolePermissionsTest {

    private static RolePermissions defaults() {
        IslandsConfig config = new IslandsConfig();
        RolePermissions.Result result = config.rolePermissions();
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        return result.permissions();
    }

    @Test
    void shippedDefaultsAreSane() {
        RolePermissions perms = defaults();
        for (IslandPerm perm : IslandPerm.values()) {
            assertTrue(perms.allows(null, PermRole.LEADER, perm), "leader: " + perm);
            assertTrue(perms.allows(null, PermRole.MOD, perm), "mod: " + perm);
            assertFalse(perms.allows(null, PermRole.VISITOR, perm), "visitors walk and look only: " + perm);
        }
        assertTrue(perms.allows(null, PermRole.MEMBER, IslandPerm.BREAK));
        assertTrue(perms.allows(null, PermRole.MEMBER, IslandPerm.CONTAINERS));
        assertFalse(perms.allows(null, PermRole.MEMBER, IslandPerm.SPAWNERS));
        assertTrue(perms.allows(null, PermRole.COOP, IslandPerm.PLACE));
        assertFalse(perms.allows(null, PermRole.COOP, IslandPerm.FIRE));
        assertFalse(perms.allows(null, PermRole.COOP, IslandPerm.SPAWNERS));
    }

    @Test
    void overridesBeatDefaultsAndResetFollowsDefaultsAgain() {
        RolePermissions perms = defaults();
        IslandPerms island = new IslandPerms();
        assertTrue(island.isEmpty());

        island.set(PermRole.VISITOR, IslandPerm.INTERACT, true);
        island.set(PermRole.MEMBER, IslandPerm.CONTAINERS, false);
        assertTrue(perms.allows(island, PermRole.VISITOR, IslandPerm.INTERACT));
        assertFalse(perms.allows(island, PermRole.VISITOR, IslandPerm.CONTAINERS), "only the overridden one changes");
        assertFalse(perms.allows(island, PermRole.MEMBER, IslandPerm.CONTAINERS));
        assertTrue(perms.allows(island, PermRole.MEMBER, IslandPerm.BREAK));
        assertEquals(Boolean.TRUE, island.override(PermRole.VISITOR, IslandPerm.INTERACT));
        assertEquals(Boolean.FALSE, island.override(PermRole.MEMBER, IslandPerm.CONTAINERS));
        assertNull(island.override(PermRole.MEMBER, IslandPerm.BREAK));

        island.set(PermRole.MEMBER, IslandPerm.CONTAINERS, true); // flip deny -> allow
        assertTrue(perms.allows(island, PermRole.MEMBER, IslandPerm.CONTAINERS));
        island.set(PermRole.MEMBER, IslandPerm.CONTAINERS, null);
        island.set(PermRole.VISITOR, IslandPerm.INTERACT, null);
        assertTrue(island.isEmpty());
        assertFalse(perms.allows(island, PermRole.VISITOR, IslandPerm.INTERACT));
    }

    @Test
    void leaderCanNeverBeRestricted() {
        RolePermissions perms = defaults();
        IslandPerms island = new IslandPerms();
        island.set(PermRole.LEADER, IslandPerm.BREAK, false);
        assertTrue(island.isEmpty(), "leader overrides are ignored");
        assertTrue(perms.allows(island, PermRole.LEADER, IslandPerm.BREAK));
        RolePermissions none = RolePermissions.none();
        assertTrue(none.allows(null, PermRole.LEADER, IslandPerm.SPAWNERS));
        assertFalse(none.allows(null, PermRole.MOD, IslandPerm.BREAK));
    }

    @Test
    void parsingSkipsUnknownKeysAndAcceptsWildcardAndCase() {
        RolePermissions.Result result = RolePermissions.parse(List.of("*"), List.of("BREAK", "armor_stands", "fly"),
                List.of(" place "), List.of("ender-pearls", ""));
        assertEquals(1, result.problems().size());
        assertTrue(result.problems().getFirst().contains("fly"));
        RolePermissions perms = result.permissions();
        assertEquals(IslandPerm.all(), perms.defaultMask(PermRole.MOD));
        assertTrue(perms.defaultAllows(PermRole.MEMBER, IslandPerm.BREAK));
        assertTrue(perms.defaultAllows(PermRole.MEMBER, IslandPerm.ARMOR_STANDS));
        assertFalse(perms.defaultAllows(PermRole.MEMBER, IslandPerm.PLACE));
        assertTrue(perms.defaultAllows(PermRole.COOP, IslandPerm.PLACE));
        assertTrue(perms.defaultAllows(PermRole.VISITOR, IslandPerm.ENDER_PEARLS));
        assertEquals(IslandPerm.ENDER_PEARLS.bit(), perms.defaultMask(PermRole.VISITOR));
    }

    @Test
    void rolesMapToPermissionRoles() {
        assertEquals(PermRole.VISITOR, PermRole.of(null));
        assertEquals(PermRole.LEADER, PermRole.of(Role.LEADER));
        assertEquals(PermRole.MOD, PermRole.of(Role.MOD));
        assertEquals(PermRole.MEMBER, PermRole.of(Role.MEMBER));
        assertEquals(PermRole.COOP, PermRole.of(Role.COOP));
        assertEquals(PermRole.VISITOR, PermRole.byKey("Visitor").orElseThrow());
        assertTrue(PermRole.byKey("owner").isEmpty());
        assertFalse(PermRole.LEADER.editable());
    }

    @Test
    void permissionKeysAreUniqueAndStable() {
        assertEquals(IslandPerm.values().length, java.util.Arrays.stream(IslandPerm.values()).map(IslandPerm::key).distinct().count());
        assertTrue(IslandPerm.values().length < 64, "masks are longs");
        for (IslandPerm perm : IslandPerm.values()) {
            assertEquals(perm, IslandPerm.byKey(perm.key()).orElseThrow());
        }
        assertEquals("armor-stands", IslandPerm.ARMOR_STANDS.key());
        assertEquals("ender-pearls", IslandPerm.ENDER_PEARLS.key());
    }
}
